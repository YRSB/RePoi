package com.nobody.repoi.ui.person

import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.PersonDetailRepository
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
 * 人物详情收藏切换测试：验证爱心切换真的走 `ToggleItemCollectUseCase`（发网络），
 * 不是旧实现的「纯本地翻转」。类名刻意不含 `ViewModel`，避免被既有测试过滤误伤。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailCollectToggleTest {

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
            detailMock.ok("""{"result":{"person":{"id":321,"nameCN":"人物A","collect":10},"isAddFav":false}}""")
        )
        collectMock.route("collect/state", collectMock.ok("{}"))
        collectMock.route("collect/item/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.loadDetail(PersonNavParam(personId = 300L))
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
        assertEquals("321", recorded.formEntries()["id"])
    }

    @Test
    fun toggleCollect_whenFailure_rollsBackAndReportsMessage() = runTest(testDispatcher) {
        val detailMock = MockApi()
        val collectMock = MockApi()
        val viewModel = newViewModel(detailMock, collectMock)
        detailMock.enqueue(
            detailMock.ok("""{"result":{"person":{"id":321,"nameCN":"人物A","collect":10},"isAddFav":true}}""")
        )
        collectMock.route("collect/state", collectMock.ok("""{"collect":{"id":321,"state":"care"}}"""))
        collectMock.route("collect/del", collectMock.failure(500, "收藏失败"))

        viewModel.loadDetail(PersonNavParam(personId = 300L))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCollected)
        collectMock.drainPaths()

        viewModel.toggleCollect()
        advanceUntilIdle()

        // 失败回滚：乐观翻转为未收藏后又翻回已收藏，delta 归零并透出错误文案
        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)
        assertEquals("收藏失败", viewModel.uiState.value.commentActionMessage)
    }

    /** 消费并丢弃已排队的请求记录（首屏加载会先打 collect/state）。 */
    private fun MockApi.drainPaths(): List<String?> = buildList {
        repeat(requestCount) { add(takeRequest().encodedPath) }
    }

    private fun newViewModel(detailMock: MockApi, collectMock: MockApi): PersonDetailViewModel {
        val session = FakeUserSession("utoken-test")
        val collectRepository = CollectRepository(collectMock.api(testDispatcher), collectMock.json, session)
        return PersonDetailViewModel(
            PersonDetailRepository(detailMock.api(testDispatcher), collectRepository),
            ToggleItemCollectUseCase(collectRepository),
            CommentInteractionDelegate(
                CommentRepository(detailMock.api(testDispatcher), session),
                StickerRepository(detailMock.api(testDispatcher)),
                session
            )
        )
    }
}
