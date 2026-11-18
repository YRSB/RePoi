package com.nobody.repoi.ui.character

import com.nobody.repoi.data.repository.CharacterDetailRepository
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import com.nobody.repoi.ui.navigation.CharacterNavParam
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
 * 角色详情收藏切换测试：验证爱心切换直接调用 CollectRepository.toggleItemCollect（发网络），
 * 成功推进收藏变更代数、失败回滚状态且不推进代数。类名刻意不含 `ViewModel`，避免被测试过滤误伤。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CharacterDetailCollectToggleTest {

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
    fun toggleCollect_callsRepositoryAndAdvancesGeneration() = runTest(testDispatcher) {
        val detailMock = MockApi()
        val collectMock = MockApi()
        val collectRepository = CollectRepository(collectMock.api(testDispatcher), collectMock.json)
        val viewModel = newViewModel(detailMock, collectRepository)
        detailMock.enqueue(
            detailMock.ok("""{"charactar":{"id":7,"itemId":70,"nameCN":"角色A"},"isAddFav":false}""")
        )
        collectMock.route("collect/item/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.loadDetail(CharacterNavParam(characterId = 7L))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isCollected)
        val generationBefore = collectRepository.collectMutationGeneration.value
        repeat(collectMock.requestCount) { collectMock.takeRequest() }

        viewModel.toggleCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(1, viewModel.uiState.value.collectCountDelta)
        assertEquals(generationBefore + 1, collectRepository.collectMutationGeneration.value)
        val recorded = collectMock.takeRequest()
        assertEquals("/api/collect/item/add", recorded.encodedPath)
        assertEquals("7", recorded.formEntries()["id"])
    }

    @Test
    fun toggleCollect_whenFailure_rollsBackAndKeepsGeneration() = runTest(testDispatcher) {
        val detailMock = MockApi()
        val collectMock = MockApi()
        val collectRepository = CollectRepository(collectMock.api(testDispatcher), collectMock.json)
        val viewModel = newViewModel(detailMock, collectRepository)
        detailMock.enqueue(
            detailMock.ok("""{"charactar":{"id":7,"itemId":70,"nameCN":"角色A"},"isAddFav":true}""")
        )
        collectMock.route("collect/del", collectMock.failure(500, "收藏失败"))

        viewModel.loadDetail(CharacterNavParam(characterId = 7L))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCollected)
        val generationBefore = collectRepository.collectMutationGeneration.value
        repeat(collectMock.requestCount) { collectMock.takeRequest() }

        viewModel.toggleCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)
        assertEquals("收藏失败", viewModel.uiState.value.commentActionMessage)
        assertEquals(generationBefore, collectRepository.collectMutationGeneration.value)
        assertEquals("/api/collect/del", collectMock.takeRequest().encodedPath)
    }

    private fun newViewModel(
        detailMock: MockApi,
        collectRepository: CollectRepository,
    ): CharacterDetailViewModel {
        val session = FakeUserSession("utoken-test")
        return CharacterDetailViewModel(
            CharacterDetailRepository(detailMock.api(testDispatcher)),
            collectRepository,
            CommentInteractionDelegate(
                CommentRepository(detailMock.api(testDispatcher)),
                StickerRepository(detailMock.api(testDispatcher)),
                session
            )
        )
    }
}
