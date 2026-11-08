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

    private fun setup(): Triple<MockApi, CommentDetailViewModel, FakeUserSession> {
        val mockApi = MockApi()
        val session = FakeUserSession("test-token")
        val commentRepo = CommentRepository(mockApi.api(), session)
        val stickerRepo = StickerRepository(mockApi.api())
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
}
