package com.nobody.repoi.ui.user.history

import com.nobody.repoi.data.repository.AlbumDetailRepository
import com.nobody.repoi.data.repository.CoverFreshnessRepository
import com.nobody.repoi.data.repository.HobbyDetailRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.UserHistoryRepository
import com.nobody.repoi.data.session.FakeUserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private class Env(dispatcher: kotlinx.coroutines.CoroutineDispatcher) {
        val historyMock = MockApi()
        val detailMock = MockApi()
        private val session = FakeUserSession("utoken-test")

        val coverFreshnessRepository = CoverFreshnessRepository(
            HobbyDetailRepository(detailMock.api(dispatcher)),
            AlbumDetailRepository(detailMock.api(dispatcher)),
        )

        val viewModel = UserHistoryViewModel(
            UserHistoryRepository(historyMock.api(dispatcher), session),
            coverFreshnessRepository,
        )
    }

    private fun Env.stubHistory(rowId: Long = 123L, itemId: Long = 59570L, cover: String = oldCover) {
        historyMock.route(
            "api/user/read/item/get",
            historyMock.ok(
                """{"readList":[{"id":$rowId,"nodeId":456,"itemId":$itemId,"itemType":"hobby","title":"手办A","cover":"$cover"}]}"""
            )
        )
    }

    private fun Env.stubHobbyDetail(cover: String?) {
        val coverJson = cover?.let { "\"$it\"" } ?: "null"
        detailMock.route(
            "api/item/get",
            detailMock.ok("""{"itemData":{"id":456,"nodeId":456,"itemId":59570,"cover":$coverJson}}""")
        )
    }

    @Test
    fun coverLoadFailed_replacesOnlyFailedRowWithFreshCover() = runTest(testDispatcher) {
        val env = Env(testDispatcher)
        env.stubHistory()
        env.stubHobbyDetail(freshCover)
        advanceUntilIdle()
        assertEquals(oldCover, env.viewModel.uiState.value.hobbyState.items.single().cover)

        env.viewModel.onCoverLoadFailed(rowId = 123L, detailId = 59570L, category = UserHistoryCategory.HOBBY)
        advanceUntilIdle()

        assertEquals(freshCover, env.viewModel.uiState.value.hobbyState.items.single().cover)
        assertEquals(1, env.detailMock.requestCount)
    }

    @Test
    fun coverLoadFailed_blankFreshCover_keepsOriginalPath() = runTest(testDispatcher) {
        val env = Env(testDispatcher)
        env.stubHistory()
        env.stubHobbyDetail(cover = null)
        advanceUntilIdle()

        env.viewModel.onCoverLoadFailed(rowId = 123L, detailId = 59570L, category = UserHistoryCategory.HOBBY)
        advanceUntilIdle()

        assertEquals(oldCover, env.viewModel.uiState.value.hobbyState.items.single().cover)
    }

    @Test
    fun coverLoadFailed_repeatedFailures_stopAfterAttemptCap() = runTest(testDispatcher) {
        val env = Env(testDispatcher)
        env.stubHistory()
        env.detailMock.route("api/item/get", env.detailMock.failure(500, "Network error"))
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
        val env = Env(testDispatcher)
        env.stubHistory()
        advanceUntilIdle()

        env.viewModel.onCoverLoadFailed(rowId = 123L, detailId = 0L, category = UserHistoryCategory.HOBBY)
        advanceUntilIdle()

        assertEquals(0, env.detailMock.requestCount)
    }

    @Test
    fun coverLoadFailed_afterDetailPageSink_hitsCacheWithoutRequest() = runTest(testDispatcher) {
        val env = Env(testDispatcher)
        env.stubHistory()
        advanceUntilIdle()

        env.coverFreshnessRepository.rememberFreshCover(
            id = 59570L,
            type = CoverFreshnessRepository.EntityType.HOBBY,
            cover = freshCover,
        )
        env.viewModel.onCoverLoadFailed(rowId = 123L, detailId = 59570L, category = UserHistoryCategory.HOBBY)
        advanceUntilIdle()

        assertEquals(freshCover, env.viewModel.uiState.value.hobbyState.items.single().cover)
        assertEquals(0, env.detailMock.requestCount, "详情页已沉淀的封面不应再触发修复请求")
    }
}
