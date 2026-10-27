package com.nobody.repoi.ui.common

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.CommentUser
import com.nobody.repoi.ui.common.comment.CommentFeedPane
import com.nobody.repoi.ui.common.comment.CommentFilterMode
import com.nobody.repoi.ui.common.comment.CommentReplyDialog
import com.nobody.repoi.ui.common.comment.CommentSectionInteraction
import com.nobody.repoi.ui.common.comment.FakePlatformActions
import com.nobody.repoi.ui.common.comment.commentSectionItems
import com.nobody.repoi.ui.common.comment.startTestKoin
import com.nobody.repoi.ui.common.comment.stopTestKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CommentSectionBehaviorTest {

    @BeforeTest
    fun setUp() {
        startTestKoin(FakePlatformActions())
    }

    @AfterTest
    fun tearDown() {
        stopTestKoin()
    }

    private fun comment(id: Long, content: String): CommentItem {
        return CommentItem(id = id, content = content)
    }

    @Test
    fun retryClick_triggersOnRetryOnce() = runComposeUiTest {
        var retryCount = 0
        setContent {
            LazyColumn {
                commentSectionItems(
                    filterMode = CommentFilterMode.DEFAULT,
                    onFilterModeChange = {},
                    totalCommentCount = 1,
                    hotComments = emptyList(),
                    latestComments = listOf(comment(1L, "first comment")),
                    isCommentsLoading = false,
                    isLoadingMoreComments = false,
                    hasMoreComments = true,
                    isDark = false,
                    commentsError = "网络波动",
                    onRetryLoadMore = { retryCount++ }
                )
            }
        }
        onNodeWithText("已加载全部讨论", substring = true).assertDoesNotExist()
        onNodeWithText("点击重试").performClick()
        assertEquals(1, retryCount)
    }

    @Test
    fun hasMoreFalse_showsFullLoadedAndNoRetry() = runComposeUiTest {
        setContent {
            LazyColumn {
                commentSectionItems(
                    filterMode = CommentFilterMode.DEFAULT,
                    onFilterModeChange = {},
                    totalCommentCount = 1,
                    hotComments = emptyList(),
                    latestComments = listOf(comment(1L, "first comment")),
                    isCommentsLoading = false,
                    isLoadingMoreComments = false,
                    hasMoreComments = false,
                    isDark = false,
                    onRetryLoadMore = {}
                )
            }
        }
        onNodeWithText("已加载全部讨论", substring = true).assertIsDisplayed()
        onNodeWithText("点击重试").assertDoesNotExist()
    }
    @Test
    fun replyDialog_showsReplyTargetHeader() = runComposeUiTest {
        // Desktop UI 测试无可靠的 StateRestorationTester（CMP desktop 的
        // StateRestorationTester 不提供 emulateSavedInstanceStateRestore），
        // 草稿保留语义由 rememberSaveable 框架保证，此处改为验证弹窗的
        // 回复目标提示行（有 target 显示"回复 @昵称"，无 target 不显示）。
        setContent {
            CommentReplyDialog(
                comment = comment(1L, "主楼"),
                target = CommentReplyItem(
                    id = 11L,
                    content = "子楼",
                    user = CommentUser(id = 2L, nickname = "胶友B")
                ),
                onDismiss = {},
                onSubmit = { _, _ -> true }
            )
        }
        onNodeWithText("回复").assertIsDisplayed()
        onNodeWithText("@胶友B").assertIsDisplayed()
    }

    @Test
    fun replyDialog_withoutTarget_hidesReplyHeader() = runComposeUiTest {
        setContent {
            CommentReplyDialog(
                comment = comment(1L, "主楼"),
                target = null,
                onDismiss = {},
                onSubmit = { _, _ -> true }
            )
        }
        onNodeWithText("输入回复内容").assertIsDisplayed()
        onNodeWithText("@").assertDoesNotExist()
    }

    @Test
    fun replyRelation_rendersReplyTarget() = runComposeUiTest {
        val item = CommentItem(
            id = 1L,
            content = "主楼",
            reply = 1,
            replys = listOf(
                CommentReplyItem(
                    id = 11L,
                    content = "有没有可能",
                    user = CommentUser(id = 1L, nickname = "Hpoi-645501用户"),
                    toUser = CommentUser(id = 2L, nickname = "lie")
                )
            )
        )
        setContent {
            LazyColumn {
                commentSectionItems(
                    filterMode = CommentFilterMode.DEFAULT,
                    onFilterModeChange = {},
                    totalCommentCount = 1,
                    hotComments = emptyList(),
                    latestComments = listOf(item),
                    isCommentsLoading = false,
                    isLoadingMoreComments = false,
                    hasMoreComments = false,
                    isDark = false,
                    onRetryLoadMore = {}
                )
            }
        }
        // “XX 回复 YY”关系必须渲染出来
        onNodeWithText("回复", substring = true).assertIsDisplayed()
        onNodeWithText("lie", substring = true).assertIsDisplayed()
    }

    @Test
    fun moreThanThreeReplies_showsViewAllAndNavigates() = runComposeUiTest {
        var viewedId = -1L
        val item = CommentItem(
            id = 7L,
            content = "主楼",
            reply = 7,
            replys = (1L..3L).map {
                CommentReplyItem(id = it, content = "预览$it", user = CommentUser(id = it, nickname = "胶友$it"))
            }
        )
        setContent {
            LazyColumn {
                commentSectionItems(
                    filterMode = CommentFilterMode.DEFAULT,
                    onFilterModeChange = {},
                    totalCommentCount = 1,
                    hotComments = emptyList(),
                    latestComments = listOf(item),
                    isCommentsLoading = false,
                    isLoadingMoreComments = false,
                    hasMoreComments = false,
                    isDark = false,
                    onRetryLoadMore = {},
                    interaction = CommentSectionInteraction(
                        onViewAllReplies = { viewedId = it.id }
                    )
                )
            }
        }
        // 预览只展示 3 条，且出现“共7条回复”
        onNodeWithText("共7条回复", substring = true).assertIsDisplayed()
        onNodeWithText("共7条回复", substring = true).performClick()
        assertEquals(7L, viewedId)
        assertTrue(viewedId != -1L)
    }

    @Test
    fun threeOrLessReplies_doesNotShowViewAllText() = runComposeUiTest {
        val item = CommentItem(
            id = 2L,
            content = "主楼",
            reply = 2,
            replys = listOf(
                CommentReplyItem(id = 1L, content = "回复1", user = CommentUser(id = 1L, nickname = "A")),
                CommentReplyItem(id = 2L, content = "回复2", user = CommentUser(id = 2L, nickname = "B"))
            )
        )
        setContent {
            LazyColumn {
                commentSectionItems(
                    filterMode = CommentFilterMode.DEFAULT,
                    onFilterModeChange = {},
                    totalCommentCount = 1,
                    hotComments = emptyList(),
                    latestComments = listOf(item),
                    isCommentsLoading = false,
                    isLoadingMoreComments = false,
                    hasMoreComments = false,
                    isDark = false,
                    onRetryLoadMore = {},
                    interaction = CommentSectionInteraction(
                        onViewAllReplies = {}
                    )
                )
            }
        }
        onNodeWithText("回复1", substring = true).assertIsDisplayed()
        onNodeWithText("条回复", substring = true).assertDoesNotExist()
    }

    @Test
    fun commentCardClick_triggersViewAllReplies() = runComposeUiTest {
        var clickedCommentId = -1L
        val item = CommentItem(
            id = 99L,
            content = "卡片整体可点击进入楼中楼",
            user = CommentUser(id = 1L, nickname = "测试用户")
        )
        setContent {
            LazyColumn {
                commentSectionItems(
                    filterMode = CommentFilterMode.DEFAULT,
                    onFilterModeChange = {},
                    totalCommentCount = 1,
                    hotComments = emptyList(),
                    latestComments = listOf(item),
                    isCommentsLoading = false,
                    isLoadingMoreComments = false,
                    hasMoreComments = false,
                    isDark = false,
                    onRetryLoadMore = {},
                    interaction = CommentSectionInteraction(
                        onViewAllReplies = { clickedCommentId = it.id }
                    )
                )
            }
        }
        // 点击卡片正文区域
        onNodeWithText("卡片整体可点击进入楼中楼").performClick()
        assertEquals(99L, clickedCommentId)
    }

    @Test
    fun headerSortButton_cyclesFilterMode() = runComposeUiTest {
        var newMode: CommentFilterMode? = null
        setContent {
            LazyColumn {
                commentSectionItems(
                    filterMode = CommentFilterMode.DEFAULT,
                    onFilterModeChange = { newMode = it },
                    totalCommentCount = 5,
                    hotComments = emptyList(),
                    latestComments = emptyList(),
                    isCommentsLoading = false,
                    isLoadingMoreComments = false,
                    hasMoreComments = false,
                    isDark = false,
                    showHeader = true,
                    onRetryLoadMore = {}
                )
            }
        }
        onNodeWithText("默认").performClick()
        assertEquals(CommentFilterMode.HOT, newMode)
    }

    @Test
    fun commentReplyDialog_rendersInputAndCanType() = runComposeUiTest {
        setContent {
            CommentReplyDialog(
                comment = CommentItem(id = 1L),
                target = null,
                onDismiss = {},
                onSubmit = { _, _ -> true }
            )
        }
        onNodeWithText("输入回复内容").assertIsDisplayed()
        onNodeWithText("输入回复内容").performTextInput("测试回复")
        onNodeWithText("测试回复").assertIsDisplayed()
    }

    @Test
    fun commentFeedPane_rendersBackAndCyclesSort() = runComposeUiTest {
        var backClicked = false
        var sortMode = CommentFilterMode.DEFAULT
        setContent {
            CommentFeedPane(
                filterMode = sortMode,
                onFilterModeChange = { sortMode = it },
                totalCommentCount = 10,
                hotComments = emptyList(),
                latestComments = emptyList(),
                isCommentsLoading = false,
                isLoadingMoreComments = false,
                hasMoreComments = false,
                isDark = false,
                title = "商品讨论",
                onBack = { backClicked = true }
            )
        }
        onNodeWithText("商品讨论").assertIsDisplayed()
        onNodeWithContentDescription("返回").performClick()
        assertTrue(backClicked)

        onNodeWithText("默认").performClick()
        assertEquals(CommentFilterMode.HOT, sortMode)
    }

    @Test
    fun commentFeedPane_fabOpensTopLevelCommentDialog() = runComposeUiTest {
        setContent {
            CommentFeedPane(
                filterMode = CommentFilterMode.DEFAULT,
                onFilterModeChange = {},
                totalCommentCount = 0,
                hotComments = emptyList(),
                latestComments = emptyList(),
                isCommentsLoading = false,
                isLoadingMoreComments = false,
                hasMoreComments = false,
                isDark = false,
                interaction = CommentSectionInteraction(
                    onAddComment = { _, _ -> true }
                )
            )
        }
        onNodeWithContentDescription("我要说两句").performClick()
        onNodeWithText("输入回复内容").assertIsDisplayed()
    }

    @Test
    fun commentFeedPane_defaultMode_rendersCommentTitle() = runComposeUiTest {
        setContent {
            CommentFeedPane(
                filterMode = CommentFilterMode.DEFAULT,
                onFilterModeChange = {},
                totalCommentCount = 3,
                hotComments = emptyList(),
                latestComments = emptyList(),
                isCommentsLoading = false,
                isLoadingMoreComments = false,
                hasMoreComments = false,
                isDark = false
            )
        }
        onNodeWithText("评论").assertIsDisplayed()
    }

    @Test
    fun commentFeedPane_modeChangeScrollsToTop() = runComposeUiTest {
        var sortMode = CommentFilterMode.DEFAULT
        var listState: LazyListState? = null
        setContent {
            val state = rememberLazyListState(initialFirstVisibleItemIndex = 5)
            listState = state
            CommentFeedPane(
                filterMode = sortMode,
                onFilterModeChange = { sortMode = it },
                totalCommentCount = 10,
                hotComments = emptyList(),
                latestComments = (1..50).map { CommentItem(id = it.toLong(), content = "内容$it") },
                isCommentsLoading = false,
                isLoadingMoreComments = false,
                hasMoreComments = false,
                isDark = false,
                listState = state
            )
        }
        assertEquals(5, listState?.firstVisibleItemIndex)
        onNodeWithText("默认").performClick()
        waitForIdle()
        assertEquals(0, listState?.firstVisibleItemIndex)
    }
}