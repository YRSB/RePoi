package com.nobody.poishelf.ui.album

import com.nobody.poishelf.data.remote.model.AlbumListItem
import com.nobody.poishelf.data.remote.model.AlbumOrder
import com.nobody.poishelf.data.remote.model.AlbumZoneTab
import com.nobody.poishelf.data.repository.AlbumRepository
import com.nobody.poishelf.data.repository.FakeTokenManager
import com.nobody.poishelf.data.repository.MockApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 相册列表 ViewModel 测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `AlbumViewModelTest`（逐用例；Mockito `mock(AlbumRepository/TokenManager)` →
 * 真实 AlbumRepository 背后 [MockApi] MockEngine + [FakeTokenManager]；
 * `stubRecommendPage(page, items, hasMore)` → routeWhen 按表单
 * (order+page) 匹配；`java.time.LocalDateTime` → kotlinx-datetime；
 * `MainDispatcherRule` → `Dispatchers.setMain`；JUnit4 → kotlin.test + `runTest`）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun itemJson(id: Long): String =
        """{"id":$id,"itemId":${1000 + id},"nameCN":"相册$id","cover":"c.jpg","praiseCount":68}"""

    private fun pageJson(ids: List<Long>): String {
        val list = ids.joinToString(",") { itemJson(it) }
        return """{"success":true,"code":200,"msg":"ok","data":{"list":[$list],"page":1}}"""
    }

    private fun MockApi.stubAlbums(order: String = "top", page: Int = 1, bodies: List<String>) {
        var call = 0
        routeWhen(
            predicate = { path, form ->
                path.contains("api/album") && form["order"] == order && form["page"] == page.toString()
            },
            body = { _, _ -> bodies.getOrElse(call++) { bodies.last() } }
        )
    }

    private fun MockApi.stubTabsOk() {
        route(
            "api/album/tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[],"homeZones":[]}}"""
        )
    }

    private fun MockApi.stubPraiseTop(type: String, bodies: List<String>) {
        var call = 0
        routeWhen(
            // M7 fix-2：getPraiseTop 为 GET + query——按 query["type"] 精确匹配。
            predicate = { path, _, query ->
                path.contains("api/item/praise/top") &&
                    (type.isEmpty() || query["type"] == type)
            },
            body = { _, _, _ -> bodies.getOrElse(call++) { bodies.last() } }
        )
    }

    @Test
    fun initialLoad_tabsAndFirstPage() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.stubTabsOk()
        mock.stubAlbums(order = "top", page = 1, bodies = listOf(pageJson(listOf(1, 2))))
        val viewModel = AlbumViewModel(AlbumRepository(mock.api()), FakeTokenManager("tok"))
        viewModel.loadInitial()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isTabsLoading)
        assertFalse(state.isLoading)
        assertEquals(2, state.items.size)
        assertEquals(1, state.page)
        assertFalse(state.hasMore)
    }

    @Test
    fun selectOrder_resetsPageWithNewOrder() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.stubTabsOk()
        mock.stubAlbums(order = "top", page = 1, bodies = listOf(pageJson(listOf(1))))
        mock.stubAlbums(order = "updTime", page = 1, bodies = listOf(pageJson(listOf(9))))
        val viewModel = AlbumViewModel(AlbumRepository(mock.api()), FakeTokenManager("tok"))
        viewModel.loadInitial()
        advanceUntilIdle()
        viewModel.selectOrder(AlbumOrder.Latest)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(AlbumOrder.Latest, state.order)
        assertEquals(1, state.page)
        assertEquals(9L, state.items.single().targetAlbumId)
    }

    @Test
    fun loadMore_appendsAndDedups() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.stubTabsOk()
        // 满页（24 条）触发 hasMore=true，第二页去重追加
        val page1 = (1L..24L).toList()
        mock.stubAlbums(order = "top", page = 1, bodies = listOf(pageJson(page1)))
        mock.stubAlbums(order = "top", page = 2, bodies = listOf(pageJson(listOf(24L, 25L, 26L))))
        val viewModel = AlbumViewModel(AlbumRepository(mock.api()), FakeTokenManager("tok"))
        viewModel.loadInitial()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals((1L..26L).toList(), state.items.map { it.targetAlbumId })
        assertFalse(state.hasMore)
        assertFalse(state.isAppending)
    }

    @Test
    fun followZoneWithoutToken_showsLoginGuide() = runTest(testDispatcher) {
        val mock = MockApi()
        val viewModel = AlbumViewModel(AlbumRepository(mock.api()), FakeTokenManager(""))
        viewModel.selectZone(AlbumZoneTab.FOLLOW)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.loginRequired)
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun rankingSingleFailure_hidesFailedSectionOnly() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.stubTabsOk()
        mock.stubAlbums(order = "top", page = 1, bodies = listOf(pageJson(emptyList())))
        mock.stubAlbums(order = "praise", bodies = listOf(pageJson(listOf(5))))
        mock.stubPraiseTop("", listOf(pageJson(listOf(6))))
        mock.stubPraiseTop("week", listOf(pageJson(listOf(7))))
        mock.stubPraiseTop("month", listOf(mock.failure(500, "榜单失败")))
        val viewModel = AlbumViewModel(AlbumRepository(mock.api()), FakeTokenManager("tok"))
        viewModel.loadInitial()
        advanceUntilIdle()
        viewModel.enterRanking()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.showRanking)
        assertFalse(state.isSectionsLoading)
        assertEquals(5, state.sections.size)
        assertTrue(state.sections.none { it.title == "本月点赞榜" })
    }

    @Test
    fun feedUnauthorized_flagsLoginRequired() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.stubTabsOk()
        mock.stubAlbums(order = "top", page = 1, bodies = listOf(mock.failure(4001, "登录过期")))
        val viewModel = AlbumViewModel(AlbumRepository(mock.api()), FakeTokenManager("tok"))
        viewModel.loadInitial()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.loginRequired)
        assertFalse(state.isLoading)
    }

    @Test
    fun tabsFailure_fallsBackToAllAndLoadsFeed() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/album/tabs", mock.failure(500, "分区失败"))
        mock.stubAlbums(order = "top", page = 1, bodies = listOf(pageJson(listOf(1))))
        val viewModel = AlbumViewModel(AlbumRepository(mock.api()), FakeTokenManager("tok"))
        viewModel.loadInitial()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(listOf(AlbumZoneTab.ALL), state.tabs)
        assertEquals(1, state.items.size)
    }

    @Test
    fun unauthorizedThenSuccess_clearsLoginRequired() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.stubTabsOk()
        mock.stubAlbums(
            order = "top", page = 1,
            bodies = listOf(mock.failure(4001, "登录过期"), pageJson(listOf(2)))
        )
        val viewModel = AlbumViewModel(AlbumRepository(mock.api()), FakeTokenManager("tok"))
        viewModel.loadInitial()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.loginRequired)
        viewModel.refresh()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(2L, state.items.single().targetAlbumId)
        assertFalse(state.loginRequired)
    }

    @Test
    fun rankWindows_monthWeekYearBounds() {
        val windows = rankWindows(LocalDateTime(2026, 9, 11, 13, 30, 0))
        assertEquals(Pair("2026-09-01 00:00:00", "2026-09-30 23:59:59"), windows.month)
        assertEquals(Pair("2026-09-07 00:00:00", "2026-09-13 23:59:59"), windows.week)
        assertEquals(Pair("2026-01-01 00:00:00", "2026-12-31 23:59:59"), windows.year)
    }

    @Test
    fun selectZone_resetsSavedScrollPosition() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.stubTabsOk()
        mock.stubAlbums(order = "top", page = 1, bodies = listOf(pageJson(listOf(1))))
        val viewModel = AlbumViewModel(AlbumRepository(mock.api()), FakeTokenManager("tok"))
        viewModel.loadInitial()
        advanceUntilIdle()
        viewModel.saveScrollPosition(12, 34)
        viewModel.selectZone(AlbumZoneTab.FOLLOW)
        advanceUntilIdle()
        assertEquals(0, viewModel.savedScrollIndex)
        assertEquals(0, viewModel.savedScrollOffset)
    }

    private fun item(id: Long): AlbumListItem =
        AlbumListItem(id = id, itemId = 1000L + id, nameCN = "相册$id")
}
