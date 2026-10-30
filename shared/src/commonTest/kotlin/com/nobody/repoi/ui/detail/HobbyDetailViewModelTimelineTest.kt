package com.nobody.repoi.ui.detail

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
 * 手办详情时间轴 ViewModel 测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `HobbyDetailViewModelTimelineTest`（逐用例；
 * Mockito 仓储/代理 mock → [HobbyDetailEnv] 真实 6 依赖 + MockEngine，
 * 时间轴走路由桩；`MainDispatcherRule` → `Dispatchers.setMain`；
 * JUnit4 → kotlin.test + `runTest`）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HobbyDetailViewModelTimelineTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun HobbyDetailEnv.stubDetailBasics(nodeId: Long = 100L, itemId: Long = 59570L) {
        detailMock.route(
            "api/item/get",
            detailMock.ok("""{"itemData":{"id":$nodeId,"nodeId":$nodeId,"itemId":$itemId,"nameCN":"测试手办"}}""")
        )
        detailMock.route("api/info/list", detailMock.ok("""{"list":[]}"""))
        detailMock.route("api/pic/list", detailMock.ok("""{"list":[]}"""))
        detailMock.route("api/user/album", detailMock.ok("""{"list":[]}"""))
        detailMock.route("api/hobby/group", detailMock.ok("""{"groups":[]}"""))
        detailMock.route("api/hobby/push", detailMock.ok("""{"hobbys":[]}"""))
        detailMock.route("api/comment/get", detailMock.ok("""{"list":[]}"""))
        collectMock.enqueue(collectMock.ok("""{}"""))
    }

    @Test
    fun loadDetail_whenTimelineSuccess_updatesTimelineState() = runTest(testDispatcher) {
        val env = HobbyDetailEnv()
        env.stubDetailBasics()
        env.detailMock.route(
            "api/user-action/timeline",
            env.detailMock.ok("""{"list":[{"actionData":"delay","actionComment":"延期至7月","actionDateString":"2024-05-20"},{"actionData":"preorder","actionComment":"开启预订","actionDateString":"2024-01-10"}]}""")
        )

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isTimelineLoading)
        assertEquals(2, state.timeline.size)
        assertEquals("出荷延期", state.timeline[0].displayTypeLabel)
        assertEquals("开订时间", state.timeline[1].displayTypeLabel)
    }

    @Test
    fun loadDetail_whenTimelineFails_silentlyDegradesToEmpty() = runTest(testDispatcher) {
        val env = HobbyDetailEnv()
        env.stubDetailBasics()
        env.detailMock.route("api/user-action/timeline", env.detailMock.failure(500, "Network error"))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isTimelineLoading)
        assertTrue(state.timeline.isEmpty())
    }
}
