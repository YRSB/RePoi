package com.nobody.poishelf.ui.common.comment

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
import com.nobody.poishelf.data.remote.model.CommentItem
import com.nobody.poishelf.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 评论区只读状态快照模型。
 * 聚合评论列表、加载标记与分页状态，精简调用点 10+ 个零散参数。
 */
data class CommentFeedState(
    val totalCommentCount: Int = 0,
    val hotComments: List<CommentItem> = emptyList(),
    val latestComments: List<CommentItem> = emptyList(),
    val isCommentsLoading: Boolean = false,
    val isLoadingMoreComments: Boolean = false,
    val hasMoreComments: Boolean = false,
    val commentsError: String? = null
)

/**
 * 自适应双窗格详情脚手架作用域契约。
 * 向内容插槽、顶栏与悬浮底栏暴露双窗格计算结果、滚动状态及常用辅助动作。
 */
interface AdaptiveDetailScaffoldScope {
    /** 当前是否为宽屏双栏形态 */
    val isWidePane: Boolean

    /** 左侧主窗格所占权重比例（0.0f ~ 1.0f） */
    val mainPaneWeight: Float

    /** 主窗格列表滚动状态（在 LazyColumn 模式下有效） */
    val listState: LazyListState

    /** 宽屏副栏评论列表滚动状态 */
    val commentPaneState: LazyListState

    /** 当前评论过滤模式（默认 / 热门 / 最新） */
    val commentFilterMode: CommentFilterMode

    /** 评论过滤模式切换动作 */
    val onCommentFilterModeChange: (CommentFilterMode) -> Unit

    /** 主列表是否已滚过头部区域（用于联动顶部导航栏微缩标题呈现） */
    val isScrolledPastHeader: Boolean

    /** 悬浮操作栏是否处于可见状态（默认为常驻呈现） */
    val isFloatingBarVisible: Boolean

    /**
     * 一键平滑滚动至评论区。
     * 宽屏模式下滚动右侧副栏评论区至指定位置（默认为顶部）；窄屏模式下若配置了自定义驱动（如异构网格布局），
     * 则委托该驱动处理，否则明确为空操作（窄屏评论已由独立全屏页面承载，主列表无内嵌评论区）。
     */
    fun scrollToComments(targetIndex: Int? = null)

    /** 主动拉起主楼评论输入弹窗 */
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
            // 窄屏且未配置自定义驱动时明确为 no-op，避免退化跳转至任意内容条目或列表末尾
        }
    }

    override fun openTopLevelCommentDialog() {
        onOpenTopLevelCommentDialog()
    }
}

/**
 * 自适应双窗格详情脚手架（底层通用版本）。
 * 托管自适应断点计算、双栏排版、宽屏副栏评论区、滚动触底加载、顶栏对齐与常驻悬浮胶囊。
 * 主窗格接收通用 Composable 插槽，支持相册瀑布流等异构网格布局。
 */
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

    // 翻页滚动检测：仅在宽屏副栏有效（窄屏下评论不在主页单流平铺，由独立全屏评论页负责翻页加载）
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

    // 头部微缩导航栏触发
    val defaultIsScrolledPastHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 240
        }
    }
    val resolvedIsScrolledPastHeader = isScrolledPastHeader ?: defaultIsScrolledPastHeader

    // 悬浮胶囊常驻静态呈现（移除滚动显隐联动）
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
        // 1. 双窗格内容层
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

        // 2. 顶部导航栏插槽（自动应用主栏宽度与顶部对齐）
        Box(
            modifier = Modifier
                .align(if (isWidePane) Alignment.TopStart else Alignment.TopCenter)
                .fillMaxWidth(if (isWidePane) resolvedMainPaneWeight else 1f)
        ) {
            scope.topBar()
        }

        // 3. 悬浮操作底栏插槽（居中常驻悬浮胶囊，自动处理主栏宽度、居中对齐与系统导航栏边距）
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

        // 4. 主楼评论发表弹窗
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

/**
 * 详情页脚手架内边距与尺寸规范默认配置。
 */
object AdaptiveDetailDefaults {
    /** 沉浸式大头图详情页默认内边距（如手办详情页），顶部不预留间距以贯通状态栏 */
    val ImmersiveContentPadding: PaddingValues = PaddingValues(bottom = 110.dp)

    /**
     * 常规非沉浸卡片式详情页内容内边距（如厂商、系列、人物、角色等）。
     * 顶部动态避让系统状态栏与悬浮顶栏（54.dp）并预留呼吸间距，底部预留悬浮胶囊避让区。
     *
     * @param topAdditional 悬浮顶栏底边之下的额外留白（默认 10.dp，与卡片外边距和谐呼应）
     * @param bottom 底部悬浮操作胶囊的避让留白（默认 110.dp）
     */
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

/**
 * 自适应双窗格详情脚手架（常规详情页推荐）。
 * 专为手办、角色、厂商、系列、人物、作品等 6 个常规单流详情页设计：
 * - 自动在主窗格承载 LazyColumn 并注入 detailContent；
 * - 窄屏模式下仅展示主内容区，通过常驻悬浮栏评论按钮跳转独立全屏评论页；
 * - 宽屏模式下自动将评论区分栏至右侧 SupportingCommentPane。
 */
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
