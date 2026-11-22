package com.nobody.repoi.ui.detail

import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.CollectData
import com.nobody.repoi.domain.usecase.collect.DeleteHobbyCollectUseCase
import com.nobody.repoi.domain.usecase.collect.SaveHobbyCollectUseCase
import com.nobody.repoi.ui.collect.CollectEditViewModel
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
class HobbyDetailViewModelCollectTest {

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
    fun loadDetail_whenCollected_isFavoriteTrue() = runTest(testDispatcher) {
        val env = HobbyDetailEnv(backgroundScope, dispatcher = testDispatcher)
        env.stubBasics(nodeId = 100L)
        env.collectMock.enqueue(env.collectMock.ok("""{"collect":{"id":100,"state":"care"}}"""))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isFavorite)
        assertEquals("care", viewModel.uiState.value.currentCollectData?.state)
    }

    @Test
    fun loadDetail_whenNotCollected_isFavoriteFalse() = runTest(testDispatcher) {
        val env = HobbyDetailEnv(backgroundScope, dispatcher = testDispatcher)
        env.stubBasics(nodeId = 100L)
        env.collectMock.enqueue(env.collectMock.ok("""{}"""))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFavorite)
    }

    @Test
    fun collectStateFlow_syncsFavoriteAndCountDelta() = runTest(testDispatcher) {
        val env = HobbyDetailEnv(backgroundScope, dispatcher = testDispatcher)
        env.stubBasics(nodeId = 100L)
        env.collectMock.enqueue(env.collectMock.ok("""{}"""))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFavorite)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)

        env.collectMock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        env.collectRepository.saveCollect(
            100L, CollectData(id = 100L, state = CollectConstants.STATE_BUY)
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isFavorite)
        assertEquals(1, viewModel.uiState.value.collectCountDelta)

        env.collectMock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        env.collectRepository.deleteCollect(
            100L, CollectData(id = 100L, state = CollectConstants.STATE_BUY)
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFavorite)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)
    }

    @Test
    fun loadDetail_whenCollectStateReturnsNull_explicitlyResetsFavoriteToFalse() = runTest(testDispatcher) {
        val env = HobbyDetailEnv(backgroundScope, dispatcher = testDispatcher)
        env.stubBasics(nodeId = 100L)
        env.collectMock.enqueue(env.collectMock.ok("""{"collect":{"id":100,"state":"care"}}"""))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isFavorite)

        env.stubBasics(nodeId = 200L, itemId = 60000L)
        env.collectMock.enqueue(env.collectMock.ok("""{}"""))

        viewModel.loadDetail(DetailNavParam(itemId = 60000L))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFavorite)
        assertNull(viewModel.uiState.value.currentCollectData)
    }

    @Test
    fun loadDetail_whenCollectFails_keepsInitialState() = runTest(testDispatcher) {
        val env = HobbyDetailEnv(backgroundScope, dispatcher = testDispatcher)
        env.stubBasics(nodeId = 100L)
        env.collectMock.enqueue(env.collectMock.failure(500, "网络异常"))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFavorite)
        assertNull(viewModel.uiState.value.currentCollectData)
    }

    @Test
    fun collectEditAddFromEmpty_thenDetailReadsFavoriteAndServerCountBase() = runTest(testDispatcher) {
        val env = HobbyDetailEnv(backgroundScope, dispatcher = testDispatcher)
        env.stubBasics(nodeId = 100L)
        env.collectMock.route("collectCount", env.collectMock.ok("""{"collect":{}}"""))
        env.collectMock.route("collect/tag", env.collectMock.ok("""{"favTags":[]}"""))
        env.collectMock.route("collect/state", env.collectMock.ok("""{}"""))
        env.collectMock.route("collect/hobby/upd", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        val detail = env.viewModel()
        detail.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()
        assertFalse(detail.uiState.value.isFavorite)
        assertEquals(0, detail.uiState.value.collectCountDelta)
        assertEquals(0L, env.collectRepository.collectMutationGeneration.value)

        val edit = CollectEditViewModel(
            env.collectRepository,
            SaveHobbyCollectUseCase(env.collectRepository),
            DeleteHobbyCollectUseCase(env.collectRepository)
        )
        edit.initData(CollectEditNavParam(itemId = 59570L, nodeId = 100L))
        advanceUntilIdle()
        assertNull(env.collectRepository.observeCollectState(100L).value)
        assertEquals(0L, env.collectRepository.collectMutationGeneration.value)

        edit.saveCollect()
        advanceUntilIdle()

        assertTrue(edit.uiState.value.saveSuccess)
        assertEquals(CollectConstants.STATE_CARE, env.collectRepository.observeCollectState(100L).value?.state)
        assertEquals(1L, env.collectRepository.collectMutationGeneration.value)
        assertTrue(detail.uiState.value.isFavorite)

        assertEquals(0L, detail.uiState.value.collectCountBase)
        assertEquals(0, detail.uiState.value.collectCountDelta)
    }

    @Test
    fun collectEditTierChangeThenDelete_thenDetailReadsNewTierAndServerCountBase() = runTest(testDispatcher) {
        val env = HobbyDetailEnv(backgroundScope, dispatcher = testDispatcher)
        env.stubBasics(nodeId = 100L)
        env.collectMock.route("collectCount", env.collectMock.ok("""{"collect":{}}"""))
        env.collectMock.route("collect/tag", env.collectMock.ok("""{"favTags":[]}"""))
        env.collectMock.route("collect/state", env.collectMock.ok("""{"collect":{"id":100,"state":"care"}}"""))
        env.collectMock.route("collect/hobby/upd", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        val detail = env.viewModel()
        detail.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()
        assertTrue(detail.uiState.value.isFavorite)
        assertEquals(CollectConstants.STATE_CARE, detail.uiState.value.currentCollectData?.state)
        assertEquals(0, detail.uiState.value.collectCountDelta)

        val edit = CollectEditViewModel(
            env.collectRepository,
            SaveHobbyCollectUseCase(env.collectRepository),
            DeleteHobbyCollectUseCase(env.collectRepository)
        )
        edit.initData(CollectEditNavParam(itemId = 59570L, nodeId = 100L))
        advanceUntilIdle()
        assertEquals(CollectConstants.STATE_CARE, edit.uiState.value.selectedState)
        assertEquals(0L, env.collectRepository.collectMutationGeneration.value)

        edit.selectState(CollectConstants.STATE_BUY)
        edit.saveCollect()
        advanceUntilIdle()

        assertEquals(CollectConstants.STATE_BUY, env.collectRepository.observeCollectState(100L).value?.state)
        assertEquals(1L, env.collectRepository.collectMutationGeneration.value)
        assertTrue(detail.uiState.value.isFavorite)
        assertEquals(CollectConstants.STATE_BUY, detail.uiState.value.currentCollectData?.state)
        assertEquals(0L, detail.uiState.value.collectCountBase)
        assertEquals(0, detail.uiState.value.collectCountDelta)

        edit.deleteCollect()
        advanceUntilIdle()

        assertNull(env.collectRepository.observeCollectState(100L).value)
        assertEquals(2L, env.collectRepository.collectMutationGeneration.value)
        assertFalse(detail.uiState.value.isFavorite)
        assertNull(detail.uiState.value.currentCollectData)
        assertEquals(0L, detail.uiState.value.collectCountBase)
        assertEquals(0, detail.uiState.value.collectCountDelta)
    }

    @Test
    fun collectEditSave_refreshesDetailCountBaseFromServerAndZeroesDelta() = runTest(testDispatcher) {
        val env = HobbyDetailEnv(backgroundScope, dispatcher = testDispatcher)
        env.stubBasics(nodeId = 100L)
        env.collectMock.route(
            "collectCount",
            env.collectMock.ok("""{"collect":{"care":10,"want":2,"preorder":0,"buy":3,"resell":0}}""")
        )
        env.collectMock.route("collect/tag", env.collectMock.ok("""{"favTags":[]}"""))
        env.collectMock.route("collect/state", env.collectMock.ok("""{}"""))
        env.collectMock.route("collect/hobby/upd", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        val detail = env.viewModel()
        detail.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()
        assertEquals(100, detail.uiState.value.detail?.collect)
        assertNull(detail.uiState.value.collectCountBase)

        val edit = CollectEditViewModel(
            env.collectRepository,
            SaveHobbyCollectUseCase(env.collectRepository),
            DeleteHobbyCollectUseCase(env.collectRepository)
        )
        edit.initData(CollectEditNavParam(itemId = 59570L, nodeId = 100L))
        advanceUntilIdle()

        env.collectMock.replaceRoute(
            "collectCount",
            env.collectMock.ok("""{"collect":{"care":35,"want":3,"preorder":0,"buy":3,"resell":0}}""")
        )
        edit.saveCollect()
        advanceUntilIdle()

        assertTrue(edit.uiState.value.saveSuccess)
        assertEquals(41L, detail.uiState.value.collectCountBase)
        assertEquals(0, detail.uiState.value.collectCountDelta)
        assertTrue(detail.uiState.value.isFavorite)
        val paths = buildList {
            repeat(env.collectMock.requestCount) { add(env.collectMock.takeRequest().encodedPath) }
        }
        assertTrue(paths.any { it?.contains("hobby/collectCount") == true })
    }

    @Test
    fun collectEditSave_whenCountRequestFails_keepsOptimisticDelta() = runTest(testDispatcher) {
        val env = HobbyDetailEnv(backgroundScope, dispatcher = testDispatcher)
        env.stubBasics(nodeId = 100L)
        env.collectMock.route(
            "collectCount",
            env.collectMock.ok("""{"collect":{"care":10,"want":2,"preorder":0,"buy":3,"resell":0}}""")
        )
        env.collectMock.route("collect/tag", env.collectMock.ok("""{"favTags":[]}"""))
        env.collectMock.route("collect/state", env.collectMock.ok("""{}"""))
        env.collectMock.route("collect/hobby/upd", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        val detail = env.viewModel()
        detail.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        val edit = CollectEditViewModel(
            env.collectRepository,
            SaveHobbyCollectUseCase(env.collectRepository),
            DeleteHobbyCollectUseCase(env.collectRepository)
        )
        edit.initData(CollectEditNavParam(itemId = 59570L, nodeId = 100L))
        advanceUntilIdle()

        env.collectMock.replaceRoute("collectCount", env.collectMock.failure(500, "计数失败"))
        edit.saveCollect()
        advanceUntilIdle()

        assertTrue(edit.uiState.value.saveSuccess)
        assertTrue(detail.uiState.value.isFavorite)
        assertNull(detail.uiState.value.collectCountBase)
        assertEquals(1, detail.uiState.value.collectCountDelta)
    }
}
