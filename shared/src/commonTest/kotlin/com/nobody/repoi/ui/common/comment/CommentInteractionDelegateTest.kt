package com.nobody.repoi.ui.common.comment

import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.CommentUser
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDataStores
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 评论交互协调委托单测：点赞乐观更新与回滚、子评论回复提交、外部广播监听与提示消费。
 *
 * KMP 侧无 Mockito：用真实仓储（MockEngine 驱动 HTTP）替代 stub；
 * 原 `verify(emit)` 改为经 delegate 自身触发真实广播后断言观察者合并结果，
 * `verify(never())` 改为断言 [MockRepoEngine.requestCount] 为 0；
 * 失败文案断言用 contains（[com.nobody.repoi.data.remote.model.ApiException] 的
 * message 形如 "API 业务异常 (code=..): .."）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommentInteractionDelegateTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var stores: TestDataStores
    private lateinit var tokenManager: TokenManager
    private lateinit var delegate: CommentInteractionDelegate

    private data class TestCommentState(
        override val hotComments: List<CommentItem> = emptyList(),
        override val latestComments: List<CommentItem> = emptyList(),
        override val pendingPraiseIds: Set<Long> = emptySet(),
        override val commentActionMessage: String? = null
    ) : CommentAwareUiState {
        override fun copyWithComments(
            hotComments: List<CommentItem>,
            latestComments: List<CommentItem>,
            pendingPraiseIds: Set<Long>,
            commentActionMessage: String?
        ): TestCommentState = copy(
            hotComments = hotComments,
            latestComments = latestComments,
            pendingPraiseIds = pendingPraiseIds,
            commentActionMessage = commentActionMessage
        )
    }

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        client = HttpClient(mock.engine) { expectSuccess = true }
        stores = TestDataStores()
        tokenManager = TokenManager(stores.createDataStore("token"))
        tokenManager.currentToken = "utoken-test"
        delegate = FakeCommentDelegates.wired(mock, client, tokenManager)
    }

    @AfterTest
    fun tearDown() {
        client.close()
        stores.cleanUp()
    }

    private suspend fun awaitCondition(timeoutMs: Long = 5000, cond: () -> Boolean) {
        withTimeout(timeoutMs) {
            while (!cond()) delay(10)
        }
    }

    private fun sampleComment(id: Long, praised: Boolean = false, praiseCount: Int = 0): CommentItem {
        return CommentItem(
            id = id,
            content = "主楼$id",
            praiseState = praised,
            praiseCount = praiseCount,
            reply = 0,
            replys = emptyList()
        )
    }

    @Test
    fun observeCommentUpdates_mergesExternalUpdate(): Unit = runBlocking {
        // toggle 走 scope.launch + 真仓储（MockEngine 有真实 IO dispatcher 跳转），
        // runTest 的 advanceUntilIdle 等不到真实 IO 线程，改用 runBlocking + 条件轮询。
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val stateFlow = MutableStateFlow(
                TestCommentState(
                    hotComments = listOf(sampleComment(1L)),
                    latestComments = listOf(sampleComment(2L))
                )
            )
            delegate.observeCommentUpdates(scope, stateFlow)

            // 经 delegate 自身触发一次真实点赞成功广播（comment 1L 变为 praised=true/count=1），
            // 观察者应将其合并进 hot 列表。
            mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
            val praiseState = MutableStateFlow(TestCommentState(latestComments = listOf(sampleComment(1L))))
            delegate.toggleCommentPraise(scope, praiseState, 1L, 100L)
            awaitCondition { stateFlow.value.hotComments.first().praiseState }

            assertEquals(true, stateFlow.value.hotComments.first().praiseState)
            assertEquals(1, stateFlow.value.hotComments.first().praiseCount)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun toggleCommentPraise_successUpdatesAndEmits(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val stateFlow = MutableStateFlow(
                TestCommentState(
                    latestComments = listOf(sampleComment(10L, praised = false, praiseCount = 2))
                )
            )
            mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

            delegate.toggleCommentPraise(scope, stateFlow, 10L, 100L)
            awaitCondition { 10L !in stateFlow.value.pendingPraiseIds }

            val comment = stateFlow.value.latestComments.first()
            assertTrue(comment.praiseState)
            assertEquals(3, comment.praiseCount)
            assertFalse(10L in stateFlow.value.pendingPraiseIds)
            assertEquals(1, mock.requestCount)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun toggleCommentPraise_failureRollsBackSetsMessageAndBroadcasts(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val stateFlow = MutableStateFlow(
                TestCommentState(
                    latestComments = listOf(sampleComment(10L, praised = false, praiseCount = 2))
                )
            )
            mock.enqueue("""{"success":false,"code":400,"msg":"点赞网络异常","data":null}""")

            delegate.toggleCommentPraise(scope, stateFlow, 10L, 100L)
            awaitCondition { 10L !in stateFlow.value.pendingPraiseIds }

            val comment = stateFlow.value.latestComments.first()
            assertFalse(comment.praiseState)
            assertEquals(2, comment.praiseCount)
            assertFalse(10L in stateFlow.value.pendingPraiseIds)
            assertTrue(stateFlow.value.commentActionMessage?.contains("点赞网络异常") == true)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun toggleCommentPraise_invalidNodeIdOrPendingSkips() = runTest {
        val stateFlow = MutableStateFlow(
            TestCommentState(
                latestComments = listOf(sampleComment(10L)),
                pendingPraiseIds = setOf(10L)
            )
        )

        // pending 中的 id 跳过
        delegate.toggleCommentPraise(this, stateFlow, 10L, 100L)
        advanceUntilIdle()
        assertEquals(0, mock.requestCount)

        // nodeId <= 0 跳过
        delegate.toggleCommentPraise(this, stateFlow, 20L, 0L)
        advanceUntilIdle()
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun submitCommentReply_successAppendsAndReturnsTrue() = runTest {
        val stateFlow = MutableStateFlow(
            TestCommentState(
                latestComments = listOf(sampleComment(10L))
            )
        )
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"reply":{"id":50,"content":"回复内容","user":{"id":1,"nickname":"我"}}}}"""
        )

        val result = delegate.submitCommentReply(stateFlow, 10L, null, "回复内容", emptyList())
        advanceUntilIdle()

        assertTrue(result)
        val comment = stateFlow.value.latestComments.first()
        assertEquals(1, comment.reply)
        assertEquals(listOf(50L), comment.replys?.map { it.id })
        assertEquals("回复发表成功", stateFlow.value.commentActionMessage)
    }

    @Test
    fun submitCommentReply_failureSetsErrorAndReturnsFalse() = runTest {
        val stateFlow = MutableStateFlow(
            TestCommentState(
                latestComments = listOf(sampleComment(10L))
            )
        )
        mock.enqueue("""{"success":false,"code":400,"msg":"敏感词拦截","data":null}""")

        val result = delegate.submitCommentReply(stateFlow, 10L, null, "回复内容", emptyList())
        advanceUntilIdle()

        assertFalse(result)
        val comment = stateFlow.value.latestComments.first()
        assertEquals(0, comment.reply)
        assertTrue(stateFlow.value.commentActionMessage?.contains("敏感词拦截") == true)
    }

    @Test
    fun submitCommentReply_blankReturnsFalseWithoutCalling() = runTest {
        val stateFlow = MutableStateFlow(TestCommentState())
        val result = delegate.submitCommentReply(stateFlow, 10L, null, "   ", emptyList())
        assertFalse(result)
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun postTopLevelComment_successSetsMessageAndReturnsTrue() = runTest {
        val stateFlow = MutableStateFlow(TestCommentState())
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = delegate.postTopLevelComment(stateFlow, 100L, "新人求问", emptyList())
        advanceUntilIdle()

        assertTrue(result)
        assertEquals("评论发表成功", stateFlow.value.commentActionMessage)
    }

    @Test
    fun postTopLevelComment_failureSetsErrorAndReturnsFalse() = runTest {
        val stateFlow = MutableStateFlow(TestCommentState())
        mock.enqueue("""{"success":false,"code":400,"msg":"内容含敏感词","data":null}""")

        val result = delegate.postTopLevelComment(stateFlow, 100L, "新人求问", emptyList())
        advanceUntilIdle()

        assertFalse(result)
        assertTrue(stateFlow.value.commentActionMessage?.contains("内容含敏感词") == true)
    }

    @Test
    fun postTopLevelComment_blankOrInvalidNodeIdReturnsFalse() = runTest {
        val stateFlow = MutableStateFlow(TestCommentState())
        assertFalse(delegate.postTopLevelComment(stateFlow, 100L, "   ", emptyList()))
        assertFalse(delegate.postTopLevelComment(stateFlow, 0L, "新人求问", emptyList()))
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun consumeCommentActionMessage_clearsMessage() {
        val stateFlow = MutableStateFlow(TestCommentState(commentActionMessage = "提示文案"))
        delegate.consumeCommentActionMessage(stateFlow)
        assertNull(stateFlow.value.commentActionMessage)
    }

    @Test
    fun deleteMainComment_removesFromBothListsAndEmitsDeleted() = runTest {
        val stateFlow = MutableStateFlow(
            TestCommentState(
                hotComments = listOf(sampleComment(1L)),
                latestComments = listOf(sampleComment(1L), sampleComment(2L))
            )
        )
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val ok = delegate.deleteMainComment(stateFlow, 1L)
        assertTrue(ok)
        assertTrue(stateFlow.value.hotComments.isEmpty())
        assertEquals(listOf(2L), stateFlow.value.latestComments.map { it.id })
        assertEquals("评论已删除", stateFlow.value.commentActionMessage)
    }

    @Test
    fun deleteSubReply_removesReplyAndDecrementsCount() = runTest {
        val host = sampleComment(1L).copy(
            reply = 2,
            replys = listOf(
                CommentReplyItem(id = 11L, content = "A", user = CommentUser(id = 7L)),
                CommentReplyItem(id = 12L, content = "B", user = CommentUser(id = 7L))
            )
        )
        val stateFlow = MutableStateFlow(TestCommentState(latestComments = listOf(host)))
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val ok = delegate.deleteSubReply(stateFlow, 1L, 12L)
        assertTrue(ok)
        val remaining = stateFlow.value.latestComments.single()
        assertEquals(listOf(11L), remaining.replys?.map { it.id })
        assertEquals(1, remaining.reply)
        assertEquals("回复已删除", stateFlow.value.commentActionMessage)
    }

    @Test
    fun observeDeletedComments_removesFromBothLists() = runTest {
        // 新增用例：覆盖 observeCommentUpdates 的 deletedComments 分支
        // （老项目无此用例；删除广播由 deleteMainComment 内部 emit，
        // 观察者收到后应从两路列表移除，删除广播本身不写 commentActionMessage）。
        val stateFlow = MutableStateFlow(
            TestCommentState(
                hotComments = listOf(sampleComment(1L)),
                latestComments = listOf(sampleComment(1L), sampleComment(2L))
            )
        )
        delegate.observeCommentUpdates(this, stateFlow)
        testScheduler.runCurrent()

        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val deleteState = MutableStateFlow(TestCommentState(latestComments = listOf(sampleComment(1L))))
        delegate.deleteMainComment(deleteState, 1L)
        advanceUntilIdle()

        assertTrue(stateFlow.value.hotComments.isEmpty())
        assertEquals(listOf(2L), stateFlow.value.latestComments.map { it.id })
        assertNull(stateFlow.value.commentActionMessage)
    }
}
