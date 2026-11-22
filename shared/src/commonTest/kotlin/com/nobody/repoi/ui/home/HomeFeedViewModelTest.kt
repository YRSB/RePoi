package com.nobody.repoi.ui.home

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.platform.DateTimeFormats
import com.nobody.repoi.ui.board.itemCount
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.repository.ItemRepository
import com.nobody.repoi.data.repository.MessageRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.ResaleRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.data.session.SessionState
import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardCapability
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.board.session.ZoneBoardIntent
import com.nobody.repoi.ui.board.session.ZoneBoardSession
import com.nobody.repoi.ui.board.session.ZoneBoardSessionImpl
import com.nobody.repoi.ui.board.session.ZoneBoardSnapshot
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {

        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private class Env(
        val feedMock: MockApi,
        val zoneMock: MockApi,
        val albumMock: MockApi,
        val resaleMock: MockApi,
        val hobbyMock: MockApi,
        val accountMock: MockApi,
        val preferences: ViewPreferencesManager,
        val zoneRepository: ZoneRepository,
        val dispatcher: CoroutineDispatcher,
    ) {
        val feedRequests: Int get() = feedMock.requestCount

        var postsRequests: Int = 0
        val userSession = FakeUserSession("utoken-test")
        val messageRepository = MessageRepository(accountMock.api(dispatcher))
        val homeRepository = HomeRepository(feedMock.api(dispatcher))
        val albumRepository = AlbumRepository(albumMock.api(dispatcher))
        val resaleRepository = ResaleRepository(resaleMock.api(dispatcher))
        val itemRepository = ItemRepository(hobbyMock.api(dispatcher))

        val sessionFactory: ZoneBoardSession.Factory = ZoneBoardSessionImpl.Factory(
            homeRepository = homeRepository,
            albumRepository = albumRepository,
            zoneRepository = zoneRepository,
            itemRepository = itemRepository,
            resaleRepository = resaleRepository,
            viewPreferencesManager = preferences
        )
    }

    private suspend fun env(
        savedTabKey: String = "all",
        savedCategoryId: Int = 0,
        savedSubType: String = "recommend",
        savedBoardKey: String = "",
    ): Env {
        val feedMock = MockApi()
        val zoneMock = MockApi()

        zoneMock.route(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[]}}"""
        )

        val preferences = ViewPreferencesManager(TestDataStore.inMemory())
        preferences.setFeedZoneTabKey(savedTabKey)
        preferences.setFeedCategory(savedCategoryId)
        preferences.setFeedSubType(savedSubType)
        if (savedBoardKey.isNotEmpty()) preferences.setFeedZoneBoard(savedBoardKey)

        val zoneRepository = ZoneRepository(
            api = zoneMock.api(testDispatcher),
            viewPreferencesManager = preferences,
            visitReportScope = CoroutineScope(testDispatcher + SupervisorJob())
        )

        val albumMock = MockApi()
        albumMock.route(
            "api/album/list",
            """{"success":true,"code":200,"msg":"ok","data":{"page":1,"list":[]}}"""
        )
        albumMock.route(
            "api/zone/categories",
            """{"success":true,"code":200,"msg":"ok","data":{"visible":true,"categories":[]}}"""
        )

        val resaleMock = MockApi()
        resaleMock.route(
            "api/resale/search",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}"""
        )

        val hobbyMock = MockApi()
        hobbyMock.route(
            "api/hobby/query-v2",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}"""
        )
        return Env(
            feedMock, zoneMock, albumMock, resaleMock, hobbyMock,
            MockApi(), preferences, zoneRepository, testDispatcher
        )
    }

    private fun Env.viewModel(
        accounts: Boolean = false,
        customSession: UserSession? = null,
    ): HomeFeedViewModel =
        HomeFeedViewModel(
            sessionFactory = sessionFactory,
            zoneRepository = zoneRepository,
            viewPreferencesManager = preferences,
            userSession = customSession ?: if (accounts) userSession else FakeUserSession(),
            messageRepository = if (accounts) messageRepository else null,
            albumRepository = albumRepository
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

        val recorded = feedMock.lastRequest()
        return listOf("categoryId", "subType", "lastTime", "zoneNodeId", "zoneIds", "pageSize")
            .mapNotNull { key -> recorded.queryParameter(key)?.let { key to it } }
            .toMap()
    }

    private fun Env.stubFocusedZone(nodeId: Long, name: String = "手办") {
        zoneMock.replaceRoute(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[{"nodeId":$nodeId,"nameCN":"$name"}]}}"""
        )
    }

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

    private fun Env.stubPosts(
        zoneNodeId: Long? = null,
        order: String = "upd",
        page: Int = 1,
        pageSize: Int = 20,
        ids: List<Long> = emptyList(),
        pinnedIds: Set<Long> = emptySet(),
    ) {
        zoneMock.routeWhen(
            predicate = { path, _, query ->
                if (!path.contains("api/posts/search")) return@routeWhen false
                this.postsRequests += 1
                (query["zoneNodeId"]?.toLongOrNull()) == zoneNodeId &&
                    (query["order"] ?: "upd") == order &&
                    query["page"] == page.toString() &&
                    query["pageSize"] == pageSize.toString()
            },
            body = { _, _, _ ->
                val list = ids.joinToString(",") { id ->
                    """{"id":$id,"title":"帖子$id","userName":"用户$id","discussCount":0,"topTime":${if (id in pinnedIds) 1794000000000L else 0L}}"""
                }
                """{"success":true,"code":200,"msg":"ok","data":{"list":[$list]}}"""
            }
        )
    }

    @Test
    fun initialLoad_usesSavedFiltersAndParsesCursor() = runTest(testDispatcher) {

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
    fun selectCategory_singleZone_landsOnServerFirstBoard() = runTest(testDispatcher) {
        val env = env()
        env.stubZoneInfo(nodeId = 100L, tabs = listOf("album", "action"))
        env.stubFeed(zoneNodeId = 100L, zoneIds = "100", items = emptyList())
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectCategory(
            FeedCategory(tabKey = "z100", name = "手办", nodeId = 100L, zoneIds = "100")
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(ZoneBoard.Album, ZoneBoard.Action), state.boards)
        assertEquals(ZoneBoard.Album, state.selectedBoard, "单专区应落到服务端首板块而非硬编码 action")
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

        assertEquals(2, viewModel.uiState.value.categories.size)

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

        env.zoneMock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        env.zoneRepository.updateSettings(
            showAll = true, sort = "recent", hiddenZoneIds = listOf(200L), customOrder = emptyList()
        )
        advanceUntilIdle()

        val categories = viewModel.uiState.value.categories

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

        env.zoneMock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        env.zoneRepository.updateSettings(
            showAll = false, sort = "custom", hiddenZoneIds = emptyList(), customOrder = listOf(200L, 100L)
        )
        advanceUntilIdle()

        val categories = viewModel.uiState.value.categories

        assertEquals(3, categories.size)
        assertEquals("follow", categories[0].tabKey)
        assertEquals("z200", categories[1].tabKey)
        assertEquals("z100", categories[2].tabKey)
    }

    @Test
    fun coldStart_savedFollow_restoresFollowAndLoadsFeed() = runTest(testDispatcher) {

        val env = env(savedTabKey = "follow")
        env.stubFocusedZone(nodeId = 100L)
        env.stubFeed(zoneIds = "100", items = listOf(item(1, "100")))

        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("follow", state.selectedCategory.tabKey)
        assertTrue(state.isColdStartReady, "冷启动落定后应标记就绪")
        assertEquals(listOf(1L), state.items.map { it.id })
        assertFalse(state.isInitialLoading)
    }

    @Test
    fun coldStart_waitsForSessionPrewarm_singleRequestWithToken() = runTest(testDispatcher) {

        val fakeSession = FakeUserSession(SessionState.Prewarming)
        val env = env()
        env.stubFeed(items = listOf(item(1, "100")))

        val viewModel = env.viewModel(customSession = fakeSession)
        advanceUntilIdle()

        assertEquals(0, env.zoneMock.requestCount, "凭据预热未结束前不得发起专区请求")
        assertFalse(viewModel.isColdStartReady.value, "凭据预热未结束前首屏不得标记就绪")

        fakeSession.setState(SessionState.Authenticated(userId = 42L, token = "utoken-verified"))
        advanceUntilIdle()

        assertEquals(1, env.zoneMock.requestCount, "预热完成后仅发起恰好一次专区请求")
        assertTrue(viewModel.isColdStartReady.value, "落定后首屏标记就绪")
    }

    @Test
    fun coldStart_networkFailure_setsColdStartError_andRetryRecovers() = runTest(testDispatcher) {

        val env = env()
        env.zoneMock.replaceRoute(
            "api/zone/home-tabs",
            """{"success":false,"code":500,"msg":"Internal Server Error"}"""
        )

        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isColdStartReady, "网络失败时首屏不得标记就绪")
        assertEquals("Internal Server Error", state.coldStartError)

        env.zoneMock.replaceRoute(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[]}}"""
        )
        env.stubFeed(items = listOf(item(1, "100")))
        viewModel.retryColdStart()
        advanceUntilIdle()

        val recoveredState = viewModel.uiState.value
        assertTrue(recoveredState.isColdStartReady, "重试成功后首屏标记就绪")
        assertNull(recoveredState.coldStartError, "重试成功后错误信息应清除")
    }

    @Test
    fun coldStart_savedBoardMemory_notOverwrittenByAction() = runTest(testDispatcher) {

        val env = env(savedTabKey = "z100", savedBoardKey = "z100|album")
        env.stubFocusedZone(100L)
        env.stubZoneInfo(100L, tabs = listOf("action", "album"))
        env.albumMock.replaceRoute(
            "api/album/list",
            """{"success":true,"code":200,"msg":"ok","data":{"page":1,"list":[{"id":1,"itemId":101,"nameCN":"相册1","cover":"c.jpg"}]}}"""
        )

        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("z100", state.selectedCategory.tabKey)
        assertEquals(ZoneBoard.Album, state.selectedBoard)
        assertTrue(state.isColdStartReady)

        assertEquals("z100|album", env.preferences.getInitialFeedZoneBoardKey())
    }

    @Test
    fun refreshFeed_triggersZoneHomeTabsRefresh() = runTest(testDispatcher) {

        val env = env()
        env.stubFeed(items = listOf(item(1, "100")))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        val zoneRequestsBefore = env.zoneMock.requestCount
        viewModel.refreshFeed()
        advanceUntilIdle()

        assertEquals(zoneRequestsBefore + 1, env.zoneMock.requestCount, "下拉刷新应重新拉取专区首页数据")
    }

    @Test
    fun coldStart_savedSingleZone_restoresWithSingleInitialFeedRequest() = runTest(testDispatcher) {

        val env = env(savedTabKey = "z100")
        env.stubFocusedZone(nodeId = 100L)
        env.stubZoneInfo(nodeId = 100L, tabs = listOf("action"))
        env.stubFeed(
            subType = "recommend",
            zoneNodeId = 100L,
            zoneIds = "100",
            items = listOf(item(1, "1700000000000"))
        )

        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("z100", state.selectedCategory.tabKey, "冷启动应恢复到上次停留的单专区")
        assertEquals(listOf(1L), state.items.map { it.id })
        assertFalse(state.isInitialLoading)
        assertNull(state.errorMessage, "作废的乐观请求不得污染最终态")
        assertEquals(
            1,
            env.feedRequests,
            "冷启动恢复单专区只允许一次首屏请求（落定前不得先按默认『全部』作用域乐观加载）"
        )
    }

    @Test
    fun coldStart_savedNonDefaultSubType_loadsOnceWithSavedSubType() = runTest(testDispatcher) {

        val env = env(savedTabKey = "all", savedSubType = "comment")
        env.stubFeed(subType = "comment", items = listOf(item(3, "1700000000000")))

        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("comment", state.selectedSubType)
        assertEquals(listOf(3L), state.items.map { it.id })
        assertFalse(state.isInitialLoading)
        assertEquals(
            1,
            env.feedRequests,
            "落盘 subType 非默认时冷启动只允许一次首屏请求"
        )
        assertEquals("comment", env.lastFeedForm()["subType"])
    }

    @Test
    fun coldStart_savedDefaults_loadExactlyOnce() = runTest(testDispatcher) {

        val env = env()
        env.stubFeed(items = listOf(item(1, "1700000000000")))

        val viewModel = env.viewModel()
        advanceUntilIdle()

        assertEquals("all", viewModel.uiState.value.selectedCategory.tabKey)
        assertEquals(listOf(1L), viewModel.uiState.value.items.map { it.id })
        assertEquals(1, env.feedRequests)
    }

    @Test
    fun scrollPosition_saveAndReset() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        val viewModel = env.viewModel()

        viewModel.saveScrollPosition(8, 120)
        viewModel.saveFilterCollapseFraction(0.75f)
        viewModel.saveBoardFilterRowScroll(viewModel.currentBoardMemoryKey, 240)
        assertEquals(8, viewModel.savedScrollIndex)
        assertEquals(120, viewModel.savedScrollOffset)
        assertEquals(0.75f, viewModel.savedFilterCollapseFraction)

        assertEquals(240, viewModel.scrollMemoryFor(viewModel.currentBoardMemoryKey).filterRowScrollX)

        assertNull(viewModel.scrollMemoryOrNull("z999|action"))

        viewModel.saveFilterCollapseFraction(1.4f)
        assertEquals(1f, viewModel.savedFilterCollapseFraction)

        viewModel.resetScrollPosition()
        assertEquals(0, viewModel.savedScrollIndex)
        assertEquals(0, viewModel.savedScrollOffset)
        assertEquals(0f, viewModel.savedFilterCollapseFraction)
        assertEquals(0, viewModel.scrollMemoryFor(viewModel.currentBoardMemoryKey).filterRowScrollX)
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
            sessionFactory = env.sessionFactory,
            zoneRepository = env.zoneRepository,
            viewPreferencesManager = env.preferences,
            userSession = session,
            messageRepository = MessageRepository(env.accountMock.api(env.dispatcher)),
            albumRepository = env.albumRepository
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
            sessionFactory = env.sessionFactory,
            zoneRepository = env.zoneRepository,
            viewPreferencesManager = env.preferences,
            userSession = session,
            messageRepository = MessageRepository(env.accountMock.api(env.dispatcher)),
            albumRepository = env.albumRepository
        )
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.unreadMessageCount)

        session.setState(SessionState.Authenticated(userId = 1L, token = "utoken-test"))
        advanceUntilIdle()

        assertEquals(15, viewModel.uiState.value.unreadMessageCount)
    }

    @Test
    fun zoneHomeTabsSync_waitsForPrewarm_andDispatchesSingleRequest() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.userSession.setState(SessionState.Prewarming)

        env.viewModel(accounts = true)
        advanceUntilIdle()

        assertEquals(0, env.zoneMock.requestCount)

        env.userSession.setState(SessionState.Authenticated(userId = 1L, token = "utoken-test"))
        advanceUntilIdle()

        assertEquals(1, env.zoneMock.requestCount)
    }

    @Test
    fun selectZoneTab_allStillSelectableWhenShowAllDisabled() = runTest(testDispatcher) {
        val env = env()

        env.zoneMock.replaceRoute(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":false},"focusZones":[]}}"""
        )
        env.stubFeed(items = listOf(item(1, "100")))

        val viewModel = env.viewModel()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.categories.none { it.tabKey == "all" })

        viewModel.selectZoneTab(ZONE_TAB_KEY_FOLLOW)
        advanceUntilIdle()
        assertEquals("follow", viewModel.uiState.value.selectedCategory.tabKey)

        viewModel.selectZoneTab(ZONE_TAB_KEY_ALL)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("all", state.selectedCategory.tabKey)
        assertEquals(0, state.selectedCategory.id)
        assertEquals(listOf(1L), state.items.map { it.id })
        assertEquals("all", env.preferences.getInitialFeedZoneTabKey())
        assertEquals(0, env.preferences.getInitialFeedCategoryId())

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

        assertEquals(before, env.feedRequests)
        assertEquals("follow", env.preferences.getInitialFeedZoneTabKey())
    }

    @Test
    fun aggregateBoards_areHomeOrderIntersectedWithImplemented() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ZoneQueryScope.All, state.zoneScope)

        assertEquals(ZoneBoardCapability.ALL_BOARDS, state.boards)
        assertEquals(ZoneBoard.HOME_ORDER, state.boards)
        assertEquals(
            listOf(
                ZoneBoard.Action, ZoneBoard.Album, ZoneBoard.Posts, ZoneBoard.Hobby, ZoneBoard.Resale,
                ZoneBoard.Company, ZoneBoard.Series, ZoneBoard.Works,
                ZoneBoard.Character, ZoneBoard.Person
            ),
            state.boards
        )
        assertTrue(ZoneBoard.Action in state.boards)
        assertTrue(ZoneBoard.Album in state.boards)
        assertTrue(ZoneBoard.Posts in state.boards)
        assertTrue(ZoneBoard.Hobby in state.boards)
        assertTrue(state.boards.any { it.isEntity })

        assertEquals(state.boards.sortedBy { ZoneBoard.HOME_ORDER.indexOf(it) }, state.boards)
        assertEquals(state.boards.first(), state.selectedBoard)

        assertEquals(1, env.zoneMock.requestCount)
    }

    @Test
    fun followBoards_hideBoardsWithoutMultiZoneChannel() = runTest(testDispatcher) {
        val env = env()
        env.stubFocusedZone(100L)
        env.stubFeed(items = emptyList())
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectZoneTab(ZONE_TAB_KEY_FOLLOW)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ZoneQueryScope.Follow(listOf(100L)), state.zoneScope)

        assertEquals(ZoneBoardCapability.FOLLOW_BOARDS, state.boards)
        assertEquals(
            listOf(
                ZoneBoard.Action, ZoneBoard.Album,
                ZoneBoard.Company, ZoneBoard.Series, ZoneBoard.Works,
                ZoneBoard.Character, ZoneBoard.Person
            ),
            state.boards
        )
        assertFalse(ZoneBoard.Posts in state.boards)
        assertFalse(ZoneBoard.Resale in state.boards)
        assertFalse(ZoneBoard.Hobby in state.boards)
        assertEquals(state.boards.first(), state.selectedBoard)
    }

    @Test
    fun selectZoneTab_fromHiddenBoard_fallsBackToFirstBoard() = runTest(testDispatcher) {

        val env = env()
        env.stubFocusedZone(100L)
        env.stubFeed(items = emptyList())
        env.stubPosts(ids = listOf(1L))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectBoard(ZoneBoard.Posts)
        advanceUntilIdle()
        assertEquals(ZoneBoard.Posts, viewModel.uiState.value.selectedBoard)

        viewModel.selectZoneTab(ZONE_TAB_KEY_FOLLOW)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ZoneBoardCapability.FOLLOW_BOARDS, state.boards)
        assertEquals(ZoneBoard.Action, state.selectedBoard)
        assertEquals(state.boards.first(), state.selectedBoard)
        assertFalse(ZoneBoard.Posts in state.boards)
    }

    @Test
    fun followScope_actionRequestSendsZoneIdsUnionAndOmitsZoneNodeId() = runTest(testDispatcher) {

        val env = env()
        env.zoneMock.replaceRoute(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[{"nodeId":100,"nameCN":"手办"},{"nodeId":200,"nameCN":"模型"}]}}"""
        )
        env.stubFeed(zoneIds = "100,200", items = listOf(item(1, "100")))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectZoneTab(ZONE_TAB_KEY_FOLLOW)
        advanceUntilIdle()

        val form = env.lastFeedForm()
        assertEquals("100,200", form["zoneIds"])
        assertNull(form["zoneNodeId"], "关注聚合不得下发单值 zoneNodeId")
        assertEquals(listOf(1L), viewModel.uiState.value.items.map { it.id })
    }

    @Test
    fun followScope_hiddenBoardsNeverRequested() = runTest(testDispatcher) {

        val env = env()
        env.stubFocusedZone(100L)
        env.stubFeed(items = emptyList())
        env.stubPosts(ids = emptyList())
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectZoneTab(ZONE_TAB_KEY_FOLLOW)
        advanceUntilIdle()
        val baselineAlbum = env.albumMock.requestCount
        assertEquals(0, env.hobbyMock.requestCount)
        assertEquals(0, env.resaleMock.requestCount)

        viewModel.selectBoard(ZoneBoard.Posts)
        viewModel.selectBoard(ZoneBoard.Hobby)
        viewModel.selectBoard(ZoneBoard.Resale)
        advanceUntilIdle()

        assertEquals(ZoneBoard.Action, viewModel.uiState.value.selectedBoard)
        assertEquals(0, env.postsRequests, "关注聚合不得请求讨论接口")
        assertEquals(0, env.hobbyMock.requestCount, "关注聚合不得请求产品接口")
        assertEquals(0, env.resaleMock.requestCount, "关注聚合不得请求二手接口")
        assertEquals(baselineAlbum, env.albumMock.requestCount, "未选中分享板块时不得请求相册接口")
    }

    @Test
    fun runtimeFollowScope_isFollowWithEmptyZoneIds() = runTest(testDispatcher) {

        val env = env()
        env.stubFeed(items = emptyList())
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectZoneTab(ZONE_TAB_KEY_FOLLOW)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ZoneQueryScope.Follow(emptyList()), state.zoneScope)
        assertTrue(state.zoneScope.isFollowEmpty)

        assertEquals(ZoneBoardCapability.FOLLOW_BOARDS, state.boards)
        assertEquals(ZoneBoardCapability.homeBoardsFor(state.zoneScope), state.boards)
        assertFalse(ZoneBoard.Posts in state.boards)
        assertFalse(ZoneBoard.Hobby in state.boards)
        assertFalse(ZoneBoard.Resale in state.boards)
    }

    @Test
    fun singleZone_boardsFromServerTabs_keepOnlyImplemented() = runTest(testDispatcher) {
        val env = env()
        env.stubZoneInfo(100L, tabs = listOf("action", "album", "shop_item", "community"))
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

        assertEquals(listOf(ZoneBoard.Action, ZoneBoard.Album), state.boards)
        assertEquals(ZoneBoard.Action, state.selectedBoard)

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
        assertEquals(listOf(ZoneBoard.Action, ZoneBoard.Album), state.boards)
    }

    @Test
    fun selectBoard_onlySelectsBoardsInCurrentList() = runTest(testDispatcher) {

        val env = env()
        env.stubFeed(items = emptyList())
        env.stubFeed(zoneNodeId = 100L, zoneIds = "100", items = emptyList())
        env.stubZoneInfo(100L, tabs = listOf("action"))
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectCategory(
            FeedCategory(tabKey = "z100", name = "手办", nodeId = 100L, zoneIds = "100")
        )
        advanceUntilIdle()
        assertEquals(listOf(ZoneBoard.Action), viewModel.uiState.value.boards)

        viewModel.selectBoard(ZoneBoard.Hobby)
        assertEquals(ZoneBoard.Action, viewModel.uiState.value.selectedBoard)
        viewModel.selectBoard(ZoneBoard.Album)
        assertEquals(ZoneBoard.Action, viewModel.uiState.value.selectedBoard)

        viewModel.selectBoard(ZoneBoard.Action)
        assertEquals(ZoneBoard.Action, viewModel.uiState.value.selectedBoard)
    }

    @Test
    fun coldStart_savedSingleZone_restoresZoneAndBoardsAfterFocusZonesReady() = runTest(testDispatcher) {

        val env = env(savedTabKey = "z100", savedBoardKey = "z100|album")
        env.stubFocusedZone(100L)
        env.stubZoneInfo(100L, tabs = listOf("action", "album"))
        env.stubFeed(items = listOf(item(1, "100")))
        env.stubFeed(zoneNodeId = 100L, zoneIds = "100", items = listOf(item(2, "200")))

        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("z100", state.selectedCategory.tabKey)
        assertEquals(100L, state.selectedCategory.nodeId)

        assertEquals(ZoneBoard.Action, state.boards.first())
        assertTrue(state.boards.all { it in ZoneBoard.CLIENT_WHITELIST })

        assertEquals(ZoneBoard.Album, state.selectedBoard)
        assertEquals(listOf(2L), state.items.map { it.id })

        assertEquals("100", env.lastFeedForm()["zoneNodeId"])
    }

    @Test
    fun coldStart_savedUnfollowedZone_fallsBackToAllWithoutZoneRequest() = runTest(testDispatcher) {

        val env = env(savedTabKey = "z999", savedBoardKey = "z999|hobby")
        env.stubFeed(items = listOf(item(1, "100")))

        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("all", state.selectedCategory.tabKey)
        assertEquals(ZoneBoard.Action, state.selectedBoard)
        assertEquals(listOf(1L), state.items.map { it.id })
        assertNull(env.lastFeedForm()["zoneNodeId"])

        assertEquals(1, env.zoneMock.requestCount)
    }

    @Test
    fun coldStart_savedBoardTypeUnavailable_fallsBackToFirstBoard() = runTest(testDispatcher) {

        val env = env(savedTabKey = "all", savedBoardKey = "all|bogus_board")
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

        val env = env(savedTabKey = "all", savedBoardKey = "z200|album")
        env.stubFeed(items = emptyList())

        val viewModel = env.viewModel()
        advanceUntilIdle()

        assertEquals("all", viewModel.uiState.value.selectedCategory.tabKey)
        assertEquals(ZoneBoard.Action, viewModel.uiState.value.selectedBoard)
    }

    @Test
    fun coldStart_savedAlbumBoard_restoresBoardAndLazyLoads() = runTest(testDispatcher) {

        val env = env(savedTabKey = "all", savedBoardKey = "all|album")
        env.stubFeed(items = emptyList())
        env.albumMock.replaceRoute(
            "api/album/list",
            """{"success":true,"code":200,"msg":"ok","data":{"page":1,"list":[{"id":51,"itemId":1051,"nameCN":"相册51","cover":"c.jpg"}]}}"""
        )

        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ZoneBoard.Album, state.selectedBoard)
        assertEquals(listOf(51L), state.albumItems.map { it.targetAlbumId })
        assertTrue(state.isAlbumLoaded)
        assertEquals(1, env.albumMock.requestCount)
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

        assertEquals("z100|action", env.preferences.getInitialFeedZoneBoardKey())
        assertEquals("z100", env.preferences.getInitialFeedZoneTabKey())
        assertEquals(ZoneBoard.Action, viewModel.uiState.value.selectedBoard)
    }

    @Test
    fun selectBoard_unavailableBoardDoesNotChangePersistedBoardKey() = runTest(testDispatcher) {
        val env = env(savedBoardKey = "all|action")
        env.stubFeed(items = emptyList())
        env.stubFeed(zoneNodeId = 100L, zoneIds = "100", items = emptyList())
        env.stubZoneInfo(100L, tabs = listOf("action", "album"))
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectCategory(FeedCategory("z100", "手办", 100L, "100"))
        advanceUntilIdle()

        viewModel.selectBoard(ZoneBoard.Posts)
        advanceUntilIdle()

        assertEquals(ZoneBoard.Action, viewModel.uiState.value.selectedBoard)
        assertEquals("z100|action", env.preferences.getInitialFeedZoneBoardKey())
    }

    @Test
    fun scrollMemory_survivesZoneSwitchAndReturnsToFirstBoard() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.stubFeed(zoneNodeId = 100L, zoneIds = "100", items = emptyList())
        env.stubZoneInfo(100L, tabs = listOf("action"))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.saveScrollPosition(7, 90)
        assertEquals(7, viewModel.scrollMemoryFor("all|action").index)

        viewModel.selectCategory(FeedCategory("z100", "手办", 100L, "100"))
        advanceUntilIdle()
        assertEquals(ZoneBoard.Action, viewModel.uiState.value.selectedBoard)
        assertEquals(HomeBoardScrollMemory(), viewModel.scrollMemoryFor("z100|action"))

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

        assertEquals(
            "follow:100,200|action",
            homeBoardMemoryKey(ZoneQueryScope.Follow(listOf(200L, 100L, 200L)), ZoneBoard.Action)
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

        assertEquals(
            ZoneQueryScope.Single(100L),
            FeedCategory(tabKey = "z100", name = "手办", nodeId = 0L, zoneIds = "").toZoneQueryScope()
        )
    }

    @Test
    fun postsBoard_lazyLoadsOnSelection_andAllScopeOmitsZoneFilter() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.stubPosts(ids = listOf(101L, 102L))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        assertEquals(0, env.postsRequests)
        assertEquals(ZoneBoardCapability.ALL_BOARDS, viewModel.uiState.value.boards)

        viewModel.selectBoard(ZoneBoard.Posts)
        advanceUntilIdle()

        assertEquals(1, env.postsRequests)
        val recorded = env.zoneMock.lastRequest()
        assertTrue(recorded.encodedPath.orEmpty().contains("api/posts/search"))

        assertNull(recorded.queryParameter("zoneNodeId"))
        assertNull(recorded.queryParameter("zones"))
        assertEquals("upd", recorded.queryParameter("order"))
        assertEquals("1", recorded.queryParameter("page"))
        assertEquals("20", recorded.queryParameter("pageSize"))

        val state = viewModel.uiState.value
        assertEquals(listOf(101L, 102L), state.posts.items.map { it.id })

        assertEquals(emptyList(), state.posts.pinnedPosts)

        assertEquals(state.posts.items, state.postsBoardContent.items)
    }

    @Test
    fun postsRefresh_cancelledByOrderChange_clearsRefreshing() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.stubPosts(ids = listOf(1L, 2L))
        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Posts)
        advanceUntilIdle()

        viewModel.refresh()

        viewModel.selectPostsOrder("add")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing, "取消刷新后必须复位 isRefreshing")
        assertFalse(viewModel.uiState.value.currentBoardIsRefreshing)
    }

    @Test
    fun postsBoard_singleZone_requestCarriesZoneNodeId_andSplitsPinnedPosts() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.stubFeed(zoneNodeId = 100L, zoneIds = "100", items = emptyList())
        env.stubZoneInfo(100L, tabs = listOf("action", "posts"))
        env.stubPosts(zoneNodeId = 100L, ids = listOf(1L, 2L, 3L, 4L, 5L, 6L), pinnedIds = setOf(6L))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectCategory(
            FeedCategory(tabKey = "z100", name = "手办", nodeId = 100L, zoneIds = "100")
        )
        advanceUntilIdle()
        assertEquals(listOf(ZoneBoard.Action, ZoneBoard.Posts), viewModel.uiState.value.boards)

        viewModel.selectBoard(ZoneBoard.Posts)
        advanceUntilIdle()

        val recorded = env.zoneMock.lastRequest()
        assertEquals("100", recorded.queryParameter("zoneNodeId"))
        assertNull(recorded.queryParameter("zones"))
        val state = viewModel.uiState.value
        assertEquals(listOf(6L), state.posts.pinnedPosts.map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), state.posts.items.map { it.id })
    }

    @Test
    fun postsOrderSwitch_resetsItemsAndReloadsFirstPageWithNewOrder() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.stubPosts(order = "upd", ids = (1L..20L).toList())
        env.stubPosts(order = "add", ids = listOf(201L, 202L))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectBoard(ZoneBoard.Posts)
        advanceUntilIdle()
        assertEquals(20, viewModel.uiState.value.posts.items.size)
        assertTrue(viewModel.uiState.value.posts.hasMore)

        viewModel.selectPostsOrder("add")
        advanceUntilIdle()

        val recorded = env.zoneMock.lastRequest()
        assertEquals("add", recorded.queryParameter("order"))
        assertEquals("1", recorded.queryParameter("page"))
        val state = viewModel.uiState.value
        assertEquals(listOf(201L, 202L), state.posts.items.map { it.id })
        assertFalse(state.posts.hasMore)
    }

    @Test
    fun postsPagination_appendsNextPageAndKeepsZoneFilter() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.stubPosts(page = 1, ids = (1L..20L).toList())
        env.stubPosts(page = 2, ids = listOf(21L, 22L))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectBoard(ZoneBoard.Posts)
        advanceUntilIdle()
        viewModel.loadNextPage()
        advanceUntilIdle()

        val recorded = env.zoneMock.lastRequest()
        assertEquals("2", recorded.queryParameter("page"))
        assertNull(recorded.queryParameter("zoneNodeId"))
        val state = viewModel.uiState.value
        assertEquals((1L..22L).toList(), state.posts.items.map { it.id })
        assertFalse(state.posts.hasMore)
    }

    @Test
    fun postsBoard_stateIsPreservedWhenSwitchingAwayAndBack() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.stubPosts(ids = listOf(11L, 12L))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectBoard(ZoneBoard.Posts)
        advanceUntilIdle()
        assertEquals(1, env.postsRequests)

        viewModel.selectBoard(ZoneBoard.Action)
        advanceUntilIdle()
        viewModel.selectBoard(ZoneBoard.Posts)
        advanceUntilIdle()

        assertEquals(1, env.postsRequests)
        assertEquals(listOf(11L, 12L), viewModel.uiState.value.posts.items.map { it.id })
    }

    @Test
    fun followScope_hidesPostsBoard_andNeverRequestsPosts() = runTest(testDispatcher) {
        val env = env()
        env.stubFocusedZone(100L)
        env.stubFeed(items = emptyList())

        env.stubPosts(ids = emptyList())
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectZoneTab(ZONE_TAB_KEY_FOLLOW)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ZoneQueryScope.Follow(listOf(100L)), state.zoneScope)
        assertEquals(ZoneBoardCapability.FOLLOW_BOARDS, state.boards)
        assertFalse(ZoneBoard.Posts in state.boards)

        viewModel.selectBoard(ZoneBoard.Posts)
        advanceUntilIdle()
        assertEquals(ZoneBoard.Action, viewModel.uiState.value.selectedBoard)
        assertEquals(0, env.postsRequests)
    }

    @Test
    fun scopeChange_resetsPostsContentAndReloadsWithNewZoneFilter() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.stubFeed(zoneNodeId = 100L, zoneIds = "100", items = emptyList())
        env.stubZoneInfo(100L, tabs = listOf("action", "posts"))
        env.stubPosts(ids = listOf(1L, 2L))
        env.stubPosts(zoneNodeId = 100L, ids = listOf(9L))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectBoard(ZoneBoard.Posts)
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.posts.items.map { it.id })

        viewModel.selectCategory(
            FeedCategory(tabKey = "z100", name = "手办", nodeId = 100L, zoneIds = "100")
        )
        advanceUntilIdle()
        assertEquals(emptyList(), viewModel.uiState.value.posts.items)
        assertFalse(viewModel.uiState.value.posts.isLoaded)

        viewModel.selectBoard(ZoneBoard.Posts)
        advanceUntilIdle()
        assertEquals("100", env.zoneMock.lastRequest().queryParameter("zoneNodeId"))
        assertEquals(listOf(9L), viewModel.uiState.value.posts.items.map { it.id })
    }

    @Test
    fun sessionIntegration_sessionFactoryCreatesSessionAndTracksActiveSnapshot() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = listOf(item(101, "1000")))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        val session = viewModel.session
        val activeSnapshot = session.activeSnapshot.value
        assertEquals(ZoneBoard.Action, activeSnapshot.board)
        val actionContent = activeSnapshot.content as ZoneBoardContent.Action
        assertEquals(listOf(101L), actionContent.items.map { it.id })
        assertEquals(listOf(101L), viewModel.uiState.value.items.map { it.id })
        assertFalse(activeSnapshot.pageView.isLoading)
    }

    @Test
    fun sessionIntegration_boardSelectionDispatchesToSessionAndUpdatesActiveSnapshot() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.stubPosts(ids = listOf(201L, 202L))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectBoard(ZoneBoard.Posts)
        advanceUntilIdle()

        val activeSnapshot = viewModel.session.activeSnapshot.value
        assertEquals(ZoneBoard.Posts, activeSnapshot.board)
        val postsContent = activeSnapshot.content as ZoneBoardContent.Posts
        assertEquals(listOf(201L, 202L), postsContent.items.map { it.id })
        assertEquals(listOf(201L, 202L), viewModel.uiState.value.posts.items.map { it.id })
    }

    @Test
    fun sessionIntegration_scopeUpdateDelegatesToSessionAndResetsState() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.stubFeed(zoneNodeId = 100L, zoneIds = "100", items = listOf(item(301, "3000")))
        env.stubZoneInfo(100L, tabs = listOf("action"))
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.selectCategory(FeedCategory("z100", "手办", 100L, "100"))
        advanceUntilIdle()

        val activeSnapshot = viewModel.session.activeSnapshot.value
        assertEquals(ZoneBoard.Action, activeSnapshot.board)
        val actionContent = activeSnapshot.content as ZoneBoardContent.Action
        assertEquals(listOf(301L), actionContent.items.map { it.id })
    }

    @Test
    fun sessionIntegration_scrollMemoryDelegatesToSession() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        val viewModel = env.viewModel()
        advanceUntilIdle()

        viewModel.saveBoardScrollPosition(viewModel.currentBoardMemoryKey, index = 5, offset = 120)
        viewModel.saveBoardFilterCollapseFraction(viewModel.currentBoardMemoryKey, fraction = 0.8f)
        viewModel.saveBoardFilterRowScroll(viewModel.currentBoardMemoryKey, scrollX = 45)

        val scrollPos = viewModel.session.activeSnapshot.value.scrollPosition
        assertEquals(5, scrollPos.index)
        assertEquals(120, scrollPos.offset)
        assertEquals(0.8f, scrollPos.filterCollapseFraction)
        assertEquals(45, scrollPos.filterRowScrollX)
    }
}
