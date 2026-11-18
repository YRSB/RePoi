package com.nobody.repoi.ui.gallery

import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.MockApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

/**
 * 全屏画廊收藏切换测试：写侧直接调用 [CollectRepository.toggleItemCollect]，
 * 成功后由仓储推进收藏变更代数（不再向事件总线广播）。
 *
 * 类名刻意不含 `ViewModel`，避免受既有 ViewModel 测试过滤规则影响。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GalleryCollectToggleTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun toggleCollect_addsItemAndAdvancesMutationGeneration() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        collectMock.route("collect/item/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        val generationBefore = collectRepository.collectMutationGeneration.value

        val nextCollected = viewModel.toggleCollect(UserCollectPicItem(id = 88L, collect = 5))
        advanceUntilIdle()

        assertTrue(nextCollected)
        assertTrue(viewModel.uiState.value.isCollected)
        // 徽章在乐观更新时 +1（初始 0，列表项自带的 collect 由切页回填，不在本用例路径内）
        assertEquals(1, viewModel.uiState.value.collectCount)
        assertEquals(CollectConstants.STATE_CARE, collectRepository.observeCollectState(88L).value?.state)
        assertEquals(generationBefore + 1, collectRepository.collectMutationGeneration.value)
        val recorded = collectMock.takeRequest()
        assertEquals("/api/collect/item/add", recorded.encodedPath)
        assertEquals("88", recorded.formEntries()["id"])
    }

    @Test
    fun toggleCollect_whenFailure_rollsBackAndKeepsGeneration() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        collectMock.route("collect/item/add", collectMock.failure(500, "收藏失败"))
        val generationBefore = collectRepository.collectMutationGeneration.value

        viewModel.toggleCollect(UserCollectPicItem(id = 88L, collect = 5))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCollected)
        assertEquals(0, viewModel.uiState.value.collectCount)
        assertEquals("收藏失败", viewModel.uiState.value.toastMessage)
        assertEquals(generationBefore, collectRepository.collectMutationGeneration.value)
        assertNull(collectRepository.observeCollectState(88L).value)
        val recordedFailure = collectMock.takeRequest()
        assertEquals("/api/collect/item/add", recordedFailure.encodedPath)
        assertEquals("88", recordedFailure.formEntries()["id"])
    }

    @Test
    fun toggleCollect_afterPageRefresh_repeatedTogglesReturnToServerCount() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        val item = UserCollectPicItem(id = 88L, collect = 5)
        collectMock.route("collect/state", collectMock.ok("{}"))
        collectMock.route("collect/item/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectMock.route("collect/del", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.onPageChanged(item, 0)
        advanceTimeBy(501)
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.collectCount)
        assertFalse(viewModel.uiState.value.isCollected)
        assertNull(collectRepository.observeCollectState(88L).value)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        collectMock.drainRequests()

        assertTrue(viewModel.toggleCollect(item))
        advanceUntilIdle()

        assertEquals(6, viewModel.uiState.value.collectCount, "未收藏基线添加后人数应比服务端原始值 +1")
        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(CollectConstants.STATE_CARE, collectRepository.observeCollectState(88L).value?.state)
        assertEquals(1L, collectRepository.collectMutationGeneration.value)
        collectMock.assertWriteRequest("/api/collect/item/add")

        assertFalse(viewModel.toggleCollect(item))
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.collectCount, "未收藏基线往返后人数必须回到服务端原始值")
        assertFalse(viewModel.uiState.value.isCollected)
        assertNull(collectRepository.observeCollectState(88L).value)
        assertEquals(2L, collectRepository.collectMutationGeneration.value)
        collectMock.assertWriteRequest("/api/collect/del")
    }

    @Test
    fun toggleCollect_whenInitiallyCollected_repeatedTogglesReturnToServerCount() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        val item = UserCollectPicItem(id = 88L, collect = 5)
        collectMock.route("collect/state", collectMock.ok("""{"collect":{"id":88,"state":"care"}}"""))
        collectMock.route("collect/item/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectMock.route("collect/del", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.onPageChanged(item, 0)
        advanceTimeBy(501)
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.collectCount)
        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(CollectConstants.STATE_CARE, collectRepository.observeCollectState(88L).value?.state)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        collectMock.drainRequests()

        assertFalse(viewModel.toggleCollect(item))
        advanceUntilIdle()

        assertEquals(4, viewModel.uiState.value.collectCount, "已收藏基线取消后人数应比服务端原始值 -1")
        assertFalse(viewModel.uiState.value.isCollected)
        assertNull(collectRepository.observeCollectState(88L).value)
        assertEquals(1L, collectRepository.collectMutationGeneration.value)
        collectMock.assertWriteRequest("/api/collect/del")

        assertTrue(viewModel.toggleCollect(item))
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.collectCount, "已收藏基线往返后人数必须回到服务端原始值")
        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(CollectConstants.STATE_CARE, collectRepository.observeCollectState(88L).value?.state)
        assertEquals(2L, collectRepository.collectMutationGeneration.value)
        collectMock.assertWriteRequest("/api/collect/item/add")
    }

    @Test
    fun toggleCollect_whenAddFails_restoresServerCount() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        val item = UserCollectPicItem(id = 88L, collect = 5)
        collectMock.route("collect/state", collectMock.ok("{}"))
        collectMock.route("collect/item/add", collectMock.failure(500, "收藏失败"))

        viewModel.onPageChanged(item, 0)
        advanceTimeBy(501)
        advanceUntilIdle()
        collectMock.drainRequests()

        assertTrue(viewModel.toggleCollect(item))
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.collectCount, "添加失败必须把人数恢复为服务端原始值")
        assertFalse(viewModel.uiState.value.isCollected)
        assertEquals("收藏失败", viewModel.uiState.value.toastMessage)
        assertNull(collectRepository.observeCollectState(88L).value)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        assertEquals("/api/collect/item/add", collectMock.takeRequest().encodedPath)
    }

    @Test
    fun toggleCollect_whenDeleteFails_restoresServerCount() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        val item = UserCollectPicItem(id = 88L, collect = 5)
        collectMock.route("collect/state", collectMock.ok("""{"collect":{"id":88,"state":"care"}}"""))
        collectMock.route("collect/del", collectMock.failure(500, "取消收藏失败"))

        viewModel.onPageChanged(item, 0)
        advanceTimeBy(501)
        advanceUntilIdle()
        collectMock.drainRequests()

        assertFalse(viewModel.toggleCollect(item))
        advanceUntilIdle()

        assertEquals(5, viewModel.uiState.value.collectCount, "取消失败必须把人数恢复为服务端原始值")
        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals("取消收藏失败", viewModel.uiState.value.toastMessage)
        assertEquals(CollectConstants.STATE_CARE, collectRepository.observeCollectState(88L).value?.state)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        assertEquals("/api/collect/del", collectMock.takeRequest().encodedPath)
    }


    @Test
    fun toggleCollect_refreshesCountFromServer() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        val item = UserCollectPicItem(id = 88L, collect = 5)
        collectMock.route("collect/state", collectMock.ok("{}"))
        collectMock.route("collect/item/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectMock.route(
            "hobby/collectCount",
            """{"success":true,"code":200,"msg":"ok","data":{"collect":{"care":38,"want":1,"preorder":0,"buy":2,"resell":0}}}"""
        )

        viewModel.onPageChanged(item, 0)
        advanceTimeBy(501)
        advanceUntilIdle()
        assertEquals(5, viewModel.uiState.value.collectCount)
        collectMock.drainRequests()

        assertTrue(viewModel.toggleCollect(item))
        advanceUntilIdle()

        assertEquals(41, viewModel.uiState.value.collectCount, "写成功后徽章应取服务端五分类人数总和")
        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(1L, collectRepository.collectMutationGeneration.value)
        val paths = buildList {
            repeat(collectMock.requestCount) { add(collectMock.takeRequest().encodedPath) }
        }
        assertTrue(paths.any { it?.contains("hobby/collectCount") == true }, "写成功后必须拉取服务端计数")
    }

    @Test
    fun toggleCollect_afterPageChange_resetsCountToNewItemBaseline() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        val first = UserCollectPicItem(id = 88L, collect = 5)
        val second = UserCollectPicItem(id = 99L, collect = 9)
        collectMock.route("collect/state", collectMock.ok("{}"))
        collectMock.route("collect/item/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectMock.route(
            "hobby/collectCount",
            """{"success":true,"code":200,"msg":"ok","data":{"collect":{"care":38,"want":1,"preorder":0,"buy":2,"resell":0}}}"""
        )

        viewModel.onPageChanged(first, 0)
        advanceTimeBy(501)
        advanceUntilIdle()
        collectMock.drainRequests()

        viewModel.toggleCollect(first)
        advanceUntilIdle()
        assertEquals(41, viewModel.uiState.value.collectCount)

        // 切图后服务端基线属于上一张图，徽章必须立即回到新图自身的人数
        viewModel.onPageChanged(second, 1)
        assertEquals(9, viewModel.uiState.value.collectCount, "切图后徽章必须重置为新图基线")
        advanceTimeBy(501)
        advanceUntilIdle()
        assertEquals(9, viewModel.uiState.value.collectCount, "切图后的延迟回填也不得沿用上一张图的计数")
    }
    /** 消费并丢弃切页阶段产生的请求记录，保证后续断言只针对切换收藏。 */
    private fun MockApi.drainRequests() {
        repeat(requestCount) { takeRequest() }
    }

    /** 写后会追加 collectCount 计数请求，故按路径筛选写请求而不是按 FIFO 首位断言。 */
    private fun MockApi.assertWriteRequest(path: String) {
        val recorded = (0 until requestCount).map { takeRequest() }
            .first { it.encodedPath == path }
        assertEquals("88", recorded.formEntries()["id"])
    }

    private fun newRepository(collectMock: MockApi): CollectRepository =
        CollectRepository(collectMock.api(testDispatcher), collectMock.json)

    private fun newViewModel(collectRepository: CollectRepository): PictureGalleryViewModel {
        // 本用例不触发原图下载，下载 HttpClient 用空响应 MockEngine 占位
        val downloadClient = HttpClient(MockEngine { respond(content = "", status = HttpStatusCode.OK) })
        return PictureGalleryViewModel(
            collectRepository,
            downloadClient
        )
    }
}
