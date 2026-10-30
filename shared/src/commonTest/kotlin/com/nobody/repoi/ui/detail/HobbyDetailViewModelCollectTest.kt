package com.nobody.repoi.ui.detail

import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.CollectData
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
 * 手办详情收藏链路 ViewModel 测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `HobbyDetailViewModelCollectTest`（逐用例；Mockito 仓储/代理 mock →
 * [HobbyDetailEnv] 真实 6 依赖 + MockEngine；`collectUpdates MutableSharedFlow`
 * 桩 → 真实广播（`saveCollect`/`deleteCollect` 成功即 `tryEmit`）；
 * `MainDispatcherRule` → `Dispatchers.setMain`；JUnit4 → kotlin.test + `runTest`）。
 */
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
        val env = HobbyDetailEnv()
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
        val env = HobbyDetailEnv()
        env.stubBasics(nodeId = 100L)
        env.collectMock.enqueue(env.collectMock.ok("""{}"""))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFavorite)
    }

    @Test
    fun collectUpdates_broadcastSync_updatesFavoriteAndCountDelta() = runTest(testDispatcher) {
        val env = HobbyDetailEnv()
        env.stubBasics(nodeId = 100L)
        env.collectMock.enqueue(env.collectMock.ok("""{}"""))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFavorite)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)

        // 外部保存收藏广播 -> 触发状态转变为已收藏，delta + 1
        env.collectMock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        env.collectRepository.saveCollect(
            100L, CollectData(id = 100L, state = CollectConstants.STATE_BUY)
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isFavorite)
        assertEquals(1, viewModel.uiState.value.collectCountDelta)

        // 外部取消收藏广播 -> 触发状态转变为未收藏，delta 回退为 0
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
        val env = HobbyDetailEnv()
        env.stubBasics(nodeId = 100L)
        env.collectMock.enqueue(env.collectMock.ok("""{"collect":{"id":100,"state":"care"}}"""))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isFavorite)

        // 加载新条目，该条目接口返回未收藏 (null)
        env.stubBasics(nodeId = 200L, itemId = 60000L)
        env.collectMock.enqueue(env.collectMock.ok("""{}"""))

        viewModel.loadDetail(DetailNavParam(itemId = 60000L))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFavorite)
        assertEquals(null, viewModel.uiState.value.currentCollectData)
    }

    @Test
    fun loadDetail_whenCollectFails_keepsInitialState() = runTest(testDispatcher) {
        val env = HobbyDetailEnv()
        env.stubBasics(nodeId = 100L)
        env.collectMock.enqueue(env.collectMock.failure(500, "网络异常"))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        // 失败不误报为未收藏，保持初始态
        assertFalse(viewModel.uiState.value.isFavorite)
        assertEquals(null, viewModel.uiState.value.currentCollectData)
    }
}
