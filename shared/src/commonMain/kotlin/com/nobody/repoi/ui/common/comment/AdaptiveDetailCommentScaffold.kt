package com.nobody.repoi.ui.common.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface AdaptiveDetailScaffoldScope {

    val isWidePane: Boolean

    val mainPaneWeight: Float

    val listState: LazyListState

    val commentPaneState: LazyListState

    val commentFilterMode: CommentFilterMode

    val onCommentFilterModeChange: (CommentFilterMode) -> Unit

    val isScrolledPastHeader: Boolean

    val isFloatingBarVisible: Boolean

    fun scrollToComments(targetIndex: Int? = null)

    fun openTopLevelCommentDialog()
}

internal class AdaptiveDetailScaffoldScopeImpl(
    override val isWidePane: Boolean,
    override val mainPaneWeight: Float,
    override val listState: LazyListState,
    override val commentPaneState: LazyListState,
    override val commentFilterMode: CommentFilterMode,
    override val onCommentFilterModeChange: (CommentFilterMode) -> Unit,
    override val isScrolledPastHeader: Boolean,
    override val isFloatingBarVisible: Boolean,
    private val coroutineScope: CoroutineScope,
    private val onCustomScrollToCommentsInCompact: (() -> Unit)? = null,
    private val onOpenTopLevelCommentDialog: () -> Unit
) : AdaptiveDetailScaffoldScope {

    override fun scrollToComments(targetIndex: Int?) {
        coroutineScope.launch {
            if (isWidePane) {
                commentPaneState.animateScrollToItem(targetIndex ?: 0)
            } else if (onCustomScrollToCommentsInCompact != null) {
                onCustomScrollToCommentsInCompact.invoke()
            }

        }
    }

    override fun openTopLevelCommentDialog() {
        onOpenTopLevelCommentDialog()
    }
}

@Composable
fun AdaptiveDetailCommentScaffold(
    commentState: CommentFeedState,
    onLoadMoreComments: () -> Unit,
    modifier: Modifier = Modifier,
    onRefreshComments: (() -> Unit)? = null,
    interaction: CommentSectionInteraction = CommentSectionInteraction(),
    relateNodeId: Long = 0L,
    commentTitle: String = "评论",
    customMainPaneWeight: Float? = null,
    isDark: Boolean = LocalIsDarkTheme.current,
    listState: LazyListState = rememberLazyListState(),
    commentPaneState: LazyListState = rememberLazyListState(),
    isScrolledPastHeader: Boolean? = null,
    isFloatingBarVisible: Boolean? = null,
    shouldLoadMoreCommentsInCompact: (() -> Boolean)? = null,
    onScrollToCommentsInCompact: (() -> Unit)? = null,
    topBar: @Composable AdaptiveDetailScaffoldScope.() -> Unit = {},
    floatingToolbar: @Composable AdaptiveDetailScaffoldScope.() -> Unit = {},
    content: @Composable AdaptiveDetailScaffoldScope.() -> Unit
) {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val windowSizeClass = adaptiveInfo.windowSizeClass
    val isExpandedWidth = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
    val isMediumWidth = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val isWidePane = isExpandedWidth || (isMediumWidth && !windowSizeClass.isHeightAtLeastBreakpoint(HEIGHT_DP_EXPANDED_LOWER_BOUND))
    val resolvedMainPaneWeight = customMainPaneWeight ?: if (isExpandedWidth) 0.68f else 0.5f

    val coroutineScope = rememberCoroutineScope()
    var showTopLevelCommentDialog by rememberSaveable { mutableStateOf(false) }
    var commentFilterMode by rememberSaveable { mutableStateOf(CommentFilterMode.DEFAULT) }

    val onCommentFilterModeChange: (CommentFilterMode) -> Unit = { mode ->
        commentFilterMode = mode
        coroutineScope.launch {
            if (isWidePane) {
                commentPaneState.scrollToItem(0)
            } else if (onScrollToCommentsInCompact != null) {
                onScrollToCommentsInCompact.invoke()
            }
        }
    }

    val shouldLoadMoreComments by remember(isWidePane, commentFilterMode, shouldLoadMoreCommentsInCompact) {
        derivedStateOf {
            if (commentFilterMode == CommentFilterMode.HOT) {
                false
            } else if (isWidePane) {
                val layoutInfo = commentPaneState.layoutInfo
                val total = layoutInfo.totalItemsCount
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                total > 4 && lastVisible >= total - 2
            } else if (shouldLoadMoreCommentsInCompact != null) {
                shouldLoadMoreCommentsInCompact()
            } else {
                false
            }
        }
    }

    LaunchedEffect(shouldLoadMoreComments, commentState.isLoadingMoreComments, commentState.hasMoreComments) {
        if (shouldLoadMoreComments && !commentState.isLoadingMoreComments && commentState.hasMoreComments) {
            onLoadMoreComments()
        }
    }

    val defaultIsScrolledPastHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 240
        }
    }
    val resolvedIsScrolledPastHeader = isScrolledPastHeader ?: defaultIsScrolledPastHeader

    val resolvedIsFloatingBarVisible = isFloatingBarVisible ?: true

    val scope = remember(
        isWidePane,
        resolvedMainPaneWeight,
        listState,
        commentPaneState,
        commentFilterMode,
        resolvedIsScrolledPastHeader,
        resolvedIsFloatingBarVisible,
        onScrollToCommentsInCompact
    ) {
        AdaptiveDetailScaffoldScopeImpl(
            isWidePane = isWidePane,
            mainPaneWeight = resolvedMainPaneWeight,
            listState = listState,
            commentPaneState = commentPaneState,
            commentFilterMode = commentFilterMode,
            onCommentFilterModeChange = onCommentFilterModeChange,
            isScrolledPastHeader = resolvedIsScrolledPastHeader,
            isFloatingBarVisible = resolvedIsFloatingBarVisible,
            coroutineScope = coroutineScope,
            onCustomScrollToCommentsInCompact = onScrollToCommentsInCompact,
            onOpenTopLevelCommentDialog = { showTopLevelCommentDialog = true }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(if (isWidePane) resolvedMainPaneWeight else 1f)
                    .fillMaxHeight()
            ) {
                scope.content()
            }

            if (isWidePane) {
                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                SupportingCommentPane(
                    filterMode = commentFilterMode,
                    onFilterModeChange = onCommentFilterModeChange,
                    totalCommentCount = commentState.totalCommentCount,
                    hotComments = commentState.hotComments,
                    latestComments = commentState.latestComments,
                    isCommentsLoading = commentState.isCommentsLoading,
                    isLoadingMoreComments = commentState.isLoadingMoreComments,
                    hasMoreComments = commentState.hasMoreComments,
                    isDark = isDark,
                    title = commentTitle,
                    commentsError = commentState.commentsError,
                    onRetryLoadMore = onLoadMoreComments,
                    onRefreshComments = onRefreshComments,
                    interaction = interaction,
                    relateNodeId = relateNodeId,
                    listState = commentPaneState,
                    bottomContentPadding = 110.dp,
                    modifier = Modifier
                        .weight(1f - resolvedMainPaneWeight)
                        .fillMaxHeight()
                )
            }
        }

        Box(
            modifier = Modifier
                .align(if (isWidePane) Alignment.TopStart else Alignment.TopCenter)
                .fillMaxWidth(if (isWidePane) resolvedMainPaneWeight else 1f)
        ) {
            scope.topBar()
        }

        if (resolvedIsFloatingBarVisible) {
            Box(
                modifier = Modifier
                    .align(if (isWidePane) Alignment.BottomStart else Alignment.BottomCenter)
                    .fillMaxWidth(if (isWidePane) resolvedMainPaneWeight else 1f)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                scope.floatingToolbar()
            }
        }

        if (showTopLevelCommentDialog && interaction.onAddComment != null) {
            TopLevelCommentDialog(
                onDismiss = { showTopLevelCommentDialog = false },
                onSubmit = { content, stickers ->
                    val ok = interaction.onAddComment.invoke(content, stickers)
                    if (ok) {
                        showTopLevelCommentDialog = false
                    }
                    ok
                },
                onLoadStickerPacks = interaction.onLoadStickerPacks
            )
        }
    }
}

