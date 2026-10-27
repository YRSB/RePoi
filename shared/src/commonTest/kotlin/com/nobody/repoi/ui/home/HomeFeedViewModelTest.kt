package com.nobody.repoi.ui.home

import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDataStores
import com.nobody.repoi.test.TestDispatchers
import io.ktor.client.HttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HomeFeedViewModel 的 KMP 移植版（真实仓储 + MockEngine，替代 Mockito）。
 *
 * 注意 VM init 时会并发触发：loadInitialFeed + getZoneHomeTabs，因此每个测试
 * enqueue 的顺序必须先满足订阅流再满足网络——ZoneRepository 的 flow 初始值即用，
 * getZoneHomeTabs 的请求需要一个排队响应。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedViewModelTest {

    private val dispatchers = TestDispatchers()
    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var stores: TestDataStores
    private lateinit var preferences: ViewPreferencesManager
    private lateinit var repository: HomeRepository
    private lateinit var zoneRepository: ZoneRepository

    @BeforeTest
    fun setUp() {
        dispatchers.setUp()
        mock = MockRepoEngine(dispatchers.engineDispatcher)
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        stores = TestDataStores()
        preferences = ViewPreferencesManager(stores.createDataStore("view_prefs"))
        repository = HomeRepository(api)
        zoneRepository = ZoneRepository(apiService = api, viewPreferencesManager = preferences)
    }

    @AfterTest
    fun tearDown() {
        client.close()
        stores.cleanUp()
        dispatchers.tearDown()
    }

    private fun feedJson(vararg items: String): String {
        return """{"success":true,"code":200,"msg":"ok","data":{"list":[${items.joinToString(",")}]}}"""
    }

    /**
     * 注册 zone-tabs 规则响应（VM init 与 loadInitialFeed 并发，队列顺序不稳定，
     * 因此 zone 请求走规则、feed 请求走队列）。
     */
    private fun stubZoneTabs(body: String = zoneTabsJson()) {
        mock.respondWhen(Regex.fromLiteral("api/zone/home-tabs"), body)
    }

    private fun itemJson(id: Long, date: String? = null): String {
        val datePart = if (date == null) "null" else "\"$date\""
        return """{"id":$id,"actionDate":$datePart,"actionComment":"动态$id"}"""
    }

    private fun zoneTabsJson(): String {
        // getZoneHomeTabs 的静默同步响应：空关注 + 默认设置
        return """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true,"sort":"recent","hiddenZoneIds":[],"customOrder":[],"recentZoneIds":[]},"focusZones":[],"groups":[]}}"""
    }

    private suspend fun createViewModel(
        savedTabKey: String = "all",
        savedSubType: String = "recommend"
    ): HomeFeedViewModel {
        preferences.setFeedZoneTabKey(savedTabKey)
        preferences.setFeedSubType(savedSubType)
        return HomeFeedViewModel(repository, zoneRepository, preferences)
    }

    @Test
    fun initialLoad_usesSavedFiltersAndParsesCursor() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(feedJson(itemJson(1, "1700000000000"), itemJson(2, "1600000000000")))
        stubZoneTabs()

        val viewModel = createViewModel(savedTabKey = "all", savedSubType = "all")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("all", state.selectedCategory.tabKey)
        assertEquals("all", state.selectedSubType)
        assertEquals(listOf(1L, 2L), state.items.map { it.id })
        assertEquals(1600000000000L, state.lastTime)
        assertTrue(state.hasMore)
        assertFalse(state.isInitialLoading)
        assertNull(state.errorMessage)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun initialLoad_failureSetsErrorMessage() = runTest(dispatchers.testDispatcher) {
        mock.enqueue("""{"success":false,"code":500,"msg":"网络异常","data":null}""")
        stubZoneTabs()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.items.isEmpty())
        assertFalse(state.isInitialLoading)
        assertTrue(state.errorMessage?.contains("网络异常") == true)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun initialLoad_emptyPageClearsHasMore() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(feedJson())
        stubZoneTabs()

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertFalse(viewModel.uiState.value.hasMore)
        assertEquals(0L, viewModel.uiState.value.lastTime)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun selectCategory_persistsFilterAndReloads() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(feedJson(itemJson(1, "100")))
        stubZoneTabs()

        val viewModel = createViewModel()
        advanceUntilIdle()
        mock.enqueue(feedJson(itemJson(2, "200")))
        viewModel.selectCategory(FeedCategory(tabKey = "z100", name = "手办", nodeId = 100L, zoneIds = "100"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("z100", state.selectedCategory.tabKey)
        assertEquals(100L, state.selectedCategory.nodeId)
        assertEquals(listOf(2L), state.items.map { it.id })
        assertEquals(200L, state.lastTime)
        // 真 DataStore 持久化验证
        assertEquals("z100", preferences.feedZoneTabKey)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun selectSameCategory_doesNotReload() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(feedJson(itemJson(1, "100")))
        stubZoneTabs()

        val viewModel = createViewModel()
        advanceUntilIdle()
        val countBefore = mock.requestCount
        viewModel.selectCategory(FeedCategory(tabKey = "all", name = "全部", nodeId = 0L, zoneIds = ""))
        advanceUntilIdle()

        assertEquals(countBefore, mock.requestCount)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun selectSubType_persistsFilterAndReloads() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(feedJson(itemJson(1, "100")))
        stubZoneTabs()

        val viewModel = createViewModel()
        advanceUntilIdle()
        mock.enqueue(feedJson(itemJson(2, "200")))
        viewModel.selectSubType("confirm")
        advanceUntilIdle()

        assertEquals("confirm", viewModel.uiState.value.selectedSubType)
        assertEquals(listOf(2L), viewModel.uiState.value.items.map { it.id })
        assertEquals("confirm", preferences.feedSubType)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun resetFilters_restoresDefaultsAndPersists() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(feedJson(itemJson(1, "100")))
        stubZoneTabs()

        val viewModel = createViewModel(savedTabKey = "z100", savedSubType = "all")
        advanceUntilIdle()
        mock.enqueue(feedJson(itemJson(2, "200")))
        viewModel.resetFilters()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("all", state.selectedCategory.tabKey)
        assertEquals("recommend", state.selectedSubType)
        assertEquals(listOf(2L), state.items.map { it.id })
        assertEquals("all", preferences.feedZoneTabKey)
        assertEquals("recommend", preferences.feedSubType)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun refreshFeed_replacesItemsAndClearsError() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(feedJson(itemJson(1, "100")))
        stubZoneTabs()

        val viewModel = createViewModel()
        advanceUntilIdle()
        mock.enqueue(feedJson(itemJson(2, "200")))
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(2L), state.items.map { it.id })
        assertEquals(200L, state.lastTime)
        assertFalse(state.isRefreshing)
        assertNull(state.errorMessage)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun refreshFeed_failureKeepsExistingItems() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(feedJson(itemJson(1, "100")))
        stubZoneTabs()

        val viewModel = createViewModel()
        advanceUntilIdle()
        mock.enqueue("""{"success":false,"code":500,"msg":"刷新失败","data":null}""")
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1L), state.items.map { it.id })
        assertFalse(state.isRefreshing)
        assertTrue(state.errorMessage?.contains("刷新失败") == true)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadNextPage_appendsAndUsesLastTimeCursor() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(feedJson(itemJson(1, "100")))
        stubZoneTabs()

        val viewModel = createViewModel()
        advanceUntilIdle()
        mock.enqueue(feedJson(itemJson(2, "200"), itemJson(3, "300")))
        viewModel.loadNextPage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1L, 2L, 3L), state.items.map { it.id })
        assertEquals(300L, state.lastTime)
        assertFalse(state.isLoadingMore)
        assertTrue(state.hasMore)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadNextPage_emptyPageClearsHasMore() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(feedJson(itemJson(1, "100")))
        stubZoneTabs()

        val viewModel = createViewModel()
        advanceUntilIdle()
        mock.enqueue(feedJson())
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(listOf(1L), viewModel.uiState.value.items.map { it.id })
        assertFalse(viewModel.uiState.value.hasMore)
        assertFalse(viewModel.uiState.value.isLoadingMore)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadNextPage_failureKeepsItemsAndSetsLoadMoreError() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(feedJson(itemJson(1, "100")))
        stubZoneTabs()

        val viewModel = createViewModel()
        advanceUntilIdle()
        mock.enqueue("""{"success":false,"code":500,"msg":"翻页失败","data":null}""")
        viewModel.loadNextPage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1L), state.items.map { it.id })
        assertTrue(state.loadMoreError?.contains("翻页失败") == true)
        assertFalse(state.isLoadingMore)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadNextPage_whenHasMoreIsFalse_skipsRequest() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(feedJson())
        stubZoneTabs()

        val viewModel = createViewModel()
        advanceUntilIdle()
        val countBefore = mock.requestCount
        viewModel.loadNextPage()
        advanceUntilIdle()

        assertEquals(countBefore, mock.requestCount)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun scrollPosition_saveAndReset() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(feedJson())
        stubZoneTabs()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.saveScrollPosition(8, 120)
        assertEquals(8, viewModel.savedScrollIndex)
        assertEquals(120, viewModel.savedScrollOffset)

        viewModel.resetScrollPosition()
        assertEquals(0, viewModel.savedScrollIndex)
        assertEquals(0, viewModel.savedScrollOffset)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun dynamicCategories_updatesWhenFocusZonesEmit() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(feedJson(itemJson(1, "100")))
        stubZoneTabs()

        val viewModel = createViewModel()
        advanceUntilIdle()

        // 初始仅有全部与关注
        assertEquals(2, viewModel.uiState.value.categories.size)

        // 模拟用户关注了手办专区 (nodeId = 100)
        mock.dynamicResponder = {
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true,"sort":"recent","hiddenZoneIds":[],"customOrder":[],"recentZoneIds":[]},"focusZones":[{"nodeId":100,"name":"figure","nameCN":"手办","icon":"figure.jpg"}],"groups":[]}}""" to io.ktor.http.HttpStatusCode.OK
        }
        zoneRepository.getZoneHomeTabs(forceRefresh = true)
        advanceUntilIdle()

        val categories = viewModel.uiState.value.categories
        assertEquals(3, categories.size)
        assertEquals("all", categories[0].tabKey)
        assertEquals("follow", categories[1].tabKey)
        assertEquals("100", categories[1].zoneIds)
        assertEquals("z100", categories[2].tabKey)
        assertEquals("手办", categories[2].name)
        assertEquals(100L, categories[2].nodeId)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun dynamicCategories_respectsHiddenZoneIds() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(feedJson())
        stubZoneTabs()

        val viewModel = createViewModel()
        advanceUntilIdle()

        mock.dynamicResponder = {
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true,"sort":"recent","hiddenZoneIds":[200],"customOrder":[],"recentZoneIds":[]},"focusZones":[{"nodeId":100,"nameCN":"手办"},{"nodeId":200,"nameCN":"模型"}],"groups":[]}}""" to io.ktor.http.HttpStatusCode.OK
        }
        zoneRepository.getZoneHomeTabs(forceRefresh = true)
        advanceUntilIdle()

        val categories = viewModel.uiState.value.categories
        // 应仅有 全部、关注、手办（模型已被隐藏剔除）
        assertEquals(3, categories.size)
        assertEquals("all", categories[0].tabKey)
        assertEquals("follow", categories[1].tabKey)
        assertEquals("z100", categories[2].tabKey)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun dynamicCategories_respectsCustomOrder() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(feedJson())
        stubZoneTabs()

        val viewModel = createViewModel()
        advanceUntilIdle()

        mock.dynamicResponder = {
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":false,"sort":"custom","hiddenZoneIds":[],"customOrder":[200,100],"recentZoneIds":[]},"focusZones":[{"nodeId":100,"nameCN":"手办"},{"nodeId":200,"nameCN":"模型"}],"groups":[]}}""" to io.ktor.http.HttpStatusCode.OK
        }
        zoneRepository.getZoneHomeTabs(forceRefresh = true)
        advanceUntilIdle()

        val categories = viewModel.uiState.value.categories
        // 不展示全部；首先为关注，接着为 模型(200)，最后为 手办(100)
        assertEquals(3, categories.size)
        assertEquals("follow", categories[0].tabKey)
        assertEquals("z200", categories[1].tabKey)
        assertEquals("z100", categories[2].tabKey)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun emptyFollow_doesNotQueryAllFeed() = runTest(dispatchers.testDispatcher) {
        stubZoneTabs()

        val viewModel = createViewModel(savedTabKey = "follow")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("follow", state.selectedCategory.tabKey)
        assertTrue(state.items.isEmpty())
        assertFalse(state.hasMore)
        assertFalse(state.isInitialLoading)
        // 验证没有向 feed 接口请求：只有 zone-tabs 1 个请求
        assertEquals(1, mock.requestCount)
        viewModel.viewModelScope.cancel()
    }
}
