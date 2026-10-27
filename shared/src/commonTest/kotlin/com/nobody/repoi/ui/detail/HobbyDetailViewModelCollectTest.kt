package com.nobody.repoi.ui.detail

import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.CollectData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 收藏爱心分支的 KMP 移植版（真实仓储 + MockEngine）。
 *
 * 广播同步测试直接调用真 `CollectRepository.saveCollect/deleteCollect`（`api/collect/hobby/upd`
 * 走规则返回成功），触发 `_collectUpdates.tryEmit`，替代原先手持 MutableSharedFlow 的 emit。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HobbyDetailViewModelCollectTest {

    private val env = HobbyDetailTestEnv()

    @BeforeTest
    fun setUp() = env.setUp()

    @AfterTest
    fun tearDown() = env.tearDown()

    private val careBody: String =
        """{"success":true,"code":200,"msg":"ok","data":{"collect":{"id":100,"state":"care"}}}"""

    private val nullBody: String =
        """{"success":true,"code":200,"msg":"ok","data":{"collect":null}}"""

    @Test
    fun loadDetail_whenCollected_isFavoriteTrue() = runTest(env.dispatchers.testDispatcher) {
        env.stubBasics(collectBody = careBody)

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isFavorite)
        assertEquals(
            CollectData(id = 100L, state = CollectConstants.STATE_CARE),
            viewModel.uiState.value.currentCollectData
        )
        viewModel.close()
    }

    @Test
    fun loadDetail_whenNotCollected_isFavoriteFalse() = runTest(env.dispatchers.testDispatcher) {
        env.stubBasics(collectBody = nullBody)

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFavorite)
        viewModel.close()
    }

    @Test
    fun collectUpdates_broadcastSync_updatesFavoriteAndCountDelta() = runTest(env.dispatchers.testDispatcher) {
        env.stubBasics(collectBody = nullBody)
        env.mock.respondWhen(
            Regex.fromLiteral("api/collect/hobby/upd"),
            """{"success":true,"code":200,"msg":"ok","data":{}}"""
        )

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFavorite)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)

        // 外部保存收藏广播 -> 触发状态转变为已收藏，delta + 1
        val newData = CollectData(id = 100L, state = CollectConstants.STATE_BUY)
        assertTrue(env.collectRepo.saveCollect(100L, newData).isSuccess)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isFavorite)
        assertEquals(1, viewModel.uiState.value.collectCountDelta)

        // 外部取消收藏广播 -> 触发状态转变为未收藏，delta 回退为 0
        assertTrue(env.collectRepo.deleteCollect(100L, newData).isSuccess)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFavorite)
        assertEquals(0, viewModel.uiState.value.collectCountDelta)
        viewModel.close()
    }

    @Test
    fun loadDetail_whenCollectStateReturnsNull_explicitlyResetsFavoriteToFalse() = runTest(env.dispatchers.testDispatcher) {
        // 按 itemId/nodeId 分流：59570/100 走已收藏，60000/200 走未收藏
        env.mock.dynamicResponder = { req ->
            when {
                req.pathHas("api/item/get-v2") -> {
                    val itemId = req.url.parameters["itemId"] ?: "59570"
                    val nodeId = if (itemId == "60000") 200L else 100L
                    (env.detailJson(itemId.toLong(), nodeId) to HttpStatusCode.OK)
                }
                req.pathHas("api/collect/state") -> {
                    val body = if (req.formField("id") == "200") nullBody else careBody
                    (body to HttpStatusCode.OK)
                }
                else -> null
            }
        }
        env.stubBasics()

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isFavorite)

        // 加载新条目，该条目接口返回未收藏 (null)
        viewModel.loadDetail(DetailNavParam(itemId = 60000L))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFavorite)
        assertNull(viewModel.uiState.value.currentCollectData)
        viewModel.close()
    }

    @Test
    fun loadDetail_whenCollectFails_keepsInitialState() = runTest(env.dispatchers.testDispatcher) {
        env.stubBasics(collectBody = env.failEnvelope(msg = "网络异常"))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        // 失败不误报为未收藏，保持初始态
        assertFalse(viewModel.uiState.value.isFavorite)
        assertNull(viewModel.uiState.value.currentCollectData)
        viewModel.close()
    }
}
