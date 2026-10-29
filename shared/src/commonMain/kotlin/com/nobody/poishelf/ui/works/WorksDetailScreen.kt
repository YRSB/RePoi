package com.nobody.poishelf.ui.works

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import com.nobody.poishelf.ui.home.components.ShimmerHost
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.nobody.poishelf.data.remote.model.AlbumData
import com.nobody.poishelf.data.remote.model.HobbyItemSummary
import com.nobody.poishelf.data.remote.model.WorksCharacterItem
import com.nobody.poishelf.data.remote.model.WorksData
import com.nobody.poishelf.data.remote.model.WorksSeriesRef
import com.nobody.poishelf.platform.LocalAppMessages
import com.nobody.poishelf.platform.rememberTextSharing
import com.nobody.poishelf.ui.album.AlbumNavParam
import com.nobody.poishelf.ui.common.AdaptiveChunkedGrid
import com.nobody.poishelf.ui.common.DetailTopNavigation
import com.nobody.poishelf.ui.common.ExpressiveFloatingToolbar
import com.nobody.poishelf.ui.common.ExpressiveToolbarButton
import com.nobody.poishelf.ui.common.LocalImageViewer
import com.nobody.poishelf.ui.common.comment.AdaptiveDetailDefaults
import com.nobody.poishelf.ui.common.comment.AdaptiveDetailLazyColumnCommentScaffold
import com.nobody.poishelf.ui.common.comment.CommentFeedState
import com.nobody.poishelf.ui.common.comment.CommentSectionInteraction
import com.nobody.poishelf.ui.comment.CommentDetailNavParam
import com.nobody.poishelf.ui.comment.HobbyCommentsNavParam
import com.nobody.poishelf.ui.detail.DetailNavParam
import com.nobody.poishelf.ui.home.components.shimmerBackground
import com.nobody.poishelf.ui.item.list.HobbyListNavParam
import com.nobody.poishelf.ui.navigation.CharacterNavParam
import com.nobody.poishelf.ui.theme.ExpressiveElevatedCard
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.ui.theme.LocalIsDarkTheme
import com.nobody.poishelf.util.CdnUrlUtils

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WorksDetailScreen(
    navParam: WorksNavParam,
    onBack: () -> Unit,
    onNavigateToHobby: (DetailNavParam) -> Unit,
    onNavigateToAlbum: (AlbumNavParam) -> Unit,
    onNavigateToHobbyList: ((HobbyListNavParam) -> Unit)? = null,
    onNavigateToSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToCharacter: ((CharacterNavParam) -> Unit)? = null,
    onNavigateToCommentDetail: ((CommentDetailNavParam) -> Unit)? = null,
    onNavigateToComments: ((HobbyCommentsNavParam) -> Unit)? = null,
    viewModel: WorksDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current
    val messages = LocalAppMessages.current
    val textSharing = rememberTextSharing()
    val imageViewer = LocalImageViewer.current

    // 初始化加载
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

    var commentUserId by remember { mutableStateOf(viewModel.currentCommentUserId) }
    LaunchedEffect(Unit) { commentUserId = viewModel.resolveCommentUserId() }

    if (uiState.isInitialLoading && uiState.works == null) {
        ShimmerHost { WorksDetailSkeleton(isDark = isDark, modifier = Modifier.padding(top = 64.dp)) }
    } else if (uiState.errorMessage != null && uiState.works == null) {
        WorksErrorState(
            message = uiState.errorMessage ?: "加载失败",
            onRetry = { viewModel.loadDetail(navParam) },
            isDark = isDark
        )
    } else {
        val works = uiState.works
        val commentInteraction = remember(uiState.pendingPraiseIds, uiState.worksNodeId, commentUserId) {
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
                            relateNodeId = uiState.worksNodeId,
                            snapshot = comment
                        )
                    )
                }
            )
        }

        val commentFeedState = remember(uiState, works) {
            CommentFeedState(
                totalCommentCount = works?.commentCount ?: (uiState.hotComments.size + uiState.latestComments.size),
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
            relateNodeId = uiState.worksNodeId,
            contentPadding = AdaptiveDetailDefaults.nonHeroContentPadding(),
            modifier = modifier,
            topBar = {
                DetailTopNavigation(
                    isScrolled = isScrolledPastHeader,
                    title = works?.displayName ?: navParam.initialName ?: "作品",
                    subtitle = uiState.worksSeries?.displayName,
                    onBack = onBack,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            floatingToolbar = {
                if (uiState.works != null) {
                    WorksFloatingActionBar(
                        commentCount = works?.commentCount ?: (uiState.hotComments.size + uiState.latestComments.size),
                        showCommentButton = !isWidePane,
                        onCommentClick = {
                            val nodeId = if (uiState.worksNodeId > 0L) uiState.worksNodeId else navParam.worksId
                            if (nodeId <= 0L) return@WorksFloatingActionBar
                            onNavigateToComments?.invoke(
                                HobbyCommentsNavParam(
                                    nodeId = nodeId,
                                    itemType = "works",
                                    title = works?.displayName ?: navParam.initialName ?: "评论",
                                    initialCommentCount = works?.commentCount ?: 0
                                )
                            )
                        },
                        onShareClick = {
                            val title = works?.displayName ?: navParam.initialName ?: "作品"
                            textSharing.share("【$title】作品详情 - PoiShelf 推荐：https://www.hpoi.net.cn/works/${navParam.worksId}")
                        },
                        isDark = isDark
                    )
                }
            }
        ) {
                        if (works != null) {
                            item(key = "section_header") {
                                WorksHeaderSection(
                                    works = works,
                                    ratingAvg = uiState.ratingAvg,
                                    initialName = navParam.initialName,
                                    initialCover = navParam.initialCover,
                                    isIntroExpanded = uiState.isIntroExpanded,
                                    onToggleIntro = { viewModel.toggleIntroExpanded() },
                                    onCoverClick = {
                                        val url = CdnUrlUtils.getRawCoverUrl(works.cover ?: navParam.initialCover)
                                        if (url.isNotBlank()) {
                                            imageViewer.show(url)
                                        }
                                    },
                                    isDark = isDark,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                        val seriesRef = uiState.worksSeries
                        if (seriesRef != null && (seriesRef.displayName.isNotBlank())) {
                            item(key = "section_series") {
                                WorksSeriesSection(
                                    seriesRef = seriesRef,
                                    onSeriesClick = { onNavigateToSearch(seriesRef.displayName) },
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                        item(key = "section_characters") {
                            WorksCharactersSection(
                                characters = uiState.characters,
                                isLoading = uiState.isCharactersLoading,
                                onCharacterClick = { character ->
                                    if (onNavigateToCharacter != null && character.charactarId > 0L) {
                                        onNavigateToCharacter(
                                            CharacterNavParam(
                                                characterId = character.charactarId,
                                                initialName = character.displayName,
                                                initialCover = character.cCover
                                            )
                                        )
                                    } else if (character.displayName.isNotBlank()) {
                                        onNavigateToSearch(character.displayName)
                                    }
                                },
                                onViewMore = {
                                    val keyword = works?.displayName ?: navParam.initialName ?: ""
                                    if (keyword.isNotBlank()) onNavigateToSearch(keyword)
                                },
                                isDark = isDark,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                        item(key = "section_hobbys") {
                            WorksHobbysSection(
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
                                    val nodeId = works?.actualNodeId ?: 0L
                                    if (nodeId > 0L && onNavigateToHobbyList != null) {
                                        onNavigateToHobbyList(
                                            HobbyListNavParam(
                                                title = "${works?.displayName ?: "作品"}的周边",
                                                works = nodeId.toString()
                                            )
                                        )
                                    } else {
                                        val keyword = works?.displayName ?: navParam.initialName ?: ""
                                        if (keyword.isNotBlank()) {
                                            onNavigateToSearch(keyword)
                                        } else {
                                            messages.show("正在检索中...")
                                        }
                                    }
                                },
                                isDark = isDark,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                        item(key = "section_albums") {
                            WorksAlbumsSection(
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
                                isDark = isDark,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 底部悬浮操作栏 (WorksFloatingActionBar)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun WorksFloatingActionBar(
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    commentCount: Int = 0,
    showCommentButton: Boolean = true
) {
    ExpressiveFloatingToolbar(modifier = modifier) {
        // 1. 评论（小屏显示，统一显示数字，没有则显示0；大屏隐藏）
        if (showCommentButton) {
            ExpressiveToolbarButton(
                onClick = onCommentClick,
                text = "${commentCount.coerceAtLeast(0)}",
                icon = Icons.AutoMirrored.Filled.Comment,
                contentDescription = "评论",
                isProminent = true
            )
        }

        // 2. 分享（次级操作，纯图标）
        ExpressiveToolbarButton(
            onClick = onShareClick,
            icon = Icons.Filled.Share,
            contentDescription = "分享",
            isProminent = false
        )
    }
}



@Composable
internal fun WorksHeaderSection(
    works: WorksData,
    ratingAvg: Float,
    initialName: String?,
    initialCover: String?,
    isIntroExpanded: Boolean,
    onToggleIntro: () -> Unit,
    onCoverClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    ExpressiveElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                val coverUrl = CdnUrlUtils.getCoverUrl(works.cover ?: initialCover)
                Box(
                    modifier = Modifier.width(96.dp).aspectRatio(9f / 13f).clip(ExpressiveTokens.MediumCardShape)
                        .background(if (isDark) Color(0xFF22242E) else Color(0xFFEBEDF2))
                        .clickable(onClick = onCoverClick)
                ) {
                    if (coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = works.displayName.ifBlank { initialName.orEmpty() }.ifBlank { "未知作品" },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!works.name.isNullOrBlank() && works.name != works.nameCN) {
                        Text(
                            text = works.name.orEmpty(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!works.releaseDateString.isNullOrBlank()) {
                        Text(
                            text = works.releaseDateString.orEmpty(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (ratingAvg > 0f) {
                            Text(
                                text = "评分 ${"%.1f".format(ratingAvg)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "人气 ${works.hits}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "收藏 ${works.collect}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (works.detailPlainText.isNotBlank()) {
                Column(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleIntro).animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isIntroExpanded) works.detailPlainText else works.detailPlainText.take(120) + if (works.detailPlainText.length > 120) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isIntroExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isIntroExpanded) "收起" else "展开全文",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
internal fun WorksSeriesSection(
    seriesRef: WorksSeriesRef,
    onSeriesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExpressiveElevatedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onSeriesClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val coverUrl = CdnUrlUtils.getCoverUrl(seriesRef.cover)
            Box(
                modifier = Modifier.size(48.dp).clip(ExpressiveTokens.SmallCardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "所属系列",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = seriesRef.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
internal fun WorksCharactersSection(
    characters: List<WorksCharacterItem>,
    isLoading: Boolean,
    onCharacterClick: (WorksCharacterItem) -> Unit,
    onViewMore: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLoading && characters.isEmpty()) return
    ExpressiveElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "登场角色",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onViewMore) {
                    Text(text = "查看更多")
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (isLoading && characters.isEmpty()) {
                ShimmerHost { Box(modifier = Modifier.fillMaxWidth().height(64.dp).shimmerBackground()) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    characters.take(6).forEach { character ->
                        val coverUrl = CdnUrlUtils.getCoverUrl(character.cCover)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveTokens.CardShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            onClick = { onCharacterClick(character) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(48.dp).clip(ExpressiveTokens.SmallCardShape)
                                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                ) {
                                    if (coverUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = coverUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = character.displayName.ifBlank { "未知角色" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subLine = listOfNotNull(
                                        character.jsCareerType?.trim()?.ifBlank { null },
                                        character.voiceName.trim().ifBlank { null }?.let { "配音 $it" }
                                    ).joinToString(" · ")
                                    if (subLine.isNotBlank()) {
                                        Text(
                                            text = subLine,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
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
internal fun WorksHobbysSection(
    hobbys: List<HobbyItemSummary>,
    isLoading: Boolean,
    onHobbyClick: (HobbyItemSummary) -> Unit,
    onViewMore: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLoading && hobbys.isEmpty()) return
    ExpressiveElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "相关周边",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onViewMore) {
                    Text(text = "查看更多")
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (isLoading && hobbys.isEmpty()) {
                ShimmerHost { Box(modifier = Modifier.fillMaxWidth().height(120.dp).shimmerBackground()) }
            } else {
                AdaptiveChunkedGrid(
                    items = hobbys.take(9),
                    maxRows = 3,
                    mediumColumns = 5,
                    modifier = Modifier.fillMaxWidth()
                ) { item ->
                    val coverUrl = CdnUrlUtils.getCoverUrl(item.cover)
                    Box(
                        modifier = Modifier.fillMaxWidth().aspectRatio(9f / 13f)
                            .clip(ExpressiveTokens.CardShape)
                            .background(if (isDark) Color(0xFF22242E) else Color(0xFFEBEDF2))
                            .clickable { onHobbyClick(item) }
                    ) {
                        if (coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorksAlbumsSection(
    albums: List<AlbumData>,
    isLoading: Boolean,
    onAlbumClick: (AlbumData) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLoading && albums.isEmpty()) return
    ExpressiveElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "相关相册",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isLoading && albums.isEmpty()) {
                ShimmerHost { Box(modifier = Modifier.fillMaxWidth().height(96.dp).shimmerBackground()) }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    albums.take(10).forEach { album ->
                        Column(
                            modifier = Modifier.width(120.dp).clip(ExpressiveTokens.CardShape)
                                .clickable { onAlbumClick(album) },
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val coverUrl = CdnUrlUtils.getCoverUrl(album.cover)
                            Box(
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                    .clip(ExpressiveTokens.CardShape)
                                    .background(if (isDark) Color(0xFF22242E) else Color(0xFFEBEDF2))
                            ) {
                                if (coverUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = coverUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Text(
                                text = album.displayName.ifBlank { "未知相册" },
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorksDetailSkeleton(isDark: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp).shimmerBackground())
        Box(modifier = Modifier.fillMaxWidth().height(120.dp).shimmerBackground())
        Box(modifier = Modifier.fillMaxWidth().height(200.dp).shimmerBackground())
    }
}

@Composable
internal fun WorksErrorState(message: String, onRetry: () -> Unit, isDark: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            shape = ExpressiveTokens.LargeCardShape,
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                androidx.compose.material3.Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(text = "点击重试")
                }
            }
        }
    }
}
