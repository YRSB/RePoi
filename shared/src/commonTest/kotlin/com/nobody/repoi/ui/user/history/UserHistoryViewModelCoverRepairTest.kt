package com.nobody.repoi.ui.user.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.repository.AlbumDetailRepository
import com.nobody.repoi.data.repository.CoverFreshnessRepository
import com.nobody.repoi.data.repository.HobbyDetailRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.UserHistoryRepository
import com.nobody.repoi.data.session.FakeUserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 浏览历史封面按条修复测试。
 *
 * 场景：历史接口返回的 `cover` 是写入快照（可能已被服务端重新落盘 → 404），
 * 封面加载失败后应回详情接口取当前封面，并且**只替换失败的那一行**。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserHistoryViewModelCoverRepairTest {

    private val testDispatcher = StandardTestDispatcher()

    private val oldCover = "2020/01/stale_hobby.jpg"
    private val freshCover = "2026/11/fresh_hobby.jpg"

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private class Env(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        scope: CoroutineScope,
        preferences: DataStore<Preferences> = TestDataStore.coverFreshnessPrefs(),
    ) {
        val historyMock = MockApi()
        val detailMock = MockApi()
        val albumMock = MockApi()
        private val session = FakeUserSession("utoken-test")

        val coverFreshnessRepository = CoverFreshnessRepository(
            HobbyDetailRepository(detailMock.api(dispatcher)),
            AlbumDetailRepository(albumMock.api(dispatcher)),
            preferences,
            scope,
        )

        /** 冷启动预热需真实 DataStore I/O；在推平虚拟调度器前显式等待，避免测试与读盘竞态。 */
        suspend fun warmUp() = coverFreshnessRepository.ensureWarmed()

        val viewModel = UserHistoryViewModel(
            UserHistoryRepository(historyMock.api(dispatcher), session, coverFreshnessRepository),
            coverFreshnessRepository,
        )
    }

    private data class HistoryRow(
        val rowId: Long,
        val itemId: Long,
        val cover: String,
        val itemType: String = UserHistoryCategory.HOBBY.itemType,
        val nodeId: Long = 456L,
    )

    private fun Env.stubHistory(
        rowId: Long = 123L,
        itemId: Long = 59570L,
        cover: String = oldCover,
    ) = stubHistoryRows(HistoryRow(rowId = rowId, itemId = itemId, cover = cover))

    /** 按 `itemType` 分组注册历史列表桩：手办与相册 loader 共用路径，仅表单字段不同。 */
    private fun Env.stubHistoryRows(vararg rows: HistoryRow) {
        rows.groupBy { it.itemType }.forEach { (itemType, group) ->
            val readList = group.joinToString(",") { row ->
                """{"id":${row.rowId},"nodeId":${row.nodeId},"itemId":${row.itemId},"itemType":"$itemType","title":"条目${row.rowId}","cover":"${row.cover}"}"""
            }
            historyMock.routeWhen(
                predicate = { _, form -> form["itemType"] == itemType },
                body = { _, _ -> historyMock.ok("""{"readList":[$readList]}""") },
            )
        }
    }

    private fun Env.stubHobbyDetail(cover: String?) {
        val coverJson = cover?.let { "\"$it\"" } ?: "null"
        detailMock.route(
            "api/item/get",
            detailMock.ok("""{"itemData":{"id":456,"nodeId":456,"itemId":59570,"cover":$coverJson}}""")
        )
    }

    private fun Env.stubAlbumDetail(cover: String?) {
        val coverJson = cover?.let { "\"$it\"" } ?: "null"
        albumMock.route(
            "api/album/detail",
            albumMock.ok("""{"album":{"id":8888,"cover":$coverJson}}""")
        )
    }

    @Test
    fun coverLoadFailed_replacesOnlyFailedRowWithFreshCover() = runTest(testDispatcher) {
        val env = Env(testDispatcher, backgroundScope)
        env.stubHistoryRows(
            HistoryRow(rowId = 123L, itemId = 59570L, cover = oldCover),
            HistoryRow(rowId = 124L, itemId = 59571L, cover = oldCover),
        )
        env.stubHobbyDetail(freshCover)
        env.warmUp()
        advanceUntilIdle()
        assertEquals(2, env.viewModel.uiState.value.hobbyState.items.size)

        env.viewModel.onCoverLoadFailed(rowId = 123L, detailId = 59570L, category = UserHistoryCategory.HOBBY)
        advanceUntilIdle()

        val items = env.viewModel.uiState.value.hobbyState.items.associateBy { it.id }
        assertEquals(freshCover, items.getValue(123L).cover)
        assertEquals(oldCover, items.getValue(124L).cover, "只应替换失败的那一行")
        assertEquals(1, env.detailMock.requestCount)
    }

    @Test
    fun coverLoadFailed_album_replacesAlbumRowWithFreshCover() = runTest(testDispatcher) {
        val env = Env(testDispatcher, backgroundScope)
        val staleAlbumCover = "2020/01/stale_album.jpg"
        val freshAlbumCover = "2026/11/fresh_album.jpg"
        env.stubHistoryRows(
            HistoryRow(
                rowId = 321L,
                itemId = 0L,
                cover = staleAlbumCover,
                itemType = UserHistoryCategory.ALBUM.itemType,
                nodeId = 8888L,
            )
        )
        env.stubAlbumDetail(freshAlbumCover)
        env.warmUp()
        env.viewModel.selectCategory(UserHistoryCategory.ALBUM)
        advanceUntilIdle()
        assertEquals(staleAlbumCover, env.viewModel.uiState.value.albumState.items.single().cover)

        env.viewModel.onCoverLoadFailed(rowId = 321L, detailId = 8888L, category = UserHistoryCategory.ALBUM)
        advanceUntilIdle()

        assertEquals(freshAlbumCover, env.viewModel.uiState.value.albumState.items.single().cover)
        assertEquals(1, env.albumMock.requestCount)
        assertEquals(0, env.detailMock.requestCount, "相册修复不应打手办详情接口")
    }

    @Test
    fun coverLoadFailed_successDoesNotConsumeAttemptBudget() = runTest(testDispatcher) {
        val env = Env(testDispatcher, backgroundScope)
        env.stubHistory()
        env.stubHobbyDetail(freshCover)
        env.warmUp()
        advanceUntilIdle()

        repeat(3) {
            env.viewModel.onCoverLoadFailed(rowId = 123L, detailId = 59570L, category = UserHistoryCategory.HOBBY)
            advanceUntilIdle()
            assertEquals(freshCover, env.viewModel.uiState.value.hobbyState.items.single().cover)

            // 清空缓存模拟「服务端快照仍是旧路径且本地无沉淀」（冷启动/登出后）：
            // 成功若占用失败额度，第 3 次修复就会被永久放弃
            env.coverFreshnessRepository.clear()
            env.viewModel.refresh(UserHistoryCategory.HOBBY)
            advanceUntilIdle()
            assertEquals(oldCover, env.viewModel.uiState.value.hobbyState.items.single().cover)
        }

        env.viewModel.onCoverLoadFailed(rowId = 123L, detailId = 59570L, category = UserHistoryCategory.HOBBY)
        advanceUntilIdle()
        assertEquals(freshCover, env.viewModel.uiState.value.hobbyState.items.single().cover)
        assertEquals(4, env.detailMock.requestCount, "每次成功都应重置失败额度并继续回源修复")
    }

    @Test
    fun coverLoadFailed_blankFreshCover_keepsOriginalPath() = runTest(testDispatcher) {
        val env = Env(testDispatcher, backgroundScope)
        env.stubHistory()
        env.stubHobbyDetail(cover = null)
        env.warmUp()
        advanceUntilIdle()

        env.viewModel.onCoverLoadFailed(rowId = 123L, detailId = 59570L, category = UserHistoryCategory.HOBBY)
        advanceUntilIdle()

        assertEquals(oldCover, env.viewModel.uiState.value.hobbyState.items.single().cover)
    }

    @Test
    fun coverLoadFailed_repeatedFailures_stopAfterAttemptCap() = runTest(testDispatcher) {
        val env = Env(testDispatcher, backgroundScope)
        env.stubHistory()
        env.detailMock.route("api/item/get", env.detailMock.failure(500, "Network error"))
        env.warmUp()
        advanceUntilIdle()

        // 逐次调用之间必须推平调度器：并发失败会被 `CoverFreshnessRepository` 的单飞合并成一个请求，
        // 那样测到的是去重而不是「次数上限」
        repeat(5) {
            env.viewModel.onCoverLoadFailed(rowId = 123L, detailId = 59570L, category = UserHistoryCategory.HOBBY)
            advanceUntilIdle()
        }

        // MAX_COVER_REPAIR_ATTEMPTS = 2：超过上限后不再打详情接口
        assertEquals(2, env.detailMock.requestCount)
    }

    @Test
    fun coverLoadFailed_invalidDetailId_doesNothing() = runTest(testDispatcher) {
        val env = Env(testDispatcher, backgroundScope)
        env.stubHistory()
        env.warmUp()
        advanceUntilIdle()

        env.viewModel.onCoverLoadFailed(rowId = 123L, detailId = 0L, category = UserHistoryCategory.HOBBY)
        advanceUntilIdle()

        assertEquals(0, env.detailMock.requestCount)
    }

    @Test
    fun coverLoadFailed_penetratesSunkCoverAndRepaintsRow() = runTest(testDispatcher) {
        val env = Env(testDispatcher, backgroundScope)
        env.stubHistory()
        env.warmUp()
        advanceUntilIdle()

        // 详情页曾沉淀过封面；但它随后也在服务端失效（404），此时必须穿透该正缓存重新回源
        env.coverFreshnessRepository.rememberFreshCover(
            id = 59570L,
            type = CoverFreshnessRepository.EntityType.HOBBY,
            cover = "2026/10/previous_hobby.jpg",
        )
        val newestCover = "2026/12/newest_hobby.jpg"
        env.stubHobbyDetail(newestCover)
        env.viewModel.onCoverLoadFailed(rowId = 123L, detailId = 59570L, category = UserHistoryCategory.HOBBY)
        advanceUntilIdle()

        assertEquals(newestCover, env.viewModel.uiState.value.hobbyState.items.single().cover)
        assertEquals(1, env.detailMock.requestCount, "404 必须穿透已沉淀的正缓存重新回源")
    }

    /**
     * 工单 #117 AC：404 强制穿透成功后就地重绘，并同步落盘，冷启动仍为新封面。
     */
    @Test
    fun coverLoadFailed_forceRefresh_persistsNewCoverForColdStart() = runTest(testDispatcher) {
        val prefs = TestDataStore.coverFreshnessPrefs()
        val seeder = CoverFreshnessRepository(
            HobbyDetailRepository(MockApi().api(testDispatcher)),
            AlbumDetailRepository(MockApi().api(testDispatcher)),
            prefs,
            backgroundScope,
        )
        seeder.ensureWarmed()
        seeder.rememberFreshCover(
            id = 59570L,
            type = CoverFreshnessRepository.EntityType.HOBBY,
            cover = oldCover,
        )
        advanceTimeBy(501)
        advanceUntilIdle()
        prefs.awaitPendingWrites()

        val env = Env(testDispatcher, backgroundScope, prefs)
        env.stubHistoryRows(
            HistoryRow(rowId = 123L, itemId = 59570L, cover = oldCover),
            HistoryRow(rowId = 124L, itemId = 59571L, cover = oldCover),
        )
        env.stubHobbyDetail(freshCover)
        env.warmUp()
        advanceUntilIdle()
        assertEquals(oldCover, env.viewModel.uiState.value.hobbyState.items.first { it.id == 123L }.cover)

        env.viewModel.onCoverLoadFailed(rowId = 123L, detailId = 59570L, category = UserHistoryCategory.HOBBY)
        advanceUntilIdle()

        val items = env.viewModel.uiState.value.hobbyState.items.associateBy { it.id }
        assertEquals(freshCover, items.getValue(123L).cover, "失败行应就地重绘")
        assertEquals(oldCover, items.getValue(124L).cover, "未失败行不得被替换")
        assertEquals(1, env.detailMock.requestCount, "已缓存旧地址 404 时必须穿透正缓存回源")

        advanceTimeBy(501)
        advanceUntilIdle()
        prefs.awaitPendingWrites()

        val coldStart = CoverFreshnessRepository(
            HobbyDetailRepository(MockApi().api(testDispatcher)),
            AlbumDetailRepository(MockApi().api(testDispatcher)),
            prefs,
            backgroundScope,
        )
        coldStart.ensureWarmed()
        assertEquals(
            freshCover,
            coldStart.getCachedCover(59570L, CoverFreshnessRepository.EntityType.HOBBY),
            "自愈结果应经防抖落盘，重建仓储后仍为新封面",
        )
        assertNull(
            coldStart.getCachedCover(59571L, CoverFreshnessRepository.EntityType.HOBBY),
            "未参与自愈的条目不应被写入持久化缓存",
        )
    }

    /**
     * 工单 #116 AC：冷启动预置持久化缓存后，分页装载直接呈现新封面，详情接口零请求。
     */
    @Test
    fun coldStart_persistedCache_rendersFreshCoverWithoutDetailRequest() = runTest(testDispatcher) {
        val prefs = TestDataStore.coverFreshnessPrefs()
        val seeder = CoverFreshnessRepository(
            HobbyDetailRepository(MockApi().api(testDispatcher)),
            AlbumDetailRepository(MockApi().api(testDispatcher)),
            prefs,
            backgroundScope,
        )
        seeder.ensureWarmed()
        seeder.rememberFreshCover(
            id = 59570L,
            type = CoverFreshnessRepository.EntityType.HOBBY,
            cover = freshCover,
        )
        advanceTimeBy(501)
        advanceUntilIdle()
        prefs.awaitPendingWrites()

        val env = Env(testDispatcher, backgroundScope, prefs)
        env.stubHistory()
        env.warmUp()
        advanceUntilIdle()

        val items = env.viewModel.uiState.value.hobbyState.items
        assertEquals(freshCover, items.single().cover, "冷启动应从持久化缓存前置重写列表封面")
        assertEquals(0, env.detailMock.requestCount, "前置重写命中后不应发出详情请求")
    }

    /** 空 `edit` 排在所有在途写入之后，作为 DataStore 落盘的同步屏障。 */
    private suspend fun DataStore<Preferences>.awaitPendingWrites() {
        edit { }
    }
}
