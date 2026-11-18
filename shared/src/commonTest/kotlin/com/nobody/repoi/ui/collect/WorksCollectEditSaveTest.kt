package com.nobody.repoi.ui.collect

import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.domain.usecase.collect.DeleteWorksCollectUseCase
import com.nobody.repoi.domain.usecase.collect.SaveWorksCollectUseCase
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

/**
 * 作品收藏编辑页写侧测试：保存委托 `SaveWorksCollectUseCase`（三态校验 + 标签串清洗），
 * 取消收藏委托 `DeleteWorksCollectUseCase`（worksId 守卫 + 仓储删除）。
 *
 * 类名刻意不含 `ViewModel`，避免受既有 ViewModel 测试过滤规则影响。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorksCollectEditSaveTest {

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
    fun saveCollect_delegatesToUseCaseWithNormalizedTags() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = WorksCollectEditViewModel(
            collectRepository,
            SaveWorksCollectUseCase(collectRepository),
            DeleteWorksCollectUseCase(collectRepository)
        )
        collectMock.route("user/glean/textTag/get", collectMock.ok("""[]"""))
        collectMock.route("works/glean/get", collectMock.ok("{}"))
        collectMock.route("works/glean/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.initData(WorksCollectEditNavParam(worksId = 66L))
        advanceUntilIdle()
        // 丢弃 initData 阶段已消费的请求，下面断言只针对保存写入
        collectMock.drainPaths()

        // 标签清洗由用例负责：trim 后写入
        assertTrue(viewModel.addTag("  热血  "))
        viewModel.saveCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saveSuccess)
        val recorded = collectMock.takeRequest()
        assertEquals("/api/works/glean/add", recorded.encodedPath)
        val form = recorded.formEntries()
        assertEquals("66", form["itemId"])
        assertEquals("热血", form["tagStr"])
        assertEquals("1", form["state"])
    }

    @Test
    fun deleteCollect_stillDelegatesToRepository() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = WorksCollectEditViewModel(
            collectRepository,
            SaveWorksCollectUseCase(collectRepository),
            DeleteWorksCollectUseCase(collectRepository)
        )
        collectMock.route("user/glean/textTag/get", collectMock.ok("""[]"""))
        collectMock.route(
            "works/glean/get",
            collectMock.ok("""{"glean":{"state":2,"tagStr":"热血","comment":"不错"}}""")
        )
        collectMock.route("works/glean/del", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.initData(WorksCollectEditNavParam(worksId = 66L))
        advanceUntilIdle()
        collectMock.drainPaths()

        viewModel.deleteCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.deleteSuccess)
        assertEquals("/api/works/glean/del", collectMock.takeRequest().encodedPath)
    }

    /** 三态依次切换：每次请求 state 参数真实变化，代数逐步 +1，手办状态池不被跨接口污染。 */
    @Test
    fun saveCollect_threeStatesInSequence_advancesGenerationPerSave() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = WorksCollectEditViewModel(
            collectRepository,
            SaveWorksCollectUseCase(collectRepository),
            DeleteWorksCollectUseCase(collectRepository)
        )
        collectMock.route("user/glean/textTag/get", collectMock.ok("""[]"""))
        collectMock.route("works/glean/get", collectMock.ok("{}"))
        collectMock.route("works/glean/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.initData(WorksCollectEditNavParam(worksId = 66L))
        advanceUntilIdle()
        assertEquals(WorksStateOption.WANT, viewModel.uiState.value.selectedState)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        assertNull(collectRepository.observeCollectState(66L).value)
        collectMock.drainPaths()

        listOf(WorksStateOption.WATCHING, WorksStateOption.WATCHED, WorksStateOption.WANT)
            .forEachIndexed { index, state ->
                viewModel.selectState(state)
                viewModel.saveCollect()
                advanceUntilIdle()

                val recorded = collectMock.takeRequest()
                assertEquals("/api/works/glean/add", recorded.encodedPath)
                val form = recorded.formEntries()
                assertEquals("Works", form["itemType"])
                assertEquals("66", form["itemId"])
                assertEquals(state.toString(), form["state"])
                assertTrue(viewModel.uiState.value.saveSuccess)
                assertEquals(state, viewModel.uiState.value.selectedState)
                assertTrue(viewModel.uiState.value.isAlreadyCollected)
                assertEquals((index + 1).toLong(), collectRepository.collectMutationGeneration.value)
                // 作品走 glean 独立协议，不写手办状态池
                assertNull(collectRepository.observeCollectState(66L).value)
            }
    }

    /** 改档后取消收藏：UI 三态与已收藏标记复位，代数累计 +2，手办状态池仍为空。 */
    @Test
    fun saveThenDeleteCollect_clearsCollectedFlagsAndAdvancesGenerationTwice() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = WorksCollectEditViewModel(
            collectRepository,
            SaveWorksCollectUseCase(collectRepository),
            DeleteWorksCollectUseCase(collectRepository)
        )
        collectMock.route("user/glean/textTag/get", collectMock.ok("""[]"""))
        collectMock.route("works/glean/get", collectMock.ok("""{"glean":{"state":2,"tagStr":"热血","comment":"不错"}}"""))
        collectMock.route("works/glean/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectMock.route("works/glean/del", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.initData(WorksCollectEditNavParam(worksId = 66L))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isAlreadyCollected)
        assertTrue(viewModel.uiState.value.hasDelete)
        assertEquals(WorksStateOption.WATCHING, viewModel.uiState.value.selectedState)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        collectMock.drainPaths()

        viewModel.selectState(WorksStateOption.WATCHED)
        viewModel.saveCollect()
        advanceUntilIdle()
        val add = collectMock.takeRequest()
        assertEquals("/api/works/glean/add", add.encodedPath)
        assertEquals("3", add.formEntries()["state"])
        assertEquals(1L, collectRepository.collectMutationGeneration.value)

        viewModel.deleteCollect()
        advanceUntilIdle()
        val del = collectMock.takeRequest()
        assertEquals("/api/works/glean/del", del.encodedPath)
        assertEquals("Works", del.formEntries()["itemType"])
        assertEquals("66", del.formEntries()["itemId"])
        assertTrue(viewModel.uiState.value.deleteSuccess)
        assertFalse(viewModel.uiState.value.isAlreadyCollected)
        assertFalse(viewModel.uiState.value.hasDelete)
        assertEquals(2L, collectRepository.collectMutationGeneration.value)
        assertNull(collectRepository.observeCollectState(66L).value)
    }

    /** 作品保存网络失败：代数不推进、saveSuccess 为假、errorMessage 有值。 */
    @Test
    fun saveCollect_onNetworkFailure_keepsGenerationAndReportsError() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = WorksCollectEditViewModel(
            collectRepository,
            SaveWorksCollectUseCase(collectRepository),
            DeleteWorksCollectUseCase(collectRepository)
        )
        collectMock.route("user/glean/textTag/get", collectMock.ok("""[]"""))
        collectMock.route("works/glean/get", collectMock.ok("{}"))
        collectMock.route("works/glean/add", collectMock.failure(500, "作品保存失败"))

        viewModel.initData(WorksCollectEditNavParam(worksId = 66L))
        advanceUntilIdle()
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        collectMock.drainPaths()

        viewModel.selectState(WorksStateOption.WATCHED)
        viewModel.saveCollect()
        advanceUntilIdle()

        assertEquals("/api/works/glean/add", collectMock.takeRequest().encodedPath)
        assertTrue(viewModel.uiState.value.errorMessage?.isNotBlank() == true)
        assertFalse(viewModel.uiState.value.saveSuccess)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(WorksStateOption.WATCHED, viewModel.uiState.value.selectedState)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        assertNull(collectRepository.observeCollectState(66L).value)
    }

    /** 作品取消网络失败：已收藏标记保留、代数不推进、errorMessage 有值。 */
    @Test
    fun deleteCollect_onNetworkFailure_keepsGenerationAndReportsError() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = WorksCollectEditViewModel(
            collectRepository,
            SaveWorksCollectUseCase(collectRepository),
            DeleteWorksCollectUseCase(collectRepository)
        )
        collectMock.route("user/glean/textTag/get", collectMock.ok("""[]"""))
        collectMock.route("works/glean/get", collectMock.ok("""{"glean":{"state":2,"tagStr":"热血"}}"""))
        collectMock.route("works/glean/del", collectMock.failure(500, "作品取消失败"))

        viewModel.initData(WorksCollectEditNavParam(worksId = 66L))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isAlreadyCollected)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        collectMock.drainPaths()

        viewModel.deleteCollect()
        advanceUntilIdle()

        assertEquals("/api/works/glean/del", collectMock.takeRequest().encodedPath)
        assertTrue(viewModel.uiState.value.errorMessage?.isNotBlank() == true)
        assertFalse(viewModel.uiState.value.deleteSuccess)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertTrue(viewModel.uiState.value.isAlreadyCollected)
        assertTrue(viewModel.uiState.value.hasDelete)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        assertNull(collectRepository.observeCollectState(66L).value)
    }

    /** 消费并丢弃已排队的请求记录。 */
    private fun MockApi.drainPaths(): List<String?> = buildList {
        repeat(requestCount) { add(takeRequest().encodedPath) }
    }

    private fun newRepository(collectMock: MockApi): CollectRepository =
        CollectRepository(collectMock.api(testDispatcher), collectMock.json)
}
