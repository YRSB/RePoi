package com.nobody.poishelf.ui.vendor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import com.nobody.poishelf.platform.rememberTextSharing
import com.nobody.poishelf.platform.LocalAppMessages
import com.nobody.poishelf.ui.home.components.ShimmerHost
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.poishelf.ui.comment.CommentDetailNavParam
import com.nobody.poishelf.ui.comment.HobbyCommentsNavParam
import com.nobody.poishelf.data.remote.model.ActionItem
import com.nobody.poishelf.ui.common.DetailTopNavigation
import com.nobody.poishelf.ui.common.LocalImageViewer
import com.nobody.poishelf.ui.common.comment.AdaptiveDetailDefaults
import com.nobody.poishelf.ui.common.comment.AdaptiveDetailLazyColumnCommentScaffold
import com.nobody.poishelf.ui.common.comment.CommentFeedState
import com.nobody.poishelf.ui.common.comment.CommentSectionInteraction
import com.nobody.poishelf.ui.detail.DetailNavParam
import com.nobody.poishelf.ui.series.SeriesNavParam
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.ui.theme.LocalIsDarkTheme
import com.nobody.poishelf.util.FeedActionDispatcher

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VendorDetailScreen(
    navParam: VendorNavParam,
    onBack: () -> Unit,
    onNavigateToHobby: (DetailNavParam) -> Unit,
    onNavigateToSearch: (companyNodeId: Long) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSeries: (SeriesNavParam) -> Unit = {},
    onNavigateToVendorActions: (companyNodeId: Long, companyName: String?) -> Unit = { _, _ -> },
    onNavigateToLogin: () -> Unit = {},
    onNavigateToCommentDetail: ((CommentDetailNavParam) -> Unit)? = null,
    onNavigateToComments: ((HobbyCommentsNavParam) -> Unit)? = null,
    viewModel: VendorDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current
    val messages = LocalAppMessages.current
    val textSharing = rememberTextSharing()
    val imageViewer = LocalImageViewer.current

    val onActionItemClick = remember(onNavigateToHobby) {
        { actionItem: ActionItem ->
            FeedActionDispatcher.dispatch(
                actionItem = actionItem,
                onMessage = { messages.show(it) },
                onNavigateToDetail = onNavigateToHobby
            )
        }
    }

    // 初始化加载
    LaunchedEffect(navParam) {
        viewModel.loadDetail(navParam)
    }

    // 评论互动一次性提示（点赞/回复结果）
    val commentActionMessage = uiState.commentActionMessage
    LaunchedEffect(commentActionMessage) {
        if (commentActionMessage != null) {
            messages.show(commentActionMessage)
            viewModel.consumeCommentActionMessage()
        }
    }

    // 印象打分与投票一次性提示
    val impressionMessage = uiState.impressionMessage
    LaunchedEffect(impressionMessage) {
        if (impressionMessage != null) {
            messages.show(impressionMessage)
            viewModel.consumeImpressionMessage()
        }
    }

    var commentUserId by remember { mutableStateOf(viewModel.currentCommentUserId) }
    LaunchedEffect(Unit) { commentUserId = viewModel.resolveCommentUserId() }

    // 屏蔽确认弹窗
    var showMaskConfirmDialog by rememberSaveable { mutableStateOf(false) }

    // 厂商详细信息抽屉
    var showInfoSheet by rememberSaveable { mutableStateOf(false) }

    when {
        uiState.isInitialLoading && uiState.company == null -> {
            Box(modifier = modifier.fillMaxSize()) {
                ShimmerHost { VendorDetailSkeleton(onBack = onBack, isDark = isDark) }
            }
        }

        uiState.errorMessage != null && uiState.company == null -> {
            Box(modifier = modifier.fillMaxSize()) {
                VendorErrorState(
                    errorMessage = uiState.errorMessage ?: "加载失败",
                    onRetry = { viewModel.loadDetail(navParam) },
                    onBack = onBack,
                    isDark = isDark
                )
            }
        }

        else -> {
            val company = uiState.company
            val commentInteraction = remember(uiState.pendingPraiseIds, uiState.companyNodeId, commentUserId) {
                CommentSectionInteraction(
                    pendingPraiseIds = uiState.pendingPraiseIds,
                    currentUserId = commentUserId,
                    onPraiseClick = { viewModel.toggleCommentPraise(it) },
                    onReplySubmit = { comment, target, content, stickers ->
                        viewModel.submitCommentReply(comment.id, target, content, stickers)
                    },
                    onDeleteComment = { viewModel.deleteComment(it) },
                    onDeleteReply = { comment, reply -> viewModel.deleteReply(comment, reply) },
                    onAddComment = { content, stickers ->
                        viewModel.submitTopLevelComment(content, stickers)
                    },
                    onLoadStickerPacks = { viewModel.loadStickerPacks() },
                    onViewAllReplies = { comment ->
                        onNavigateToCommentDetail?.invoke(
                            CommentDetailNavParam(
                                commentId = comment.id,
                                relateNodeId = uiState.companyNodeId,
                                snapshot = comment
                            )
                        )
                    }
                )
            }

            val commentFeedState = remember(uiState, company) {
                CommentFeedState(
                    totalCommentCount = company?.commentCount ?: (uiState.hotComments.size + uiState.latestComments.size),
                    hotComments = uiState.hotComments,
                    latestComments = uiState.latestComments,
                    isCommentsLoading = uiState.isCommentsLoading,
                    isLoadingMoreComments = uiState.isLoadingMoreComments,
                    hasMoreComments = uiState.hasMoreComments,
                    commentsError = uiState.commentsError
                )
            }

            AdaptiveDetailLazyColumnCommentScaffold(
                commentState = commentFeedState,
                onLoadMoreComments = { viewModel.loadNextCommentsPage() },
                onRefreshComments = { viewModel.refreshComments() },
                interaction = commentInteraction,
                relateNodeId = uiState.companyNodeId,
                contentPadding = AdaptiveDetailDefaults.nonHeroContentPadding(),
                modifier = modifier,
                topBar = {
                    DetailTopNavigation(
                        isScrolled = isScrolledPastHeader,
                        title = company?.displayName ?: navParam.initialName ?: "厂商",
                        subtitle = company?.name?.takeIf { it.isNotBlank() && it != company?.displayName },
                        onBack = onBack,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                floatingToolbar = {
                    if (company != null || !uiState.isInitialLoading) {
                        val baseCollect = company?.collect ?: 0L
                        val displayCollect = (baseCollect + uiState.collectCountDelta).coerceAtLeast(0L)
                        VendorFloatingActionBar(
                            collectCount = displayCollect,
                            isCollected = uiState.isCollected,
                            commentCount = company?.commentCount ?: (uiState.hotComments.size + uiState.latestComments.size),
                            showCommentButton = !isWidePane,
                            onToggleCollect = {
                                viewModel.toggleCollect()
                                val text = if (!uiState.isCollected) "已关注厂商 ♥" else "已取消关注"
                                messages.show(text)
                            },
                            onCommentClick = {
                                val nodeId = if (uiState.companyNodeId > 0L) uiState.companyNodeId else navParam.companyNodeId
                                if (nodeId <= 0L) return@VendorFloatingActionBar
                                onNavigateToComments?.invoke(
                                    HobbyCommentsNavParam(
                                        nodeId = nodeId,
                                        itemType = "company",
                                        title = company?.displayName ?: navParam.initialName ?: "评论",
                                        initialCommentCount = company?.commentCount ?: 0
                                    )
                                )
                            },
                            onShareClick = {
                                val shareName = company?.displayName ?: "厂商"
                                textSharing.share("【PoiShelf 厂商分享】$shareName\n了解更多手办企划与出荷情报！")
                            },
                            isDark = isDark
                        )
                    }
                }
            ) {
                // ── 区块 0：厂商主体头部（封面+评分微标、中英文名、简介折叠、标签）──
                item(key = "section_header") {
                    VendorHeaderSection(
                        company = company,
                        initialName = navParam.initialName,
                        initialCover = navParam.initialCover,
                        isIntroExpanded = uiState.isIntroExpanded,
                        onToggleIntro = { viewModel.toggleIntroExpanded() },
                        onShowDetailInfo = { showInfoSheet = true },
                        isDark = isDark
                    )
                }

                // ── 区块 1：动态（最新动向卡片）──
                item(key = "section_action") {
                    VendorActionSection(
                        actions = uiState.actions,
                        isLoading = uiState.isActionsLoading,
                        onActionClick = onActionItemClick,
                        onViewMore = {
                            val nodeId = if (uiState.companyNodeId > 0L) uiState.companyNodeId else navParam.companyNodeId
                            onNavigateToVendorActions(nodeId, company?.displayName ?: navParam.initialName)
                        },
                        onImageClick = { rawUrls, index -> imageViewer.show(rawUrls, index) }
                    )
                }

                // ── 区块 2：品牌标签 ──
                item(key = "section_tags") {
                    VendorTagsSection(
                        tags = uiState.tags,
                        isLoading = uiState.isTagsLoading,
                        onMyImpressionClick = {
                            viewModel.openImpressionSheet(
                                onNeedLogin = {
                                    messages.show("请先登录")
                                    onNavigateToLogin()
                                }
                            )
                        },
                        isDark = isDark
                    )
                }

                // ── 区块 3：产品（3x3 九宫格）──
                item(key = "section_products") {
                    VendorProductsSection(
                        products = uiState.products,
                        isLoading = uiState.isProductsLoading,
                        onProductClick = { product ->
                            onNavigateToHobby(
                                DetailNavParam(
                                    itemId = product.itemId,
                                    initialName = product.displayName,
                                    initialCover = product.cover
                                )
                            )
                        },
                        onViewMore = {
                            val nodeId = uiState.companyNodeId
                            if (nodeId > 0) {
                                onNavigateToSearch(nodeId)
                            } else {
                                messages.show("正在检索中...")
                            }
                        },
                        isDark = isDark
                    )
                }

                // ── 区块 4：旗下系列 ──
                item(key = "section_series") {
                    VendorSeriesSection(
                        seriesList = uiState.series,
                        isLoading = uiState.isSeriesLoading,
                        onSeriesClick = { series ->
                            onNavigateToSeries(
                                SeriesNavParam(
                                    seriesId = series.itemId,
                                    seriesNodeId = series.id,
                                    initialName = series.displayName,
                                    initialCover = series.cover
                                )
                            )
                        },
                        onViewMore = {
                            messages.show("更多系列正在开发中")
                        },
                        isDark = isDark
                    )
                }
            }
        }
    }

    // 屏蔽确认弹窗
    if (showMaskConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showMaskConfirmDialog = false },
            title = { Text(if (uiState.isMasked) "取消屏蔽厂商" else "屏蔽厂商") },
            text = {
                Text(
                    if (uiState.isMasked) "确定要取消屏蔽该厂商吗？取消后将在动态中恢复展示该厂商的条目。"
                    else "确定要屏蔽该厂商吗？屏蔽后可在设置或屏蔽管理中解除。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.toggleMask()
                        showMaskConfirmDialog = false
                        val tip = if (uiState.isMasked) "已取消屏蔽" else "已屏蔽该厂商"
                        messages.show(tip)
                    }
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMaskConfirmDialog = false }) {
                    Text("取消")
                }
            },
            shape = ExpressiveTokens.DialogShape
        )
    }

    // ── 我的印象打分与品牌标签底部抽屉 ──
    VendorImpressionBottomSheet(
        visible = uiState.isImpressionSheetVisible,
        isLoading = uiState.isImpressionLoading,
        isSubmitting = uiState.isImpressionSubmitting,
        allTags = uiState.impressionAllTags,
        selectedNormalTagIds = uiState.selectedNormalTagIds,
        scoreVotes = uiState.scoreVotes,
        onToggleNormalTag = { viewModel.toggleNormalTag(it) },
        onScoreChange = { tagId, score -> viewModel.setScoreVote(tagId, score) },
        onSubmit = { viewModel.submitImpression() },
        onDismiss = { viewModel.dismissImpressionSheet() }
    )

    // ── 厂商详细信息底部抽屉 (方案 B) ──
    VendorInfoBottomSheet(
        company = uiState.company,
        visible = showInfoSheet,
        onDismiss = { showInfoSheet = false }
    )
}
