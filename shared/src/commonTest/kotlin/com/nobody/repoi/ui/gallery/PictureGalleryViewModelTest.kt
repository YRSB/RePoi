package com.nobody.repoi.ui.gallery

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.FakePlatformActions
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDispatchers
import com.nobody.repoi.test.recordedBody
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
 * PictureGalleryViewModel 的 KMP 移植版（真实 CollectRepository + MockEngine）。
 *
 * `verify()` 翻译为请求历史断言（按 path + 表单 id 过滤）；切页回填的 500ms delay
 * 由 runTest 虚拟时间自动推进。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PictureGalleryViewModelTest {

    private val dispatchers = TestDispatchers()
    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var repository: CollectRepository
    private lateinit var imageClient: HttpClient
    private lateinit var imageMock: MockRepoEngine

    @BeforeTest
    fun setUp() {
        dispatchers.setUp()
        mock = MockRepoEngine(dispatchers.engineDispatcher)
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        repository = CollectRepository(api, json)
        imageMock = MockRepoEngine(dispatchers.engineDispatcher)
        imageClient = HttpClient(imageMock.engine) { expectSuccess = true }
    }

    @AfterTest
    fun tearDown() {
        client.close()
        imageClient.close()
        dispatchers.tearDown()
    }

    private fun pic(id: Long = 100L, collect: Int = 5): UserCollectPicItem {
        return UserCollectPicItem(
            id = id,
            itemId = 900L,
            path = "2026/09/pic_$id.jpg",
            name = "图片$id",
            collect = collect
        )
    }

    private fun createViewModel(): PictureGalleryViewModel {
        return PictureGalleryViewModel(repository, imageClient, FakePlatformActions())
    }

    private fun collectStateBody(state: String?): String =
        if (state == null) {
            """{"success":true,"code":200,"msg":"ok","data":{"collect":null}}"""
        } else {
            """{"success":true,"code":200,"msg":"ok","data":{"collect":{"state":"$state"}}}"""
        }

    private fun relateBody(vararg items: String): String =
        """{"success":true,"code":200,"msg":"ok","data":{"list":[${items.joinToString(",")}]}}"""

    /** 统计指向某端点、表单 id 匹配的请求数。 */
    private fun countRequests(pathFragment: String, id: Long? = null): Int =
        mock.engine.requestHistory.count { req ->
            req.url.encodedPath.contains(pathFragment) &&
                (id == null || req.recordedBody().contains("id=$id"))
        }

    @Test
    fun onPageChanged_resetsStateAndBackfillsCollectAndRelate() = runTest(dispatchers.testDispatcher) {
        val item = pic()
        mock.respondWhen(
            Regex.fromLiteral("api/collect/state"),
            collectStateBody(CollectConstants.STATE_BUY)
        )
        mock.respondWhen(
            Regex.fromLiteral("api/pic/relate"),
            relateBody("""{"relateItem":{"id":7,"nameCN":"关联相册"}}""")
        )

        val viewModel = createViewModel()
        viewModel.markLongImage()
        viewModel.toggleRawSpec()

        viewModel.onPageChanged(item, 0)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isCollected)
        assertEquals(5, state.collectCount)
        assertEquals(listOf(7L), state.relateItems.map { it.id })
        assertFalse(state.isRelateLoading)
        assertFalse(state.showRaw)
        assertFalse(state.isLongImage)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun onPageChanged_onlyLatestIndexBackfillsCollectState() = runTest(dispatchers.testDispatcher) {
        val first = pic(1L)
        val second = pic(2L)
        mock.respondWhen(
            Regex.fromLiteral("api/collect/state"),
            collectStateBody(CollectConstants.STATE_CARE)
        )
        mock.respondWhen(Regex.fromLiteral("api/pic/relate"), relateBody())

        val viewModel = createViewModel()

        viewModel.onPageChanged(first, 0)
        viewModel.onPageChanged(second, 1)
        advanceUntilIdle()

        // 第一次切页的延迟回填任务被取消，getCollectState(1) 永不发出
        assertEquals(0, countRequests("api/collect/state", 1L))
        assertEquals(1, countRequests("api/collect/state", 2L))
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun toggleCollect_optimisticSuccessAndFailureRollback() = runTest(dispatchers.testDispatcher) {
        val item = pic()
        mock.respondWhen(
            Regex.fromLiteral("api/collect/state"),
            collectStateBody(null)
        )
        mock.respondWhen(Regex.fromLiteral("api/pic/relate"), relateBody())
        mock.respondWhen(
            Regex.fromLiteral("api/collect/item/add"),
            """{"success":false,"code":500,"msg":"收藏失败","data":null}"""
        )

        val viewModel = createViewModel()
        viewModel.onPageChanged(item, 0)
        advanceUntilIdle()
        assertEquals(5, viewModel.uiState.value.collectCount)

        val nowCollected = viewModel.toggleCollect(item)
        // 乐观更新同步生效
        assertTrue(nowCollected)
        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(6, viewModel.uiState.value.collectCount)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCollected)
        assertEquals(5, viewModel.uiState.value.collectCount)
        assertEquals("收藏失败", viewModel.uiState.value.toastMessage)
        assertEquals(1, countRequests("api/collect/item/add", item.id))
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun toggleCollect_uncollectUsesDeleteEndpoint() = runTest(dispatchers.testDispatcher) {
        val item = pic()
        mock.respondWhen(
            Regex.fromLiteral("api/collect/state"),
            collectStateBody(CollectConstants.STATE_BUY)
        )
        mock.respondWhen(Regex.fromLiteral("api/pic/relate"), relateBody())
        mock.respondWhen(
            Regex.fromLiteral("api/collect/del"),
            """{"success":true,"code":200,"msg":"ok","data":{}}"""
        )

        val viewModel = createViewModel()
        viewModel.onPageChanged(item, 0)
        advanceUntilIdle()

        val nowCollected = viewModel.toggleCollect(item)
        advanceUntilIdle()

        assertFalse(nowCollected)
        assertFalse(viewModel.uiState.value.isCollected)
        assertEquals(4, viewModel.uiState.value.collectCount)
        assertEquals(1, countRequests("api/collect/del", item.id))
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadRelate_requestsEachPictureOnlyOnceUnlessFailed() = runTest(dispatchers.testDispatcher) {
        val item = pic()
        mock.enqueue("""{"success":false,"code":500,"msg":"关联失败","data":null}""")
        mock.enqueue(relateBody("""{"relateItem":{"id":8}}"""))

        val viewModel = createViewModel()

        viewModel.loadRelate(item)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.relateItems.isEmpty())
        assertEquals(1, countRequests("api/pic/relate"))

        viewModel.loadRelate(item)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.relateItems.size)
        assertEquals(2, countRequests("api/pic/relate"))

        viewModel.loadRelate(item)
        advanceUntilIdle()
        assertEquals(2, countRequests("api/pic/relate"))
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun submitReport_successAndFailureSetToast() = runTest(dispatchers.testDispatcher) {
        val item = pic()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        mock.enqueue("""{"success":false,"code":500,"msg":"举报失败","data":null}""")

        val viewModel = createViewModel()

        viewModel.submitReport(item, "违规内容")
        advanceUntilIdle()
        assertEquals("举报已提交，感谢您的反馈", viewModel.uiState.value.toastMessage)

        viewModel.consumeMessage()
        assertNull(viewModel.uiState.value.toastMessage)

        viewModel.submitReport(item, "失败")
        advanceUntilIdle()
        assertEquals("举报失败", viewModel.uiState.value.toastMessage)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun rawSpecAndLongImage_flagsToggleIndependently() {
        val viewModel = createViewModel()

        assertTrue(viewModel.toggleRawSpec())
        assertTrue(viewModel.uiState.value.showRaw)
        assertFalse(viewModel.toggleRawSpec())
        assertFalse(viewModel.uiState.value.showRaw)

        viewModel.markLongImage()
        assertTrue(viewModel.uiState.value.isLongImage)
        viewModel.viewModelScope.cancel()
    }
}
