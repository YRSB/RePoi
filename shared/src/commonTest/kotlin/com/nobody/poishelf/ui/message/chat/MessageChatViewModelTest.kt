package com.nobody.poishelf.ui.message.chat

import com.nobody.poishelf.data.repository.FakeTokenManager
import com.nobody.poishelf.data.repository.MessageRepository
import com.nobody.poishelf.data.repository.MockApi
import com.nobody.poishelf.ui.navigation.MessageChatNavParam
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
 * [MessageChatViewModel] 单元测试：私信加载、发送消息与撤回消息（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `MessageChatViewModelTest`（逐用例；Mockito `mock(MessageRepository)` →
 * 真实 MessageRepository 背后 [MockApi] MockEngine（`thenReturn` 序列 →
 * routeWhen 计数闭包；`verify send` → 请求体断言）；
 * `MainDispatcherRule` → `Dispatchers.setMain`；JUnit4 → kotlin.test + `runTest`）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun MockApi.chatJson(messages: String): String =
        """{"success":true,"code":200,"msg":"ok","data":{"direction":1,"messages":[$messages]}}"""

    private fun MockApi.stubChatHistory(bodies: List<String>) {
        var call = 0
        routeWhen(
            predicate = { path, _ -> path.contains("api/message/get-v2") },
            body = { _, _ -> bodies.getOrElse(call++) { bodies.last() } }
        )
    }

    private fun MockApi.stubLatestMsgId(id: Long) {
        route("api/message/latestMsgId", """{"success":true,"code":200,"msg":"ok","data":{"latestMsgId":$id}}""")
    }

    private fun viewModelOf(mock: MockApi): MessageChatViewModel {
        val repository = MessageRepository(mock.api(), FakeTokenManager("utoken-test"))
        return MessageChatViewModel(repository)
    }

    @Test
    fun loadInitial_successPopulatesMessages() = runTest {
        val mock = MockApi()
        val param = MessageChatNavParam(targetUserId = 2002L, targetUserName = "测试手办党")
        mock.stubChatHistory(
            listOf(mock.chatJson("""{"messageId":101,"fromId":2002,"toId":1001,"content":"第一条测试消息"}"""))
        )
        mock.stubLatestMsgId(101L)

        val viewModel = viewModelOf(mock)
        viewModel.loadInitial(param)
        viewModel.stopPolling()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(2002L, state.targetUserId)
        assertEquals(1, state.messages.size)
        assertEquals("第一条测试消息", state.messages[0].content)
    }

    @Test
    fun sendTextMessage_successSendsAndClearsInput() = runTest {
        val mock = MockApi()
        val param = MessageChatNavParam(targetUserId = 2002L, targetUserName = "测试手办党")
        mock.stubChatHistory(
            listOf(
                mock.chatJson(""),
                mock.chatJson("""{"messageId":102,"fromId":1001,"toId":2002,"content":"新发送的内容"}""")
            )
        )
        mock.route("api/message/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        mock.stubLatestMsgId(102L)

        val viewModel = viewModelOf(mock)
        viewModel.loadInitial(param)
        viewModel.stopPolling()
        advanceUntilIdle()

        viewModel.onInputContentChange("新发送的内容")
        assertEquals("新发送的内容", viewModel.uiState.value.inputContent)

        viewModel.sendTextMessage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.inputContent.isEmpty())
        assertFalse(state.isSending)
        assertEquals(listOf("新发送的内容"), state.messages.map { it.content })
        val sends = (1..mock.requestCount).map { mock.takeRequest() }
        val sendReq = sends.first { it.encodedPath?.contains("api/message/add") == true }
        val form = sendReq.formEntries()
        assertEquals("2002", form["toId"])
        assertEquals("新发送的内容", form["content"])
    }

    @Test
    fun withdrawMessage_successRemovesMessageFromUiState() = runTest {
        val mock = MockApi()
        val param = MessageChatNavParam(targetUserId = 2002L, targetUserName = "测试手办党")
        mock.stubChatHistory(
            listOf(mock.chatJson("""{"messageId":555,"fromId":1001,"toId":2002,"content":"需要撤回的消息"}"""))
        )
        mock.route("api/message/withdraw", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        mock.stubLatestMsgId(555L)

        val viewModel = viewModelOf(mock)
        viewModel.loadInitial(param)
        viewModel.stopPolling()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.messages.size)

        viewModel.withdrawMessage(viewModel.uiState.value.messages.single())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.messages.isEmpty())
        assertEquals("消息已撤回", state.actionMessage)
    }

    @Test
    fun loadMoreHistory_successAppendsAndPreservesExistingMessages() = runTest {
        val mock = MockApi()
        val param = MessageChatNavParam(targetUserId = 2002L, targetUserName = "测试手办党")
        val initialMessages = (0 until 20).joinToString(",") {
            """{"messageId":${101 + it},"fromId":2002,"toId":1001,"content":"第${it + 1}条消息","date":${2000 + it}}"""
        }
        mock.stubChatHistory(
            listOf(
                mock.chatJson(initialMessages),
                mock.chatJson("""{"messageId":100,"fromId":2002,"toId":1001,"content":"更早的历史消息","date":1000}""")
            )
        )
        mock.stubLatestMsgId(120L)

        val viewModel = viewModelOf(mock)
        viewModel.loadInitial(param)
        viewModel.stopPolling()
        advanceUntilIdle()

        assertEquals(20, viewModel.uiState.value.messages.size)

        viewModel.loadMoreHistory()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(21, state.messages.size)
        assertEquals(100L, state.messages[0].actualMessageId)
        assertEquals(101L, state.messages[1].actualMessageId)
    }
}
