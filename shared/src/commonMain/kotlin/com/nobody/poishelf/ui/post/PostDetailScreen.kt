package com.nobody.poishelf.ui.post

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import com.nobody.poishelf.platform.rememberTextSharing
import com.nobody.poishelf.platform.LocalAppMessages
import com.nobody.poishelf.ui.home.components.ShimmerHost
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.nobody.poishelf.data.remote.model.PostDetailData
import com.nobody.poishelf.data.remote.model.PostRelateItem
import com.nobody.poishelf.ui.album.AlbumNavParam
import com.nobody.poishelf.ui.comment.CommentDetailNavParam
import com.nobody.poishelf.ui.common.DetailTopNavigation
import com.nobody.poishelf.ui.common.ExpressiveFloatingToolbar
import com.nobody.poishelf.ui.common.ExpressiveToolbarButton
import com.nobody.poishelf.ui.common.LevelPill
import com.nobody.poishelf.ui.common.LocalImageViewer
import com.nobody.poishelf.ui.common.comment.AdaptiveDetailDefaults
import com.nobody.poishelf.ui.common.comment.AdaptiveDetailLazyColumnCommentScaffold
import com.nobody.poishelf.ui.common.comment.CommentFilterMode
import com.nobody.poishelf.ui.common.comment.CommentSectionInteraction
import com.nobody.poishelf.ui.common.comment.commentSectionItems
import com.nobody.poishelf.ui.detail.DetailNavParam
import com.nobody.poishelf.ui.navigation.CharacterNavParam
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.ui.theme.LocalIsDarkTheme
import com.nobody.poishelf.util.CdnUrlUtils
import com.nobody.poishelf.util.HpoiLinkDispatcher
import com.nobody.poishelf.util.HtmlRichTextParser
import kotlinx.coroutines.launch

