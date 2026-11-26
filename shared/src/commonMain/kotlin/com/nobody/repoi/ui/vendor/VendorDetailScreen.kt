package com.nobody.repoi.ui.vendor

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
import com.nobody.repoi.platform.rememberTextSharing
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.ui.common.loading.PoiLoadingView
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
import com.nobody.repoi.ui.comment.CommentDetailNavParam
import com.nobody.repoi.ui.comment.HobbyCommentsNavParam
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.ui.common.DetailTopNavigation
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.comment.AdaptiveDetailDefaults
import com.nobody.repoi.ui.common.comment.AdaptiveDetailLazyColumnCommentScaffold
import com.nobody.repoi.ui.common.comment.CommentFeedState
import com.nobody.repoi.ui.common.comment.CommentSectionInteraction
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.theme.LocalIsDarkTheme

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
    onActionClick: ((ActionItem) -> Unit)? = null,
    viewModel: VendorDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current
    val messages = LocalAppMessages.current
    val textSharing = rememberTextSharing()
    val imageViewer = LocalImageViewer.current

    LaunchedEffect(navParam) {
        viewModel.loadDetail(navParam)
    }

    val commentActionMessage = uiState.commentActionMessage
    LaunchedEffect(commentActionMessage) {
        if (commentActionMessage != null) {
            messages.show(commentActionMessage)
            viewModel.consumeCommentActionMessage()
        }
    }

    val impressionMessage = uiState.impressionMessage
    LaunchedEffect(impressionMessage) {
        if (impressionMessage != null) {
            messages.show(impressionMessage)
            viewModel.consumeImpressionMessage()
        }
    }

    var commentUserId by remember { mutableStateOf(viewModel.currentCommentUserId) }
    LaunchedEffect(Unit) { commentUserId = viewModel.resolveCommentUserId() }

    var showMaskConfirmDialog by rememberSaveable { mutableStateOf(false) }

    var showInfoSheet by rememberSaveable { mutableStateOf(false) }

    when {
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
                    isCommentsLoading = uiState.isCommentsLoading || (uiState.isInitialLoading && company == null),
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
                        subtitle = company?.name?.takeIf { it.isNotBlank() && it != company.displayName },
                        onBack = onBack,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                floatingToolbar = {
                    if (company != null) {
                        val baseCollect = uiState.collectCountBase ?: company.collect
                        val displayCollect = (baseCollect + uiState.collectCountDelta).coerceAtLeast(0L)
                        VendorFloatingActionBar(
                            collectCount = displayCollect,
                            isCollected = uiState.isCollected,
                            commentCount = company.commentCount,
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
                                        title = company.displayName,
                                        initialCommentCount = company.commentCount
                                    )
                                )
                            },
                            onShareClick = {
                                val shareName = company.displayName
                                textSharing.share("【PoiShelf 厂商分享】$shareName\n了解更多手办企划与出荷情报！")
                            },
                            isDark = isDark
                        )
                    }
                }
            ) {
                if (uiState.isInitialLoading && company == null) {
                    item(key = "vendor_detail_initial_loading") {
                        PoiLoadingView(modifier = Modifier.fillParentMaxSize())
                    }
                } else {

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

                item(key = "section_action") {
                    VendorActionSection(
                        actions = uiState.actions,
                        isLoading = uiState.isActionsLoading,
                        onActionClick = onActionClick,
                        onViewMore = {
                            val nodeId = if (uiState.companyNodeId > 0L) uiState.companyNodeId else navParam.companyNodeId
                            onNavigateToVendorActions(nodeId, company?.displayName ?: navParam.initialName)
                        },
                        onImageClick = { rawUrls, index -> imageViewer.show(rawUrls, index) }
                    )
                }

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
}

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
            shape = MaterialTheme.shapes.extraLarge
        )
    }

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

    VendorInfoBottomSheet(
        company = uiState.company,
        visible = showInfoSheet,
        onDismiss = { showInfoSheet = false }
    )
}
