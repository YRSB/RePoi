package com.nobody.poishelf.ui.message.notice

import com.nobody.poishelf.data.repository.FakeTokenManager
import com.nobody.poishelf.data.repository.MessageRepository
import com.nobody.poishelf.data.repository.MockApi
import com.nobody.poishelf.ui.navigation.MessageNoticeNavParam
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
 * [MessageNoticeViewModel] 单元测试：分类通知加载、标已读与批量删除（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `MessageNoticeViewModelTest`（逐用例；Mockito `mock(MessageRepository)` →
 * 真实 MessageRepository 背后 [MockApi] MockEngine；`MainDispatcherRule` →
 * `Dispatchers.setMain`；JUnit4 → kotlin.test + `runTest`）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageNoticeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun viewModelOf(mock: MockApi): MessageNoticeViewModel {
        val repository = MessageRepository(mock.api(), FakeTokenManager("utoken-test"))
        return MessageNoticeViewModel(repository)
    }

    private fun MockApi.stubPushMessages(body: String) {
        route(
            "api/push/reply",
            body
        )
    }

    private fun pushJson(items: String): String =
        """{"success":true,"code":200,"msg":"ok","data":{"pushMessage":[$items]}}"""

    @Test
    fun loadInitial_successPopulatesMessages() = runTest(testDispatcher) {
        val mock = MockApi()
        val param = MessageNoticeNavParam(type = "reply", typeNumber = 2, title = "回复通知")
        mock.stubPushMessages(pushJson("""{"id":11,"type":2,"state":0,"content":"有人回复了你"}"""))

        val viewModel = viewModelOf(mock)
        viewModel.loadInitial(param)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("reply", state.type)
        assertEquals(1, state.messages.size)
        assertTrue(state.messages[0].isUnread)
    }

    @Test
    fun markAllRead_successUpdatesAllMessagesToRead() = runTest(testDispatcher) {
        val mock = MockApi()
        val param = MessageNoticeNavParam(type = "reply", typeNumber = 2, title = "回复通知")
        mock.stubPushMessages(pushJson("""{"id":11,"type":2,"state":0,"content":"有人回复了你"}"""))
        mock.route("api/push/read", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        val viewModel = viewModelOf(mock)
        viewModel.loadInitial(param)
        advanceUntilIdle()

        viewModel.markAllRead()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.messages[0].isUnread)
        assertEquals("已标为全部已读", state.actionMessage)
    }

    @Test
    fun batchDeleteSelected_successRemovesSelectedItems() = runTest(testDispatcher) {
        val mock = MockApi()
        val param = MessageNoticeNavParam(type = "reply", typeNumber = 2, title = "回复通知")
        mock.stubPushMessages(pushJson("""{"id":101,"type":2,"state":1},{"id":102,"type":2,"state":1}"""))
        mock.route("api/push/batch/del", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        val viewModel = viewModelOf(mock)
        viewModel.loadInitial(param)
        advanceUntilIdle()

        viewModel.toggleManagerMode()
        viewModel.toggleSelectId(101L)
        assertEquals(1, viewModel.uiState.value.selectedCount)

        viewModel.batchDeleteSelected()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.messages.size)
        assertEquals(102L, state.messages[0].id)
        assertFalse(state.isManagerMode)
    }

    @Test
    fun invertSelection_successInvertsSelectedItems() = runTest(testDispatcher) {
        val mock = MockApi()
        val param = MessageNoticeNavParam(type = "reply", typeNumber = 2, title = "回复通知")
        mock.stubPushMessages(pushJson("""{"id":101,"type":2,"state":1},{"id":102,"type":2,"state":1}"""))

        val viewModel = viewModelOf(mock)
        viewModel.loadInitial(param)
        advanceUntilIdle()

        viewModel.toggleSelectId(101L)
        assertEquals(setOf(101L), viewModel.uiState.value.selectedIds)

        viewModel.invertSelection()
        assertEquals(setOf(102L), viewModel.uiState.value.selectedIds)
    }
}
