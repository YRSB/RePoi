package com.nobody.repoi.ui.comment

import app.cash.turbine.test
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.FakeTokenManager
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.data.repository.UserRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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

/**
 * 评论详情与回复 ViewModel 状态机单元测试（Turbine + Kotest）。
 *
 * 验证主楼数据挂载、输入草稿变更、回复目标设定与取消回复等交互。
 */
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

    private fun setup(): Triple<MockApi, CommentDetailViewModel, FakeTokenManager> {
        val mockApi = MockApi()
        val tokenManager = FakeTokenManager("test-token")
        val commentRepo = CommentRepository(mockApi.api(), tokenManager)
        val stickerRepo = StickerRepository(mockApi.api())
        val userRepo = UserRepository(mockApi.api(), tokenManager)
        val viewModel = CommentDetailViewModel(
            commentRepository = commentRepo,
            stickerRepository = stickerRepo,
            tokenManager = tokenManager,
            userRepository = userRepo,
        )
        return Triple(mockApi, viewModel, tokenManager)
    }

    @Test
    fun onInputTextChange_updatesUiStateInputText() = runTest(testDispatcher) {
        val (_, viewModel, _) = setup()

        viewModel.uiState.test {
            awaitItem().inputText shouldBe ""

            viewModel.onInputTextChange("第一条回复测试")
            awaitItem().inputText shouldBe "第一条回复测试"

            viewModel.onInputTextChange("")
            awaitItem().inputText shouldBe ""
        }
    }

    @Test
    fun setReplyTargetAndCancelReply_updatesStateCorrectly() = runTest(testDispatcher) {
        val (_, viewModel, _) = setup()
        val reply = CommentReplyItem(id = 101L, content = "被回复的内容", toUserId = 888L)

        viewModel.setReplyTarget(reply)
        viewModel.uiState.value.replyTarget.shouldNotBeNull()
        viewModel.uiState.value.replyTarget?.id shouldBe 101L

        viewModel.cancelReply()
        viewModel.uiState.value.replyTarget.shouldBeNull()
    }

    @Test
    fun loadThread_withSnapshot_prePopulatesComment() = runTest(testDispatcher) {
        val (mockApi, viewModel, _) = setup()
        mockApi.route(
            "api/comment/info",
            mockApi.ok("""{"comment":{"id":1001,"content":"快照测试评论"}}""")
        )

        val snapshot = CommentItem(id = 1001L, content = "快照测试评论")
        val param = CommentDetailNavParam(commentId = 1001L, relateNodeId = 500L, snapshot = snapshot)

        viewModel.loadThread(param)
        viewModel.uiState.value.comment?.content shouldBe "快照测试评论"
        viewModel.uiState.value.commentId shouldBe 1001L
        viewModel.uiState.value.relateNodeId shouldBe 500L
    }
}
