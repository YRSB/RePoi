package com.nobody.repoi.ui.home

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.repository.ItemRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.ResaleRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.ui.board.HobbyBoardOptions
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.item.list.components.HobbyAdvancedFilterParam
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 首页产品板块单测（issue #159 验收标准）。
 *
 * 覆盖：懒加载（首次切到才请求）、三作用域请求参数（单专区 `zones=<nodeId>` 且无 `homeScope` /
 * 全部聚合省略 `zones` / 关注聚合隐藏板块且零请求）、筛选切换的重置重载、分页去重追加、
 * 切走再切回状态保留、网格 / 列表切换与偏好写回。
 *
 * 请求参数的事实依据：`docs/research/aggregate-api-verification.md` §2.2/§3/§4.3/§5
 * （产品 `zones` 与非空 `homeScope` 互斥；关注聚合需登录态复验，当前隐藏）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedHobbyBoardTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        // 排空仍排在 Main 上的任务（StandardTestDispatcher 的 context 无 Job，cancelChildren 是空操作），
        // 避免续体在 resetMain() 之后恢复并污染下一个测试。
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private class Env(
        val feedMock: MockApi,
        val zoneMock: MockApi,
        val hobbyMock: MockApi,
        val preferences: ViewPreferencesManager,
        val zoneRepository: ZoneRepository,
        val dispatcher: CoroutineDispatcher,
    ) {
        val userSession = FakeUserSession()

        fun viewModel(): HomeFeedViewModel = HomeFeedViewModel(
            HomeRepository(feedMock.api(dispatcher)),
            AlbumRepository(MockApi().api(dispatcher)),
            ResaleRepository(MockApi().api(dispatcher)),
            ItemRepository(hobbyMock.api(dispatcher)),
            zoneRepository,
            preferences,
            userSession
        )
    }

    private suspend fun env(savedTabKey: String = "all"): Env {
        val feedMock = MockApi()
        feedMock.route(
            "api/user-action/list-v3",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}"""
        )
        val zoneMock = MockApi()
        zoneMock.route(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[]}}"""
        )
        val preferences = ViewPreferencesManager(TestDataStore.inMemory())
        preferences.setFeedZoneTabKey(savedTabKey)
        preferences.setFeedSubType("recommend")
        val zoneRepository = ZoneRepository(zoneMock.api(testDispatcher), preferences)
        return Env(feedMock, zoneMock, MockApi(), preferences, zoneRepository, testDispatcher)
    }

    private fun hobbyItemJson(id: Long): String =
        """{"id":$id,"nameCN":"产品$id","cover":"c.jpg"}"""

    private fun hobbyPageJson(ids: List<Long>): String =
        """{"success":true,"code":200,"msg":"ok","data":{"list":[${ids.joinToString(",") { hobbyItemJson(it) }}]}}"""

    /** 按页码路由产品列表（页大小 24；整页才 `hasMore`）。 */
    private fun Env.stubHobbyPages(pages: Map<Int, List<Long>>) {
        hobbyMock.routeWhen(
            predicate = { path, _, _ -> path.contains("api/hobby/query-v2") },
            body = { _, form, _ ->
                val page = form["page"]?.toIntOrNull() ?: 1
                hobbyPageJson(pages[page].orEmpty())
            }
        )
    }

    /** 单专区 `api/zone/info` 路由（首页据此装配二级板块）。 */
    private fun Env.stubZoneInfo(nodeId: Long, tabs: List<String>) {
        val tabsJson = tabs.joinToString(",") { type -> "\"$type\"" }
        zoneMock.routeWhen(
            predicate = { path, _, query -> path.contains("api/zone/info") && query["nodeId"] == nodeId.toString() },
            body = { _, _, _ ->
                """{"success":true,"code":200,"msg":"ok","data":{"zone":{"nodeId":$nodeId,"nameCN":"手办"},"focusState":false,"followCount":0,"tabs":[$tabsJson]}}"""
            }
        )
    }

    private fun Env.stubFocusedZone(nodeId: Long) {
        zoneMock.replaceRoute(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[{"nodeId":$nodeId,"nameCN":"手办"}]}}"""
        )
    }

    @Test
    fun hobbyBoard_isLazyLoadedOnlyAfterSwitch() = runTest(testDispatcher) {
        val env = env()
        env.stubHobbyPages(mapOf(1 to listOf(7L, 8L)))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        // 聚合态二级 Tab 自动包含产品，但未切过去之前零请求
        assertTrue(ZoneBoard.Hobby in viewModel.uiState.value.boards)
        assertEquals(0, env.hobbyMock.requestCount)

        viewModel.selectBoard(ZoneBoard.Hobby)
        advanceUntilIdle()

        assertEquals(1, env.hobbyMock.requestCount)
        val request = env.hobbyMock.takeRequest()
        assertEquals("/api/hobby/query-v2", request.encodedPath)
        val form = request.formEntries()
        assertEquals("1", form["page"])
        assertEquals(HobbyBoardOptions.PAGE_SIZE.toString(), form["pageSize"])
        assertEquals(HobbyBoardOptions.DEFAULT_ORDER, form["order"])
        assertEquals(HobbyBoardOptions.DEFAULT_CATEGORY_ID.toString(), form["category"])
        // 全部作用域：省略 zones，且绝不发送 homeScope
        assertFalse(form.containsKey("zones"))
        assertFalse(form.containsKey("homeScope"))

        val state = viewModel.uiState.value
        assertEquals(listOf(7L, 8L), state.hobbyBoard.items.map { it.actualItemId })
        assertTrue(state.hobbyBoard.isLoaded)
        assertTrue(state.currentBoardContent is ZoneBoardContent.Hobby)
    }

    @Test
    fun hobbySingleZone_sendsNodeIdZonesWithoutHomeScope() = runTest(testDispatcher) {
        val env = env()
        env.stubZoneInfo(nodeId = 100L, tabs = listOf("action", "hobby"))
        env.stubHobbyPages(mapOf(1 to listOf(21L)))
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectCategory(
            FeedCategory(tabKey = "z100", name = "手办", nodeId = 100L, zoneIds = "100")
        )
        advanceUntilIdle()
        assertEquals(listOf(ZoneBoard.Action, ZoneBoard.Hobby), viewModel.uiState.value.boards)

        viewModel.selectBoard(ZoneBoard.Hobby)
        advanceUntilIdle()

        val request = env.hobbyMock.takeRequest()
        val form = request.formEntries()
        assertEquals("100", form["zones"])
        assertFalse(form.containsKey("homeScope"))
        assertEquals("1", form["page"])
    }

    @Test
    fun hobbyFollowScope_hidesBoardAndSendsNoRequest() = runTest(testDispatcher) {
        val env = env()
        env.stubFocusedZone(100L)
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectZoneTab(ZONE_TAB_KEY_FOLLOW)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(ZoneBoard.Hobby in state.boards)
        // 关注态隐藏后，选择产品板块被忽略且不发起请求（避免展示降级成全站的假关注内容）
        viewModel.selectBoard(ZoneBoard.Hobby)
        advanceUntilIdle()
        assertFalse(ZoneBoard.Hobby == viewModel.uiState.value.selectedBoard)
        assertEquals(0, env.hobbyMock.requestCount)
    }

    @Test
    fun emptyFollow_shortCircuitsHobbyWithoutRequest() = runTest(testDispatcher) {
        // 控制器级防御：即使调用方绕开能力矩阵，空关注集合也绝不能发请求
        //（空 zones 会被服务端当作全站，属于比隐藏更危险的假「关注」内容）。
        val env = env()
        val controller = HomeHobbyBoardController(
            repository = ItemRepository(env.hobbyMock.api(testDispatcher)),
            scope = backgroundScope
        )

        controller.loadInitial(ZoneQueryScope.Follow(emptyList()))
        advanceUntilIdle()

        assertEquals(0, env.hobbyMock.requestCount)
        val state = controller.state.value
        assertTrue(state.isLoaded)
        assertTrue(state.items.isEmpty())
        assertFalse(state.hasMore)
        assertFalse(state.isLoading)
    }

    @Test
    fun hobbyOrderChange_resetsListAndReloadsFirstPage() = runTest(testDispatcher) {
        val env = env()
        env.hobbyMock.routeWhen(
            predicate = { path, _, _ -> path.contains("api/hobby/query-v2") },
            body = { _, form, _ ->
                when (form["order"]) {
                    "rating" -> hobbyPageJson(listOf(31L, 32L))
                    else -> hobbyPageJson(listOf(1L, 2L))
                }
            }
        )
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Hobby)
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.hobbyBoard.items.map { it.actualItemId })

        viewModel.selectHobbyOrder("rating")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("rating", state.hobbyBoard.filter.order)
        assertEquals(listOf(31L, 32L), state.hobbyBoard.items.map { it.actualItemId })
        assertEquals(1, state.hobbyBoard.page)
        val form = env.hobbyMock.lastRequest().formEntries()
        assertEquals("rating", form["order"])
        assertEquals("1", form["page"])

        // 非法排序键被忽略，不发请求
        val before = env.hobbyMock.requestCount
        viewModel.selectHobbyOrder("bogus")
        advanceUntilIdle()
        assertEquals(before, env.hobbyMock.requestCount)
    }

    @Test
    fun hobbyYearMonthAndCategory_reloadWithParams() = runTest(testDispatcher) {
        val env = env()
        env.stubHobbyPages(mapOf(1 to listOf(41L)))
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Hobby)
        advanceUntilIdle()

        viewModel.selectHobbyYear(2026)
        advanceUntilIdle()
        var form = env.hobbyMock.lastRequest().formEntries()
        assertEquals("2026", form["releaseYear"])
        assertEquals("1", form["page"])

        viewModel.selectHobbyMonth(3)
        advanceUntilIdle()
        form = env.hobbyMock.lastRequest().formEntries()
        assertEquals("2026", form["releaseYear"])
        assertEquals("3", form["releaseMonth"])

        viewModel.selectHobbyCategory(categoryId = 100, subCategoryId = 1)
        advanceUntilIdle()
        form = env.hobbyMock.lastRequest().formEntries()
        assertEquals("1", form["category"])
        assertEquals("1", form["page"])
        assertEquals(100, viewModel.uiState.value.hobbyBoard.filter.categoryId)
        assertEquals(1, viewModel.uiState.value.hobbyBoard.filter.subCategoryId)
    }

    @Test
    fun hobbyAdvancedFilters_applyAndReset() = runTest(testDispatcher) {
        val env = env()
        env.stubHobbyPages(mapOf(1 to listOf(51L)))
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Hobby)
        advanceUntilIdle()

        viewModel.applyHobbyAdvancedFilters(
            HobbyAdvancedFilterParam(sex = 0, specs = "649223", scale = "7.0", r18 = 18)
        )
        advanceUntilIdle()
        val applied = env.hobbyMock.lastRequest().formEntries()
        assertEquals("0", applied["sex"])
        assertEquals("649223", applied["specs"])
        assertEquals("7.0", applied["scale"])
        assertEquals("18", applied["r18"])
        assertTrue(viewModel.uiState.value.hobbyBoard.hasAdvancedFilters)
        // 应用后弹层关闭，避免列表重载时面板复挂载「回弹」
        assertFalse(viewModel.uiState.value.hobbyBoard.showFilterSheet)

        viewModel.resetHobbyAdvancedFilters()
        advanceUntilIdle()
        val reset = env.hobbyMock.lastRequest().formEntries()
        assertFalse(reset.containsKey("sex"))
        assertFalse(reset.containsKey("specs"))
        assertFalse(reset.containsKey("scale"))
        assertFalse(reset.containsKey("r18"))
        assertFalse(viewModel.uiState.value.hobbyBoard.hasAdvancedFilters)
    }

    @Test
    fun hobbyPagination_appendsDistinctNextPage() = runTest(testDispatcher) {
        val env = env()
        val firstPage = (1L..24L).toList()
        // 第二页含重复项 24，验证宿主按 actualItemId 去重；非整页 → hasMore=false
        env.stubHobbyPages(mapOf(1 to firstPage, 2 to listOf(24L, 25L, 26L)))
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Hobby)
        advanceUntilIdle()

        val page1 = viewModel.uiState.value
        assertEquals(24, page1.hobbyBoard.items.size)
        assertTrue(page1.hobbyBoard.hasMore)
        assertTrue(page1.currentBoardHasMore)

        viewModel.loadMoreSelectedBoard()
        advanceUntilIdle()

        val page2 = viewModel.uiState.value
        assertEquals(26, page2.hobbyBoard.items.size)
        assertEquals(listOf(24L, 25L, 26L), page2.hobbyBoard.items.map { it.actualItemId }.takeLast(3))
        assertEquals(2, page2.hobbyBoard.page)
        assertFalse(page2.hobbyBoard.hasMore)
        assertEquals("2", env.hobbyMock.lastRequest().formEntries()["page"])
    }

    @Test
    fun hobbyPagination_allDuplicatePage_stopsPaging() = runTest(testDispatcher) {
        // 末页整页重复时去重后无新增，应判定到底（与分享板块同一口径）
        val env = env()
        val firstPage = (1L..24L).toList()
        env.stubHobbyPages(mapOf(1 to firstPage, 2 to firstPage))
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Hobby)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hobbyBoard.hasMore)

        viewModel.loadMoreSelectedBoard()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(24, state.hobbyBoard.items.size)
        assertFalse(state.hobbyBoard.hasMore, "整页均为重复项时应判定到底")
    }

    @Test
    fun hobbySwitchAwayAndBack_keepsStateWithoutReload() = runTest(testDispatcher) {
        val env = env()
        env.stubHobbyPages(mapOf(1 to listOf(61L, 62L)))
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Hobby)
        advanceUntilIdle()
        viewModel.selectHobbyOrder("hits")
        advanceUntilIdle()
        val requestCount = env.hobbyMock.requestCount

        viewModel.selectBoard(ZoneBoard.Action)
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Hobby)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ZoneBoard.Hobby, state.selectedBoard)
        // 切走再切回：不重复请求，列表 / 排序保留
        assertEquals(requestCount, env.hobbyMock.requestCount)
        assertEquals("hits", state.hobbyBoard.filter.order)
        assertTrue(state.hobbyBoard.isLoaded)
    }

    @Test
    fun hobbyGridModeToggle_persistsPreference() = runTest(testDispatcher) {
        val env = env()
        env.stubHobbyPages(mapOf(1 to listOf(71L)))
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Hobby)
        advanceUntilIdle()
        // 默认网格模式（与 ViewPreferencesManager 默认值一致）
        assertTrue(viewModel.uiState.value.hobbyBoard.isGridMode)

        viewModel.toggleHobbyViewMode()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hobbyBoard.isGridMode)
        assertFalse(env.preferences.isHobbyListGridMode)
        assertFalse(
            (viewModel.uiState.value.currentBoardContent as ZoneBoardContent.Hobby).isGridMode
        )

        viewModel.toggleHobbyViewMode()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hobbyBoard.isGridMode)
        assertTrue(env.preferences.isHobbyListGridMode)
    }
}
