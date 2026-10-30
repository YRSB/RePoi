package com.nobody.poishelf.ui.album

import com.nobody.poishelf.data.repository.AlbumRepository
import com.nobody.poishelf.data.repository.MockApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * 相册榜单 ViewModel 测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `AlbumRankListViewModelTest`（逐用例；Mockito `mock(AlbumRepository)` →
 * 真实 AlbumRepository 背后 [MockApi] MockEngine（`thenReturn` 序列 →
 * routeWhen 计数闭包；`java.time.LocalDateTime` → kotlinx-datetime）；
 * `MainDispatcherRule` → `Dispatchers.setMain`；JUnit4 → kotlin.test + `runTest`）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumRankListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private val windowParam = AlbumRankListNavParam(
        title = "本周人气相册",
        releaseStart = "2026-09-07 00:00:00",
        releaseEnd = "2026-09-13 23:59:59"
    )
    private val praiseParam = AlbumRankListNavParam(title = "本月点赞榜", praiseType = "month")

    private fun itemJson(id: Long): String =
        """{"id":$id,"itemId":${1000 + id},"nameCN":"相册$id","cover":"c.jpg","praiseCount":68,"user":{"userId":9,"nickname":"胶友"}}"""

    private fun pageJson(ids: List<Long>, page: Int): String {
        val list = ids.joinToString(",") { itemJson(it) }
        return """{"success":true,"code":200,"msg":"ok","data":{"list":[$list],"page":$page}}"""
    }

    private fun MockApi.stubAlbums(
        releaseStart: String? = null,
        releaseEnd: String? = null,
        bodies: List<String>,
    ) {
        var call = 0
        routeWhen(
            // M7 fix-2：getAlbums 为 POST + form——form 匹配保持；本注释锚定传输以便复核。
            predicate = { path, form ->
                path.contains("api/album") &&
                    (releaseStart == null || form["releaseStart"] == releaseStart) &&
                    (releaseEnd == null || form["releaseEnd"] == releaseEnd)
            },
            body = { _, _ -> bodies.getOrElse(call++) { bodies.last() } }
        )
    }

    private fun MockApi.stubPraiseTop(bodies: List<String>) {
        var call = 0
        routeWhen(
            // M7 fix-2：getPraiseTop 为 GET + query——按 query["type"] 匹配
            //（encodedPath 不含查询串；request.url.parameters 由 MockApi 透传）。
            predicate = { path, _, query ->
                path.contains("api/item/praise/top") &&
                    (query["type"].isNullOrEmpty() || query["type"] == "month")
            },
            body = { _, _, _ -> bodies.getOrElse(call++) { bodies.last() } }
        )
    }

    @Test
    fun windowInitial_loadsPraiseWindow() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.stubAlbums(
            releaseStart = "2026-09-07 00:00:00",
            releaseEnd = "2026-09-13 23:59:59",
            bodies = listOf(pageJson(listOf(1, 2), 1))
        )
        val viewModel = AlbumRankListViewModel(AlbumRepository(mock.api()))
        viewModel.loadInitial(windowParam)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2, state.items.size)
        assertEquals("本周人气相册", state.title)
    }

    @Test
    fun praiseInitial_loadsPraiseTop() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.stubPraiseTop(listOf(pageJson(listOf(3), 1)))
        val viewModel = AlbumRankListViewModel(AlbumRepository(mock.api()))
        viewModel.loadInitial(praiseParam)
        advanceUntilIdle()
        assertEquals(3L, viewModel.uiState.value.items.single().targetAlbumId)
    }

    @Test
    fun loadMore_appendsSecondPage() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.stubPraiseTop(
            listOf(pageJson((1L..10L).toList(), 1), pageJson(listOf(11), 2))
        )
        val viewModel = AlbumRankListViewModel(AlbumRepository(mock.api()))
        viewModel.loadInitial(praiseParam)
        advanceUntilIdle()
        viewModel.loadMore(praiseParam)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(11, state.items.size)
        assertEquals(2, state.page)
        assertFalse(state.hasMore)
    }

    @Test
    fun loadMoreOverlap_ranksFollowServerPositions() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.stubPraiseTop(
            listOf(pageJson((1L..10L).toList(), 1), pageJson(listOf(10, 11), 2))
        )
        val viewModel = AlbumRankListViewModel(AlbumRepository(mock.api()))
        viewModel.loadInitial(praiseParam)
        advanceUntilIdle()
        viewModel.loadMore(praiseParam)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(10, state.ranks[10L])
        assertEquals(12, state.ranks[11L])
    }

    @Test
    fun appendFailure_routesToLoadMoreError() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.stubPraiseTop(
            listOf(pageJson(listOf(1), 1), mock.failure(500, "追加失败"))
        )
        val viewModel = AlbumRankListViewModel(AlbumRepository(mock.api()))
        viewModel.loadInitial(praiseParam)
        advanceUntilIdle()
        viewModel.loadMore(praiseParam)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(null, state.errorMessage)
        assertEquals("追加失败", state.loadMoreError)
        assertEquals(1, state.items.size)
        assertFalse(state.isAppending)
    }

    @Test
    fun failure_showsErrorAndRetryRecovers() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.stubPraiseTop(
            listOf(mock.failure(500, "榜单失败"), pageJson(listOf(4), 1))
        )
        val viewModel = AlbumRankListViewModel(AlbumRepository(mock.api()))
        viewModel.loadInitial(praiseParam)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.errorMessage != null)
        viewModel.retry(praiseParam)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(null, state.errorMessage)
        assertEquals(4L, state.items.single().targetAlbumId)
    }

    @Test
    fun shiftPeriod_previousWeekReloadsWindow() = runTest(testDispatcher) {
        val mock = MockApi()
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val current = periodWindow(RankPeriod.WEEK, 0, now)
        val previous = periodWindow(RankPeriod.WEEK, -1, now)
        val param = windowParam.copy(period = RankPeriod.WEEK)
        mock.stubAlbums(
            releaseStart = current.first, releaseEnd = current.second,
            bodies = listOf(pageJson(listOf(1), 1))
        )
        mock.stubAlbums(
            releaseStart = previous.first, releaseEnd = previous.second,
            bodies = listOf(pageJson(listOf(2), 1))
        )
        val viewModel = AlbumRankListViewModel(AlbumRepository(mock.api()))
        viewModel.loadInitial(param)
        advanceUntilIdle()
        viewModel.shiftPeriod(param, -1)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(-1, state.diff)
        assertEquals("上周", state.periodLabel)
        assertEquals(2L, state.items.single().targetAlbumId)
    }

    @Test
    fun shiftPeriod_cannotGoFuture() = runTest(testDispatcher) {
        val mock = MockApi()
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val current = periodWindow(RankPeriod.WEEK, 0, now)
        val param = windowParam.copy(period = RankPeriod.WEEK)
        mock.stubAlbums(
            releaseStart = current.first, releaseEnd = current.second,
            bodies = listOf(pageJson(listOf(1), 1))
        )
        val viewModel = AlbumRankListViewModel(AlbumRepository(mock.api()))
        viewModel.loadInitial(param)
        advanceUntilIdle()
        viewModel.shiftPeriod(param, 1)
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.diff)
    }

    @Test
    fun periodWindow_monthYearBounds() {
        val now = LocalDateTime(2026, 9, 11, 13, 30, 0)
        assertEquals(
            Pair("2026-09-01 00:00:00", "2026-09-30 23:59:59"),
            periodWindow(RankPeriod.MONTH, 0, now)
        )
        assertEquals(
            Pair("2026-08-01 00:00:00", "2026-08-31 23:59:59"),
            periodWindow(RankPeriod.MONTH, -1, now)
        )
        assertEquals(
            Pair("2026-01-01 00:00:00", "2026-12-31 23:59:59"),
            periodWindow(RankPeriod.YEAR, 0, now)
        )
        assertEquals("本月", periodLabel(RankPeriod.MONTH, 0))
        assertEquals("上月", periodLabel(RankPeriod.MONTH, -1))
        assertEquals("本年", periodLabel(RankPeriod.YEAR, 0))
    }

    @Test
    fun userLevelOf_matchesOfficialFormula() {
        assertEquals(0, userLevelOf(0, 0, 0))
        assertEquals(8, userLevelOf(9000, 7000, 7000))
        assertEquals(displayLevelOf(5, 9000, 7000, 7000), 5)
        assertEquals(displayLevelOf(0, 9000, 7000, 7000), 8)
    }

    @Test
    fun levelTier_bands() {
        assertEquals(0, levelTier(0))
        assertEquals(1, levelTier(5))
        assertEquals(2, levelTier(9))
    }
}
