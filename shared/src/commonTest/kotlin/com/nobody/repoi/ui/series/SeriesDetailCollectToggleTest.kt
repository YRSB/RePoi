package com.nobody.repoi.ui.series

import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.SeriesDetailRepository
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.domain.usecase.collect.ToggleItemCollectUseCase
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
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
 * 系列详情收藏切换测试：验证爱心切换真的走 `ToggleItemCollectUseCase`（发网络），
 * 不是旧实现的「纯本地翻转」。类名刻意不含 `ViewModel`，避免被既有测试过滤误伤。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailCollectToggleTest {

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
    fun toggleCollect_callsUseCaseAndFlipsState() = runTest(testDispatcher) {
        val detailMock = MockApi()
        val collectMock = MockApi()
        val viewModel = newViewModel(detailMock, collectMock)
        detailMock.enqueue(
            detailMock.ok("""{"itemData":{"id":555,"nameCN":"系列A","collect":30}}""")
        )
        collectMock.route("collect/state", collectMock.ok("{}"))
        collectMock.route("collect/item/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.loadDetail(SeriesNavParam(seriesId = 500L))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isCollected)
        // 丢弃首屏阶段已消费的请求，保证下面断言只针对切换收藏
        collectMock.drainPaths()

        viewModel.toggleCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(1, viewModel.uiState.value.collectCountDelta)
        val recorded = collectMock.takeRequest()
        assertEquals("/api/collect/item/add", recorded.encodedPath)
        assertEquals("555", recorded.formEntries()["id"])
    }

    @Test
    fun toggleCollect_whenFailure_rollsBackAndReportsMessage() = runTest(testDispatcher) {
        val detailMock = MockApi()
        val collectMock = MockApi()
        val viewModel = newViewModel(detailMock, collectMock)
        detailMock.enqueue(
            detailMock.ok("""{"itemData":{"id":555,"nameCN":"系列A","collect":30}}""")
        )
        collectMock.route("collect/state", collectMock.ok("""{"collect":{"id":555,"state":"care"}}"""))
        collectMock.route("collect/del", collectMock.failure(500, "收藏失败"))

        viewModel.loadDetail(SeriesNavParam(seriesId = 500L))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCollected)
        collectMock.drainPaths()

        viewModel.toggleCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)
        assertEquals("收藏失败", viewModel.uiState.value.commentActionMessage)
    }

    /** 消费并丢弃已排队的请求记录（首屏加载会先打 collect/state）。 */
    private fun MockApi.drainPaths(): List<String?> = buildList {
        repeat(requestCount) { add(takeRequest().encodedPath) }
    }

    private fun newViewModel(detailMock: MockApi, collectMock: MockApi): SeriesDetailViewModel {
        val session = FakeUserSession("utoken-test")
        val collectRepository = CollectRepository(collectMock.api(testDispatcher), collectMock.json, session)
        return SeriesDetailViewModel(
            SeriesDetailRepository(detailMock.api(testDispatcher), collectRepository),
            ToggleItemCollectUseCase(collectRepository),
            CommentInteractionDelegate(
                CommentRepository(detailMock.api(testDispatcher), session),
                StickerRepository(detailMock.api(testDispatcher)),
                session
            )
        )
    }
}
