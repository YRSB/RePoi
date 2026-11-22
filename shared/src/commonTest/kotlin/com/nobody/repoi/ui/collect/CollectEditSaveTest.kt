package com.nobody.repoi.ui.collect

import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.domain.usecase.collect.DeleteHobbyCollectUseCase
import com.nobody.repoi.domain.usecase.collect.SaveHobbyCollectUseCase
import com.nobody.repoi.ui.navigation.CollectEditNavParam
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CollectEditSaveTest {

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
    fun saveCollect_delegatesToSaveUseCaseAndWritesStatePool() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        collectMock.route("collectCount", collectMock.ok("""{"collect":{}}"""))
        collectMock.route("collect/tag", collectMock.ok("""{"favTags":[]}"""))
        collectMock.route("collect/state", collectMock.ok("""{}"""))
        collectMock.route("collect/hobby/upd", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.initData(CollectEditNavParam(itemId = 59570L, nodeId = 100L))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.nodeId == 100L)
        assertNull(collectRepository.observeCollectState(100L).value)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)

        collectMock.drainPaths()

        viewModel.saveCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saveSuccess)
        val recorded = collectMock.takeRequest()
        assertEquals("/api/collect/hobby/upd", recorded.encodedPath)
        val form = recorded.formEntries()
        assertEquals("100", form["nodeId"])
        assertTrue(form["json"]?.contains("\"state\":\"care\"") == true)
        assertEquals(CollectConstants.STATE_CARE, collectRepository.observeCollectState(100L).value?.state)
        assertEquals(1L, collectRepository.collectMutationGeneration.value)
    }

    @Test
    fun deleteCollect_delegatesToDeleteUseCaseAndClearsStatePool() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        collectMock.route("collectCount", collectMock.ok("""{"collect":{}}"""))
        collectMock.route("collect/tag", collectMock.ok("""{"favTags":[]}"""))
        collectMock.route("collect/state", collectMock.ok("""{"collect":{"id":100,"state":"buy"}}"""))
        collectMock.route("collect/hobby/upd", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.initData(CollectEditNavParam(itemId = 59570L, nodeId = 100L))
        advanceUntilIdle()
        assertEquals(CollectConstants.STATE_BUY, collectRepository.observeCollectState(100L).value?.state)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        collectMock.drainPaths()

        viewModel.deleteCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.deleteSuccess)
        val recorded = collectMock.takeRequest()
        assertEquals("/api/collect/hobby/upd", recorded.encodedPath)
        val form = recorded.formEntries()
        assertEquals("100", form["nodeId"])
        assertTrue(form["json"]?.contains("\"state\":\"delete\"") == true)
        assertNull(collectRepository.observeCollectState(100L).value)

        assertNull(collectRepository.observeCollectState(100L).value)
        assertEquals(1L, collectRepository.collectMutationGeneration.value)
    }

    @Test
    fun saveCollect_whenCollectedChangesTier_writesNewTierAndAdvancesGeneration() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        collectMock.route("collectCount", collectMock.ok("""{"collect":{}}"""))
        collectMock.route("collect/tag", collectMock.ok("""{"favTags":[]}"""))
        collectMock.route("collect/state", collectMock.ok("""{"collect":{"id":100,"state":"care"}}"""))
        collectMock.route("collect/hobby/upd", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.initData(CollectEditNavParam(itemId = 59570L, nodeId = 100L))
        advanceUntilIdle()
        assertEquals(CollectConstants.STATE_CARE, collectRepository.observeCollectState(100L).value?.state)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        collectMock.drainPaths()

        viewModel.selectState(CollectConstants.STATE_BUY)
        viewModel.saveCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saveSuccess)
        assertEquals(CollectConstants.STATE_BUY, viewModel.uiState.value.selectedState)
        val recorded = collectMock.takeRequest()
        assertEquals("/api/collect/hobby/upd", recorded.encodedPath)
        val form = recorded.formEntries()
        assertEquals("100", form["nodeId"])
        assertTrue(form["json"]?.contains("\"state\":\"buy\"") == true)
        val updated = collectRepository.observeCollectState(100L).value
        assertEquals(CollectConstants.STATE_BUY, updated?.state)
        assertEquals(100L, updated?.id)
        assertEquals(1L, collectRepository.collectMutationGeneration.value)
    }

    @Test
    fun saveCollect_onNetworkFailure_keepsPoolAndGenerationAndReportsError() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        collectMock.route("collectCount", collectMock.ok("""{"collect":{}}"""))
        collectMock.route("collect/tag", collectMock.ok("""{"favTags":[]}"""))
        collectMock.route("collect/state", collectMock.ok("""{"collect":{"id":100,"state":"care"}}"""))
        collectMock.route("collect/hobby/upd", collectMock.failure(500, "保存失败"))

        viewModel.initData(CollectEditNavParam(itemId = 59570L, nodeId = 100L))
        advanceUntilIdle()
        val before = collectRepository.observeCollectState(100L).value
        assertEquals(CollectConstants.STATE_CARE, before?.state)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        collectMock.drainPaths()

        viewModel.selectState(CollectConstants.STATE_BUY)
        viewModel.saveCollect()
        advanceUntilIdle()

        assertEquals("/api/collect/hobby/upd", collectMock.takeRequest().encodedPath)
        assertTrue(viewModel.uiState.value.errorMessage?.isNotBlank() == true)
        assertFalse(viewModel.uiState.value.saveSuccess)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(before, collectRepository.observeCollectState(100L).value)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
    }

    @Test
    fun deleteCollect_onNetworkFailure_keepsPoolAndGenerationAndReportsError() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        collectMock.route("collectCount", collectMock.ok("""{"collect":{}}"""))
        collectMock.route("collect/tag", collectMock.ok("""{"favTags":[]}"""))
        collectMock.route("collect/state", collectMock.ok("""{"collect":{"id":100,"state":"care"}}"""))
        collectMock.route("collect/hobby/upd", collectMock.failure(500, "取消收藏失败"))

        viewModel.initData(CollectEditNavParam(itemId = 59570L, nodeId = 100L))
        advanceUntilIdle()
        val before = collectRepository.observeCollectState(100L).value
        assertEquals(CollectConstants.STATE_CARE, before?.state)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        collectMock.drainPaths()

        viewModel.deleteCollect()
        advanceUntilIdle()

        assertEquals("/api/collect/hobby/upd", collectMock.takeRequest().encodedPath)
        assertTrue(viewModel.uiState.value.errorMessage?.isNotBlank() == true)
        assertFalse(viewModel.uiState.value.deleteSuccess)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertEquals(before, collectRepository.observeCollectState(100L).value)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
    }

    private fun MockApi.drainPaths(): List<String?> = buildList {
        repeat(requestCount) { add(takeRequest().encodedPath) }
    }

    private fun newRepository(collectMock: MockApi): CollectRepository =
        CollectRepository(collectMock.api(testDispatcher), collectMock.json)

    private fun newViewModel(collectRepository: CollectRepository): CollectEditViewModel =
        CollectEditViewModel(
            collectRepository,
            SaveHobbyCollectUseCase(collectRepository),
            DeleteHobbyCollectUseCase(collectRepository)
        )

    @Test
    fun saveCollect_writesServerCountSnapshot() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        collectMock.route(
            "collectCount",
            collectMock.ok("""{"collect":{"care":10,"want":2,"preorder":0,"buy":3,"resell":0}}""")
        )
        collectMock.route("collect/tag", collectMock.ok("""{"favTags":[]}"""))
        collectMock.route("collect/state", collectMock.ok("""{}"""))
        collectMock.route("collect/hobby/upd", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.initData(CollectEditNavParam(itemId = 59570L, nodeId = 100L))
        advanceUntilIdle()
        assertNull(collectRepository.observeCollectCountSnapshot(100L).value)
        collectMock.drainPaths()

        collectMock.replaceRoute(
            "collectCount",
            collectMock.ok("""{"collect":{"care":35,"want":3,"preorder":0,"buy":3,"resell":0}}""")
        )
        viewModel.saveCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saveSuccess)
        assertEquals(41L, collectRepository.observeCollectCountSnapshot(100L).value?.total)
        assertTrue(collectMock.drainPaths().any { it?.contains("hobby/collectCount") == true })
    }

    @Test
    fun saveCollect_whenCountRequestFails_keepsSnapshotUnchanged() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        collectMock.route("collectCount", collectMock.ok("""{"collect":{"care":10,"want":2,"preorder":0,"buy":3,"resell":0}}"""))
        collectMock.route("collect/tag", collectMock.ok("""{"favTags":[]}"""))
        collectMock.route("collect/state", collectMock.ok("""{}"""))
        collectMock.route("collect/hobby/upd", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.initData(CollectEditNavParam(itemId = 59570L, nodeId = 100L))
        advanceUntilIdle()
        collectMock.drainPaths()

        collectMock.replaceRoute("collectCount", collectMock.failure(500, "计数失败"))
        viewModel.saveCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saveSuccess)
        assertNull(collectRepository.observeCollectCountSnapshot(100L).value)
        assertEquals(1L, collectRepository.collectMutationGeneration.value)
    }
}
