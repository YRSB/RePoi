package com.nobody.repoi.ui.series

import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.SeriesDetailRepository
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
    fun toggleCollect_addsItemSyncsPoolAndAdvancesGeneration() = runTest(testDispatcher) {
        val detailMock = MockApi()
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(detailMock, collectRepository)
        detailMock.enqueue(
            detailMock.ok("""{"itemData":{"id":555,"nameCN":"系列A","collect":30}}""")
        )
        collectMock.route("collect/state", collectMock.ok("{}"))
        collectMock.route("collect/item/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.loadDetail(SeriesNavParam(seriesId = 500L))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isCollected)
        assertFalse(viewModel.uiState.value.originalCollected)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)
        assertNull(collectRepository.observeCollectState(555L).value)
        val generationBefore = collectRepository.collectMutationGeneration.value

        collectMock.drainPaths()

        viewModel.toggleCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(1, viewModel.uiState.value.collectCountDelta)
        val pooled = collectRepository.observeCollectState(555L).value
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
            detailMock.ok("""{"itemData":{"id":555,"nameCN":"系列A","collect":30}}""")
        )
        collectMock.route("collect/state", collectMock.ok("""{"collect":{"id":555,"state":"care"}}"""))
        collectMock.route("collect/del", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.loadDetail(SeriesNavParam(seriesId = 500L))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCollected)
        assertTrue(viewModel.uiState.value.originalCollected)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)
        assertTrue(collectRepository.observeCollectState(555L).value!!.isCollected)
        val generationBefore = collectRepository.collectMutationGeneration.value
        collectMock.drainPaths()

        viewModel.toggleCollect()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCollected)
        assertEquals(-1, viewModel.uiState.value.collectCountDelta)
        assertNull(collectRepository.observeCollectState(555L).value)
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
            detailMock.ok("""{"itemData":{"id":555,"nameCN":"系列A","collect":30}}""")
        )
        collectMock.route("collect/state", collectMock.ok("""{"collect":{"id":555,"state":"care"}}"""))
        collectMock.route("collect/del", collectMock.failure(500, "收藏失败"))

        viewModel.loadDetail(SeriesNavParam(seriesId = 500L))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)
        val pooledBefore = collectRepository.observeCollectState(555L).value
        val generationBefore = collectRepository.collectMutationGeneration.value
        collectMock.drainPaths()

        viewModel.toggleCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)
        assertEquals("收藏失败", viewModel.uiState.value.commentActionMessage)
        assertEquals(pooledBefore, collectRepository.observeCollectState(555L).value)
        assertEquals(generationBefore, collectRepository.collectMutationGeneration.value)
        assertCollectRequest(collectMock, "/api/collect/del")
    }

    @Test
    fun toggleCollect_repeatedKeepsDeltaWithinOneOfServerBaseline() = runTest(testDispatcher) {

        runRepeatedSequence(initialCollected = false)

        runRepeatedSequence(initialCollected = true)
    }

    private fun MockApi.drainPaths() {
        repeat(requestCount) { takeRequest() }
    }

    private fun assertCollectRequest(collectMock: MockApi, expectedPath: String) {
        val recorded = buildList {
            repeat(collectMock.requestCount) { add(collectMock.takeRequest()) }
        }.first { it.encodedPath == expectedPath }
        assertEquals("555", recorded.formEntries()["id"])
    }

    private suspend fun TestScope.runRepeatedSequence(initialCollected: Boolean) {
        val detailMock = MockApi()
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(detailMock, collectRepository)
        detailMock.enqueue(
            detailMock.ok("""{"itemData":{"id":555,"nameCN":"系列A","collect":30}}""")
        )
        val stateBody = if (initialCollected) """{"collect":{"id":555,"state":"care"}}""" else "{}"
        collectMock.route("collect/state", collectMock.ok(stateBody))
        collectMock.route("collect/item/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectMock.route("collect/del", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.loadDetail(SeriesNavParam(seriesId = 500L))
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
            val pooled = collectRepository.observeCollectState(555L).value
            if (expectedCollected) {
                assertTrue(pooled!!.isCollected)
            } else {
                assertNull(pooled)
            }
            assertNull(viewModel.uiState.value.commentActionMessage)
            assertCollectRequest(collectMock, path)
        }
    }

    @Test
    fun toggleCollect_refreshesCountBaseFromServer() = runTest(testDispatcher) {
        val detailMock = MockApi()
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(detailMock, collectRepository)
        detailMock.enqueue(
            detailMock.ok("""{"itemData":{"id":555,"nameCN":"系列A","collect":30}}""")
        )
        collectMock.route("collect/state", collectMock.ok("{}"))
        collectMock.route("collect/item/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectMock.route(
            "hobby/collectCount",
            """{"success":true,"code":200,"msg":"ok","data":{"collect":{"care":41,"want":2,"preorder":0,"buy":3,"resell":0}}}"""
        )

        viewModel.loadDetail(SeriesNavParam(seriesId = 500L))
        advanceUntilIdle()
        assertEquals(30, viewModel.uiState.value.series?.collect ?: 0)

        viewModel.toggleCollect()
        advanceUntilIdle()

        assertEquals(46, viewModel.uiState.value.collectCountBase)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)
        assertTrue(viewModel.uiState.value.isCollected)
        assertTrue(viewModel.uiState.value.originalCollected)
        val paths = buildList {
            repeat(collectMock.requestCount) { add(collectMock.takeRequest().encodedPath) }
        }
        assertTrue(paths.any { it?.contains("hobby/collectCount") == true })
    }
    private fun newRepository(collectMock: MockApi): CollectRepository =
        CollectRepository(collectMock.api(testDispatcher), collectMock.json)

    private fun newViewModel(detailMock: MockApi, collectRepository: CollectRepository): SeriesDetailViewModel {
        val session = FakeUserSession("utoken-test")
        return SeriesDetailViewModel(
            SeriesDetailRepository(detailMock.api(testDispatcher), collectRepository),
            collectRepository,
            CommentInteractionDelegate(
                CommentRepository(detailMock.api(testDispatcher)),
                StickerRepository(detailMock.api(testDispatcher)),
                session
            )
        )
    }
}
