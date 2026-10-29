package com.nobody.poishelf.ui.home

import com.nobody.poishelf.data.local.TestDataStore
import com.nobody.poishelf.data.local.ViewPreferencesManager
import com.nobody.poishelf.data.remote.model.ActionItem
import com.nobody.poishelf.data.repository.HomeRepository
import com.nobody.poishelf.data.repository.MockApi
import com.nobody.poishelf.data.repository.ZoneRepository
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
        val preferences: ViewPreferencesManager,
        val zoneRepository: ZoneRepository,
    ) {
        val feedRequests: Int get() = feedMock.requestCount
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
        return Env(feedMock, zoneMock, preferences, zoneRepository)
    }

    private fun Env.viewModel(): HomeFeedViewModel =
        HomeFeedViewModel(HomeRepository(feedMock.api()), zoneRepository, preferences)

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
            predicate = { path, form ->
                path.contains("api/user-action/list-v3") &&
                    form["categoryId"] == categoryId.toString() &&
                    form["subType"] == subType &&
                    form["lastTime"] == lastTime.toString() &&
                    (form["zoneNodeId"]?.toLongOrNull() == zoneNodeId) &&
                    ((form["zoneIds"] ?: "") == (zoneIds ?: ""))
            },
            body = { _, _ -> feedJson(items) }
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
            predicate = { path, form ->
                path.contains("api/user-action/list-v3") &&
                    form["categoryId"] == categoryId.toString() &&
                    form["subType"] == subType &&
                    form["lastTime"] == lastTime.toString() &&
                    (form["zoneNodeId"]?.toLongOrNull() == zoneNodeId) &&
                    ((form["zoneIds"] ?: "") == (zoneIds ?: ""))
            },
            body = { _, _ -> feedMock.failure(500, message) }
        )
    }

    private fun Env.lastFeedForm(): Map<String, String> = feedMock.takeRequest().formEntries()

    @Test
    fun initialLoad_usesSavedFiltersAndParsesCursor() = runTest {
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
    fun initialLoad_failureSetsErrorMessage() = runTest {
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
    fun initialLoad_emptyPageClearsHasMore() = runTest {
        val env = env()
        env.stubFeed(items = emptyList())

        val viewModel = env.viewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertFalse(viewModel.uiState.value.hasMore)
        assertEquals(0L, viewModel.uiState.value.lastTime)
    }

    @Test
    fun selectCategory_persistsFilterAndReloads() = runTest {
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
    fun selectSameCategory_doesNotReload() = runTest {
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
    fun selectSubType_persistsFilterAndReloads() = runTest {
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
    fun resetFilters_restoresDefaultsAndPersists() = runTest {
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
    fun refreshFeed_replacesItemsAndClearsError() = runTest {
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
    fun refreshFeed_failureKeepsExistingItems() = runTest {
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
    fun loadNextPage_appendsAndUsesLastTimeCursor() = runTest {
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
    fun loadNextPage_emptyPageClearsHasMore() = runTest {
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
    fun loadNextPage_failureKeepsItemsAndSetsLoadMoreError() = runTest {
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
    fun loadNextPage_whenHasMoreIsFalse_skipsRequest() = runTest {
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
    fun dynamicCategories_updatesWhenFocusZonesEmit() = runTest {
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
    fun dynamicCategories_respectsHiddenZoneIds() = runTest {
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
    fun dynamicCategories_respectsCustomOrder() = runTest {
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
    fun emptyFollow_doesNotQueryAllFeed() = runTest {
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
    fun scrollPosition_saveAndReset() = runTest {
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
}