/**
 * 讨论贴详情页。
 * 遵循项目自适应双窗格设计与通用评论体系，提供富文本渲染、大图预览、关联手办导航与回帖互动能力。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostDetailScreen(
    postId: Long,
    initialTitle: String? = null,
    nodeId: Long? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: ((DetailNavParam) -> Unit)? = null,
    onNavigateToZone: ((Long) -> Unit)? = null,
    onNavigateToUser: ((Long) -> Unit)? = null,
    onNavigateToAlbum: ((AlbumNavParam) -> Unit)? = null,
    onNavigateToCharacter: ((CharacterNavParam) -> Unit)? = null,
    onNavigateToPost: ((Long) -> Unit)? = null,
    onNavigateToCommentDetail: ((CommentDetailNavParam) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: PostDetailViewModel = koinViewModel()
) {
    val messages = LocalAppMessages.current
    val textSharing = rememberTextSharing()
    val uriHandler = LocalUriHandler.current
    val isDark = LocalIsDarkTheme.current
    val imageViewer = LocalImageViewer.current
    val coroutineScope = rememberCoroutineScope()
    val mainListState = androidx.compose.foundation.lazy.rememberLazyListState()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val rawPicUrls = remember(uiState.detail?.picList) {
        uiState.detail?.picList?.map { CdnUrlUtils.getPostsRawUrl(it) } ?: emptyList()
    }

    var showDeletePostConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(postId, nodeId) {
        viewModel.setPostId(postId, initialTitle, nodeId)
    }

    LaunchedEffect(uiState.postDeleted) {
        if (uiState.postDeleted) {
            messages.show("帖子已删除")
            onNavigateBack()
        }
    }

    val commentInteraction = remember(uiState.currentUserId, onNavigateToCommentDetail) {
        CommentSectionInteraction(
            currentUserId = uiState.currentUserId,
            onAddComment = { content, stickers ->
                val ok = viewModel.submitTopLevelComment(content, stickers)
                if (!ok) {
                    messages.show("发表回帖失败，请检查登录状态或网络")
                }
                ok
            },
            onReplySubmit = { comment, target, content, stickers ->
                val ok = viewModel.submitReply(comment, target, content, stickers)
                if (!ok) {
                    messages.show("回复失败，请检查登录状态或网络")
                }
                ok
            },
            onDeleteComment = { comment ->
                val ok = viewModel.deleteComment(comment)
                if (!ok) {
                    messages.show("删除失败，请检查登录状态或权限")
                }
                ok
            },
            onDeleteReply = { comment, reply ->
                val ok = viewModel.deleteReply(comment, reply)
                if (!ok) {
                    messages.show("删除失败，请检查登录状态或权限")
                }
                ok
            },
            onViewAllReplies = { comment ->
                onNavigateToCommentDetail?.invoke(
                    CommentDetailNavParam(
                        commentId = comment.id,
                        relateNodeId = uiState.detail?.id ?: nodeId ?: postId,
                        snapshot = comment,
                        isHot = uiState.commentFeedState.hotComments.any { it.id == comment.id },
                        isDiscuss = true
                    )
                )
            },
            onLoadStickerPacks = {
                viewModel.loadStickerPacks()
            }
        )
    }

    if (showDeletePostConfirm) {
        AlertDialog(
            onDismissRequest = { showDeletePostConfirm = false },
            title = { Text("删除帖子") },
            text = { Text("确定要删除该讨论贴吗？删除后将无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeletePostConfirm = false
                        coroutineScope.launch {
                            val ok = viewModel.deletePost()
                            if (!ok) {
                                messages.show("删除帖子失败")
                            }
                        }
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePostConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    AdaptiveDetailLazyColumnCommentScaffold(
        commentState = uiState.commentFeedState,
        onLoadMoreComments = { viewModel.loadNextCommentsPage() },
        onRefreshComments = { viewModel.refresh() },
        interaction = commentInteraction,
        relateNodeId = uiState.detail?.id ?: nodeId ?: postId,
        commentTitle = "回帖",
        isDark = isDark,
        listState = mainListState,
        shouldLoadMoreCommentsInCompact = {
            val layoutInfo = mainListState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 2 && lastVisible >= total - 3
        },
        onScrollToCommentsInCompact = {
            coroutineScope.launch {
                val detail = uiState.detail
                var targetIndex = 1
                if (detail?.picList?.isNotEmpty() == true) targetIndex++
                if (detail?.relateItem != null && detail.relateItem.itemId > 0L) targetIndex++
                if (!detail?.zoneIds.isNullOrEmpty()) targetIndex++
                mainListState.animateScrollToItem(targetIndex)
            }
        },
        contentPadding = AdaptiveDetailDefaults.nonHeroContentPadding(),
        modifier = modifier,
        topBar = {
            DetailTopNavigation(
                isScrolled = isScrolledPastHeader,
                title = uiState.detail?.title ?: initialTitle ?: "讨论",
                onBack = onNavigateBack,
                modifier = Modifier.fillMaxWidth()
            )
        },
        floatingToolbar = {
            val detail = uiState.detail
            val isAuthor = detail?.user?.effectiveUserId == uiState.currentUserId && uiState.currentUserId > 0L

            ExpressiveFloatingToolbar {
                // 1. 回帖主按钮
                ExpressiveToolbarButton(
                    onClick = { openTopLevelCommentDialog() },
                    text = "回帖",
                    icon = Icons.AutoMirrored.Filled.Comment,
                    contentDescription = "回帖",
                    isProminent = true
                )

                // 2. 评论数按钮（窄屏点击平滑定位到回帖区）
                ExpressiveToolbarButton(
                    onClick = { scrollToComments() },
                    text = "${uiState.totalCommentCount}",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = "回帖列表",
                    isProminent = false
                )

                // 3. 分享操作
                ExpressiveToolbarButton(
                    onClick = {
                        val title = detail?.title ?: initialTitle ?: "讨论"
                        val shareItemId = detail?.itemId?.takeIf { it > 0L } ?: postId
                        textSharing.share("【$title】PoiShelf 讨论：https://www.hpoi.net.cn/posts/$shareItemId")
                    },
                    icon = Icons.Filled.Share,
                    contentDescription = "分享",
                    isProminent = false
                )

                // 4. 作者删除操作
                if (isAuthor) {
                    ExpressiveToolbarButton(
                        onClick = { showDeletePostConfirm = true },
                        icon = Icons.Filled.Delete,
                        contentDescription = "删除帖子",
                        isProminent = false,
                        contentColor = MaterialTheme.colorScheme.error,
                        iconTint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    ) { scaffoldScope ->

        // ── 1. 骨架态或错误态 ──
        if (uiState.isInitialLoading) {
            item(key = "post_loading_skeleton") {
                ShimmerHost { PostDetailSkeleton(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
            }
        } else if (uiState.detailError != null && uiState.detail == null) {
            item(key = "post_error_state") {
                PostErrorCard(
                    errorMessage = uiState.detailError ?: "加载失败",
                    onRetry = { viewModel.loadDetail() },
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            val detail = uiState.detail
            if (detail != null) {
                // ── 2. 主楼卡片：作者头部、标题与正文 ──
                item(key = "post_main_card") {
                    ElevatedCard(
                        shape = ExpressiveTokens.CardShape,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.elevatedCardElevation(
                            defaultElevation = ExpressiveTokens.ElevationDefault
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 2.1 作者信息行
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = CdnUrlUtils.getHeadUrl(detail.user?.header),
                                    contentDescription = detail.user?.nickname,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable {
                                            detail.user?.effectiveUserId?.let { uid ->
                                                if (uid > 0L) onNavigateToUser?.invoke(uid)
                                            }
                                        }
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = detail.user?.nickname ?: "胶友",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        if ((detail.user?.level ?: 0) > 0) {
                                            LevelPill(level = detail.user?.level ?: 0)
                                        }

                                        detail.forum?.name?.let { forumName ->
                                            if (forumName.isNotBlank()) {
                                                Surface(
                                                    shape = ExpressiveTokens.PillShape,
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                ) {
                                                    Text(
                                                        text = forumName,
                                                        fontSize = 10.5.sp,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Text(
                                        text = "${detail.formattedTimeText} · 浏览 ${detail.hits}",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                thickness = 0.5.dp
                            )

                            // 2.2 帖子标题
                            Text(
                                text = detail.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 28.sp
                            )

                            // 2.3 帖子富文本正文
                            if (!detail.content.isNullOrBlank()) {
                                val richText = remember(detail.content) {
                                    HtmlRichTextParser.parse(
                                        html = detail.content,
                                        linkColor = ColorLink,
                                        onLinkClick = { url ->
                                            HpoiLinkDispatcher.dispatch(
                                                url = url,
                                                uriHandler = uriHandler,
                                                onMessage = { messages.show(it) },
                                                onNavigateToHobbyDetail = onNavigateToDetail,
                                                onNavigateToAlbum = { id -> onNavigateToAlbum?.invoke(AlbumNavParam(albumId = id)) },
                                                onNavigateToCharacter = onNavigateToCharacter,
                                                onNavigateToPost = onNavigateToPost
                                            )
                                        }
                                    )
                                }

                                Text(
                                    text = richText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 24.sp
                                )
                            }
                        }
                    }
                }

                // ── 3. 帖子配图画廊（自适应瀑布流排版） ──
                if (detail.picList.isNotEmpty()) {
                    item(key = "post_pics_gallery") {
                        // 说明：此处位于 Lazy 列表项内，避免 BoxWithConstraints 触发子组合与二次测量，
                        // 改用窗口断点计算列数（展开屏 3 列、中等屏 2 列，对应原 900dp/480dp 阈值）。
                        val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
                        val columns = when {
                            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) -> 3
                            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) -> 2
                            else -> 1
                        }.coerceAtMost(detail.picList.size.coerceAtLeast(1))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {

                            if (columns <= 1) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    detail.picList.forEachIndexed { globalIndex, picPath ->
                                        PostPicCard(
                                            picPath = picPath,
                                            index = globalIndex,
                                            onClick = { imageViewer.show(rawPicUrls, globalIndex) }
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (colIndex in 0 until columns) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            detail.picList.forEachIndexed { globalIndex, picPath ->
                                                if (globalIndex % columns == colIndex) {
                                                    PostPicCard(
                                                        picPath = picPath,
                                                        index = globalIndex,
                                                        onClick = { imageViewer.show(rawPicUrls, globalIndex) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 4. 关联手办卡片 ──
                if (detail.relateItem != null && detail.relateItem.itemId > 0L) {
                    item(key = "post_relate_hobby") {
                        PostRelateHobbyCard(
                            item = detail.relateItem,
                            onClick = {
                                onNavigateToDetail?.invoke(DetailNavParam(itemId = detail.relateItem.itemId))
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                // ── 5. 所属专区标签 ──
                if (!detail.zoneIds.isNullOrEmpty()) {
                    item(key = "post_zone_tags") {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            detail.zoneIds.forEach { zoneId ->
                                Surface(
                                    shape = ExpressiveTokens.PillShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.clickable {
                                        onNavigateToZone?.invoke(zoneId)
                                    }
                                ) {
                                    Text(
                                        text = "# 专区 $zoneId",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 6. 紧凑模式下的回帖流 ──
                if (!scaffoldScope.isWidePane) {
                    // 回帖表头与排序切换栏
                    item(key = "compact_comments_header_bar") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "回帖 (${uiState.totalCommentCount})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = uiState.order.isEmpty(),
                                    onClick = { viewModel.setOrder("") },
                                    label = { Text("最新", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )

                                FilterChip(
                                    selected = uiState.order == "asc",
                                    onClick = { viewModel.setOrder("asc") },
                                    label = { Text("最早", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }

                    // 评论流复用
                    commentSectionItems(
                        filterMode = CommentFilterMode.LATEST,
                        onFilterModeChange = { },
                        totalCommentCount = uiState.totalCommentCount,
                        hotComments = emptyList(),
                        latestComments = uiState.comments,
                        isCommentsLoading = uiState.isCommentsLoading,
                        isLoadingMoreComments = uiState.isLoadingMoreComments,
                        hasMoreComments = uiState.hasMoreComments,
                        isDark = isDark,
                        title = "回帖",
                        commentsError = uiState.commentsError,
                        onRetryLoadMore = { viewModel.loadNextCommentsPage() },
                        showHeader = false,
                        interaction = commentInteraction
                    )
                }
            }
        }
    }
}

/**
 * 关联手办卡片。
 */
