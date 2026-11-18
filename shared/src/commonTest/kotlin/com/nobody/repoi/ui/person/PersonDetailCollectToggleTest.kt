package com.nobody.repoi.ui.person

import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.PersonDetailRepository
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
 * 人物详情收藏切换一致性测试：覆盖添加、取消、失败回滚与重复切换四条链路，
 * 同时断言公开 UIState、CollectRepository 状态池与收藏变更代数。
 * 人物的基线 originalCollected 来自详情接口的 isAddFav 字段（加载时不写状态池）。
 * 类名刻意不含 `ViewModel`，避免被既有测试过滤误伤。
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
    fun toggleCollect_addsItemSyncsPoolAndAdvancesGeneration() = runTest(testDispatcher) {
        val detailMock = MockApi()
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(detailMock, collectRepository)
        detailMock.enqueue(
            detailMock.ok("""{"result":{"person":{"id":321,"nameCN":"人物A","collect":10},"isAddFav":false}}""")
        )
        collectMock.route("collect/item/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.loadDetail(PersonNavParam(personId = 300L))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isCollected)
        assertFalse(viewModel.uiState.value.originalCollected)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)
        assertNull(collectRepository.observeCollectState(321L).value)
        val generationBefore = collectRepository.collectMutationGeneration.value
        // 丢弃首屏阶段已消费的请求，保证下面断言只针对切换收藏
        collectMock.drainPaths()

        viewModel.toggleCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(1, viewModel.uiState.value.collectCountDelta)
        val pooled = collectRepository.observeCollectState(321L).value
        assertTrue(pooled!!.isCollected)
        assertEquals(CollectConstants.STATE_CARE, pooled.state)
        assertEquals(generationBefore + 1, collectRepository.collectMutationGeneration.value)
        assertCollectRequest(collectMock, "/api/collect/item/add")
    }

    @Test
    fun toggleCollect_whenCollected_removesItemClearsPoolAndAdvancesGeneration() = runTest(testDispatcher) {
        val detailMock = MockApi()
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(detailMock, collectRepository)
        detailMock.enqueue(
            detailMock.ok("""{"result":{"person":{"id":321,"nameCN":"人物A","collect":10},"isAddFav":true}}""")
        )
        collectMock.route("collect/del", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.loadDetail(PersonNavParam(personId = 300L))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCollected)
        assertTrue(viewModel.uiState.value.originalCollected)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)
        assertNull(collectRepository.observeCollectState(321L).value)
        val generationBefore = collectRepository.collectMutationGeneration.value
        collectMock.drainPaths()

        viewModel.toggleCollect()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCollected)
        assertEquals(-1, viewModel.uiState.value.collectCountDelta)
        assertNull(collectRepository.observeCollectState(321L).value)
        assertEquals(generationBefore + 1, collectRepository.collectMutationGeneration.value)
        assertCollectRequest(collectMock, "/api/collect/del")
    }

    @Test
    fun toggleCollect_whenFailure_rollsBackAndKeepsPoolAndGeneration() = runTest(testDispatcher) {
        val detailMock = MockApi()
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(detailMock, collectRepository)
        detailMock.enqueue(
            detailMock.ok("""{"result":{"person":{"id":321,"nameCN":"人物A","collect":10},"isAddFav":true}}""")
        )
        collectMock.route("collect/del", collectMock.failure(500, "收藏失败"))

        viewModel.loadDetail(PersonNavParam(personId = 300L))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)
        val pooledBefore = collectRepository.observeCollectState(321L).value
        val generationBefore = collectRepository.collectMutationGeneration.value
        collectMock.drainPaths()

        viewModel.toggleCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)
        assertEquals("收藏失败", viewModel.uiState.value.commentActionMessage)
        assertEquals(pooledBefore, collectRepository.observeCollectState(321L).value)
        assertEquals(generationBefore, collectRepository.collectMutationGeneration.value)
        assertCollectRequest(collectMock, "/api/collect/del")
    }

    @Test
    fun toggleCollect_repeatedKeepsDeltaWithinOneOfServerBaseline() = runTest(testDispatcher) {
        // 未收藏基线：添加→取消→添加，增量必须为 +1 / 0 / +1，不得逐次累加
        runRepeatedSequence(initialCollected = false)
        // 已收藏基线：取消→添加→取消，增量必须为 -1 / 0 / -1
        runRepeatedSequence(initialCollected = true)
    }

    /** 消费并丢弃已排队的请求记录（首屏加载阶段可能预热其他端点）。 */
    private fun MockApi.drainPaths() {
        repeat(requestCount) { takeRequest() }
    }

    private fun assertCollectRequest(collectMock: MockApi, expectedPath: String) {
        val recorded = collectMock.takeRequest()
        assertEquals(expectedPath, recorded.encodedPath)
        assertEquals("321", recorded.formEntries()["id"])
    }

    private suspend fun TestScope.runRepeatedSequence(initialCollected: Boolean) {
        val detailMock = MockApi()
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(detailMock, collectRepository)
        detailMock.enqueue(
            detailMock.ok(
                """{"result":{"person":{"id":321,"nameCN":"人物A","collect":10},"isAddFav":$initialCollected}}"""
            )
        )
        collectMock.route("collect/item/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectMock.route("collect/del", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.loadDetail(PersonNavParam(personId = 300L))
        advanceUntilIdle()
        assertEquals(initialCollected, viewModel.uiState.value.originalCollected)
        assertEquals(initialCollected, viewModel.uiState.value.isCollected)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)
        val generationBefore = collectRepository.collectMutationGeneration.value
        collectMock.drainPaths()

        val paths = if (initialCollected) {
            listOf("/api/collect/del", "/api/collect/item/add", "/api/collect/del")
        } else {
            listOf("/api/collect/item/add", "/api/collect/del", "/api/collect/item/add")
        }
        val expectedDeltas = if (initialCollected) listOf(-1, 0, -1) else listOf(1, 0, 1)
        paths.forEachIndexed { index, path ->
            viewModel.toggleCollect()
            advanceUntilIdle()

            assertEquals(expectedDeltas[index], viewModel.uiState.value.collectCountDelta)
            val expectedCollected = if (initialCollected) expectedDeltas[index] == 0 else expectedDeltas[index] == 1
            assertEquals(expectedCollected, viewModel.uiState.value.isCollected)
            assertEquals(generationBefore + index + 1, collectRepository.collectMutationGeneration.value)
            val pooled = collectRepository.observeCollectState(321L).value
            if (expectedCollected) {
                assertTrue(pooled!!.isCollected)
            } else {
                assertNull(pooled)
            }
            assertNull(viewModel.uiState.value.commentActionMessage)
            assertCollectRequest(collectMock, path)
        }
    }

    private fun newRepository(collectMock: MockApi): CollectRepository =
        CollectRepository(collectMock.api(testDispatcher), collectMock.json)

    private fun newViewModel(detailMock: MockApi, collectRepository: CollectRepository): PersonDetailViewModel {
        val session = FakeUserSession("utoken-test")
        return PersonDetailViewModel(
            PersonDetailRepository(detailMock.api(testDispatcher), collectRepository),
            collectRepository,
            CommentInteractionDelegate(
                CommentRepository(detailMock.api(testDispatcher)),
                StickerRepository(detailMock.api(testDispatcher)),
                session
            )
        )
    }
}
