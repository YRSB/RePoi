package com.nobody.repoi.ui.series

import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.SeriesDetailRepository
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDataStores
import com.nobody.repoi.test.TestDispatchers
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * SeriesDetailViewModel 的 KMP 移植版（真实仓储 + MockEngine + 真 CommentInteractionDelegate，
 * 替代 Mockito）。
 *
 * `loadDetail` 第二阶段并发 8 路，队列顺序不稳定：评论与子/相关系列
 * （同一端点 `api/series/link/series`，表单 `isChild` 区分）走
 * [MockRepoEngine.dynamicResponder] 分流，其余端点走 path 规则。
 * 原 `verify(...)` 翻译为对应 path 的请求计数断言。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailViewModelTest {

    private val dispatchers = TestDispatchers()
    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var stores: TestDataStores
    private lateinit var tokenManager: TokenManager
    private lateinit var repository: SeriesDetailRepository
    private lateinit var delegate: CommentInteractionDelegate

    @BeforeTest
    fun setUp() {
        dispatchers.setUp()
        mock = MockRepoEngine(dispatchers.engineDispatcher)
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        stores = TestDataStores()
        tokenManager = TokenManager(stores.createDataStore("token"))
        tokenManager.currentToken = "utoken-test"
        repository = SeriesDetailRepository(api, CollectRepository(api, json))
        delegate = CommentInteractionDelegate(
            CommentRepository(api, tokenManager),
            StickerRepository(api),
            tokenManager,
            UserRepository(api, tokenManager)
        )
    }

    @AfterTest
    fun tearDown() {
        client.close()
        stores.cleanUp()
        dispatchers.tearDown()
    }

    private fun ok(dataJson: String): String =
        """{"success":true,"code":200,"msg":"ok","data":$dataJson}"""

    private val failEnvelope: String =
        """{"success":false,"code":500,"msg":"server error","data":null}"""

    private val seriesJson: String =
        """{"id":1,"nodeId":2,"itemId":3,"nameCN":"测试系列","commentCount":8}"""

    private fun countPath(fragment: String): Int =
        mock.engine.requestHistory.count { it.url.encodedPath.contains(fragment) }

    /**
     * 注册全部次级端点成功规则；评论与 link/series 走 dynamicResponder 分流。
     */
    private fun stubAll(
        detailBody: String = ok("""{"itemData":$seriesJson}"""),
        collectBody: String = ok("""{"collect":null}"""),
        maskBody: String = ok("""{"state":0}"""),
        secondaryFail: Boolean = false
    ) {
        mock.respondWhen(Regex.fromLiteral("api/item/get-v2"), detailBody)
        val sub = if (secondaryFail) failEnvelope else ok("""{"list":[{"id":20,"nameCN":"子系列"}]}""")
        val relate = if (secondaryFail) failEnvelope else ok("""{"list":[{"id":30,"nameCN":"相关系列"}]}""")
        val hobbies = if (secondaryFail) failEnvelope else ok("""{"hobbys":[{"id":10,"nameCN":"关联手办"}]}""")
        val albums = if (secondaryFail) failEnvelope else ok("""{"album":[{"id":40,"name":"相册"}]}""")
        val goods = if (secondaryFail) failEnvelope else ok("""{"list":[{"id":50,"name":"商品"}]}""")
        val forum = if (secondaryFail) failEnvelope else ok("""{"forum":{"id":1,"name":"论坛","postCount":3}}""")
        mock.respondWhen(Regex.fromLiteral("api/item/relate/hobby"), hobbies)
        mock.respondWhen(Regex.fromLiteral("api/series/album"), albums)
        mock.respondWhen(Regex.fromLiteral("api/goods/tb/more-v2"), goods)
        mock.respondWhen(Regex.fromLiteral("api/series/forum"), forum)
        mock.respondWhen(Regex.fromLiteral("api/collect/state"), collectBody)
        mock.respondWhen(Regex.fromLiteral("api/mask/state-v2"), maskBody)
        val emptyComments = ok("""{"list":[]}""")
        mock.dynamicResponder = { req: HttpRequestData ->
            val path = req.url.encodedPath
            when {
                path.contains("api/comment/get") -> emptyComments to HttpStatusCode.OK
                path.contains("api/series/link/series") -> {
                    val form = (req.body as? FormDataContent)?.formData
                    val isChild = form?.get("isChild") ?: req.url.parameters["isChild"]
                    val body = if (isChild == "2") relate else sub
                    body to HttpStatusCode.OK
                }
                else -> null
            }
        }
    }

    @Test
    fun loadDetail_populatesAllSections() = runTest(dispatchers.testDispatcher) {
        stubAll(
            collectBody = ok("""{"collect":{"state":"want"}}"""),
            maskBody = ok("""{"state":1}""")
        )

        val viewModel = SeriesDetailViewModel(repository, delegate)
        viewModel.loadDetail(SeriesNavParam(seriesId = 1L, seriesNodeId = 2L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isInitialLoading)
        assertEquals("测试系列", state.series?.nameCN)
        assertEquals(2L, state.seriesNodeId)
        assertEquals(listOf(10L), state.hobbys.map { it.id })
        assertEquals(listOf(20L), state.subSeries.map { it.id })
        assertEquals(listOf(30L), state.relateSeries.map { it.id })
        assertEquals(listOf(40L), state.albums.map { it.id })
        assertEquals(listOf(50L), state.goods.map { it.id })
        assertEquals("论坛", state.forum?.forum?.name)
        assertTrue(state.isCollected)
        assertTrue(state.originalCollected)
        assertTrue(state.isMasked)
        assertFalse(state.isHobbysLoading)
        assertFalse(state.isSubSeriesLoading)
        assertFalse(state.isRelateSeriesLoading)
        assertFalse(state.isAlbumsLoading)
        assertFalse(state.isGoodsLoading)
        assertFalse(state.isForumLoading)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadDetail_coreFailureStopsSecondaryLoads() = runTest(dispatchers.testDispatcher) {
        stubAll(detailBody = """{"success":false,"code":500,"msg":"系列不存在","data":null}""")

        val viewModel = SeriesDetailViewModel(repository, delegate)
        viewModel.loadDetail(SeriesNavParam(seriesId = 1L, seriesNodeId = 2L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isInitialLoading)
        // ApiException message 形如 "API 业务异常 (code=..): .."，用 contains 断言。
        assertTrue(state.errorMessage?.contains("系列不存在") == true)
        assertTrue(state.hobbys.isEmpty())
        assertEquals(0, countPath("api/item/relate/hobby"))
        assertEquals(0, countPath("api/series/album"))
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun secondaryFailures_degradeToEmptySections() = runTest(dispatchers.testDispatcher) {
        stubAll(secondaryFail = true)

        val viewModel = SeriesDetailViewModel(repository, delegate)
        viewModel.loadDetail(SeriesNavParam(seriesId = 1L, seriesNodeId = 2L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("测试系列", state.series?.nameCN)
        assertTrue(state.hobbys.isEmpty())
        assertTrue(state.subSeries.isEmpty())
        assertTrue(state.relateSeries.isEmpty())
        assertTrue(state.albums.isEmpty())
        assertTrue(state.goods.isEmpty())
        assertNull(state.forum)
        assertFalse(state.isCollected)
        assertFalse(state.isMasked)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadDetail_sameSeries_isIgnoredAfterSuccess() = runTest(dispatchers.testDispatcher) {
        stubAll()

        val viewModel = SeriesDetailViewModel(repository, delegate)
        viewModel.loadDetail(SeriesNavParam(seriesId = 1L, seriesNodeId = 2L))
        advanceUntilIdle()
        viewModel.loadDetail(SeriesNavParam(seriesId = 1L, seriesNodeId = 2L))
        advanceUntilIdle()

        assertEquals(1, countPath("api/item/get-v2"))
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun toggleCollect_andMask_updateState() = runTest(dispatchers.testDispatcher) {
        stubAll(collectBody = ok("""{"collect":{"state":"want"}}"""))

        val viewModel = SeriesDetailViewModel(repository, delegate)
        viewModel.loadDetail(SeriesNavParam(seriesId = 1L, seriesNodeId = 2L))
        advanceUntilIdle()

        viewModel.toggleCollect()
        assertFalse(viewModel.uiState.value.isCollected)
        assertEquals(-1, viewModel.uiState.value.collectCountDelta)

        viewModel.toggleCollect()
        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)

        viewModel.toggleMask()
        assertTrue(viewModel.uiState.value.isMasked)
        viewModel.toggleMask()
        assertFalse(viewModel.uiState.value.isMasked)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun toggleIntroExpanded_flipsState() = runTest(dispatchers.testDispatcher) {
        stubAll()
        val viewModel = SeriesDetailViewModel(repository, delegate)
        viewModel.loadDetail(SeriesNavParam(seriesId = 1L, seriesNodeId = 2L))
        advanceUntilIdle()

        viewModel.toggleIntroExpanded()
        assertTrue(viewModel.uiState.value.isIntroExpanded)

        viewModel.toggleIntroExpanded()
        assertFalse(viewModel.uiState.value.isIntroExpanded)
        viewModel.viewModelScope.cancel()
    }
}