@Composable
private fun PostRelateHobbyCard(
    item: PostRelateItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        shape = ExpressiveTokens.CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = ExpressiveTokens.ElevationLevel1
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = CdnUrlUtils.getCoverSmallUrl(item.cover),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(ExpressiveTokens.MediumCardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = ExpressiveTokens.PillShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = item.itemType ?: "关联手办",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = item.displayName,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 帖子详情加载骨架。
 */
@Composable
private fun PostDetailSkeleton(modifier: Modifier = Modifier) {
    ElevatedCard(
        shape = ExpressiveTokens.CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(width = 120.dp, height = 14.dp)
                            .clip(ExpressiveTokens.PillShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 10.dp)
                            .clip(ExpressiveTokens.PillShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(20.dp)
                    .clip(ExpressiveTokens.PillShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(ExpressiveTokens.MediumCardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
    }
}

/**
 * 帖子加载错误卡片。
 */
@Composable
private fun PostErrorCard(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        shape = ExpressiveTokens.CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(onClick = onRetry) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "重试")
                Spacer(modifier = Modifier.width(6.dp))
                Text("重新加载")
            }
        }
    }
}

/**
 * 帖子配图单个卡片，采用固定宽度、真实纵横比自适应高度展示，支持点击大图预览。
 */
@Composable
private fun PostPicCard(
    picPath: String,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbUrl = remember(picPath) { CdnUrlUtils.getPostsUrl(picPath) }
    Card(
        shape = ExpressiveTokens.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(ExpressiveTokens.CardShape)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = thumbUrl,
            contentDescription = "附图 ${index + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        )
    }
}

private val ColorLink = androidx.compose.ui.graphics.Color(0xFF1E88E5)
