package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 评论互动仓储契约单测：子评论详情分页、点赞切换、回复发表。
 *
 * 通过 [MockApi] 驱动真实 HpoiApi；令牌失效与评论未命中的判定已下沉到客户端网络管道，
 * 本测试只校验仓储对外可观察的 `Result<T>` 语义。
 */
class CommentRepositoryTest {

    private fun setup(token: String = "utoken-test"): Pair<MockApi, CommentRepository> {
        val mock = MockApi()
        return mock to CommentRepository(mock.api(tokenProvider = { token }))
    }

    @Test
    fun getReplies_mapsCommentAndList() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"comment":{"id":6305111,"content":"主楼","reply":1},"list":[{"id":6305321,"content":"子评论","user":{"id":2430462,"nickname":"Terry"},"toUser":null}]}}"""
        )
        val result = repository.getReplies(6305111L, 1)
        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        assertEquals(6305111L, page.comment?.id)
        assertEquals(1, page.replies.size)
        assertEquals("Terry", page.replies.first().user?.nickname)
        val recorded = mock.takeRequest()
        assertTrue(recorded.bodyContains("id=6305111"))
    }

    @Test
    fun getReplies_failureThrows() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(404, "评论已删除"))
        val result = repository.getReplies(1L, 1)
        assertTrue(result.isFailure)
        assertEquals(404, (result.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun getReplies_notFoundReturnsEmptyPage() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(-9007, "找不到该评论"))
        val result = repository.getReplies(1L, 1)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().replies.isEmpty())
    }

    @Test
    fun getReplies_isDiscuss_callsDiscussReplyGet() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":-1,"msg":"","data":{"discuss":{"id":36213759,"floor":7,"replyCount":1,"content":"讨论主楼","user":{"id":10,"nickname":"Bob"}},"discussReplyList":[{"id":39647123,"content":"讨论子回复","user":{"id":100,"nickname":"Alice"}}]}}"""
        )
        val result = repository.getReplies(commentId = 36213759L, page = 1, isDiscuss = true)
        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        assertEquals(36213759L, page.comment?.id)
        assertEquals(1, page.replies.size)
        assertEquals("Alice", page.replies.first().user?.nickname)
        val recorded = mock.takeRequest()
        assertTrue(recorded.encodedPath!!.startsWith("/api/discuss/reply/get"))
        assertTrue(recorded.bodyContains("discussNodeId=36213759"))
    }

    @Test
    fun getReplies_isDiscussFailure_throwsWithoutFallingBack() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(400, "楼层不存在"))
        mock.enqueue("""{"comment":{"id":1},"list":[]}""")

        val result = repository.getReplies(commentId = 36213759L, page = 1, isDiscuss = true)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull() as ApiException
        assertEquals(400, exception.code)
        assertEquals("楼层不存在", exception.message)
        // 讨论楼层失败后不得静默回落常规评论接口
        assertEquals(1, mock.requestCount)
    }

    @Test
    fun getReplies_fallbackToDiscussOnDeletedComment() = runTest {
        val (mock, repository) = setup()
        // 第一次调用通用接口失败（-900 该评论已被删除）
        mock.enqueue(mock.failure(-900, "该评论已被删除"))
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
    }

    @Test
    fun togglePraise_unpraisedCallsAdd() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val result = repository.togglePraise(commentId = 10L, relateNodeId = 100L, praised = false)
        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertTrue(recorded.encodedPath!!.startsWith("/api/item/praise/add"))
        val body = recorded.formBodyText()
        assertTrue(body.contains("id=10"))
        assertTrue(body.contains("itemType=comment"))
    }

    @Test
    fun togglePraise_praisedCallsDel() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val result = repository.togglePraise(commentId = 10L, relateNodeId = 100L, praised = true)
        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertTrue(recorded.encodedPath!!.startsWith("/api/item/praise/del"))
    }

    @Test
    fun addReply_mapsNewReplyAndSendsTargets() = runTest {
        val (mock, repository) = setup()
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
        val recorded = mock.takeRequest()
        val body = recorded.formBodyText()
        assertTrue(body.contains("commentId=10"))
        assertTrue(body.contains("toUserId=2"))
        assertTrue(body.contains("toReply=8"))
    }

    @Test
    fun addReply_missingReplyFallsBackToGetReplies() = runTest {
        val (mock, repository) = setup()
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
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val result = repository.addComment(nodeId = 5483525L, content = "test")
        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        val body = recorded.formBodyText()
        assertTrue(body.contains("id=5483525"))
        assertTrue(body.contains("content=test"))
    }

    @Test
    fun addComment_failureThrows() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(400, "内容含敏感词"))
        val result = repository.addComment(nodeId = 1L, content = "hi")
        assertTrue(result.isFailure)
        assertEquals(400, (result.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun deleteComment_mapsNodeIdAndType() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val result = repository.deleteComment(nodeId = 10L, type = "comment")
        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        val body = recorded.formBodyText()
        assertTrue(body.contains("nodeId=10"))
        assertTrue(body.contains("type=comment"))
    }

    @Test
    fun deleteComment_failureThrows() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(400, "删除评论失败"))
        val result = repository.deleteComment(nodeId = 10L, type = "comment")
        assertTrue(result.isFailure)
        assertEquals(400, (result.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun addComment_tokenExpired_notifiesDeepPipeline() = runTest {
        val (mock, repository) = setup(token = "tok-comment")
        mock.enqueue("""{"success":false,"code":4001,"msg":"登录已失效"}""")
        val result = repository.addComment(nodeId = 123L, content = "测试评论")
        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
        mock.assertTokenExpiredNotified("tok-comment")
    }

    @Test
    fun togglePraise_tokenExpired_notifiesDeepPipeline() = runTest {
        val (mock, repository) = setup(token = "tok-praise")
        mock.enqueue("""{"success":false,"code":4001,"msg":"登录已失效"}""")
        val result = repository.togglePraise(commentId = 123L, relateNodeId = 456L, praised = false)
        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
        mock.assertTokenExpiredNotified("tok-praise")
    }

    @Test
    fun getReplies_非领域码即使消息含删除也不回落Discuss() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(4003, "该评论已被删除"))
        mock.enqueue(
            """{"success":true,"code":-1,"msg":"","data":{"discuss":{"id":36213759,"floor":7,"replyCount":0,"content":"自愈主楼"},"discussReplyList":[]}}"""
        )

        val result = repository.getReplies(commentId = 36213759L, page = 1)

        assertTrue(result.isFailure)
        assertEquals(4003, (result.exceptionOrNull() as ApiException).code)
        assertEquals(1, mock.requestCount)
    }

}
