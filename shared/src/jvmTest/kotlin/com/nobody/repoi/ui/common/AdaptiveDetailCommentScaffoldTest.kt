package com.nobody.repoi.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.ui.common.comment.AdaptiveDetailCommentScaffold
import com.nobody.repoi.ui.common.comment.AdaptiveDetailDefaults
import com.nobody.repoi.ui.common.comment.AdaptiveDetailLazyColumnCommentScaffold
import com.nobody.repoi.ui.common.comment.AdaptiveDetailScaffoldScope
import com.nobody.repoi.ui.common.comment.CommentFeedState
import com.nobody.repoi.ui.common.comment.CommentSectionInteraction
import com.nobody.repoi.ui.common.comment.FakePlatformActions
import com.nobody.repoi.ui.common.comment.startTestKoin
import com.nobody.repoi.ui.common.comment.stopTestKoin
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AdaptiveDetailCommentScaffoldTest {

    @AfterTest
    fun tearDown() {
        stopTestKoin()
    }

    @Test
    fun lazyColumnScaffold_compactPane_rendersMainContentWithoutInlinedComments() = runComposeUiTest {
        startTestKoin(FakePlatformActions())
        var loadMoreTriggered = false
        var capturedScope: AdaptiveDetailScaffoldScope? = null

        setContent {
            AdaptiveDetailLazyColumnCommentScaffold(
                commentState = CommentFeedState(
                    totalCommentCount = 1,
                    latestComments = listOf(CommentItem(id = 100L, content = "测试脚手架评论")),
                    hasMoreComments = false
                ),
                onLoadMoreComments = { loadMoreTriggered = true },
                topBar = {
                    Text(text = "顶部测试标题")
                },
                floatingToolbar = {
                    Text(text = "悬浮测试底栏")
                },
                detailContent = { scope ->
                    capturedScope = scope
                    item {
                        Text(text = "主详情测试内容区块")
                    }
                }
            )
        }

        // 验证主内容渲染
        onNodeWithText("主详情测试内容区块").assertIsDisplayed()
        // 验证顶部导航栏插槽渲染
        onNodeWithText("顶部测试标题").assertIsDisplayed()
        // 验证小屏模式下评论区不直接平铺在主内容列表中
        onNodeWithText("测试脚手架评论").assertDoesNotExist()
        // 验证悬浮底栏插槽渲染
        onNodeWithText("悬浮测试底栏").assertIsDisplayed()

        assertNotNull(capturedScope)
        assertFalse(loadMoreTriggered)
    }

    @Test
    fun lazyColumnScaffold_retryClick_triggersLoadMore() = runComposeUiTest {
        startTestKoin(FakePlatformActions())
        var loadMoreCount = 0

        setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.WindowSize(DpSize(1024.dp, 800.dp))
            ) {
                AdaptiveDetailLazyColumnCommentScaffold(
                    commentState = CommentFeedState(
                        totalCommentCount = 1,
                        latestComments = listOf(CommentItem(id = 101L, content = "现有单条评论")),
                        hasMoreComments = true,
                        commentsError = "加载失败测试"
                    ),
                    onLoadMoreComments = { loadMoreCount++ },
                    detailContent = {
                        item {
                            Text("主内容")
                        }
                    }
                )
            }
        }

        // 验证宽屏副栏中的错误重试提示并点击重试
        onNodeWithText("点击重试").performClick()
        assertEquals(1, loadMoreCount)
    }

    @Test
    fun openTopLevelCommentDialog_showsDialog() = runComposeUiTest {
        startTestKoin(FakePlatformActions())
        var capturedScope: AdaptiveDetailScaffoldScope? = null
        var submittedContent: String? = null

        setContent {
            AdaptiveDetailLazyColumnCommentScaffold(
                commentState = CommentFeedState(
                    totalCommentCount = 0,
                    hasMoreComments = false
                ),
                onLoadMoreComments = {},
                interaction = CommentSectionInteraction(
                    onAddComment = { content, _ ->
                        submittedContent = content
                        true
                    }
                ),
                topBar = {
                    Text(
                        text = "打开弹窗触发器",
                        modifier = androidx.compose.ui.Modifier.clickable {
                            openTopLevelCommentDialog()
                        }
                    )
                },
                detailContent = { scope ->
                    capturedScope = scope
                    item {
                        Text("主内容区")
                    }
                }
            )
        }

        capturedScope?.openTopLevelCommentDialog()
        waitForIdle()

        // 验证评论弹窗占位符出现
        onNodeWithText("输入回复内容").assertIsDisplayed()
        assertNull(submittedContent)
    }

    @Test
    fun scaffold_widePane_rendersDualPaneWithSupportingCommentPane() = runComposeUiTest {
        startTestKoin(FakePlatformActions())
        var capturedScope: AdaptiveDetailScaffoldScope? = null

        setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.WindowSize(DpSize(1024.dp, 800.dp))
            ) {
                AdaptiveDetailCommentScaffold(
                    commentState = CommentFeedState(
                        totalCommentCount = 2,
                        latestComments = listOf(CommentItem(id = 201L, content = "宽屏独立副窗格评论")),
                        hasMoreComments = false
                    ),
                    onLoadMoreComments = {},
                    commentTitle = "评论副窗格",
                    content = {
                        capturedScope = this
                        Text("主窗格网格或内容")
                    }
                )
            }
        }

        // 验证主窗格渲染
        onNodeWithText("主窗格网格或内容").assertIsDisplayed()
        // 验证宽屏副栏标题与评论渲染
        onNodeWithText("评论副窗格").assertIsDisplayed()
        onNodeWithText("宽屏独立副窗格评论").assertIsDisplayed()

        assertNotNull(capturedScope)
        assertEquals(true, capturedScope?.isWidePane)
    }

    @Test
    fun baseScaffold_customScrollDrivers_delegatedProperly() = runComposeUiTest {
        startTestKoin(FakePlatformActions())
        var customScrollInvoked = false
        var capturedScope: AdaptiveDetailScaffoldScope? = null

        setContent {
            AdaptiveDetailCommentScaffold(
                commentState = CommentFeedState(),
                onLoadMoreComments = {},
                isScrolledPastHeader = true,
                isFloatingBarVisible = true,
                onScrollToCommentsInCompact = { customScrollInvoked = true },
                content = {
                    capturedScope = this
                    Text("测试内容")
                }
            )
        }

        assertEquals(true, capturedScope?.isScrolledPastHeader)
        assertEquals(true, capturedScope?.isFloatingBarVisible)
        capturedScope?.scrollToComments()
        assertEquals(true, customScrollInvoked)
    }

    @Test
    fun adaptiveDetailDefaults_nonHeroContentPadding_reservesTopForTopBarAndStatusBars() = runComposeUiTest {
        startTestKoin(FakePlatformActions())
        var padding: PaddingValues? = null

        setContent {
            padding = AdaptiveDetailDefaults.nonHeroContentPadding()
        }

        assertNotNull(padding)
        // 顶部必须至少预留 54.dp (顶栏) + 10.dp (呼吸间距) = 64.dp
        assertTrue((padding?.calculateTopPadding() ?: 0.dp) >= 64.dp)
        assertEquals(110.dp, padding?.calculateBottomPadding())
        assertEquals(0.dp, AdaptiveDetailDefaults.ImmersiveContentPadding.calculateTopPadding())
    }

    @Test
    fun scaffold_floatingBarHidden_whenIsFloatingBarVisibleFalse() = runComposeUiTest {
        startTestKoin(FakePlatformActions())
        setContent {
            AdaptiveDetailCommentScaffold(
                commentState = CommentFeedState(),
                onLoadMoreComments = {},
                isFloatingBarVisible = false,
                floatingToolbar = {
                    Text("应被隐藏的底栏")
                },
                content = {
                    Text("主内容")
                }
            )
        }

        onNodeWithText("主内容").assertIsDisplayed()
        onNodeWithText("应被隐藏的底栏").assertDoesNotExist()
    }

    @Test
    fun lazyColumnScaffold_widePane_rendersDualPaneWithSupportingCommentPane() = runComposeUiTest {
        startTestKoin(FakePlatformActions())
        setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.WindowSize(DpSize(1024.dp, 800.dp))
            ) {
                AdaptiveDetailLazyColumnCommentScaffold(
                    commentState = CommentFeedState(
                        totalCommentCount = 1,
                        latestComments = listOf(CommentItem(id = 301L, content = "宽屏测试脚手架评论")),
                        hasMoreComments = false
                    ),
                    onLoadMoreComments = {},
                    commentTitle = "宽屏评论区",
                    detailContent = {
                        item {
                            Text(text = "宽屏主详情内容")
                        }
                    }
                )
            }
        }

        // 验证主窗格与右侧副栏均正常渲染
        onNodeWithText("宽屏主详情内容").assertIsDisplayed()
        onNodeWithText("宽屏评论区").assertIsDisplayed()
        onNodeWithText("宽屏测试脚手架评论").assertIsDisplayed()
    }

    @Test
    fun lazyColumnScaffold_compactPane_hasMoreComments_scrollingToBottomDoesNotTriggerLoadMore() = runComposeUiTest {
        startTestKoin(FakePlatformActions())
        var loadMoreTriggered = false
        lateinit var listState: LazyListState

        setContent {
            listState = rememberLazyListState()
            AdaptiveDetailLazyColumnCommentScaffold(
                listState = listState,
                commentState = CommentFeedState(
                    totalCommentCount = 20,
                    hasMoreComments = true,
                    isLoadingMoreComments = false
                ),
                onLoadMoreComments = { loadMoreTriggered = true },
                detailContent = {
                    items(50) { index ->
                        Text(text = "条目 $index")
                    }
                }
            )
        }

        // 滚动主内容列表至底部
        runOnIdle {
            runBlocking {
                listState.scrollToItem(49)
            }
        }
        waitForIdle()

        // 验证窄屏下主内容列表触底不会错误触发评论加载（窄屏评论由独立全屏页面负责）
        assertFalse(loadMoreTriggered)
    }

    @Test
    fun scaffold_compactPane_scrollToCommentsWithoutDriver_isSafeNoOp() = runComposeUiTest {
        startTestKoin(FakePlatformActions())
        var capturedScope: AdaptiveDetailScaffoldScope? = null
        lateinit var listState: LazyListState

        setContent {
            listState = rememberLazyListState(initialFirstVisibleItemIndex = 5)
            AdaptiveDetailCommentScaffold(
                commentState = CommentFeedState(),
                onLoadMoreComments = {},
                listState = listState,
                content = {
                    capturedScope = this
                    LazyColumn(state = listState) {
                        items(20) { index ->
                            Text("测试条目 $index")
                        }
                    }
                }
            )
        }

        waitForIdle()
        assertEquals(5, listState.firstVisibleItemIndex)

        // 验证窄屏无自定义驱动时，调用 scrollToComments() 不会对主列表造成任何偏移或重置
        capturedScope?.scrollToComments()
        waitForIdle()
        assertEquals(5, listState.firstVisibleItemIndex)

        // 验证即使传递特定 targetIndex，窄屏无驱动下仍明确为安全 no-op，避免退化跳转
        capturedScope?.scrollToComments(targetIndex = 0)
        waitForIdle()
        assertEquals(5, listState.firstVisibleItemIndex)
    }

    @Test
    fun scaffold_compactPane_customShouldLoadMoreDriver_triggersLoadMore() = runComposeUiTest {
        startTestKoin(FakePlatformActions())
        var loadMoreCount = 0
        var allowLoadMore by mutableStateOf(false)

        setContent {
            AdaptiveDetailCommentScaffold(
                commentState = CommentFeedState(
                    hasMoreComments = true,
                    isLoadingMoreComments = false
                ),
                onLoadMoreComments = { loadMoreCount++ },
                shouldLoadMoreCommentsInCompact = { allowLoadMore },
                content = {
                    Text("自定义网格内容")
                }
            )
        }

        waitForIdle()
        assertEquals(0, loadMoreCount)

        // 模拟外部自定义驱动条件达成（如相册瀑布流触底）
        runOnIdle {
            allowLoadMore = true
        }
        waitForIdle()
        assertEquals(1, loadMoreCount)
    }

    @Test
    fun scaffold_widePane_scrollToComments_scrollsCommentPane() = runComposeUiTest {
        startTestKoin(FakePlatformActions())
        var capturedScope: AdaptiveDetailScaffoldScope? = null
        val comments = List(25) { index ->
            CommentItem(id = index.toLong() + 1, content = "宽屏评论 $index")
        }
        lateinit var commentPaneState: LazyListState

        setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.WindowSize(DpSize(1024.dp, 800.dp))
            ) {
                commentPaneState = rememberLazyListState()
                AdaptiveDetailCommentScaffold(
                    commentState = CommentFeedState(
                        totalCommentCount = 25,
                        latestComments = comments,
                        hasMoreComments = false
                    ),
                    commentPaneState = commentPaneState,
                    onLoadMoreComments = {},
                    content = {
                        capturedScope = this
                        Text("主窗格内容")
                    }
                )
            }
        }

        waitForIdle()
        assertEquals(0, commentPaneState.firstVisibleItemIndex)

        capturedScope?.scrollToComments(targetIndex = 10)
        waitForIdle()
        assertEquals(10, commentPaneState.firstVisibleItemIndex)
    }
}
