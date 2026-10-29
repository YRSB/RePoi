package com.nobody.poishelf.ui.detail

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
import kotlin.test.assertTrue

/**
 * 手办详情关联推荐 ViewModel 测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `HobbyDetailViewModelRelatedTest`（逐用例；
 * Mockito 仓储/代理 mock → [HobbyDetailEnv] 真实 6 依赖 + MockEngine，
 * 关联走 api/hobby/group + api/hobby/push 路由；`MainDispatcherRule` →
 * `Dispatchers.setMain`；JUnit4 → kotlin.test + `runTest`）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HobbyDetailViewModelRelatedTest {

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
        detailMock.route("api/user-action/timeline", detailMock.ok("""{"list":[]}"""))
        detailMock.route("api/comment/get", detailMock.ok("""{"list":[]}"""))
        collectMock.enqueue(collectMock.ok("""{}"""))
    }

    @Test
    fun loadDetail_whenRelatedHobbiesSuccess_updatesUiState() = runTest {
        val env = HobbyDetailEnv()
        env.stubDetailBasics()
        env.detailMock.route(
            "api/hobby/group",
            env.detailMock.ok("""{"groups":[{"typeName":"再版","items":[{"id":101,"itemId":101,"nameCN":"初音未来 再版"}]}]}""")
        )
        env.detailMock.route(
            "api/hobby/push/list",
            env.detailMock.ok("""{"hobbys":[{"id":101,"itemId":101,"nameCN":"初音未来 再版"},{"id":102,"itemId":102,"nameCN":"初音未来 韶华"}]}""")
        )

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.relatedHobbies.size)
        assertEquals(101L, state.relatedHobbies[0].actualItemId)
        assertEquals("再版", state.relatedHobbies[0].typeName)
        assertEquals("初音未来 再版", state.relatedHobbies[0].displayName)
        assertEquals(102L, state.relatedHobbies[1].actualItemId)
    }

    @Test
    fun loadDetail_whenRelatedHobbiesFails_silentlyDegradesToEmpty() = runTest {
        val env = HobbyDetailEnv()
        env.stubDetailBasics()
        env.detailMock.route("api/hobby/group", env.detailMock.failure(500, "Network error"))
        env.detailMock.route("api/hobby/push/list", env.detailMock.failure(500, "Network error"))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.relatedHobbies.isEmpty())
    }
}
