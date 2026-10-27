package com.nobody.repoi.ui.common

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
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
import org.junit.Rule
import org.junit.Test
import org.junit.After
import org.junit.Before
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommentSectionBehaviorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        startTestKoin(FakePlatformActions())
    }

    @After
    fun tearDown() {
        stopTestKoin()
    }

    private fun comment(id: Long, content: String): CommentItem {
        return CommentItem(id = id, content = content)
    }

    @Test
    fun retryClick_triggersOnRetryOnce() {
        var retryCount = 0
        composeRule.setContent {
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
        composeRule.onNodeWithText("已加载全部讨论", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("点击重试").performClick()
        assertEquals(1, retryCount)
    }

    @Test
    fun hasMoreFalse_showsFullLoadedAndNoRetry() {
        composeRule.setContent {
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
        composeRule.onNodeWithText("已加载全部讨论", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("点击重试").assertDoesNotExist()
    }

    @Test
    fun restoration_preservesDraftInput() {
        val tester = StateRestorationTester(composeRule)
        tester.setContent {
            CommentReplyDialog(
                comment = comment(1L, "主楼"),
                target = null,
                onDismiss = {},
                onSubmit = { _, _ -> true }
            )
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("未发送的草稿")
        composeRule.onNode(hasSetTextAction()).assertTextContains("未发送的草稿")

        tester.emulateSavedInstanceStateRestore()

        composeRule.onNode(hasSetTextAction()).assertTextContains("未发送的草稿")
    }

    @Test
    fun replyRelation_rendersReplyTarget() {
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
        composeRule.setContent {
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
        composeRule.onNodeWithText("回复", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("lie", substring = true).assertIsDisplayed()
    }

    @Test
    fun moreThanThreeReplies_showsViewAllAndNavigates() {
        var viewedId = -1L
        val item = CommentItem(
            id = 7L,
            content = "主楼",
            reply = 7,
            replys = (1L..3L).map {
                CommentReplyItem(id = it, content = "预览$it", user = CommentUser(id = it, nickname = "胶友$it"))
            }
        )
        composeRule.setContent {
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
        composeRule.onNodeWithText("共7条回复", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("共7条回复", substring = true).performClick()
        assertEquals(7L, viewedId)
        assertTrue(viewedId != -1L)
    }

    @Test
    fun threeOrLessReplies_doesNotShowViewAllText() {
        val item = CommentItem(
            id = 2L,
            content = "主楼",
            reply = 2,
            replys = listOf(
                CommentReplyItem(id = 1L, content = "回复1", user = CommentUser(id = 1L, nickname = "A")),
                CommentReplyItem(id = 2L, content = "回复2", user = CommentUser(id = 2L, nickname = "B"))
            )
        )
        composeRule.setContent {
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
        composeRule.onNodeWithText("回复1", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("条回复", substring = true).assertDoesNotExist()
    }

    @Test
    fun commentCardClick_triggersViewAllReplies() {
        var clickedCommentId = -1L
        val item = CommentItem(
            id = 99L,
            content = "卡片整体可点击进入楼中楼",
            user = CommentUser(id = 1L, nickname = "测试用户")
        )
        composeRule.setContent {
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
        composeRule.onNodeWithText("卡片整体可点击进入楼中楼").performClick()
        assertEquals(99L, clickedCommentId)
    }

    @Test
    fun headerSortButton_cyclesFilterMode() {
        var newMode: CommentFilterMode? = null
        composeRule.setContent {
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
        composeRule.onNodeWithText("默认").performClick()
        assertEquals(CommentFilterMode.HOT, newMode)
    }

    @Test
    fun commentReplyDialog_rendersInputAndCanType() {
        composeRule.setContent {
            CommentReplyDialog(
                comment = CommentItem(id = 1L),
                target = null,
                onDismiss = {},
                onSubmit = { _, _ -> true }
            )
        }
        composeRule.onNodeWithText("输入回复内容").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performTextInput("测试回复")
        composeRule.onNode(hasSetTextAction()).assertTextContains("测试回复")
    }

    @Test
    fun commentFeedPane_rendersBackAndCyclesSort() {
        var backClicked = false
        var sortMode = CommentFilterMode.DEFAULT
        composeRule.setContent {
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
        composeRule.onNodeWithText("商品讨论").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("返回").performClick()
        assertTrue(backClicked)

        composeRule.onNodeWithText("默认").performClick()
        assertEquals(CommentFilterMode.HOT, sortMode)
    }

    @Test
    fun commentFeedPane_fabOpensTopLevelCommentDialog() {
        composeRule.setContent {
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
        composeRule.onNodeWithContentDescription("我要说两句").performClick()
        composeRule.onNodeWithText("输入回复内容").assertIsDisplayed()
    }

    @Test
    fun commentFeedPane_defaultMode_rendersCommentTitle() {
        composeRule.setContent {
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
        composeRule.onNodeWithText("评论").assertIsDisplayed()
    }

    @Test
    fun commentFeedPane_modeChangeScrollsToTop() {
        var sortMode = CommentFilterMode.DEFAULT
        var listState: LazyListState? = null
        composeRule.setContent {
            val state = rememberLazyListState(initialFirstVisibleItemIndex = 5)
            listState = state
            CommentFeedPane(
                filterMode = sortMode,
                onFilterModeChange = { sortMode = it },
                totalCommentCount = 10,
                hotComments = emptyList(),
                latestComments = (1..10).map { CommentItem(id = it.toLong(), content = "内容$it") },
                isCommentsLoading = false,
                isLoadingMoreComments = false,
                hasMoreComments = false,
                isDark = false,
                listState = state
            )
        }
        assertEquals(5, listState?.firstVisibleItemIndex)
        composeRule.onNodeWithText("默认").performClick()
        composeRule.waitForIdle()
        assertEquals(0, listState?.firstVisibleItemIndex)
    }
}
