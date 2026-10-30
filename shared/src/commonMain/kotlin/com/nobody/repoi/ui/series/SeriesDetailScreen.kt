package com.nobody.repoi.ui.series

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import com.nobody.repoi.platform.rememberTextSharing
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.ui.home.components.ShimmerHost
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.comment.CommentDetailNavParam
import com.nobody.repoi.ui.comment.HobbyCommentsNavParam
import com.nobody.repoi.ui.common.DetailTopNavigation
import com.nobody.repoi.ui.common.comment.AdaptiveDetailDefaults
import com.nobody.repoi.ui.common.comment.AdaptiveDetailLazyColumnCommentScaffold
import com.nobody.repoi.ui.common.comment.CommentFeedState
import com.nobody.repoi.ui.common.comment.CommentSectionInteraction
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.item.list.HobbyListNavParam
import com.nobody.repoi.ui.navigation.HobbyAlbumListNavParam
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.ui.theme.LocalIsDarkTheme

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SeriesDetailScreen(
    navParam: SeriesNavParam,
    onBack: () -> Unit,
    onNavigateToHobby: (DetailNavParam) -> Unit,
    onNavigateToAlbum: (AlbumNavParam) -> Unit,
    onNavigateToSeries: (SeriesNavParam) -> Unit,
    onNavigateToSearch: (seriesKeyword: String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToHobbyList: ((HobbyListNavParam) -> Unit)? = null,
    onNavigateToAlbumList: ((HobbyAlbumListNavParam) -> Unit)? = null,
    onNavigateToCommentDetail: ((CommentDetailNavParam) -> Unit)? = null,
    onNavigateToComments: ((HobbyCommentsNavParam) -> Unit)? = null,
    viewModel: SeriesDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current
    val messages = LocalAppMessages.current
    val textSharing = rememberTextSharing()
    val uriHandler = LocalUriHandler.current

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

    var commentUserId by remember { mutableStateOf(viewModel.currentCommentUserId) }
    LaunchedEffect(Unit) { commentUserId = viewModel.resolveCommentUserId() }

    var showMaskConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showInfoSheet by rememberSaveable { mutableStateOf(false) }

    when {
        uiState.isInitialLoading && uiState.series == null -> {
            Box(modifier = modifier.fillMaxSize()) {
                ShimmerHost {
                    SeriesDetailSkeleton(
                        isDark = isDark,
                        modifier = Modifier.padding(top = 64.dp)
                    )
                }
            }
        }

        uiState.errorMessage != null && uiState.series == null -> {
            Box(modifier = modifier.fillMaxSize()) {
                SeriesErrorState(
                    message = uiState.errorMessage ?: "加载失败",
                    onRetry = { viewModel.loadDetail(navParam) },
                    isDark = isDark
                )
            }
        }

        else -> {
            val series = uiState.series
            val commentInteraction = remember(uiState.pendingPraiseIds, uiState.seriesNodeId, commentUserId) {
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
                                relateNodeId = uiState.seriesNodeId,
                                snapshot = comment
                            )
                        )
                    }
                )
            }

            val commentFeedState = remember(uiState, series) {
                CommentFeedState(
                    totalCommentCount = series?.commentCount ?: (uiState.hotComments.size + uiState.latestComments.size),
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
                relateNodeId = uiState.seriesNodeId,
                contentPadding = AdaptiveDetailDefaults.nonHeroContentPadding(),
                modifier = modifier,
                topBar = {
                    DetailTopNavigation(
                        isScrolled = isScrolledPastHeader,
                        title = uiState.series?.displayName ?: navParam.initialName ?: "系列",
                        onBack = onBack,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                floatingToolbar = {
                    if (series != null) {
                        val finalCollectCount = (series.collect) + uiState.collectCountDelta
                        SeriesFloatingActionBar(
                            collectCount = maxOf(0, finalCollectCount),
                            isCollected = uiState.isCollected,
                            showCommentButton = !isWidePane,
                            onCollectClick = {
                                viewModel.toggleCollect()
                                val next = !uiState.isCollected
                                messages.show(if (next) "已收藏该系列" else "已取消收藏")
                            },
                            commentCount = series.commentCount ?: (uiState.hotComments.size + uiState.latestComments.size),
                            onCommentClick = {
                                val nodeId = if (uiState.seriesNodeId > 0L) uiState.seriesNodeId else navParam.seriesId
                                if (nodeId <= 0L) return@SeriesFloatingActionBar
                                onNavigateToComments?.invoke(
                                    HobbyCommentsNavParam(
                                        nodeId = nodeId,
                                        itemType = "series",
                                        title = series.displayName ?: navParam.initialName ?: "评论",
                                        initialCommentCount = series.commentCount ?: 0
                                    )
                                )
                            },
                            onShareClick = {
                                val title = series.displayName ?: navParam.initialName ?: "系列"
                                textSharing.share("【$title】系列周边 - PoiShelf 推荐：https://www.hpoi.net.cn/series/${navParam.seriesId}")
                            },
                            isDark = isDark
                        )
                    }
                }
            ) {
                // ── 区块 0：系列主体头部 ──
                if (series != null) {
                    item(key = "section_header") {
                        SeriesHeaderSection(
                            series = series,
                            onShowDetailInfo = { showInfoSheet = true },
                            isDark = isDark,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                // ── 区块 1：周边手办 (3x3 纯图九宫格) ──
                item(key = "section_hobbys") {
                    SeriesHobbysSection(
                        hobbys = uiState.hobbys,
                        isLoading = uiState.isHobbysLoading,
                        onHobbyClick = { item ->
                            onNavigateToHobby(
                                DetailNavParam(
                                    itemId = item.itemId,
                                    initialName = item.displayName,
                                    initialCover = item.cover
                                )
                            )
                        },
                        onViewMore = {
                            val nodeId = if (uiState.seriesNodeId > 0L) uiState.seriesNodeId else series?.actualNodeId ?: navParam.seriesId
                            val title = series?.displayName ?: navParam.initialName ?: "系列周边"
                            if (onNavigateToHobbyList != null && nodeId > 0L) {
                                onNavigateToHobbyList(
                                    HobbyListNavParam(
                                        title = "${title}的相关周边",
                                        workers = nodeId.toString()
                                    )
                                )
                            } else {
                                val keyword = series?.displayName ?: navParam.initialName ?: ""
                                onNavigateToSearch(keyword)
                            }
                        },
                        isDark = isDark,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // ── 区块 2：子系列 ──
                item(key = "section_sub_series") {
                    SeriesSubSeriesSection(
                        subSeriesList = uiState.subSeries,
                        isLoading = uiState.isSubSeriesLoading,
                        onSubSeriesClick = { item ->
                            onNavigateToSeries(
                                SeriesNavParam(
                                    seriesId = item.actualSeriesId,
                                    seriesNodeId = item.id,
                                    initialName = item.displayName,
                                    initialCover = item.cover
                                )
                            )
                        },
                        isDark = isDark,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // ── 区块 3：相关系列 ──
                item(key = "section_relate_series") {
                    SeriesRelateSeriesSection(
                        relateSeriesList = uiState.relateSeries,
                        isLoading = uiState.isRelateSeriesLoading,
                        onRelateSeriesClick = { item ->
                            onNavigateToSeries(
                                SeriesNavParam(
                                    seriesId = item.actualSeriesId,
                                    seriesNodeId = item.id,
                                    initialName = item.displayName,
                                    initialCover = item.cover
                                )
                            )
                        },
                        isDark = isDark,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // ── 区块 4：相关相册 ──
                item(key = "section_albums") {
                    SeriesAlbumsSection(
                        albums = uiState.albums,
                        isLoading = uiState.isAlbumsLoading,
                        onAlbumClick = { album ->
                            onNavigateToAlbum(
                                AlbumNavParam(
                                    albumId = album.actualAlbumId,
                                    initialTitle = album.displayName,
                                    initialCover = album.cover
                                )
                            )
                        },
                        onViewMore = {
                            val nodeId = if (uiState.seriesNodeId > 0L) uiState.seriesNodeId else series?.actualNodeId ?: navParam.seriesId
                            val title = series?.displayName ?: navParam.initialName ?: "系列相册"
                            if (onNavigateToAlbumList != null && nodeId > 0L) {
                                onNavigateToAlbumList(
                                    HobbyAlbumListNavParam(
                                        nodeId = nodeId,
                                        itemType = "series",
                                        title = "${title}的相关相册"
                                    )
                                )
                            }
                        },
                        isDark = isDark,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // ── 区块 5：在售周边 ──
                item(key = "section_goods") {
                    SeriesGoodsSection(
                        goodsList = uiState.goods,
                        isLoading = uiState.isGoodsLoading,
                        onGoodsClick = { goods ->
                            if (!goods.link.isNullOrBlank()) {
                                try {
                                    uriHandler.openUri(goods.link!!)
                                } catch (_: Exception) {
                                    messages.show("无法打开商品链接")
                                }
                            }
                        },
                        isDark = isDark,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    // 系列详细信息抽屉
    if (showInfoSheet) {
        SeriesInfoBottomSheet(
            series = uiState.series,
            onDismissRequest = { showInfoSheet = false }
        )
    }

    // 4. 屏蔽确认弹窗
    if (showMaskConfirmDialog) {
        val isCurrentlyMasked = uiState.isMasked
        AlertDialog(
            onDismissRequest = { showMaskConfirmDialog = false },
            title = {
                Text(
                    text = if (isCurrentlyMasked) "解除屏蔽" else "屏蔽系列",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isCurrentlyMasked) {
                        "确认解除对该系列的屏蔽吗？解除后该系列及其周边将重新出现在推荐与动态中。"
                    } else {
                        "确认屏蔽该系列吗？屏蔽后该系列及其周边将不再出现在动态与推荐列表中。"
                    },
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMaskConfirmDialog = false
                        viewModel.toggleMask()
                        messages.show(if (isCurrentlyMasked) "已解除屏蔽" else "已加入屏蔽黑名单")
                    }
                ) {
                    Text(
                        text = "确定",
                        color = if (isCurrentlyMasked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
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
}
