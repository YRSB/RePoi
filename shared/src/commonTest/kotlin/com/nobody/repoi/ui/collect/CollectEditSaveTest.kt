package com.nobody.repoi.ui.collect

import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.session.FakeUserSession
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 手办收藏编辑页写侧测试：保存/取消收藏均委托 Domain UseCase（`SaveHobbyCollectUseCase` /
 * `DeleteHobbyCollectUseCase`），成功后收藏仓储状态池同步更新。
 *
 * 类名刻意不含 `ViewModel`，避免受既有 ViewModel 测试过滤规则影响。
 */
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
        collectMock.route("collect/state", collectMock.ok("""{"collect":{"id":100,"state":"care"}}"""))
        collectMock.route("collect/hobby/upd", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.initData(CollectEditNavParam(itemId = 59570L, nodeId = 100L))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.nodeId == 100L)
        // 丢弃 initData 阶段已消费的请求（统计/标签/状态），下面断言只针对保存写入
        collectMock.drainPaths()

        viewModel.saveCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saveSuccess)
        val recorded = collectMock.takeRequest()
        assertEquals("/api/collect/hobby/upd", recorded.encodedPath)
        assertEquals("care", collectRepository.observeCollectState(100L).value?.state)
    }

    @Test
    fun deleteCollect_delegatesToDeleteUseCaseAndClearsStatePool() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = newViewModel(collectRepository)
        collectMock.route(
            "collectCount",
            collectMock.ok("""{"collect":{}}""")
        )
        collectMock.route("collect/tag", collectMock.ok("""{"favTags":[]}"""))
        collectMock.route("collect/state", collectMock.ok("""{"collect":{"id":100,"state":"buy"}}"""))
        collectMock.route("collect/hobby/upd", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.initData(CollectEditNavParam(itemId = 59570L, nodeId = 100L))
        advanceUntilIdle()
        collectMock.drainPaths()

        viewModel.deleteCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.deleteSuccess)
        val recorded = collectMock.takeRequest()
        assertEquals("/api/collect/hobby/upd", recorded.encodedPath)
        assertNull(collectRepository.observeCollectState(100L).value)
    }

    /** 消费并丢弃已排队的请求记录。 */
    private fun MockApi.drainPaths(): List<String?> = buildList {
        repeat(requestCount) { add(takeRequest().encodedPath) }
    }

    private fun newRepository(collectMock: MockApi): CollectRepository =
        CollectRepository(collectMock.api(testDispatcher), collectMock.json, FakeUserSession("utoken-test"))

    private fun newViewModel(collectRepository: CollectRepository): CollectEditViewModel =
        CollectEditViewModel(
            collectRepository,
            SaveHobbyCollectUseCase(collectRepository),
            DeleteHobbyCollectUseCase(collectRepository)
        )
}
