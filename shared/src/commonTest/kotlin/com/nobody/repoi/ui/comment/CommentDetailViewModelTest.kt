package com.nobody.repoi.ui.comment

import app.cash.turbine.test
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.data.session.FakeUserSession
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommentDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun setup(): Triple<MockApi, CommentDetailViewModel, FakeUserSession> {
        val mockApi = MockApi()
        val session = FakeUserSession("test-token")
        val commentRepo = CommentRepository(mockApi.api(testDispatcher))
        val stickerRepo = StickerRepository(mockApi.api(testDispatcher))
        val viewModel = CommentDetailViewModel(
            repository = commentRepo,
            stickerRepository = stickerRepo,
            userSession = session,
        )
        return Triple(mockApi, viewModel, session)
    }

    @Test
    fun onInputTextChanged_updatesUiStateInputText() = runTest(testDispatcher) {
        val (_, viewModel, _) = setup()

        viewModel.uiState.test {
            awaitItem().inputText shouldBe ""

            viewModel.onInputTextChanged("第一条回复测试")
            awaitItem().inputText shouldBe "第一条回复测试"

            viewModel.onInputTextChanged("")
            awaitItem().inputText shouldBe ""
        }
    }

    @Test
    fun replyTarget_settingAndClearing_updatesStateCorrectly() = runTest(testDispatcher) {
        val (_, viewModel, _) = setup()
        val reply = CommentReplyItem(id = 101L, content = "被回复的内容")

        viewModel.onReplyTargetChanged(reply)
        viewModel.uiState.value.replyTarget.shouldNotBeNull()
        viewModel.uiState.value.replyTarget?.id shouldBe 101L

        viewModel.clearReplyTarget()
        viewModel.uiState.value.replyTarget.shouldBeNull()
    }

    @Test
    fun replyDialog_forHeaderComment_opensWithNullTarget() = runTest(testDispatcher) {
        val (_, viewModel, _) = setup()

        viewModel.openReplyDialog(null)
        viewModel.uiState.value.isReplyDialogOpen shouldBe true
        viewModel.uiState.value.replyTarget.shouldBeNull()

        viewModel.closeReplyDialog()
        viewModel.uiState.value.isReplyDialogOpen shouldBe false
        viewModel.uiState.value.replyTarget.shouldBeNull()
    }

    @Test
    fun replyDialog_forChildReply_opensWithTarget() = runTest(testDispatcher) {
        val (_, viewModel, _) = setup()
        val reply = CommentReplyItem(id = 101L, content = "被回复的内容")

        viewModel.openReplyDialog(reply)
        viewModel.uiState.value.isReplyDialogOpen shouldBe true
        viewModel.uiState.value.replyTarget?.id shouldBe 101L

        viewModel.closeReplyDialog()
        viewModel.uiState.value.isReplyDialogOpen shouldBe false
        viewModel.uiState.value.replyTarget.shouldBeNull()
    }

    @Test
    fun loadDetail_withSnapshot_prePopulatesComment() = runTest(testDispatcher) {
        val (mockApi, viewModel, _) = setup()
        mockApi.route(
            "api/comment/getreply",
            mockApi.ok("""{"comment":{"id":1001,"content":"快照测试评论"},"replies":[]}""")
        )

        val snapshot = CommentItem(id = 1001L, content = "快照测试评论")
        val param = CommentDetailNavParam(commentId = 1001L, relateNodeId = 500L, snapshot = snapshot)

        viewModel.uiState.test {
            awaitItem().comment shouldBe null

            viewModel.loadDetail(param)
            val loadingState = awaitItem()
            loadingState.comment?.content shouldBe "快照测试评论"
            loadingState.commentId shouldBe 1001L
            loadingState.relateNodeId shouldBe 500L
            loadingState.phase shouldBe CommentDetailPhase.Loading

            val successState = awaitItem()
            successState.comment?.content shouldBe "快照测试评论"
            successState.phase shouldBe CommentDetailPhase.Success
        }
    }

    @Test
    fun loadDetail_whenHeaderIsShielded_hidesHeaderAndDropsAllReplies() = runTest(testDispatcher) {
        val (mockApi, viewModel, _) = setup()
        mockApi.route(
            "api/comment/getreply",
            mockApi.ok("""{"comment":{"id":2001,"content":"(已屏蔽)"},"replies":[{"id":1,"content":"正常回复"}]}""")
        )

        val snapshot = CommentItem(id = 2001L, content = "(已屏蔽)")
        val param = CommentDetailNavParam(commentId = 2001L, relateNodeId = 500L, snapshot = snapshot)

        viewModel.uiState.test {
            awaitItem().comment shouldBe null

            viewModel.loadDetail(param)
            val loadingState = awaitItem()
            loadingState.comment shouldBe null
            loadingState.replies shouldBe emptyList()

            val successState = awaitItem()
            successState.comment shouldBe null
            successState.replies shouldBe emptyList()
            successState.hasMore shouldBe false
        }
    }

    @Test
    fun loadDetail_whenHeaderIsMaskFlag_hidesHeaderAndDropsAllReplies() = runTest(testDispatcher) {
        val (mockApi, viewModel, _) = setup()
        mockApi.route(
            "api/comment/getreply",
            mockApi.ok("""{"comment":{"id":2002,"content":"正常文字但被标记","isMask":1},"replies":[{"id":1,"content":"正常回复"}]}""")
        )

        val param = CommentDetailNavParam(commentId = 2002L, relateNodeId = 500L, snapshot = null)

        viewModel.uiState.test {
            awaitItem().comment shouldBe null

            viewModel.loadDetail(param)
            awaitItem().phase shouldBe CommentDetailPhase.Loading

            val successState = awaitItem()
            successState.comment shouldBe null
            successState.replies shouldBe emptyList()
            successState.hasMore shouldBe false
        }
    }

    @Test
    fun loadDetail_whenHeaderNormalWithMixedReplies_filtersShieldedRepliesOnly() = runTest(testDispatcher) {
        val (mockApi, viewModel, _) = setup()
        mockApi.route(
            "api/comment/getreply",
            mockApi.ok(
                """{
                    "comment":{
                        "id":2003,
                        "content":"正常主楼",
                        "replys":[{"id":1,"content":"正常回复1"},{"id":2,"content":"(已屏蔽)"},{"id":3,"content":"正常回复3"}]
                    },
                    "list":[{"id":1,"content":"正常回复1"},{"id":2,"content":"(已屏蔽)"},{"id":3,"content":"正常回复3"}]
                }"""
            )
        )

        val param = CommentDetailNavParam(commentId = 2003L, relateNodeId = 500L, snapshot = null)

        viewModel.uiState.test {
            awaitItem().comment shouldBe null

            viewModel.loadDetail(param)
            awaitItem().phase shouldBe CommentDetailPhase.Loading

            val successState = awaitItem()
            successState.comment?.id shouldBe 2003L
            successState.comment?.replys?.map { it.id } shouldBe listOf(1L, 3L)
            successState.replies.map { it.id } shouldBe listOf(1L, 3L)
        }
    }

    @Test
    fun loadNextPage_filtersShieldedReplies() = runTest(testDispatcher) {
        val (mockApi, viewModel, _) = setup()
        mockApi.route(
            "api/comment/getreply",
            mockApi.ok(
                """{
                    "comment":{"id":2004,"content":"正常主楼"},
                    "list":[
                        {"id":1,"content":"正常1"},{"id":2,"content":"正常2"},{"id":3,"content":"正常3"},{"id":4,"content":"正常4"},{"id":5,"content":"正常5"},
                        {"id":6,"content":"正常6"},{"id":7,"content":"正常7"},{"id":8,"content":"正常8"},{"id":9,"content":"正常9"},{"id":10,"content":"正常10"}
                    ]
                }"""
            )
        )

        val param = CommentDetailNavParam(commentId = 2004L, relateNodeId = 500L, snapshot = null)
        viewModel.loadDetail(param)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.value.replies.size shouldBe 10
        viewModel.uiState.value.hasMore shouldBe true

        mockApi.replaceRoute(
            "api/comment/getreply",
            mockApi.ok(
                """{
                    "comment":{"id":2004,"content":"正常主楼"},
                    "list":[
                        {"id":11,"content":"(已屏蔽)"},
                        {"id":12,"content":"正常12"}
                    ]
                }"""
            )
        )

        viewModel.loadNextPage()
        testDispatcher.scheduler.advanceUntilIdle()

        val ids = viewModel.uiState.value.replies.map { it.id }
        ids shouldBe (1L..10L).toList() + listOf(12L)
    }
}
