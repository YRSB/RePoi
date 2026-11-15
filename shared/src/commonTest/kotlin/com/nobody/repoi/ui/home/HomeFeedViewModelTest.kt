package com.nobody.repoi.ui.home

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.repository.MessageRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.data.session.SessionState
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneQueryScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 首页动态 ViewModel 测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `HomeFeedViewModelTest`（逐用例；Mockito 三 mock →
 * 真实 HomeRepository + 真实 ZoneRepository + 真实 ViewPreferencesManager，
 * 全部背后 [MockApi]/[TestDataStore]；
 * `stubFeed(categoryId,subType,lastTime,zoneNodeId,zoneIds)` →
 * [MockApi.routeWhen] 按体匹配（`verify getHomeFeed(...)` →
 * `takeRequest().formEntries()` 断言；`clearInvocations/verifyNoInteractions` →
 * `requestCount` 差值断言）；`focusZonesFlow/settingsFlow` 直接设值 →
 * 真实 ZoneRepository 驱动（`getZoneHomeTabs` 首屏 payload /
 * `toggleFocus` / `updateSettings`）；`MainDispatcherRule` →
 * `Dispatchers.setMain`；JUnit4 → kotlin.test + `runTest`）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        // 先取消仍排在 Main 上的任务，再交还平台 dispatcher；否则 reset 后恢复的续体会抛
        // "Dispatchers.Main was accessed ... after resetMain()"，并污染下一个测试。
        testDispatcher.cancelChildren()
        Dispatchers.resetMain()
    }

    private class Env(
        val feedMock: MockApi,
        val zoneMock: MockApi,
        val accountMock: MockApi,
        val preferences: ViewPreferencesManager,
        val zoneRepository: ZoneRepository,
        val dispatcher: CoroutineDispatcher,
    ) {
        val feedRequests: Int get() = feedMock.requestCount
        val userSession = FakeUserSession("utoken-test")
        val messageRepository = MessageRepository(accountMock.api(dispatcher), userSession)
    }

    private suspend fun env(
        savedTabKey: String = "all",
        savedCategoryId: Int = 0,
        savedSubType: String = "recommend",
        savedBoardKey: String = "",
    ): Env {
        val feedMock = MockApi()
        val zoneMock = MockApi()
        // 专区 tabs 首屏：默认空聚焦 + showAll，供 VM init 静默同步消费
        zoneMock.route(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[]}}"""
        )
        // 内存 DataStore：无真实文件 IO 与后台写协程，避免续体在 resetMain() 之后恢复污染
        // 后续用例（docs/testing.md「DataStore 测试」约定 ViewModel 测试默认用内存实例）。
        val preferences = ViewPreferencesManager(TestDataStore.inMemory())
        preferences.setFeedZoneTabKey(savedTabKey)
        preferences.setFeedCategory(savedCategoryId)
        preferences.setFeedSubType(savedSubType)
        if (savedBoardKey.isNotEmpty()) preferences.setFeedZoneBoard(savedBoardKey)
        val zoneRepository = ZoneRepository(zoneMock.api(testDispatcher), preferences)
        return Env(feedMock, zoneMock, MockApi(), preferences, zoneRepository, testDispatcher)
    }

    private fun Env.viewModel(accounts: Boolean = false): HomeFeedViewModel =
        HomeFeedViewModel(
            HomeRepository(feedMock.api(dispatcher)),
            zoneRepository,
            preferences,
            if (accounts) userSession else FakeUserSession(),
            if (accounts) messageRepository else null
        )

    private fun item(id: Long, date: String? = null): ActionItem {
        return ActionItem(id = id, actionDate = date, actionComment = "动态$id")
    }

    private fun feedJson(items: List<ActionItem>): String {
        val list = items.joinToString(",") { """{"id":${it.id},"actionDate":${if (it.actionDate == null) "null" else "\"${it.actionDate}\""}}""" }
        return """{"success":true,"code":200,"msg":"ok","data":{"list":[$list]}}"""
    }

    private fun Env.stubFeed(
        categoryId: Int = 0,
        subType: String = "recommend",
        lastTime: Long = 0L,
        zoneNodeId: Long? = null,
        zoneIds: String? = null,
        items: List<ActionItem>,
    ) {
        // M7 fix-2：getActionList 为 GET + query——按 query 映射匹配（encodedPath
        // 不含查询串；MockApi.routeWhen 第三入参透传 request.url.parameters）。
        feedMock.routeWhen(
            predicate = { path, _, query ->
                path.contains("api/user-action/list-v3") &&
                    query["categoryId"] == categoryId.toString() &&
                    query["subType"] == subType &&
                    query["lastTime"] == lastTime.toString() &&
                    ((query["zoneNodeId"]?.toLongOrNull() ?: zoneNodeId) == zoneNodeId) &&
                    ((query["zoneIds"] ?: "") == (zoneIds ?: ""))
            },
            body = { _, _, _ -> feedJson(items) }
        )
    }

    private fun Env.stubFeedFailure(
        categoryId: Int = 0,
        subType: String = "recommend",
        lastTime: Long = 0L,
        zoneNodeId: Long? = null,
        zoneIds: String? = null,
        message: String = "网络异常",
    ) {
        feedMock.routeWhen(
            predicate = { path, _, query ->
                path.contains("api/user-action/list-v3") &&
                    query["categoryId"] == categoryId.toString() &&
                    query["subType"] == subType &&
                    query["lastTime"] == lastTime.toString() &&
                    ((query["zoneNodeId"]?.toLongOrNull() ?: zoneNodeId) == zoneNodeId) &&
                    ((query["zoneIds"] ?: "") == (zoneIds ?: ""))
            },
            body = { _, _, _ -> feedMock.failure(500, message) }
        )
    }

    private fun Env.lastFeedForm(): Map<String, String> {
        // M7 fix-2：GET 请求无 POST form——按 queryParameter 逐键读取查询串。
        // 断言对象是「最近一次」请求；takeRequest 是 FIFO，多次请求后只能取到最早那条。
        val recorded = feedMock.lastRequest()
        return listOf("categoryId", "subType", "lastTime", "zoneNodeId", "zoneIds", "pageSize")
            .mapNotNull { key -> recorded.queryParameter(key)?.let { key to it } }
            .toMap()
    }

    /** 首屏专区 tabs 路由：把指定专区放进「我的专区」，供 `savedTabKey=z{nodeId}` 解析到真实分类。 */
    private fun Env.stubFocusedZone(nodeId: Long, name: String = "手办") {
        zoneMock.replaceRoute(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[{"nodeId":$nodeId,"nameCN":"$name"}]}}"""
        )
    }

    /** 单专区 `api/zone/info` 路由（首页据此装配二级板块）。 */
    private fun Env.stubZoneInfo(
        nodeId: Long,
        tabs: List<String>,
        success: Boolean = true
    ) {
        val tabsJson = tabs.joinToString(",") { type -> "\"$type\"" }
        zoneMock.routeWhen(
            predicate = { path, _, query ->
                path.contains("api/zone/info") && query["nodeId"] == nodeId.toString()
            },
            body = { _, _, _ ->
                if (success) {
                    """{"success":true,"code":200,"msg":"ok","data":{"zone":{"nodeId":$nodeId,"nameCN":"手办"},"focusState":false,"followCount":0,"tabs":[$tabsJson]}}"""
                } else {
                    zoneMock.failure(500, "专区信息加载失败")
                }
            }
        )
    }

    @Test
    fun initialLoad_usesSavedFiltersAndParsesCursor() = runTest(testDispatcher) {
        // 老用户落盘的是旧键 all：启动时应归一为 allInfo 再渲染与请求
        val env = env(savedTabKey = "all", savedSubType = "all")
        env.stubFeed(
            categoryId = 0,
            subType = "allInfo",
            zoneNodeId = null,
            zoneIds = null,
            items = listOf(item(1, "1700000000000"), item(2, "1600000000000"))
        )

        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("all", state.selectedCategory.tabKey)
        assertEquals("allInfo", state.selectedSubType)
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
        assertEquals(1600000000000L, state.lastTime)
        assertTrue(state.hasMore)
        assertFalse(state.isInitialLoading)
        assertNull(state.errorMessage)
        val form = env.lastFeedForm()
        assertEquals("0", form["categoryId"])
        assertEquals("allInfo", form["subType"])
        assertEquals("0", form["lastTime"])
    }

    @Test
    fun initialLoad_failureSetsErrorMessage() = runTest(testDispatcher) {
        val env = env()
        env.stubFeedFailure(message = "网络异常")

        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.items.isEmpty())
        assertFalse(state.isInitialLoading)
        assertEquals("网络异常", state.errorMessage)
    }

    @Test
    fun initialLoad_emptyPageClearsHasMore() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())

        val viewModel = env.viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertFalse(viewModel.uiState.value.hasMore)
        assertEquals(0L, viewModel.uiState.value.lastTime)
    }

    @Test
    fun selectCategory_persistsFilterAndReloads() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = listOf(item(1, "100")))
        env.stubFeed(zoneNodeId = 100L, zoneIds = "100", items = listOf(item(2, "200")))

        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectCategory(FeedCategory(tabKey = "z100", name = "手办", nodeId = 100L, zoneIds = "100"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("z100", state.selectedCategory.tabKey)
        assertEquals(100L, state.selectedCategory.nodeId)
        assertEquals(listOf(2L), state.items.map { it.id })
        assertEquals(200L, state.lastTime)
        assertEquals("z100", env.preferences.getInitialFeedZoneTabKey())
        assertEquals(100, env.preferences.getInitialFeedCategoryId())
        val form = env.lastFeedForm()
        assertEquals("100", form["zoneNodeId"])
        assertEquals("100", form["zoneIds"])
    }

    @Test
    fun selectSameCategory_doesNotReload() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = listOf(item(1, "100")))

        val viewModel = env.viewModel()
        advanceUntilIdle()
        val before = env.feedRequests
        viewModel.selectCategory(FeedCategory(tabKey = "all", name = "全部", nodeId = 0L, zoneIds = ""))
        advanceUntilIdle()

        assertEquals(before, env.feedRequests)
    }

    @Test
    fun selectSubType_persistsFilterAndReloads() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = listOf(item(1, "100")))
        env.stubFeed(subType = "confirm", items = listOf(item(2, "200")))

        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectSubType("confirm")
        advanceUntilIdle()

        assertEquals("confirm", viewModel.uiState.value.selectedSubType)
        assertEquals(listOf(2L), viewModel.uiState.value.items.map { it.id })
        assertEquals("confirm", env.preferences.getInitialFeedSubType())
        assertEquals("confirm", env.lastFeedForm()["subType"])
    }

    @Test
    fun selectSubType_legacyAllValueIsNormalizedToAllInfo() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = listOf(item(1, "100")))
        env.stubFeed(subType = "allInfo", items = listOf(item(2, "200")))

        val viewModel = env.viewModel()
        advanceUntilIdle()
        // 旧键 all 无论从何处传入，都不得原样发给接口（服务端会静默回落「推荐」）
        viewModel.selectSubType("all")
        advanceUntilIdle()

        assertEquals("allInfo", viewModel.uiState.value.selectedSubType)
        assertEquals(listOf(2L), viewModel.uiState.value.items.map { it.id })
        assertEquals("allInfo", env.lastFeedForm()["subType"])
        assertEquals("allInfo", env.preferences.getInitialFeedSubType())
    }

    @Test
    fun resetFilters_restoresDefaultsAndPersists() = runTest(testDispatcher) {
        val env = env(savedTabKey = "z100", savedSubType = "all")
        // 首屏 tabs 即带 z100 聚焦（init 静默同步消费）。route() 是追加式、首条命中优先，
        // 二次声明同路径不会生效，必须用 replaceRoute()。
        env.zoneMock.replaceRoute(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[{"nodeId":100,"nameCN":"手办"}]}}"""
        )
        env.stubFeed(subType = "allInfo", items = listOf(item(9, "900")))
        env.stubFeed(subType = "allInfo", zoneNodeId = 100L, zoneIds = "100", items = listOf(item(1, "100")))
        env.stubFeed(items = listOf(item(2, "200")))

        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.resetFilters()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("all", state.selectedCategory.tabKey)
        assertEquals("recommend", state.selectedSubType)
        assertEquals(listOf(2L), state.items.map { it.id })
        assertEquals("all", env.preferences.getInitialFeedZoneTabKey())
        assertEquals("recommend", env.preferences.getInitialFeedSubType())
    }

    @Test
    fun refreshFeed_replacesItemsAndClearsError() = runTest(testDispatcher) {
        val env = env()
        var call = 0
        env.feedMock.routeWhen(
            predicate = { path, _ -> path.contains("api/user-action/list-v3") },
            body = { _, _ ->
                if (call++ == 0) feedJson(listOf(item(1, "100"))) else feedJson(listOf(item(2, "200")))
            }
        )

        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(2L), state.items.map { it.id })
        assertEquals(200L, state.lastTime)
        assertFalse(state.isRefreshing)
        assertNull(state.errorMessage)
    }

    @Test
    fun refreshFeed_failureKeepsExistingItems() = runTest(testDispatcher) {
        val env = env()
        var call = 0
        env.feedMock.routeWhen(
            predicate = { path, _ -> path.contains("api/user-action/list-v3") },
            body = { _, _ ->
                if (call++ == 0) feedJson(listOf(item(1, "100"))) else env.feedMock.failure(500, "刷新失败")
            }
        )

        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1L), state.items.map { it.id })
        assertFalse(state.isRefreshing)
        assertEquals("刷新失败", state.errorMessage)
    }

    @Test
    fun loadNextPage_appendsAndUsesLastTimeCursor() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = listOf(item(1, "100")))
        env.stubFeed(lastTime = 100L, items = listOf(item(2, "200"), item(3, "300")))

        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1L, 2L, 3L), state.items.map { it.id })
        assertEquals(300L, state.lastTime)
        assertFalse(state.isLoadingMore)
        assertTrue(state.hasMore)
        assertEquals("100", env.lastFeedForm()["lastTime"])
    }

    @Test
    fun loadNextPage_deduplicatesOverlappingItems() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = listOf(item(1, "100"), item(2, "90")))
        // 下一页与首页存在重叠条目（2/90），应被去重，仅追加真正的新条目
        env.stubFeed(lastTime = 90L, items = listOf(item(2, "90"), item(3, "80")))

        val viewModel = env.viewModel()
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.items.map { it.id })

        viewModel.loadNextPage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1L, 2L, 3L), state.items.map { it.id })
        assertEquals(80L, state.lastTime)
        assertTrue(state.hasMore)
        assertFalse(state.isLoadingMore)
    }

    @Test
    fun loadNextPage_emptyPageClearsHasMore() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = listOf(item(1, "100")))
        env.stubFeed(lastTime = 100L, items = emptyList())

        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(1L), viewModel.uiState.value.items.map { it.id })
        assertFalse(viewModel.uiState.value.hasMore)
        assertFalse(viewModel.uiState.value.isLoadingMore)
    }

    @Test
    fun loadNextPage_failureKeepsItemsAndSetsLoadMoreError() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = listOf(item(1, "100")))
        env.stubFeedFailure(lastTime = 100L, message = "翻页失败")

        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1L), state.items.map { it.id })
        assertEquals("翻页失败", state.loadMoreError)
        assertFalse(state.isLoadingMore)
    }

    @Test
    fun loadNextPage_whenHasMoreIsFalse_skipsRequest() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())

        val viewModel = env.viewModel()
        advanceUntilIdle()
        val before = env.feedRequests
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(before, env.feedRequests)
    }

    @Test
    fun dynamicCategories_updatesWhenFocusZonesEmit() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = listOf(item(1, "100")))

        val viewModel = env.viewModel()
        advanceUntilIdle()

        // 初始仅有全部与关注
        assertEquals(2, viewModel.uiState.value.categories.size)

        // 模拟用户关注了手办专区 (nodeId = 100)
        env.zoneMock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"focus":true}}""")
        env.zoneRepository.toggleFocus(100L, "手办", "figure.jpg")
        advanceUntilIdle()

        val categories = viewModel.uiState.value.categories
        assertEquals(3, categories.size)
        assertEquals("all", categories[0].tabKey)
        assertEquals("follow", categories[1].tabKey)
        assertEquals("100", categories[1].zoneIds)
        assertEquals("z100", categories[2].tabKey)
        assertEquals("手办", categories[2].name)
        assertEquals(100L, categories[2].nodeId)
    }

    @Test
    fun dynamicCategories_respectsHiddenZoneIds() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())

        val viewModel = env.viewModel()
        advanceUntilIdle()

        env.zoneMock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"focus":true}}""")
        env.zoneRepository.toggleFocus(100L, "手办")
        env.zoneMock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"focus":true}}""")
        env.zoneRepository.toggleFocus(200L, "模型")
        // 隐藏模型专区 (nodeId = 200)
        env.zoneMock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        env.zoneRepository.updateSettings(
            showAll = true, sort = "recent", hiddenZoneIds = listOf(200L), customOrder = emptyList()
        )
        advanceUntilIdle()

        val categories = viewModel.uiState.value.categories
        // 应仅有 全部、关注、手办（模型已被隐藏剔除）
        assertEquals(3, categories.size)
        assertEquals("all", categories[0].tabKey)
        assertEquals("follow", categories[1].tabKey)
        assertEquals("z100", categories[2].tabKey)
    }

    @Test
    fun dynamicCategories_respectsCustomOrder() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())

        val viewModel = env.viewModel()
        advanceUntilIdle()

        env.zoneMock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"focus":true}}""")
        env.zoneRepository.toggleFocus(100L, "手办")
        env.zoneMock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"focus":true}}""")
        env.zoneRepository.toggleFocus(200L, "模型")
        // 设置自定义排序：将 200 排在 100 前面
        env.zoneMock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        env.zoneRepository.updateSettings(
            showAll = false, sort = "custom", hiddenZoneIds = emptyList(), customOrder = listOf(200L, 100L)
        )
        advanceUntilIdle()

        val categories = viewModel.uiState.value.categories
        // 不展示全部；首先为关注，接着为 模型(200)，最后为 手办(100)
        assertEquals(3, categories.size)
        assertEquals("follow", categories[0].tabKey)
        assertEquals("z200", categories[1].tabKey)
        assertEquals("z100", categories[2].tabKey)
    }

    @Test
    fun coldStart_savedFollow_fallsBackToAllAndLoadsAllFeed() = runTest(testDispatcher) {
        // #156 恢复规则 1：上次为「关注」时冷启动回落「全部」，不恢复关注态
        val env = env(savedTabKey = "follow")
        env.stubFeed(items = listOf(item(1, "100")))

        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("all", state.selectedCategory.tabKey)
        assertEquals(listOf(1L), state.items.map { it.id })
        assertFalse(state.isInitialLoading)
        assertNull(env.lastFeedForm()["zoneNodeId"])
        // 运行期「关注」空态短路仍由独立用例覆盖（selectZoneTab_followWithNoFocusZones_*）
    }

    @Test
    fun scrollPosition_saveAndReset() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        val viewModel = env.viewModel()

        viewModel.saveScrollPosition(8, 120)
        viewModel.saveFilterCollapseFraction(0.75f)
        assertEquals(8, viewModel.savedScrollIndex)
        assertEquals(120, viewModel.savedScrollOffset)
        assertEquals(0.75f, viewModel.savedFilterCollapseFraction)

        viewModel.saveFilterCollapseFraction(1.4f)
        assertEquals(1f, viewModel.savedFilterCollapseFraction)

        viewModel.resetScrollPosition()
        assertEquals(0, viewModel.savedScrollIndex)
        assertEquals(0, viewModel.savedScrollOffset)
        assertEquals(0f, viewModel.savedFilterCollapseFraction)
    }

    @Test
    fun accountHeader_updatesAvatarOnInit() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.userSession.setState(
            SessionState.Authenticated(
                userId = 88888L,
                token = "utoken-test",
                profile = UserProfile(id = 88888L, nickname = "手办达人", header = "avatar/abc.jpg")
            )
        )
        val viewModel = env.viewModel(accounts = true)
        advanceUntilIdle()

        assertEquals("avatar/abc.jpg", viewModel.uiState.value.userAvatar)
    }

    @Test
    fun unreadCount_updatesOnInitAndRefresh() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = listOf(item(1, "100")))
        env.accountMock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"map":{"push":{"system":2,"reply":3,"praise":5,"recommend":0,"follownews":4,"commodity":1,"posts_reply":0}}}}""",
            """{"success":true,"code":200,"msg":"ok","data":{"map":{"push":{}}}}"""
        )
        val viewModel = env.viewModel(accounts = true)
        advanceUntilIdle()
        assertEquals(15, viewModel.uiState.value.unreadMessageCount)

        viewModel.refreshFeed()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.unreadMessageCount)
    }

    @Test
    fun loggedOutAccount_leavesAvatarAndUnreadEmpty() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        val session = FakeUserSession()
        val viewModel = HomeFeedViewModel(
            HomeRepository(env.feedMock.api(env.dispatcher)),
            env.zoneRepository,
            env.preferences,
            session,
            MessageRepository(env.accountMock.api(env.dispatcher), session)
        )
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.userAvatar)
        assertEquals(0, viewModel.uiState.value.unreadMessageCount)
    }

    @Test
    fun coldStartBeforeTokenPrewarm_stillLoadsUnreadCount() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.accountMock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"map":{"push":{"system":1,"reply":2,"praise":3,"recommend":0,"follownews":4,"commodity":5,"posts_reply":0}}}}"""
        )
        val session = FakeUserSession(SessionState.Prewarming)
        val viewModel = HomeFeedViewModel(
            HomeRepository(env.feedMock.api(env.dispatcher)),
            env.zoneRepository,
            env.preferences,
            session,
            MessageRepository(env.accountMock.api(env.dispatcher), session)
        )
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.unreadMessageCount)

        // 预热完成：凭据就绪后才触发未读数拉取
        session.setState(SessionState.Authenticated(userId = 1L, token = "utoken-test"))
        advanceUntilIdle()

        assertEquals(15, viewModel.uiState.value.unreadMessageCount)
    }

    @Test
    fun zoneHomeTabsSync_dispatchesImmediatelyAndResyncsAfterPrewarm() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.userSession.setState(SessionState.Prewarming)

        env.viewModel(accounts = true)
        advanceUntilIdle()
        // 不等待预热：首屏立即同步一次（可能暂为空令牌响应）
        assertEquals(1, env.zoneMock.requestCount)

        env.userSession.setState(SessionState.Authenticated(userId = 1L, token = "utoken-test"))
        advanceUntilIdle()
        // 预热完成后补拉一次带凭据的响应，保证关注专区最终一致
        assertEquals(2, env.zoneMock.requestCount)
    }

    @Test
    fun selectZoneTab_allStillSelectableWhenShowAllDisabled() = runTest(testDispatcher) {
        val env = env()
        // 设置隐藏「全部」：分类列表不再产出 all，但切换器「快捷·全部」仍需可选中。
        env.zoneMock.replaceRoute(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":false},"focusZones":[]}}"""
        )
        env.stubFeed(items = listOf(item(1, "100")))

        val viewModel = env.viewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.categories.none { it.tabKey == "all" })

        viewModel.selectZoneTab(ZONE_TAB_KEY_ALL)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("all", state.selectedCategory.tabKey)
        assertEquals(0, state.selectedCategory.id)
        assertEquals(listOf(1L), state.items.map { it.id })
        assertEquals("all", env.preferences.getInitialFeedZoneTabKey())
        assertEquals(0, env.preferences.getInitialFeedCategoryId())
        // 只切 tab，不修改「隐藏全部」设置
        assertFalse(env.zoneRepository.settingsFlow.value.showAll)
    }

    @Test
    fun selectZoneTab_followWithNoFocusZones_showsEmptyWithoutRequest() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = listOf(item(1, "100")))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        val before = env.feedRequests
        viewModel.selectZoneTab(ZONE_TAB_KEY_FOLLOW)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("follow", state.selectedCategory.tabKey)
        assertEquals("-1", state.selectedCategory.zoneIds)
        assertTrue(state.items.isEmpty())
        assertFalse(state.hasMore)
        assertFalse(state.isInitialLoading)
        // 未关注任何专区时切「关注」不发请求
        assertEquals(before, env.feedRequests)
        assertEquals("follow", env.preferences.getInitialFeedZoneTabKey())
    }

    // ── 二级板块装配（#155 B 阶段） ────────────────────────────────────────

    @Test
    fun aggregateBoards_areHomeOrderIntersectedWithImplemented() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ZoneQueryScope.All, state.zoneScope)
        // 聚合态固定顺序 = HOME_ORDER ∩ 首页已接入集合；本阶段只接入动态板块
        assertEquals(ZoneBoard.HOME_ORDER.filter { it in ZoneBoard.HOME_IMPLEMENTED }, state.boards)
        assertEquals(listOf(ZoneBoard.Action), state.boards)
        assertEquals(state.boards.first(), state.selectedBoard)
        // 聚合态不需要 zone/info 请求：只有启动时的 zone/home-tabs
        assertEquals(1, env.zoneMock.requestCount)
    }

    @Test
    fun followBoards_useAggregateListAndParseFocusZoneIds() = runTest(testDispatcher) {
        val env = env()
        env.stubFocusedZone(100L)
        env.stubFeed(items = emptyList())
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectZoneTab(ZONE_TAB_KEY_FOLLOW)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ZoneQueryScope.Follow(listOf(100L)), state.zoneScope)
        assertEquals(ZoneBoard.HOME_ORDER.filter { it in ZoneBoard.HOME_IMPLEMENTED }, state.boards)
        assertEquals(state.boards.first(), state.selectedBoard)
    }

    @Test
    fun runtimeFollowScope_isFollowWithEmptyZoneIds() = runTest(testDispatcher) {
        // 运行期切「关注」（冷启动 saved follow 会回落 all，见 coldStart_savedFollow_*）
        val env = env()
        env.stubFeed(items = emptyList())
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectZoneTab(ZONE_TAB_KEY_FOLLOW)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ZoneQueryScope.Follow(emptyList()), state.zoneScope)
        assertTrue(state.zoneScope.isFollowEmpty)
        assertEquals(ZoneBoard.HOME_ORDER.filter { it in ZoneBoard.HOME_IMPLEMENTED }, state.boards)
    }

    @Test
    fun singleZone_boardsFromServerTabs_keepOnlyImplemented() = runTest(testDispatcher) {
        val env = env()
        env.stubZoneInfo(100L, tabs = listOf("action", "album", "shop_item", "community", "works"))
        env.stubFeed(items = emptyList())
        env.stubFeed(zoneNodeId = 100L, zoneIds = "100", items = emptyList())

        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectCategory(
            FeedCategory(tabKey = "z100", name = "手办", nodeId = 100L, zoneIds = "100")
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ZoneQueryScope.Single(100L), state.zoneScope)
        // 服务端下发 action/album/shop_item/community/works，本阶段只接入动态：其余一律不出现在 Tab
        assertEquals(listOf(ZoneBoard.Action), state.boards)
        assertEquals(ZoneBoard.Action, state.selectedBoard)
        // zone/info 请求带上专区 nodeId
        val infoRequest = env.zoneMock.lastRequest()
        assertTrue(infoRequest.encodedPath.orEmpty().contains("api/zone/info"))
        assertEquals("100", infoRequest.queryParameter("nodeId"))
    }

    @Test
    fun singleZone_doesNotReportZoneVisit() = runTest(testDispatcher) {
        val env = env()
        env.stubZoneInfo(100L, tabs = listOf("action", "album"))
        env.stubFeed(items = emptyList())
        env.stubFeed(zoneNodeId = 100L, zoneIds = "100", items = emptyList())

        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectCategory(
            FeedCategory(tabKey = "z100", name = "手办", nodeId = 100L, zoneIds = "100")
        )
        advanceUntilIdle()

        val paths = buildList {
            repeat(env.zoneMock.requestCount) { add(env.zoneMock.takeRequest().encodedPath.orEmpty()) }
        }
        // 首页只借用 zone/info 的 tabs，不是「进入专区」：不得上报访问记录污染「最近访问」排序
        assertEquals(1, paths.count { it.contains("api/zone/info") })
        assertTrue(paths.none { it.contains("visited") }, "首页不应上报专区访问记录：$paths")
    }

    @Test
    fun singleZone_infoFailure_fallsBackToActionBoard() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.stubFeed(zoneNodeId = 100L, zoneIds = "100", items = emptyList())
        env.stubZoneInfo(100L, tabs = emptyList(), success = false)

        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectCategory(
            FeedCategory(tabKey = "z100", name = "手办", nodeId = 100L, zoneIds = "100")
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ZoneQueryScope.Single(100L), state.zoneScope)
        // 请求失败回退兜底列表，不崩溃、不出现空 Tab 条
        assertEquals(listOf(ZoneBoard.Action), state.boards)
        assertEquals(ZoneBoard.Action, state.selectedBoard)
        assertFalse(state.isInitialLoading)
    }

    @Test
    fun selectCategory_resetsSelectedBoardToFirstBoard() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.stubFeed(zoneNodeId = 100L, zoneIds = "100", items = emptyList())
        env.stubZoneInfo(100L, tabs = listOf("action", "album"))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectCategory(
            FeedCategory(tabKey = "z100", name = "手办", nodeId = 100L, zoneIds = "100")
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(state.boards.first(), state.selectedBoard)
        assertEquals(listOf(ZoneBoard.Action), state.boards)
    }

    @Test
    fun selectBoard_onlySelectsBoardsInCurrentList() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        val viewModel = env.viewModel()
        advanceUntilIdle()

        // 本阶段未接入的板块不在 boards 中，选择被忽略
        viewModel.selectBoard(ZoneBoard.Album)
        assertEquals(ZoneBoard.Action, viewModel.uiState.value.selectedBoard)
        // 重复选择当前板块为 no-op
        viewModel.selectBoard(ZoneBoard.Action)
        assertEquals(ZoneBoard.Action, viewModel.uiState.value.selectedBoard)
    }

    // ── 冷启动三级恢复（#156） ─────────────────────────────────────────

    @Test
    fun coldStart_savedSingleZone_restoresZoneAndBoardsAfterFocusZonesReady() = runTest(testDispatcher) {
        // 保存单专区：必须等关注专区数据到达后再判定可用性，不能被首帧空列表误判回落
        val env = env(savedTabKey = "z100", savedBoardKey = "z100|action")
        env.stubFocusedZone(100L)
        env.stubZoneInfo(100L, tabs = listOf("action", "album"))
        env.stubFeed(items = listOf(item(1, "100")))
        env.stubFeed(zoneNodeId = 100L, zoneIds = "100", items = listOf(item(2, "200")))

        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("z100", state.selectedCategory.tabKey)
        assertEquals(100L, state.selectedCategory.nodeId)
        assertEquals(listOf(ZoneBoard.Action), state.boards)
        assertEquals(ZoneBoard.Action, state.selectedBoard)
        assertEquals(listOf(2L), state.items.map { it.id })
        // 只为恢复后的专区请求内容，未先发「全部」请求（回落误判会留下无 zoneNodeId 的请求）
        assertEquals("100", env.lastFeedForm()["zoneNodeId"])
    }

    @Test
    fun coldStart_savedUnfollowedZone_fallsBackToAllWithoutZoneRequest() = runTest(testDispatcher) {
        // 保存的专区已不在关注集合中 → 回落「全部」，且不为不可用专区发任何请求
        val env = env(savedTabKey = "z999", savedBoardKey = "z999|hobby")
        env.stubFeed(items = listOf(item(1, "100")))

        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("all", state.selectedCategory.tabKey)
        assertEquals(ZoneBoard.Action, state.selectedBoard)
        assertEquals(listOf(1L), state.items.map { it.id })
        assertNull(env.lastFeedForm()["zoneNodeId"])
        // 仅启动时的 zone/home-tabs；没有为 z999 请求 zone/info
        assertEquals(1, env.zoneMock.requestCount)
    }

    @Test
    fun coldStart_savedBoardTypeUnavailable_fallsBackToFirstBoard() = runTest(testDispatcher) {
        // 前缀匹配但当前板块列表没有 hobby（未接入）→ 回落该专区首板块
        val env = env(savedTabKey = "all", savedBoardKey = "all|hobby")
        env.stubFeed(items = emptyList())

        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("all", state.selectedCategory.tabKey)
        assertEquals(state.boards.first(), state.selectedBoard)
        assertEquals(ZoneBoard.Action, state.selectedBoard)
    }

    @Test
    fun coldStart_savedBoardFromOtherZone_fallsBackToFirstBoard() = runTest(testDispatcher) {
        // 二级键前缀与恢复后的一级 tabKey 不匹配 → 取当前专区首板块
        val env = env(savedTabKey = "all", savedBoardKey = "z200|album")
        env.stubFeed(items = emptyList())

        val viewModel = env.viewModel()
        advanceUntilIdle()

        assertEquals("all", viewModel.uiState.value.selectedCategory.tabKey)
        assertEquals(ZoneBoard.Action, viewModel.uiState.value.selectedBoard)
    }

    @Test
    fun selectCategory_persistsFirstBoardKey() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.stubFeed(zoneNodeId = 100L, zoneIds = "100", items = emptyList())
        env.stubZoneInfo(100L, tabs = listOf("action", "album"))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectCategory(FeedCategory("z100", "手办", 100L, "100"))
        advanceUntilIdle()

        // 切专区写回 feed_zone_board = {tabKey}|{首板块 type}
        assertEquals("z100|action", env.preferences.getInitialFeedZoneBoardKey())
        assertEquals("z100", env.preferences.getInitialFeedZoneTabKey())
        assertEquals(ZoneBoard.Action, viewModel.uiState.value.selectedBoard)
    }

    @Test
    fun selectBoard_unavailableBoardDoesNotChangePersistedBoardKey() = runTest(testDispatcher) {
        val env = env(savedBoardKey = "all|action")
        env.stubFeed(items = emptyList())
        val viewModel = env.viewModel()
        advanceUntilIdle()

        // 未接入的板块不在 boards 中：选择被忽略，也不改写已持久化的二级键
        viewModel.selectBoard(ZoneBoard.Album)
        advanceUntilIdle()

        assertEquals(ZoneBoard.Action, viewModel.uiState.value.selectedBoard)
        assertEquals("all|action", env.preferences.getInitialFeedZoneBoardKey())
    }

    @Test
    fun scrollMemory_survivesZoneSwitchAndReturnsToFirstBoard() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.stubFeed(zoneNodeId = 100L, zoneIds = "100", items = emptyList())
        env.stubZoneInfo(100L, tabs = listOf("action"))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        // 「全部·动态」记下滚动位置
        viewModel.saveScrollPosition(7, 90)
        assertEquals(7, viewModel.scrollMemoryFor("all|action").index)

        // 切到单专区：落点为该专区首板块，且不覆盖旧作用域记忆
        viewModel.selectCategory(FeedCategory("z100", "手办", 100L, "100"))
        advanceUntilIdle()
        assertEquals(ZoneBoard.Action, viewModel.uiState.value.selectedBoard)
        assertEquals(HomeBoardScrollMemory(), viewModel.scrollMemoryFor("z100|action"))

        // 切回「全部」：滚动位置切走再切回不丢
        viewModel.selectCategory(FeedCategory("all", "全部"))
        advanceUntilIdle()
        assertEquals(HomeBoardScrollMemory(index = 7, offset = 90), viewModel.scrollMemoryFor("all|action"))
    }

    @Test
    fun scrollMemory_isIsolatedPerScopeAndBoard() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        val viewModel = env.viewModel()
        advanceUntilIdle()

        assertEquals("all|action", viewModel.currentBoardMemoryKey)
        viewModel.saveScrollPosition(8, 120)
        viewModel.saveFilterCollapseFraction(0.75f)
        assertEquals(8, viewModel.savedScrollIndex)
        assertEquals(120, viewModel.savedScrollOffset)
        assertEquals(0.75f, viewModel.savedFilterCollapseFraction)

        // 另一个「作用域 + 板块」键互不覆盖
        viewModel.saveBoardScrollPosition("z100|action", 3, 40)
        assertEquals(HomeBoardScrollMemory(index = 3, offset = 40), viewModel.scrollMemoryFor("z100|action"))
        assertEquals(8, viewModel.scrollMemoryFor("all|action").index)

        viewModel.resetBoardScrollMemory("all|action")
        assertEquals(HomeBoardScrollMemory(), viewModel.scrollMemoryFor("all|action"))
        assertEquals(HomeBoardScrollMemory(index = 3, offset = 40), viewModel.scrollMemoryFor("z100|action"))
        assertEquals(0, viewModel.savedScrollIndex)
    }

    @Test
    fun boardMemoryKey_alignsWithScopeStateKey() {
        assertEquals("all|action", homeBoardMemoryKey(ZoneQueryScope.All, ZoneBoard.Action))
        assertEquals(
            "follow:100,200|action",
            homeBoardMemoryKey(ZoneQueryScope.Follow(listOf(100L, 200L)), ZoneBoard.Action)
        )
        assertEquals("z100|action", homeBoardMemoryKey(ZoneQueryScope.Single(100L), ZoneBoard.Action))
    }

    @Test
    fun feedCategory_derivesQueryScope() {
        assertEquals(ZoneQueryScope.All, FeedCategory(tabKey = "all", name = "全部").toZoneQueryScope())
        assertEquals(
            ZoneQueryScope.Follow(listOf(100L, 200L)),
            FeedCategory(tabKey = "follow", name = "关注", zoneIds = "100,200").toZoneQueryScope()
        )
        // 空关注标记 -1 与非法值不得退化成「假关注集合」（否则会被当成全站）
        assertEquals(
            ZoneQueryScope.Follow(emptyList()),
            FeedCategory(tabKey = "follow", name = "关注", zoneIds = "-1").toZoneQueryScope()
        )
        assertEquals(
            ZoneQueryScope.Follow(emptyList()),
            FeedCategory(tabKey = "follow", name = "关注", zoneIds = "").toZoneQueryScope()
        )
        assertEquals(
            ZoneQueryScope.Single(100L),
            FeedCategory(tabKey = "z100", name = "手办", nodeId = 100L, zoneIds = "100").toZoneQueryScope()
        )
        // 首帧分类尚未回填 nodeId 时从 tabKey 兜底解析
        assertEquals(
            ZoneQueryScope.Single(100L),
            FeedCategory(tabKey = "z100", name = "手办", nodeId = 0L, zoneIds = "").toZoneQueryScope()
        )
    }
}