object AdaptiveDetailDefaults {

    val ImmersiveContentPadding: PaddingValues = PaddingValues(bottom = 110.dp)

    @Composable
    fun nonHeroContentPadding(
        topAdditional: Dp = 10.dp,
        bottom: Dp = 110.dp
    ): PaddingValues {
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        return PaddingValues(
            top = statusBarTop + 54.dp + topAdditional,
            bottom = bottom
        )
    }
}

@Composable
fun AdaptiveDetailLazyColumnCommentScaffold(
    commentState: CommentFeedState,
    onLoadMoreComments: () -> Unit,
    modifier: Modifier = Modifier,
    onRefreshComments: (() -> Unit)? = null,
    interaction: CommentSectionInteraction = CommentSectionInteraction(),
    relateNodeId: Long = 0L,
    commentTitle: String = "评论",
    customMainPaneWeight: Float? = null,
    isDark: Boolean = LocalIsDarkTheme.current,
    listState: LazyListState = rememberLazyListState(),
    commentPaneState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(bottom = 110.dp),
    topBar: @Composable AdaptiveDetailScaffoldScope.() -> Unit = {},
    floatingToolbar: @Composable AdaptiveDetailScaffoldScope.() -> Unit = {},
    shouldLoadMoreCommentsInCompact: (() -> Boolean)? = null,
    onScrollToCommentsInCompact: (() -> Unit)? = null,
    detailContent: LazyListScope.(scope: AdaptiveDetailScaffoldScope) -> Unit
) {
    AdaptiveDetailCommentScaffold(
        commentState = commentState,
        onLoadMoreComments = onLoadMoreComments,
        modifier = modifier,
        onRefreshComments = onRefreshComments,
        interaction = interaction,
        relateNodeId = relateNodeId,
        commentTitle = commentTitle,
        customMainPaneWeight = customMainPaneWeight,
        isDark = isDark,
        listState = listState,
        commentPaneState = commentPaneState,
        shouldLoadMoreCommentsInCompact = shouldLoadMoreCommentsInCompact,
        onScrollToCommentsInCompact = onScrollToCommentsInCompact,
        topBar = topBar,
        floatingToolbar = floatingToolbar
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding
        ) {
            detailContent(this@AdaptiveDetailCommentScaffold)
        }
    }
}
