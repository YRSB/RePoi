package com.nobody.repoi.ui.detail

import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 评论翻页分支的 KMP 移植版（真实仓储 + MockEngine + 真 CommentInteractionDelegate）。
 *
 * 首屏 hot_top 与最新两路并发，翻页追加第三路，因此评论端点全部走 [MockRepoEngine.dynamicResponder]
 * 按表单 `(type, page)` 分流，其余端点走规则。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HobbyDetailViewModelPagingTest {

    private val env = HobbyDetailTestEnv()

    @BeforeTest
    fun setUp() = env.setUp()

    @AfterTest
    fun tearDown() = env.tearDown()

    private fun commentJson(id: Long): String = """{"id":$id,"content":"Comment $id"}"""

    private fun commentsJson(range: IntRange): String =
        """{"success":true,"code":200,"msg":"ok","data":{"list":[${range.joinToString(",") { commentJson(it.toLong()) }}]}}"""

    private val emptyComments: String =
        """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}"""

    /**
     * 按 `(type, page)` 注册评论响应；key 形如 `"hot_top|1"`、`"latest|2"`（type 缺失记为 latest）。
     */
    private fun stubComments(pages: Map<String, String>) {
        env.mock.dynamicResponder = { req: HttpRequestData ->
            if (!req.pathHas("api/comment/get")) {
                null
            } else {
                val type = req.formField("type") ?: "latest"
                val page = req.formField("page") ?: "1"
                val body = pages["$type|$page"] ?: emptyComments
                body to HttpStatusCode.OK
            }
        }
    }

    private fun stubBasicsWithoutComments() = env.stubBasics(commentsBody = null)

    @Test
    fun firstPageFull_hasMoreTrue() = runTest(env.dispatchers.testDispatcher) {
        stubBasicsWithoutComments()
        stubComments(mapOf("latest|1" to commentsJson(1..10)))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        assertEquals(10, viewModel.uiState.value.latestComments.size)
        assertTrue(viewModel.uiState.value.hasMoreComments)
        viewModel.close()
    }

    @Test
    fun firstPageShort_hasMoreFalse() = runTest(env.dispatchers.testDispatcher) {
        stubBasicsWithoutComments()
        stubComments(mapOf("latest|1" to commentsJson(1..3)))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.latestComments.size)
        assertFalse(viewModel.uiState.value.hasMoreComments)
        viewModel.close()
    }

    @Test
    fun secondPage_dedupMerge() = runTest(env.dispatchers.testDispatcher) {
        stubBasicsWithoutComments()
        stubComments(
            mapOf(
                "latest|1" to commentsJson(1..10),
                "latest|2" to commentsJson(9..18)
            )
        )

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()
        viewModel.loadNextCommentsPage()
        advanceUntilIdle()

        assertEquals(18, viewModel.uiState.value.latestComments.size)
        assertTrue(viewModel.uiState.value.hasMoreComments)
        viewModel.close()
    }

    @Test
    fun duplicatePage_blocksLoopWithoutGrowth() = runTest(env.dispatchers.testDispatcher) {
        stubBasicsWithoutComments()
        stubComments(
            mapOf(
                "latest|1" to commentsJson(1..10),
                "latest|2" to commentsJson(1..10)
            )
        )

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()
        viewModel.loadNextCommentsPage()
        advanceUntilIdle()

        assertEquals(10, viewModel.uiState.value.latestComments.size)
        assertFalse(viewModel.uiState.value.hasMoreComments)
        viewModel.close()
    }

    @Test
    fun nextPageFailure_reportsErrorAndStopsLoading() = runTest(env.dispatchers.testDispatcher) {
        stubBasicsWithoutComments()
        stubComments(
            mapOf(
                "latest|1" to commentsJson(1..10),
                "latest|2" to env.failEnvelope(msg = "boom")
            )
        )

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()
        viewModel.loadNextCommentsPage()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoadingMoreComments)
        assertNotNull(viewModel.uiState.value.commentsError)
        viewModel.close()
    }
}
