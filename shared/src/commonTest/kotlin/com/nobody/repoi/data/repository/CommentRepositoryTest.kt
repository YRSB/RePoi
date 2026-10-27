package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDataStores
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.serialization.json.Json

/**
 * 评论互动仓储契约单测：子评论详情分页、点赞切换、回复发表。
 */
class CommentRepositoryTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var repository: CommentRepository
    private lateinit var tokenManager: TokenManager
    private lateinit var stores: TestDataStores

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        stores = TestDataStores()
        tokenManager = TokenManager(stores.createDataStore("token"))
        tokenManager.currentToken = "utoken-test"
        repository = CommentRepository(api, tokenManager)
    }

    @AfterTest
    fun tearDown() {
        client.close()
        stores.cleanUp()
    }

    @Test
    fun getReplies_mapsCommentAndList() = runTest {
        mock.enqueue(
                """{"success":true,"code":200,"msg":"ok","data":{"comment":{"id":6305111,"content":"主楼","reply":1},"list":[{"id":6305321,"content":"子评论","user":{"id":2430462,"nickname":"Terry"},"toUser":null}]}}"""
            )
        val result = repository.getReplies(6305111L, 1)
        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        assertEquals(6305111L, page.comment?.id)
        assertEquals(1, page.replies.size)
        assertEquals("Terry", page.replies.first().user?.nickname)
        val request = mock.takeRequest()
        assertTrue(request.recordedBody().contains("id=6305111"))
    }

    @Test
    fun getReplies_failureThrows() = runTest {
        mock.enqueue("""{"success":false,"code":404,"msg":"评论已删除","data":null}""")
        val result = repository.getReplies(1L, 1)
        assertTrue(result.isFailure)
        assertEquals(404, (result.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun getReplies_isDiscuss_callsDiscussReplyGet() = runTest {
        mock.enqueue(
                """{"success":true,"code":-1,"msg":"","data":{"discuss":{"id":36213759,"floor":7,"replyCount":1,"content":"讨论主楼","user":{"id":10,"nickname":"Bob"}},"discussReplyList":[{"id":39647123,"content":"讨论子回复","user":{"id":100,"nickname":"Alice"}}]}}"""
            )
        val result = repository.getReplies(commentId = 36213759L, page = 1, isDiscuss = true)
        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        assertEquals(36213759L, page.comment?.id)
        assertEquals(1, page.replies.size)
        assertEquals("Alice", page.replies.first().user?.nickname)
        val request = mock.takeRequest()
        assertTrue(request.recordedPath!!.startsWith("/api/discuss/reply/get"))
        assertTrue(request.recordedBody().contains("discussNodeId=36213759"))
    }

    @Test
    fun getReplies_fallbackToDiscussOnDeletedComment() = runTest {
        // 第一次调用通用接口失败（-900 该评论已被删除）
        mock.enqueue("""{"success":false,"code":-900,"msg":"该评论已被删除","data":null}""")
        // 自动降级自愈调用 discuss 接口成功
        mock.enqueue(
                """{"success":true,"code":-1,"msg":"","data":{"discuss":{"id":36213759,"floor":7,"replyCount":1,"content":"自愈主楼"},"discussReplyList":[{"id":39647123,"content":"自愈回复","user":{"id":100,"nickname":"Alice"}}]}}"""
            )
        val result = repository.getReplies(commentId = 36213759L, page = 1, isDiscuss = false)
        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        assertEquals(36213759L, page.comment?.id)
        assertEquals(1, page.replies.size)
        assertEquals("自愈回复", page.replies.first().contentCleanText)
        assertEquals(2, mock.requestCount)
    }

    @Test
    fun togglePraise_unpraisedCallsAdd() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val result = repository.togglePraise(commentId = 10L, relateNodeId = 100L, praised = false)
        assertTrue(result.isSuccess)
        val request = mock.takeRequest()
        assertTrue(request.recordedPath!!.startsWith("/api/item/praise/add"))
        val body = request.recordedBody()
        assertTrue(body.contains("id=10"))
        assertTrue(body.contains("itemType=comment"))
    }

    @Test
    fun togglePraise_praisedCallsDel() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val result = repository.togglePraise(commentId = 10L, relateNodeId = 100L, praised = true)
        assertTrue(result.isSuccess)
        val request = mock.takeRequest()
        assertTrue(request.recordedPath!!.startsWith("/api/item/praise/del"))
    }

    @Test
    fun togglePraise_withoutLoginFailsBeforeRequest() = runTest {
        tokenManager.currentToken = ""
        val result = repository.togglePraise(commentId = 10L, relateNodeId = 100L, praised = false)
        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun addReply_mapsNewReplyAndSendsTargets() = runTest {
        mock.enqueue(
                """{"success":true,"code":200,"msg":"ok","data":{"reply":{"id":99,"content":"_(´ཀ`」 ∠)_","user":{"id":1,"nickname":"我"},"toUser":{"id":2,"nickname":"目标"}}}}"""
            )
        val result = repository.addReply(
            commentId = 10L,
            content = "_(´ཀ`」 ∠)_",
            toUserId = 2L,
            toReplyId = 8L
        )
        assertTrue(result.isSuccess)
        assertEquals(99L, result.getOrThrow().id)
        assertEquals("目标", result.getOrThrow().toUser?.nickname)
        val request = mock.takeRequest()
        val body = request.recordedBody()
        assertTrue(body.contains("commentId=10"))
        assertTrue(body.contains("toUserId=2"))
        assertTrue(body.contains("toReply=8"))
    }

    @Test
    fun addReply_withoutLoginFailsBeforeRequest() = runTest {
        tokenManager.currentToken = ""
        val result = repository.addReply(commentId = 10L, content = "hi")
        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun addReply_missingReplyFallsBackToGetReplies() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        mock.enqueue(
                """{"success":true,"code":200,"msg":"ok","data":{"comment":{"id":10,"reply":1},"list":[{"id":101,"content":"fallback reply","user":{"id":1,"nickname":"我"}}]}}"""
            )
        val result = repository.addReply(commentId = 10L, content = "fallback reply")
        assertTrue(result.isSuccess)
        assertEquals(101L, result.getOrThrow().id)
        assertEquals("fallback reply", result.getOrThrow().content)
        assertEquals(2, mock.requestCount)
    }

    @Test
    fun addComment_mapsNodeIdAndContent() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val result = repository.addComment(nodeId = 5483525L, content = "test")
        assertTrue(result.isSuccess)
        val request = mock.takeRequest()
        val body = request.recordedBody()
        assertTrue(body.contains("id=5483525"))
        assertTrue(body.contains("content=test"))
    }

    @Test
    fun addComment_withoutLoginFailsBeforeRequest() = runTest {
        tokenManager.currentToken = ""
        val result = repository.addComment(nodeId = 1L, content = "hi")
        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun addComment_failureThrows() = runTest {
        mock.enqueue("""{"success":false,"code":400,"msg":"内容含敏感词","data":null}""")
        val result = repository.addComment(nodeId = 1L, content = "hi")
        assertTrue(result.isFailure)
        assertEquals(400, (result.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun deleteComment_mapsNodeIdAndType() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val result = repository.deleteComment(nodeId = 10L, type = "comment")
        assertTrue(result.isSuccess)
        val request = mock.takeRequest()
        val body = request.recordedBody()
        assertTrue(body.contains("nodeId=10"))
        assertTrue(body.contains("type=comment"))
    }

    @Test
    fun deleteComment_withoutLoginFailsBeforeRequest() = runTest {
        tokenManager.currentToken = ""
        val result = repository.deleteComment(nodeId = 10L, type = "comment")
        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun deleteComment_failureThrows() = runTest {
        mock.enqueue("""{"success":false,"code":400,"msg":"删除评论失败","data":null}""")
        val result = repository.deleteComment(nodeId = 10L, type = "comment")
        assertTrue(result.isFailure)
        assertEquals(400, (result.exceptionOrNull() as ApiException).code)
    }
}
