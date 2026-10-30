package com.nobody.poishelf.data.repository

import com.nobody.poishelf.data.remote.model.ApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [MessageRepository] 单元测试：覆盖通知未读计数、私信联系人、聊天记录、撤回与黑名单契约（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `MessageRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；Mockito → [FakeTokenManager]；
 * JUnit4 → kotlin.test）。
 */
class MessageRepositoryTest {

    private fun setup(token: String = "utoken-test"): Pair<MockApi, MessageRepository> {
        val mock = MockApi()
        return mock to MessageRepository(mock.api(), FakeTokenManager(token))
    }

    @Test
    fun getUnreadPushCount_successReturnsCategories() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"map":{"push":{"system":2,"reply":3,"praise":5,"recommend":0,"follownews":4,"commodity":1,"posts_reply":0}}}}"""
        )
        val result = repository.getUnreadPushCount()
        assertTrue(result.isSuccess)
        val pushCategory = result.getOrThrow()
        assertEquals(2, pushCategory.system)
        assertEquals(3, pushCategory.reply)
        assertEquals(5, pushCategory.praise)
        assertEquals(4, pushCategory.actualFollowCount)
        assertEquals(15, pushCategory.totalCount)
    }

    @Test
    fun getMessageContacts_successReturnsContactList() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"contact":[{"id":1001,"otherId":2002,"otherName":"测试手办党","otherHeader":"avatar.jpg","lastTime":"2026-03-29 10:00:00","topTime":"2026-03-29 10:00:00","unreadCount":2}]}}"""
        )
        val result = repository.getMessageContacts(page = 1)
        assertTrue(result.isSuccess)
        val contacts = result.getOrThrow()
        assertEquals(1, contacts.size)
        assertEquals(2002L, contacts[0].otherId)
        assertEquals("测试手办党", contacts[0].otherName)
        assertTrue(contacts[0].isTop)
        assertEquals(2, contacts[0].unreadCount)
    }

    @Test
    fun getChatMessages_successParsesHistory() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"direction":1,"messages":[{"messageId":888,"fromId":2002,"toId":1001,"content":"在的，支持包邮","date":"2026-03-29 10:05:00","type":1}]}}"""
        )
        val result = repository.getChatMessages(fromId = 2002L, page = 1)
        assertTrue(result.isSuccess)
        val payload = result.getOrThrow()
        assertEquals(1, payload.messages.size)
        assertEquals(888L, payload.messages[0].actualMessageId)
        assertEquals("在的，支持包邮", payload.messages[0].content)
        assertFalse(payload.messages[0].isPicture)
    }

    @Test
    fun sendTextMessage_successReturnsUnit() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":null}""")
        val result = repository.sendTextMessage(toId = 2002L, content = "好的，我拍下了")
        assertTrue(result.isSuccess)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/message/add-v2", recorded.path)
        val form = recorded.formEntries()
        assertEquals("2002", form["toId"])
        assertEquals("好的，我拍下了", form["content"])
        assertFalse(form.containsKey("emoticon"))
    }

    @Test
    fun withdrawMessage_successReturnsUnit() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":null}""")
        val result = repository.withdrawMessage(messageId = 888L)
        assertTrue(result.isSuccess)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/message/withdraw", recorded.path)
        assertEquals("888", recorded.formEntries()["messageId"])
    }

    @Test
    fun getBlockList_successReturnsBlockedUsers() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"userBlock":[{"id":55,"name":"黑名单用户","header":"block.jpg","intro":"测试","blockState":1}]}}"""
        )
        val result = repository.getBlockList(page = 1)
        assertTrue(result.isSuccess)
        val blockList = result.getOrThrow()
        assertEquals(1, blockList.size)
        assertEquals(55L, blockList[0].id)
        assertEquals("黑名单用户", blockList[0].name)
    }

    @Test
    fun apiError_throwsApiException() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(403, "已被对方屏蔽"))
        val result = repository.sendTextMessage(toId = 2002L, content = "你好")
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is ApiException)
        assertEquals("已被对方屏蔽", exception.message)
    }
}
