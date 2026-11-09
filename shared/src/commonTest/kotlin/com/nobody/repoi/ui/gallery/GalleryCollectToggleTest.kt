package com.nobody.repoi.ui.gallery

import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.domain.usecase.collect.ToggleItemCollectUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
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
 * 全屏画廊收藏切换测试：写侧全量委托 `ToggleItemCollectUseCase`，
 * 成功后只更新收藏仓储脏位计数（不再向事件总线广播）。
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
    fun toggleCollect_addsItemAndMarksGalleryMutated() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        collectMock.route("collect/item/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        val ticksBefore = collectRepository.galleryMutationCount.value

        val nextCollected = viewModel.toggleCollect(UserCollectPicItem(id = 88L, collect = 5))
        advanceUntilIdle()

        assertTrue(nextCollected)
        assertTrue(viewModel.uiState.value.isCollected)
        // 徽章在乐观更新时 +1（初始 0，列表项自带的 collect 由切页回填，不在本用例路径内）
        assertEquals(1, viewModel.uiState.value.collectCount)
        assertEquals(ticksBefore + 1, collectRepository.galleryMutationCount.value)
        val recorded = collectMock.takeRequest()
        assertEquals("/api/collect/item/add", recorded.encodedPath)
        assertEquals("88", recorded.formEntries()["id"])
    }

    @Test
    fun toggleCollect_whenFailure_rollsBackAndKeepsTicks() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        collectMock.route("collect/item/add", collectMock.failure(500, "收藏失败"))
        val ticksBefore = collectRepository.galleryMutationCount.value

        viewModel.toggleCollect(UserCollectPicItem(id = 88L, collect = 5))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCollected)
        assertEquals(0, viewModel.uiState.value.collectCount)
        assertEquals("收藏失败", viewModel.uiState.value.toastMessage)
        assertEquals(ticksBefore, collectRepository.galleryMutationCount.value)
    }

    private fun newRepository(collectMock: MockApi): CollectRepository =
        CollectRepository(collectMock.api(testDispatcher), collectMock.json, FakeUserSession("utoken-test"))

    private fun newViewModel(collectRepository: CollectRepository): PictureGalleryViewModel {
        // 本用例不触发原图下载，下载 HttpClient 用空响应 MockEngine 占位
        val downloadClient = HttpClient(MockEngine { respond(content = "", status = HttpStatusCode.OK) })
        return PictureGalleryViewModel(
            collectRepository,
            ToggleItemCollectUseCase(collectRepository),
            downloadClient
        )
    }
}
