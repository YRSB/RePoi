package com.nobody.poishelf.ui.message.block

import com.nobody.poishelf.data.remote.model.BlockItem
import com.nobody.poishelf.data.repository.FakeTokenManager
import com.nobody.poishelf.data.repository.MessageRepository
import com.nobody.poishelf.data.repository.MockApi
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
 * 黑名单 ViewModel 测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `MessageBlockViewModelTest`（逐用例；Mockito `mock(MessageRepository)` →
 * 真实 MessageRepository 背后 [MockApi] MockEngine（`thenReturn` 序列 →
 * routeWhen 计数闭包按 page 匹配；`verify getBlockList(page=2)` →
 * 取请求断言）；`MainDispatcherRule` → `Dispatchers.setMain`；
 * JUnit4 → kotlin.test + `runTest`）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageBlockViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun blockJson(id: Long, name: String = "用户$id"): String =
        """{"id":$id,"name":"$name"}"""

    private fun listJson(ids: List<Long>): String {
        val list = ids.joinToString(",") { blockJson(it) }
        return """{"success":true,"code":200,"msg":"ok","data":{"userBlock":[$list]}}"""
    }

    private fun MockApi.stubBlockList(bodies: List<String>) {
        route(
            "api/block/list",
            bodies.first()
        )
        if (bodies.size > 1) {
            routeWhen(
                predicate = { path, form -> path.contains("api/block/list") && form["page"] == "2" },
                body = { _, _ -> bodies[1] }
            )
        }
        // 首屏之外的后续页回落首包（按序消费由调用方 enqueue 保证）
        var extra = 1
        routeWhen(
            predicate = { path, form -> path.contains("api/block/list") && form["page"] != null && form["page"] != "1" && form["page"] != "2" },
            body = { _, _ -> bodies.getOrElse(extra++) { bodies.last() } }
        )
    }

    private fun viewModelOf(mock: MockApi): MessageBlockViewModel {
        val repository = MessageRepository(mock.api(), FakeTokenManager("utoken-test"))
        return MessageBlockViewModel(repository)
    }

    private fun block(id: Long, name: String = "用户$id"): BlockItem {
        return BlockItem(id = id, name = name)
    }

    @Test
    fun initialLoad_populatesBlockList() = runTest {
        val mock = MockApi()
        mock.stubBlockList(listOf(listJson(listOf(1, 2))))

        val viewModel = viewModelOf(mock)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1L, 2L), state.blockList.map { it.id })
        assertEquals(1, state.currentPage)
        assertFalse(state.hasMore)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun initialLoad_failureSetsError() = runTest {
        val mock = MockApi()
        mock.stubBlockList(listOf(mock.failure(500, "网络异常")))

        val viewModel = viewModelOf(mock)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("网络异常", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun loadMore_appendsAndDeduplicates() = runTest {
        val mock = MockApi()
        mock.stubBlockList(
            listOf(
                listJson((1L..50L).toList()),
                listJson(listOf(50L, 51L))
            )
        )

        val viewModel = viewModelOf(mock)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasMore)

        viewModel.loadMore()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(51, state.blockList.size)
        assertEquals(51L, state.blockList.last().id)
        assertEquals(2, state.currentPage)
        assertFalse(state.hasMore)
        mock.takeRequest() // page=1 首屏
        val last = mock.takeRequest() // page=2 翻页（POST 表单）
        assertEquals("2", last.formEntries()["page"])
    }

    @Test
    fun loadMore_whenNoMore_skipsRequest() = runTest {
        val mock = MockApi()
        mock.stubBlockList(listOf(listJson(listOf(1))))

        val viewModel = viewModelOf(mock)
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(1, mock.requestCount)
    }

    @Test
    fun executeUnblock_removesTargetAndShowsMessage() = runTest {
        val mock = MockApi()
        mock.stubBlockList(listOf(listJson(listOf(1, 2))))
        mock.route("api/message/unblock", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        mock.route("api/block/upd", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        val viewModel = viewModelOf(mock)
        advanceUntilIdle()
        viewModel.confirmUnblock(block(1))
        assertEquals(1L, viewModel.uiState.value.unblockTarget?.id)

        viewModel.executeUnblock()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(2L), state.blockList.map { it.id })
        assertNull(state.unblockTarget)
        assertEquals("已解除对「用户1」的屏蔽", state.actionMessage)
        val last = mock.takeRequest()
        assertTrue((last.formEntries()["userId"] ?: last.formEntries()["id"]) == "1")
    }

    @Test
    fun executeUnblock_failureKeepsListAndSetsError() = runTest {
        val mock = MockApi()
        mock.stubBlockList(listOf(listJson(listOf(1))))
        mock.route("api/message/unblock", mock.failure(500, "解除失败"))
        mock.route("api/block/upd", mock.failure(500, "解除失败"))

        val viewModel = viewModelOf(mock)
        advanceUntilIdle()
        viewModel.confirmUnblock(block(1))
        viewModel.executeUnblock()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(1L), state.blockList.map { it.id })
        assertEquals("解除失败", state.errorMessage)
    }

    @Test
    fun dialogsAndMessages_canBeDismissed() = runTest {
        val mock = MockApi()
        mock.stubBlockList(listOf(listJson(emptyList())))
        mock.route("api/message/unblock", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        mock.route("api/block/upd", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        val viewModel = viewModelOf(mock)
        advanceUntilIdle()

        viewModel.confirmUnblock(block(9))
        viewModel.dismissUnblockDialog()
        assertNull(viewModel.uiState.value.unblockTarget)

        viewModel.confirmUnblock(block(9))
        viewModel.executeUnblock()
        advanceUntilIdle()
        viewModel.clearActionMessage()
        assertNull(viewModel.uiState.value.actionMessage)

        viewModel.clearErrorMessage()
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
