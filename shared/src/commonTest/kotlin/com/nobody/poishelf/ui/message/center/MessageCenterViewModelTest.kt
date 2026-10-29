package com.nobody.poishelf.ui.message.center

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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [MessageCenterViewModel] 单元测试：消息中心状态加载、联系人置顶与删除（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `MessageCenterViewModelTest`（逐用例；Mockito `mock(MessageRepository)` →
 * 真实 MessageRepository 背后 [MockApi] MockEngine；`MainDispatcherRule` →
 * `Dispatchers.setMain`；JUnit4 → kotlin.test + `runTest`）。
 * 移植说明：ViewModel `init{}` 即加载，断言前一律先 `enqueue` 再构造 ViewModel，
 * 顺序与原 `when(...).thenReturn` 先桩后构造一致。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageCenterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun viewModelOf(mock: MockApi): MessageCenterViewModel {
        val repository = MessageRepository(mock.api(), FakeTokenManager("utoken-test"))
        return MessageCenterViewModel(repository)
    }

    @Test
    fun loadInitial_successPopulatesPushCountAndContacts() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"map":{"push":{"system":3,"reply":2,"praise":1}}}}""")
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"contact":[{"id":1,"otherId":100,"otherName":"测试用户1","unreadCount":1},{"id":2,"otherId":200,"otherName":"测试用户2","unreadCount":0}]}}""")

        val viewModel = viewModelOf(mock)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(3, state.unreadCounts.system)
        assertEquals(2, state.unreadCounts.reply)
        assertEquals(1, state.unreadCounts.praise)
        assertEquals(2, state.contacts.size)
        assertEquals(100L, state.contacts[0].otherId)
    }

    @Test
    fun toggleContactTop_successUpdatesList() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"map":{"push":{}}}}""")
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"contact":[{"id":10,"otherId":100,"otherName":"测试用户"}]}}""")

        val viewModel = viewModelOf(mock)
        advanceUntilIdle()

        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val contact = viewModel.uiState.value.contacts.single()
        viewModel.toggleContactTop(contact)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("已置顶会话", state.actionMessage)
        assertTrue(state.contacts[0].isTop)
        assertNotNull(state.contacts[0].topTime)
    }

    @Test
    fun deleteContact_successRemovesFromList() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"map":{"push":{}}}}""")
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"contact":[{"id":10,"otherId":100,"otherName":"测试用户"}]}}""")

        val viewModel = viewModelOf(mock)
        advanceUntilIdle()

        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        viewModel.deleteContact(viewModel.uiState.value.contacts.single())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.contacts.isEmpty())
        assertEquals("会话已删除", state.actionMessage)
    }

    @Test
    fun markAllRead_successCallsSingleApiAndResetsUnreadCounts() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"map":{"push":{"system":5}}}}""")
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"contact":[]}}""")

        val viewModel = viewModelOf(mock)
        advanceUntilIdle()

        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        viewModel.markAllRead()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.unreadCounts.system)
        assertEquals("所有通知已标为已读", state.actionMessage)
    }
}
