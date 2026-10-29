package com.nobody.poishelf.ui.detail

import com.nobody.poishelf.data.remote.model.CommentItem
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
 * 手办详情评论分页 ViewModel 测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `HobbyDetailViewModelPagingTest`（逐用例；Mockito 仓储/代理 mock →
 * [HobbyDetailEnv] 真实 6 依赖 + MockEngine；按 (type,page) 粒度
 * routeWhen 桩评论页；`MainDispatcherRule` → `Dispatchers.setMain`；
 * JUnit4 → kotlin.test + `runTest`）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HobbyDetailViewModelPagingTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun commentsJson(range: IntRange): String {
        val list = range.joinToString(",") { """{"id":$it,"content":"Comment $it"}""" }
        return """{"success":true,"code":200,"msg":"ok","data":{"list":[$list]}}"""
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
        detailMock.route("api/hobby/group", detailMock.ok("""{"groups":[]}"""))
        detailMock.route("api/hobby/push", detailMock.ok("""{"hobbys":[]}"""))
        collectMock.enqueue(collectMock.ok("""{}"""))
    }

    private fun HobbyDetailEnv.stubComments(type: String?, page: Int, body: String) {
        detailMock.routeWhen(
            predicate = { path, form ->
                path.contains("api/comment/get") &&
                    (if (type == null) !form.containsKey("type") else form["type"] == type) &&
                    form["page"] == page.toString()
            },
            body = { _, _ -> body }
        )
    }

    @Test
    fun firstPageFull_hasMoreTrue() = runTest(testDispatcher) {
        val env = HobbyDetailEnv()
        env.stubDetailBasics()
        env.stubComments("hot_top", 1, env.detailMock.ok("""{"list":[]}"""))
        env.stubComments(null, 1, commentsJson(1..10))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()
        assertEquals(10, viewModel.uiState.value.latestComments.size)
        assertTrue(viewModel.uiState.value.hasMoreComments)
    }

    @Test
    fun firstPageShort_hasMoreFalse() = runTest(testDispatcher) {
        val env = HobbyDetailEnv()
        env.stubDetailBasics()
        env.stubComments("hot_top", 1, env.detailMock.ok("""{"list":[]}"""))
        env.stubComments(null, 1, commentsJson(1..3))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.latestComments.size)
        assertFalse(viewModel.uiState.value.hasMoreComments)
    }

    @Test
    fun secondPage_dedupMerge() = runTest(testDispatcher) {
        val env = HobbyDetailEnv()
        env.stubDetailBasics()
        env.stubComments("hot_top", 1, env.detailMock.ok("""{"list":[]}"""))
        env.stubComments(null, 1, commentsJson(1..10))
        env.stubComments(null, 2, commentsJson(9..18))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()
        viewModel.loadNextCommentsPage()
        advanceUntilIdle()
        assertEquals(18, viewModel.uiState.value.latestComments.size)
        assertTrue(viewModel.uiState.value.hasMoreComments)
    }

    @Test
    fun duplicatePage_blocksLoopWithoutGrowth() = runTest(testDispatcher) {
        val env = HobbyDetailEnv()
        env.stubDetailBasics()
        env.stubComments("hot_top", 1, env.detailMock.ok("""{"list":[]}"""))
        env.stubComments(null, 1, commentsJson(1..10))
        env.stubComments(null, 2, commentsJson(1..10))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()
        viewModel.loadNextCommentsPage()
        advanceUntilIdle()
        assertEquals(10, viewModel.uiState.value.latestComments.size)
        assertFalse(viewModel.uiState.value.hasMoreComments)
    }

    @Test
    fun nextPageFailure_reportsErrorAndStopsLoading() = runTest(testDispatcher) {
        val env = HobbyDetailEnv()
        env.stubDetailBasics()
        env.stubComments("hot_top", 1, env.detailMock.ok("""{"list":[]}"""))
        env.stubComments(null, 1, commentsJson(1..10))
        env.stubComments(null, 2, env.detailMock.failure(500, "boom"))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()
        viewModel.loadNextCommentsPage()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoadingMoreComments)
        assertTrue(viewModel.uiState.value.commentsError != null)
    }

    private fun comments(range: IntRange): List<CommentItem> {
        return range.map { CommentItem(id = it.toLong(), content = "Comment $it") }
    }
}
