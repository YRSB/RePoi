package com.nobody.repoi.ui.home

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.repository.FakeTokenManager
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.repository.MessageRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.data.repository.ZoneRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        Dispatchers.resetMain()
    }

    private class Env(
        val feedMock: MockApi,
        val zoneMock: MockApi,
        val accountMock: MockApi,
        val preferences: ViewPreferencesManager,
        val zoneRepository: ZoneRepository,
    ) {
        val feedRequests: Int get() = feedMock.requestCount
        val userRepository = UserRepository(accountMock.api(), FakeTokenManager("utoken-test"))
        val messageRepository = MessageRepository(accountMock.api(), FakeTokenManager("utoken-test"))
    }

    private suspend fun env(
        savedTabKey: String = "all",
        savedCategoryId: Int = 0,
        savedSubType: String = "recommend",
    ): Env {
        val feedMock = MockApi()
        val zoneMock = MockApi()
        // 专区 tabs 首屏：默认空聚焦 + showAll，供 VM init 静默同步消费
        zoneMock.route(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[]}}"""
        )
        val preferences = ViewPreferencesManager(TestDataStore.viewPrefs())
        preferences.setFeedZoneTabKey(savedTabKey)
        preferences.setFeedCategory(savedCategoryId)
        preferences.setFeedSubType(savedSubType)
        val zoneRepository = ZoneRepository(zoneMock.api(), preferences)
        return Env(feedMock, zoneMock, MockApi(), preferences, zoneRepository)
    }

    private fun Env.viewModel(accounts: Boolean = false): HomeFeedViewModel =
        HomeFeedViewModel(
            HomeRepository(feedMock.api()),
            zoneRepository,
            preferences,
            if (accounts) userRepository else null,
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
        val recorded = feedMock.takeRequest()
        return listOf("categoryId", "subType", "lastTime", "zoneNodeId", "zoneIds", "pageSize")
            .mapNotNull { key -> recorded.queryParameter(key)?.let { key to it } }
            .toMap()
    }

    @Test
    fun initialLoad_usesSavedFiltersAndParsesCursor() = runTest(testDispatcher) {
        val env = env(savedTabKey = "all", savedSubType = "all")
        env.stubFeed(
            categoryId = 0,
            subType = "all",
            zoneNodeId = null,
            zoneIds = null,
            items = listOf(item(1, "1700000000000"), item(2, "1600000000000"))
        )

        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("all", state.selectedCategory.tabKey)
        assertEquals("all", state.selectedSubType)
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
        assertEquals(1600000000000L, state.lastTime)
        assertTrue(state.hasMore)
        assertFalse(state.isInitialLoading)
        assertNull(state.errorMessage)
        val form = env.lastFeedForm()
        assertEquals("0", form["categoryId"])
        assertEquals("all", form["subType"])
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
    fun resetFilters_restoresDefaultsAndPersists() = runTest(testDispatcher) {
        val env = env(savedTabKey = "z100", savedSubType = "all")
        // 首屏 tabs 即带 z100 聚焦（init 静默同步消费）
        env.zoneMock.route(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[{"nodeId":100,"nameCN":"手办"}]}}"""
        )
        env.stubFeed(subType = "all", zoneNodeId = 100L, zoneIds = "100", items = listOf(item(1, "100")))
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
    fun emptyFollow_doesNotQueryAllFeed() = runTest(testDispatcher) {
        val env = env(savedTabKey = "follow")
        env.stubFeed(items = listOf(item(1, "100")))

        val viewModel = env.viewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("follow", state.selectedCategory.tabKey)
        assertTrue(state.items.isEmpty())
        assertFalse(state.hasMore)
        assertFalse(state.isInitialLoading)
        // 关注为空时不应请求全部动态
        assertEquals(0, env.feedRequests)
    }

    @Test
    fun scrollPosition_saveAndReset() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        val viewModel = env.viewModel()

        viewModel.saveScrollPosition(8, 120)
        assertEquals(8, viewModel.savedScrollIndex)
        assertEquals(120, viewModel.savedScrollOffset)

        viewModel.resetScrollPosition()
        assertEquals(0, viewModel.savedScrollIndex)
        assertEquals(0, viewModel.savedScrollOffset)
    }

    @Test
    fun accountHeader_updatesAvatarOnInit() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.accountMock.route(
            "api/user/self",
            """{"success":true,"code":200,"msg":"ok","data":{"user":{"id":88888,"nickname":"手办达人","header":"avatar/abc.jpg"}}}"""
        )
        val viewModel = env.viewModel(accounts = true)
        advanceUntilIdle()

        assertEquals("avatar/abc.jpg", viewModel.uiState.value.userAvatar)
    }

    @Test
    fun unreadCount_updatesOnInitAndRefresh() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = listOf(item(1, "100")))
        env.accountMock.route("api/user/self", env.accountMock.failure(401, "未登录"))
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
        env.accountMock.route("api/user/self", env.accountMock.failure(401, "未登录"))
        val viewModel = HomeFeedViewModel(
            HomeRepository(env.feedMock.api()),
            env.zoneRepository,
            env.preferences,
            UserRepository(env.accountMock.api(), FakeTokenManager("")),
            MessageRepository(env.accountMock.api(), FakeTokenManager(""))
        )
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.userAvatar)
        assertEquals(0, viewModel.uiState.value.unreadMessageCount)
    }

    @Test
    fun coldStartBeforeTokenPrewarm_stillLoadsUnreadCount() = runTest(testDispatcher) {
        val env = env()
        env.stubFeed(items = emptyList())
        env.accountMock.route("api/user/self", env.accountMock.failure(401, "未登录"))
        env.accountMock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"map":{"push":{"system":1,"reply":2,"praise":3,"recommend":0,"follownews":4,"commodity":5,"posts_reply":0}}}}"""
        )
        val coldToken = ColdStartTokenManager("utoken-test")
        val viewModel = HomeFeedViewModel(
            HomeRepository(env.feedMock.api()),
            env.zoneRepository,
            env.preferences,
            UserRepository(env.accountMock.api(), coldToken),
            MessageRepository(env.accountMock.api(), coldToken)
        )
        advanceUntilIdle()

        assertEquals(15, viewModel.uiState.value.unreadMessageCount)
    }

    private class ColdStartTokenManager(token: String) : TokenManager(TestDataStore.userPrefs()) {
        private val tokenState = MutableStateFlow(token)
        override val tokenFlow: Flow<String> = tokenState
        override var currentToken: String = ""
        override suspend fun clearToken() {
            currentToken = ""
            tokenState.value = ""
        }

        override suspend fun awaitReady() {
            currentToken = tokenState.value
        }
    }
}
