package com.nobody.repoi.ui.album

import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.AlbumOrder
import com.nobody.repoi.data.remote.model.AlbumZoneTab
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDataStores
import com.nobody.repoi.test.TestDispatchers
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * AlbumViewModel 的 KMP 移植版（真实仓储 + MockEngine + 真 TokenManager，替代 Mockito）。
 *
 * 原 `verifyNoInteractions` 翻译为 `mock.requestCount == 0`；
 * `hasMore` 由真仓储按 `rawList.size >= pageSize` 计算，需要 `hasMore=true` 时
 * 塞满 24 条 raw（无效 id 会被 `targetAlbumId > 0` 滤掉，不污染 items 断言）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumViewModelTest {

    private val dispatchers = TestDispatchers()
    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var stores: TestDataStores
    private lateinit var tokenManager: TokenManager
    private lateinit var repository: AlbumRepository

    @BeforeTest
    fun setUp() {
        dispatchers.setUp()
        mock = MockRepoEngine(dispatchers.engineDispatcher)
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        stores = TestDataStores()
        tokenManager = TokenManager(stores.createDataStore("token"))
        repository = AlbumRepository(api)
    }

    @AfterTest
    fun tearDown() {
        client.close()
        stores.cleanUp()
        dispatchers.tearDown()
    }

    private fun albumJson(id: Long): String =
        """{"id":$id,"itemId":${1000L + id},"nameCN":"相册$id"}"""

    private fun albumListJson(ids: List<Long>): String {
        val items = ids.joinToString(",") { albumJson(it) }
        return """{"success":true,"code":200,"msg":"ok","data":{"list":[$items]}}"""
    }

    private val tabsJson: String =
        """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[],"homeZones":[]}}"""

    private fun failJson(code: Int, msg: String): String =
        """{"success":false,"code":$code,"msg":"$msg","data":null}"""

    private fun createViewModel(token: String = "tok"): AlbumViewModel {
        tokenManager.currentToken = token
        return AlbumViewModel(repository, tokenManager)
    }

    @Test
    fun initialLoad_tabsAndFirstPage() = runTest(dispatchers.testDispatcher) {
        mock.respondWhen(Regex.fromLiteral("api/zone/home-tabs"), tabsJson)
        mock.respondWhen(Regex.fromLiteral("api/album/list"), albumListJson(listOf(1L, 2L)))

        val viewModel = createViewModel()
        viewModel.loadInitial()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isTabsLoading)
        assertFalse(state.isLoading)
        assertEquals(2, state.items.size)
        assertEquals(1, state.page)
        assertFalse(state.hasMore)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun selectOrder_resetsPageWithNewOrder() = runTest(dispatchers.testDispatcher) {
        // loadInitial 串行：tabs → recommend；selectOrder 后再打 latest。队列顺序确定。
        mock.enqueue(tabsJson)
        mock.enqueue(albumListJson(listOf(1L)))
        mock.enqueue(albumListJson(listOf(9L)))

        val viewModel = createViewModel()
        viewModel.loadInitial()
        advanceUntilIdle()
        viewModel.selectOrder(AlbumOrder.Latest)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AlbumOrder.Latest, state.order)
        assertEquals(1, state.page)
        assertEquals(9L, state.items.single().targetAlbumId)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadMore_appendsAndDedups() = runTest(dispatchers.testDispatcher) {
        // page1 需 hasMore=true：塞 24 条 raw，其中 22 条 id=0 被 targetAlbumId>0 滤掉。
        val page1Raw = listOf(1L, 2L) + List(22) { 0L }
        mock.enqueue(tabsJson)
        mock.enqueue(albumListJson(page1Raw))
        mock.enqueue(albumListJson(listOf(2L, 3L)))

        val viewModel = createViewModel()
        viewModel.loadInitial()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1L, 2L, 3L), state.items.map { it.targetAlbumId })
        assertFalse(state.hasMore)
        assertFalse(state.isAppending)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun followZoneWithoutToken_showsLoginGuide() = runTest(dispatchers.testDispatcher) {
        val viewModel = createViewModel(token = "")
        viewModel.selectZone(AlbumZoneTab.FOLLOW)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.loginRequired)
        assertEquals(0, mock.requestCount)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun rankingSingleFailure_hidesFailedSectionOnly() = runTest(dispatchers.testDispatcher) {
        mock.respondWhen(Regex.fromLiteral("api/zone/home-tabs"), tabsJson)
        val emptyList = albumListJson(emptyList())
        val fail = failJson(500, "榜单失败")
        mock.dynamicResponder = { req: HttpRequestData ->
            val path = req.url.encodedPath
            when {
                // praise 走 GET + query type。
                path.contains("api/item/praise/top") -> {
                    val type = req.url.parameters["type"].orEmpty()
                    val body = when (type) {
                        "" -> albumListJson(listOf(6L))
                        "week" -> albumListJson(listOf(7L))
                        else -> fail
                    }
                    body to HttpStatusCode.OK
                }
                // 榜单窗口请求带 releaseStart；普通 feed 不带。
                path.contains("api/album/list") -> {
                    val form = (req.body as? FormDataContent)?.formData
                    val body = if (form?.get("releaseStart") != null) albumListJson(listOf(5L)) else emptyList
                    body to HttpStatusCode.OK
                }
                else -> null
            }
        }

        val viewModel = createViewModel()
        viewModel.loadInitial()
        advanceUntilIdle()
        viewModel.enterRanking()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showRanking)
        assertFalse(state.isSectionsLoading)
        assertEquals(5, state.sections.size)
        assertTrue(state.sections.none { it.title == "本月点赞榜" })
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun feedUnauthorized_flagsLoginRequired() = runTest(dispatchers.testDispatcher) {
        mock.respondWhen(Regex.fromLiteral("api/zone/home-tabs"), tabsJson)
        mock.respondWhen(Regex.fromLiteral("api/album/list"), failJson(4001, "登录过期"))

        val viewModel = createViewModel()
        viewModel.loadInitial()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.loginRequired)
        assertFalse(state.isLoading)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun tabsFailure_fallsBackToAllAndLoadsFeed() = runTest(dispatchers.testDispatcher) {
        mock.respondWhen(Regex.fromLiteral("api/zone/home-tabs"), failJson(500, "分区失败"))
        mock.respondWhen(Regex.fromLiteral("api/album/list"), albumListJson(listOf(1L)))

        val viewModel = createViewModel()
        viewModel.loadInitial()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(AlbumZoneTab.ALL), state.tabs)
        assertEquals(1, state.items.size)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun unauthorizedThenSuccess_clearsLoginRequired() = runTest(dispatchers.testDispatcher) {
        mock.enqueue(tabsJson)
        mock.enqueue(failJson(4001, "登录过期"))
        mock.enqueue(albumListJson(listOf(2L)))

        val viewModel = createViewModel()
        viewModel.loadInitial()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.loginRequired)
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2L, state.items.single().targetAlbumId)
        assertFalse(state.loginRequired)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun rankWindows_monthWeekYearBounds() {
        val windows = rankWindows(LocalDate(2026, 9, 11))
        assertEquals(Pair("2026-09-01 00:00:00", "2026-09-30 23:59:59"), windows.month)
        assertEquals(Pair("2026-09-07 00:00:00", "2026-09-13 23:59:59"), windows.week)
        assertEquals(Pair("2026-01-01 00:00:00", "2026-12-31 23:59:59"), windows.year)
    }

    @Test
    fun selectZone_resetsSavedScrollPosition() = runTest(dispatchers.testDispatcher) {
        mock.respondWhen(Regex.fromLiteral("api/zone/home-tabs"), tabsJson)
        mock.respondWhen(Regex.fromLiteral("api/album/list"), albumListJson(listOf(1L)))

        val viewModel = createViewModel()
        viewModel.loadInitial()
        advanceUntilIdle()
        viewModel.saveScrollPosition(12, 34)
        viewModel.selectZone(AlbumZoneTab.FOLLOW)
        advanceUntilIdle()

        assertEquals(0, viewModel.savedScrollIndex)
        assertEquals(0, viewModel.savedScrollOffset)
        viewModel.viewModelScope.cancel()
    }
}
