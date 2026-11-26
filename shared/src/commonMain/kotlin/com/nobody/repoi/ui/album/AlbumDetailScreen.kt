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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.nobody.repoi.ui.common.loading.PoiLoadingView
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
import com.nobody.repoi.ui.navigation.ExHobbyListNavKey
import com.nobody.repoi.ui.exhobby.ExHobbyPreviewSection
import com.nobody.repoi.ui.theme.LocalIsDarkTheme
import com.nobody.repoi.util.HtmlRichTextParser
import com.nobody.repoi.ui.theme.largeCard
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

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

    LaunchedEffect(param.albumId) {
        viewModel.loadDetail(param)
    }

    val commentActionMessage = uiState.commentActionMessage
    LaunchedEffect(commentActionMessage) {
        if (commentActionMessage != null) {
            messages.show(commentActionMessage)
            viewModel.consumeCommentActionMessage()
        }
    }

    val interactionActionMessage = uiState.interactionActionMessage
    LaunchedEffect(interactionActionMessage) {
        if (interactionActionMessage != null) {
            messages.show(interactionActionMessage)
            viewModel.consumeInteractionActionMessage()
        }
    }

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

    val allPictureUrls = remember(uiState.pictures) {
        uiState.pictures.mapNotNull { pic ->
            pic.imagePath?.let { MediaPipeline.Default.resolvePictureUrl(it, ImagePurpose.FULLSCREEN_RAW) }
        }
    }

    when {

        uiState.errorMessage != null && uiState.album == null -> {
            AlbumErrorState(
                errorMessage = uiState.errorMessage ?: "加载失败",
                onRetry = { viewModel.loadDetail(param) },
                onBack = onBack
            )
        }

        else -> {
            val album = uiState.album

            val relateNodeId = uiState.album?.actualAlbumId ?: uiState.albumId
            val commentInteraction = remember(uiState.pendingPraiseIds, relateNodeId, commentUserId, onUrlClick) {
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
                    },
                    onUrlClick = onUrlClick
                )
            }

            val commentFeedState = remember(uiState, album) {
                CommentFeedState(
                    totalCommentCount = album?.commentCount ?: (uiState.hotComments.size + uiState.latestComments.size),
                    hotComments = uiState.hotComments,
                    latestComments = uiState.latestComments,
                    isCommentsLoading = uiState.isCommentsLoading || (uiState.isInitialLoading && album == null),
                    isLoadingMoreComments = uiState.isLoadingMoreComments,
                    hasMoreComments = uiState.hasMoreComments,
                    commentsError = uiState.commentsError
                )
            }

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
                    if (curAlbum != null) {
                        AlbumFloatingActionBar(
                            isLiked = uiState.isLiked,
                            isCollected = uiState.isCollected,
                            likeCount = (curAlbum.praiseCount ?: 0L) + uiState.praiseCountDelta,
                            collectCount = (uiState.collectCountBase ?: (curAlbum.collect ?: 0L)) + uiState.collectCountDelta,
                            commentCount = curAlbum.commentCount,
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
                                        title = curAlbum.name ?: param.initialTitle ?: "评论",
                                        initialCommentCount = curAlbum.commentCount
                                    )
                                )
                            },
                            onShareClick = {
                                val title = curAlbum.name ?: param.initialTitle ?: "相册"
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
                if (uiState.isInitialLoading && album == null) {
                    PoiLoadingView()
                } else {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Adaptive(320.dp),
                        state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp
                ) {

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
                    item(key = "pics_loading", span = StaggeredGridItemSpan.FullLine) {
                        PoiLoadingView(modifier = Modifier.fillMaxWidth().padding(32.dp))
                    }
                } else {

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
                                    painter = painterResource(PoiIcons.Filled.PhotoLibrary),
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
                                LoadingIndicator(
                                    modifier = Modifier.size(28.dp)
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

                if (uiState.isExHobbyEnabled && uiState.exHobbyPics.isNotEmpty()) {
                    item(key = "section_exhobby", span = StaggeredGridItemSpan.FullLine) {
                        ExHobbyPreviewSection(
                            pics = uiState.exHobbyPics,
                            onPicClick = { index: Int ->

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
}

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

            val title = album?.displayName?.ifBlank { null } ?: initialTitle ?: "精美手办相册"
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 26.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {

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

@Composable
private fun AlbumPictureItem(
    pic: PicItem,
    index: Int,
    onImageClick: (Int) -> Unit
) {

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

        PoiAsyncImage(
            path = hobby.cover,
            category = MediaCategory.HOBBY,
            purpose = ImagePurpose.THUMBNAIL,
            contentDescription = hobby.displayName,
            modifier = Modifier.fillMaxSize()
        )
    }
}

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
                            painter = painterResource(PoiIcons.Filled.ThumbUp),
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

        ExpressiveToolbarButton(
            onClick = onToggleLike,
            text = if (likeCount > 0) "$likeCount" else null,
            icon = if (isLiked) PoiIcons.Filled.Favorite else PoiIcons.Filled.FavoriteBorder,
            contentDescription = if (isLiked) "已点赞" else "点赞",
            isProminent = isLiked,
            containerColor = if (isLiked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (isLiked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            iconTint = if (isLiked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )

        ExpressiveToolbarButton(
            onClick = onToggleCollect,
            text = if (collectCount > 0) "$collectCount" else null,
            icon = if (isCollected) PoiIcons.Filled.Star else PoiIcons.Filled.StarBorder,
            contentDescription = if (isCollected) "已收藏" else "收藏",
            isProminent = isCollected,
            containerColor = if (isCollected) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
            contentColor = if (isCollected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            iconTint = if (isCollected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (showCommentButton) {
            ExpressiveToolbarButton(
                onClick = onCommentClick,
                text = "${commentCount.coerceAtLeast(0)}",
                icon = PoiIcons.AutoMirrored.Filled.Comment,
                contentDescription = "评论",

                isProminent = false
            )
        }

        if (hasRelatedHobby) {
            ExpressiveToolbarButton(
                onClick = onNavigateToRelatedHobby,
                icon = PoiIcons.Filled.PhotoLibrary,
                contentDescription = "关联手办",
                isProminent = false
            )
        }

        ExpressiveToolbarButton(
            onClick = onShareClick,
            icon = PoiIcons.Filled.Share,
            contentDescription = "分享",
            isProminent = false
        )
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
