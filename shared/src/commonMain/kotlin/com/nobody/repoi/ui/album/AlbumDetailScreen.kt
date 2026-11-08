package com.nobody.repoi.ui.album

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.nobody.repoi.ui.home.components.ShimmerHost
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.media.resolvePictureUrl
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.platform.rememberTextSharing
import org.koin.compose.viewmodel.koinViewModel
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.repoi.data.remote.model.AlbumData
import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.PicItem
import com.nobody.repoi.data.remote.model.RelatedAlbumItem
import com.nobody.repoi.data.remote.model.RelatedHobbyItem
import com.nobody.repoi.ui.common.DetailTopNavigation
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material3.TextButton
import com.nobody.repoi.data.remote.model.ExHobbyImageItem
import com.nobody.repoi.ui.common.ExpressiveFloatingToolbar
import com.nobody.repoi.ui.common.ExpressiveToolbarButton
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.PoiAsyncImage
import com.nobody.repoi.ui.common.comment.AdaptiveDetailCommentScaffold
import com.nobody.repoi.ui.common.comment.CommentFeedState
import com.nobody.repoi.ui.common.comment.CommentSectionInteraction

import com.nobody.repoi.data.repository.FriendState
import com.nobody.repoi.ui.comment.CommentDetailNavParam
import com.nobody.repoi.ui.comment.HobbyCommentsNavParam
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.home.components.shimmerBackground
import com.nobody.repoi.ui.navigation.ExHobbyListNavKey
import com.nobody.repoi.ui.exhobby.ExHobbyPreviewSection
import com.nobody.repoi.ui.theme.LocalIsDarkTheme
import com.nobody.repoi.util.HtmlRichTextParser
import com.nobody.repoi.ui.theme.largeCard

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    param: AlbumNavParam,
    onBack: () -> Unit,
    onNavigateToHobbyDetail: (DetailNavParam) -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onUrlClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onNavigateToCommentDetail: ((CommentDetailNavParam) -> Unit)? = null,
    onNavigateToComments: ((HobbyCommentsNavParam) -> Unit)? = null,
    onNavigateToExHobbyList: ((ExHobbyListNavKey) -> Unit)? = null,
    viewModel: AlbumDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current
    val messages = LocalAppMessages.current
    val textSharing = rememberTextSharing()
    val gridState = rememberLazyStaggeredGridState()
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val windowSizeClass = adaptiveInfo.windowSizeClass
    val isExpandedWidth = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
    val isMediumWidth = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val isWidePane = isExpandedWidth || (isMediumWidth && !windowSizeClass.isHeightAtLeastBreakpoint(HEIGHT_DP_EXPANDED_LOWER_BOUND))

    // 初始化加载
    LaunchedEffect(param.albumId) {
        viewModel.loadDetail(param)
    }

    // 评论互动一次性提示（点赞/回复结果）
    val commentActionMessage = uiState.commentActionMessage
    LaunchedEffect(commentActionMessage) {
        if (commentActionMessage != null) {
            messages.show(commentActionMessage)
            viewModel.consumeCommentActionMessage()
        }
    }

    // 点赞/收藏/关注操作一次性提示
    val interactionActionMessage = uiState.interactionActionMessage
    LaunchedEffect(interactionActionMessage) {
        if (interactionActionMessage != null) {
            messages.show(interactionActionMessage)
            viewModel.consumeInteractionActionMessage()
        }
    }

    // 取消关注二次确认弹窗
    var showUnfollowConfirmDialog by remember { mutableStateOf(false) }
    if (showUnfollowConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showUnfollowConfirmDialog = false },
            title = { Text("取消关注", fontWeight = FontWeight.Bold) },
            text = { Text("确定要取消关注该作者吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnfollowConfirmDialog = false
                        viewModel.unfollowAuthor()
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnfollowConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    var commentUserId by remember { mutableStateOf(viewModel.currentCommentUserId) }

    LaunchedEffect(Unit) { commentUserId = viewModel.resolveCommentUserId() }

    val imageViewer = LocalImageViewer.current

    // 图片触底追加加载判断
    val shouldLoadMorePictures by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 2 && lastVisible >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMorePictures) {
        if (shouldLoadMorePictures && !uiState.isLoadingMorePictures && uiState.hasMorePictures) {
            viewModel.loadNextPicturesPage()
        }
    }

    // 全屏查看器只接受绝对 URL：此处结果直接喂给查看器（非 AsyncImage 下游），故保留管道解析
    val allPictureUrls = remember(uiState.pictures) {
        uiState.pictures.mapNotNull { pic ->
            pic.imagePath?.let { MediaPipeline.Default.resolvePictureUrl(it, ImagePurpose.FULLSCREEN_RAW) }
        }
    }

    when {
        // 首屏完整加载态
        uiState.isInitialLoading && uiState.album == null -> {
            ShimmerHost { AlbumDetailSkeleton(onBack = onBack) }
        }

        // 错误重试界面
        uiState.errorMessage != null && uiState.album == null -> {
            AlbumErrorState(
                errorMessage = uiState.errorMessage ?: "加载失败",
                onRetry = { viewModel.loadDetail(param) },
                onBack = onBack
            )
        }

        // 正常内容流
        else -> {
            val album = uiState.album

            val relateNodeId = uiState.album?.actualAlbumId ?: uiState.albumId
            val commentInteraction = remember(uiState.pendingPraiseIds, relateNodeId, commentUserId) {
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
                                relateNodeId = relateNodeId,
                                snapshot = comment
                            )
                        )
                    }
                )
            }

            val commentFeedState = remember(uiState, album) {
                CommentFeedState(
                    totalCommentCount = album?.commentCount ?: (uiState.hotComments.size + uiState.latestComments.size),
                    hotComments = uiState.hotComments,
                    latestComments = uiState.latestComments,
                    isCommentsLoading = uiState.isCommentsLoading,
                    isLoadingMoreComments = uiState.isLoadingMoreComments,
                    hasMoreComments = uiState.hasMoreComments,
                    commentsError = uiState.commentsError
                )
            }

            // 监测瀑布流网格滚动以联动顶部紧凑毛玻璃导航条
            val isScrolledPastHeader by remember {
                derivedStateOf {
                    gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 240
                }
            }

            AdaptiveDetailCommentScaffold(
                commentState = commentFeedState,
                onLoadMoreComments = { viewModel.loadNextCommentsPage() },
                onRefreshComments = { viewModel.refreshComments() },
                interaction = commentInteraction,
                relateNodeId = relateNodeId,
                modifier = modifier,
                isScrolledPastHeader = isScrolledPastHeader,
                topBar = {
                    AlbumTopNavigation(
                        isScrolled = isScrolledPastHeader,
                        title = uiState.album?.displayName ?: param.initialTitle ?: "相册详情",
                        author = uiState.album?.userNickname,
                        onBack = onBack,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                floatingToolbar = {
                    val curAlbum = uiState.album
                    val firstRelatedHobby = uiState.relatedHobbies.firstOrNull()
                    if (curAlbum != null || !uiState.isInitialLoading) {
                        AlbumFloatingActionBar(
                            isLiked = uiState.isLiked,
                            isCollected = uiState.isCollected,
                            likeCount = (curAlbum?.praiseCount ?: 0L) + uiState.praiseCountDelta,
                            collectCount = (curAlbum?.collect ?: 0L) + uiState.collectCountDelta,
                            commentCount = curAlbum?.commentCount ?: (uiState.hotComments.size + uiState.latestComments.size),
                            hasRelatedHobby = firstRelatedHobby != null,
                            showCommentButton = !isWidePane,
                            onToggleLike = viewModel::toggleLike,
                            onToggleCollect = viewModel::toggleCollect,
                            onCommentClick = {
                                val nodeId = param.albumId
                                if (nodeId <= 0L) return@AlbumFloatingActionBar
                                onNavigateToComments?.invoke(
                                    HobbyCommentsNavParam(
                                        nodeId = nodeId,
                                        itemType = "album",
                                        title = curAlbum?.name ?: param.initialTitle ?: "评论",
                                        initialCommentCount = curAlbum?.commentCount ?: 0
                                    )
                                )
                            },
                            onShareClick = {
                                val title = curAlbum?.name ?: param.initialTitle ?: "相册"
                                textSharing.share("【$title】相册 - PoiShelf 推荐：https://www.hpoi.net.cn/album/${param.albumId}")
                            },
                            onNavigateToRelatedHobby = {
                                if (firstRelatedHobby != null) {
                                    val hobbyParam = DetailNavParam(
                                        itemId = firstRelatedHobby.itemId,
                                        itemType = firstRelatedHobby.itemType ?: "hobby",
                                        initialName = firstRelatedHobby.displayName,
                                        initialCover = firstRelatedHobby.cover
                                    )
                                    onNavigateToHobbyDetail(hobbyParam)
                                }
                            }
                        )
                    }
                }
            ) {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(320.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp
                ) {
                // ── 区块 1：相册核心信息展台卡（独占整行）──
                item(key = "section_album_header", span = StaggeredGridItemSpan.FullLine) {
                    AlbumHeaderCard(
                        album = album,
                        initialTitle = param.initialTitle,
                        tags = uiState.tags,
                        followState = uiState.followState,
                        showFollow = !uiState.isAuthor,
                        onToggleFollow = {
                            if (uiState.isFollowed) {
                                showUnfollowConfirmDialog = true
                            } else {
                                viewModel.followAuthor()
                            }
                        },
                        onUrlClick = onUrlClick,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(top = 56.dp, bottom = 12.dp)
                    )
                }

                // ── 区块 2：摄影图集瀑布流（自然分栏，按图片类型复用）──
                // 图片请求上下文由屏级下传，避免逐项查询重复订阅
                if (uiState.pictures.isNotEmpty()) {
                    itemsIndexed(
                        items = uiState.pictures,
                        key = { index, pic -> "pic_${pic.id}_$index" },
                        contentType = { _, _ -> "album_pic" }
                    ) { index, pic ->
                        AlbumPictureItem(
                            pic = pic,
                            index = index,
                            onImageClick = { clickedIndex ->
                                if (allPictureUrls.isNotEmpty()) {
                                    imageViewer.show(
                                        images = allPictureUrls,
                                        initialIndex = clickedIndex.coerceIn(0, allPictureUrls.size - 1)
                                    )
                                }
                            }
                        )
                    }
                } else if (uiState.isPicturesLoading) {
                    // 图片列表初始加载骨架屏（展示多张不同比例骨架，让用户清晰感知正在加载照片瀑布流）
                    val placeholderHeights = listOf(320.dp, 240.dp, 360.dp, 280.dp)
                    items(placeholderHeights.size, key = { "pic_skeleton_$it" }) { index ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(placeholderHeights[index])
                                .clip(MaterialTheme.shapes.medium)
                                .shimmerBackground()
                        )
                    }
                } else {
                    // 加载完毕且无任何图片时的明确空状态提示
                    item(key = "pics_empty", span = StaggeredGridItemSpan.FullLine) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "暂无照片",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "作者还没有为该相册上传任何摄影照片",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 图片流加载中/已加载全部指示
                item(key = "pics_footer", span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isLoadingMorePictures) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "正在拉取更多原画...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (!uiState.hasMorePictures && uiState.pictures.isNotEmpty()) {
                            Text(
                                text = "—— 摄影图集共 ${uiState.pictures.size} 张原画 ——",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // ── 区块 3：关联周边横滑区 ──
                if (uiState.relatedHobbies.isNotEmpty()) {
                    item(key = "section_related_hobbies", span = StaggeredGridItemSpan.FullLine) {
                        RelatedHobbiesSection(
                            hobbies = uiState.relatedHobbies,
                            onHobbyClick = { hobby ->
                                val hobbyParam = DetailNavParam(
                                    itemId = hobby.itemId,
                                    itemType = hobby.itemType ?: "hobby",
                                    initialName = hobby.displayName,
                                    initialCover = hobby.cover
                                )
                                onNavigateToHobbyDetail(hobbyParam)
                            }
                        )
                    }
                }

                // ── 区块 4：关联相册横滑区（上下文由屏级下传）──
                if (uiState.relatedAlbums.isNotEmpty()) {
                    item(key = "section_related_albums", span = StaggeredGridItemSpan.FullLine) {
                        RelatedAlbumsSection(
                            albums = uiState.relatedAlbums,
                            onAlbumClick = { targetAlbumId ->
                                onNavigateToAlbum(targetAlbumId)
                            }
                        )
                    }
                }

                // ── 区块 4.5：ExHobby 扩展图库横滑流 ──
                if (uiState.isExHobbyEnabled && uiState.exHobbyPics.isNotEmpty()) {
                    item(key = "section_exhobby", span = StaggeredGridItemSpan.FullLine) {
                        ExHobbyPreviewSection(
                            pics = uiState.exHobbyPics,
                            onPicClick = { index: Int ->
                                // 查看器消费绝对 URL，保留管道解析（ExHobby 无 raw 档，FULLSCREEN_RAW 落 n/）
                                val allFullUrls = uiState.exHobbyPics.map {
                                    MediaPipeline.Default.resolveUrl(
                                        it.path,
                                        MediaCategory.EX_HOBBY,
                                        ImagePurpose.FULLSCREEN_RAW
                                    )
                                }
                                if (allFullUrls.isNotEmpty()) {
                                    imageViewer.show(allFullUrls, index)
                                }
                            },
                            onMoreClick = {
                                if (onNavigateToExHobbyList != null) {
                                    onNavigateToExHobbyList(
                                        ExHobbyListNavKey(itemId = param.albumId, title = "ExHobby")
                                    )
                                }
                            }
                        )
                    }
                }

                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 顶部毛玻璃导航栏 (AlbumTopNavigation)
// ─────────────────────────────────────────────────────────────

@Composable
private fun AlbumTopNavigation(
    isScrolled: Boolean,
    title: String,
    author: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    DetailTopNavigation(
        isScrolled = isScrolled,
        title = title,
        subtitle = author?.let { "by $it" },
        onBack = onBack,
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────
// 相册核心信息展台卡 (AlbumHeaderCard)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlbumHeaderCard(
    album: AlbumData?,
    initialTitle: String?,
    tags: List<String>,
    followState: FriendState,
    showFollow: Boolean = true,
    onToggleFollow: () -> Unit,
    onUrlClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isFollowed = followState.isFollowed

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeCard,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. 标题
            val title = album?.displayName?.ifBlank { null } ?: initialTitle ?: "精美手办相册"
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 26.sp
            )

            // 2. 作者微卡行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 头像沿用旧 getHeadUrl 的 head/n/ 档位（AVATAR + CARD_STANDARD），失败态由组件承担
                    PoiAsyncImage(
                        path = album?.user?.header,
                        category = MediaCategory.AVATAR,
                        purpose = ImagePurpose.CARD_STANDARD,
                        contentDescription = album?.userNickname,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = album?.userNickname ?: "摄影胶友",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // 等级标
                            val level = album?.user?.level ?: 0
                            if (level > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "Lv.$level",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        // 统计（浏览量 · 收藏数 · 图集张数）
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            if ((album?.hits ?: 0L) > 0) {
                                Text(
                                    text = "阅读 ${album?.hits}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            if ((album?.picCount ?: 0) > 0) {
                                Text(
                                    text = "·  ${album?.picCount}P 原图",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                // 关注胶囊按钮（本人时不显示）
                if (showFollow) {
                    Surface(
                        shape = CircleShape,
                        color = if (isFollowed) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onToggleFollow() }
                    ) {
                        Text(
                            text = when (followState) {
                                FriendState.MUTUAL -> "互相关注"
                                FriendState.FOLLOWING -> "已关注"
                                else -> "+ 关注"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isFollowed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // 3. 标签分类流 (Tags Flow)
            if (tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { tag ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = tag,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp)
                            )
                        }
                    }
                }
            }

            // 4. 摄影手记 / 描述说明文案（富文本平铺展示与链接分流）
            val rawHtml = album?.detail?.ifBlank { null }
            val cleanText = album?.detailPlainText?.ifBlank { null }
            val hasContent = !cleanText.isNullOrBlank()

            if (hasContent) {
                val linkColor = MaterialTheme.colorScheme.primary
                val richText = remember(rawHtml, linkColor, onUrlClick) {
                    if (!rawHtml.isNullOrBlank()) {
                        HtmlRichTextParser.parse(
                            html = rawHtml,
                            linkColor = linkColor,
                            onLinkClick = { url -> onUrlClick?.invoke(url) }
                        )
                    } else null
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (richText != null && richText.isNotBlank()) {
                            Text(
                                text = richText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        } else if (!cleanText.isNullOrBlank()) {
                            Text(
                                text = cleanText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 单张全宽原画长图项 (AlbumPictureItem)
// ─────────────────────────────────────────────────────────────

@Composable
private fun AlbumPictureItem(
    pic: PicItem,
    index: Int,
    onImageClick: (Int) -> Unit
) {
    // 相册图片走 pic/ 家族（MediaCategory 无 pic 槽位），详情页主图用 CARD_STANDARD 落 pic/n/，
    // 点击后由外层全屏查看器取 raw 档
    val pictureRatio = pic.pictureInfo?.let { info ->
        val w = info.width ?: 0
        val h = info.height ?: 0
        if (w > 0 && h > 0) w.toFloat() / h else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onImageClick(index) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // 自适应高度由基元负责：已知尺寸时以服务端宽高比为首帧占位，未知时回退 4:3
            PoiAsyncImage(
                model = MediaPipeline.Default.resolvePictureUrl(pic.imagePath, ImagePurpose.CARD_STANDARD),
                contentDescription = "相册摄影作品第 ${index + 1} 张",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth,
                adaptiveRatio = true,
                fallbackRatio = pictureRatio ?: 1.33f
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 关联周边横滑区 (RelatedHobbiesSection)
// ─────────────────────────────────────────────────────────────

@Composable
private fun RelatedHobbiesSection(
    hobbies: List<RelatedHobbyItem>,
    onHobbyClick: (RelatedHobbyItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "关联周边",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = hobbies,
                key = { it.itemId },
                contentType = { "related_hobby" }
            ) { hobby ->
                RelatedHobbyCard(
                    hobby = hobby,
                    onClick = { onHobbyClick(hobby) }
                )
            }
        }
    }
}

@Composable
private fun RelatedHobbyCard(
    hobby: RelatedHobbyItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.size(120.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        // 方块缩略图：固定尺寸由外层卡片给定，组件只负责填满与缺省/失败态
        PoiAsyncImage(
            path = hobby.cover,
            category = MediaCategory.HOBBY,
            purpose = ImagePurpose.THUMBNAIL,
            contentDescription = hobby.displayName,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 关联相册横滑区 (RelatedAlbumsSection)
// ─────────────────────────────────────────────────────────────

@Composable
private fun RelatedAlbumsSection(
    albums: List<RelatedAlbumItem>,
    onAlbumClick: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "关联相册",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = albums,
                key = { it.id },
                contentType = { "related_album" }
            ) { album ->
                RelatedAlbumCard(
                    album = album,
                    onClick = { onAlbumClick(album.id) }
                )
            }
        }
    }
}

@Composable
private fun RelatedAlbumCard(
    album: RelatedAlbumItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 无封面判定统一交给管道：显式空路径与官方缺省吉祥物都走纯文本卡
    val isNoCover = MediaPipeline.Default.isPlaceholder(album.cover)

    Card(
        onClick = onClick,
        modifier = modifier.height(160.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Box(
            modifier = Modifier
                .height(160.dp)
                .then(if (isNoCover) Modifier.aspectRatio(17f / 24f) else Modifier)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (isNoCover) {
                com.nobody.repoi.ui.album.components.AlbumTextCover(
                    title = album.displayName,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 相册无 cover/n/ 目录，管道把 CARD_STANDARD 裁决到保留纵横比的 cover/sr/；
                // 高度由 adaptiveRatio 按真实比例撑开，替换原先手写的纵横比缓存回写
                PoiAsyncImage(
                    path = album.cover,
                    category = MediaCategory.ALBUM,
                    purpose = ImagePurpose.CARD_STANDARD,
                    contentDescription = album.displayName,
                    modifier = Modifier.fillMaxSize(),
                    adaptiveRatio = true,
                    fallbackRatio = 1.0f
                )
            }

            // 右上角半透明浮层点赞徽标
            val praiseCount = album.praiseCount ?: 0L
            if (praiseCount > 0L) {
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ThumbUp,
                            contentDescription = "点赞",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = praiseCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 底部常驻悬浮操作胶囊 (AlbumFloatingActionBar)
// ─────────────────────────────────────────────────────────────

@Composable
private fun AlbumFloatingActionBar(
    isLiked: Boolean,
    isCollected: Boolean,
    likeCount: Long,
    collectCount: Long,
    hasRelatedHobby: Boolean,
    onToggleLike: () -> Unit,
    onToggleCollect: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onNavigateToRelatedHobby: () -> Unit,
    modifier: Modifier = Modifier,
    commentCount: Int = 0,
    showCommentButton: Boolean = true
) {
    ExpressiveFloatingToolbar(modifier = modifier) {
        // 1. 点赞（接线 AlbumDetailViewModel.toggleLike，只显示数字）
        ExpressiveToolbarButton(
            onClick = onToggleLike,
            text = if (likeCount > 0) "$likeCount" else null,
            icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (isLiked) "已点赞" else "点赞",
            isProminent = isLiked,
            containerColor = if (isLiked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (isLiked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            iconTint = if (isLiked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 2. 收藏（接线 AlbumDetailViewModel.toggleCollect，只显示数字）
        ExpressiveToolbarButton(
            onClick = onToggleCollect,
            text = if (collectCount > 0) "$collectCount" else null,
            icon = if (isCollected) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = if (isCollected) "已收藏" else "收藏",
            isProminent = isCollected,
            containerColor = if (isCollected) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
            contentColor = if (isCollected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            iconTint = if (isCollected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 3. 评论（小屏显示，统一显示数字，没有则显示0；大屏隐藏）
        if (showCommentButton) {
            ExpressiveToolbarButton(
                onClick = onCommentClick,
                text = "${commentCount.coerceAtLeast(0)}",
                icon = Icons.AutoMirrored.Filled.Comment,
                contentDescription = "评论",
                // 评论是导航类操作，无激活态；只让点赞/收藏这类切换控件着色
                isProminent = false
            )
        }

        // 4. 直达关联手办（若有，纯图标）
        if (hasRelatedHobby) {
            ExpressiveToolbarButton(
                onClick = onNavigateToRelatedHobby,
                icon = Icons.Filled.PhotoLibrary,
                contentDescription = "关联手办",
                isProminent = false
            )
        }

        // 5. 分享（次级操作，纯图标）
        ExpressiveToolbarButton(
            onClick = onShareClick,
            icon = Icons.Filled.Share,
            contentDescription = "分享",
            isProminent = false
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 骨架屏加载态与错误重试
// ─────────────────────────────────────────────────────────────

@Composable
private fun AlbumDetailSkeleton(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 56.dp, start = 14.dp, end = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 头部卡片骨架
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(MaterialTheme.shapes.largeCard)
                .shimmerBackground()
        )

        // 图片长图骨架
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .shimmerBackground()
            )
        }
    }
}

@Composable
private fun AlbumErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier.padding(24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "相册详情加载失败",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = CircleShape
                    ) {
                        Text("返回")
                    }

                    Button(
                        onClick = onRetry,
                        shape = CircleShape
                    ) {
                        Text("重试")
                    }
                }
            }
        }
    }
}
