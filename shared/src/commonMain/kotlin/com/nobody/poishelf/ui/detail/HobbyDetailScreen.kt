package com.nobody.poishelf.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import com.nobody.poishelf.ui.home.components.ShimmerHost
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import com.nobody.poishelf.util.HpoiLinkDispatcher
import com.nobody.poishelf.util.HtmlRichTextParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.poishelf.platform.LocalAppMessages
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ThumbUp
import com.nobody.poishelf.data.remote.model.ExHobbyImageItem
import com.nobody.poishelf.data.remote.model.HobbyAlbumItem
import com.nobody.poishelf.data.remote.model.HobbyDetailData
import com.nobody.poishelf.data.remote.model.HobbyItemSummary
import com.nobody.poishelf.data.remote.model.InfoListEntry
import com.nobody.poishelf.data.remote.model.InfoListTag
import com.nobody.poishelf.data.remote.model.PicItem
import com.nobody.poishelf.ui.detail.components.HobbyTimelineSection
import com.nobody.poishelf.ui.common.DetailTopNavigation
import com.nobody.poishelf.ui.common.ExpressiveFloatingToolbar
import com.nobody.poishelf.ui.common.ExpressiveToolbarButton
import com.nobody.poishelf.ui.common.LocalImageViewer
import com.nobody.poishelf.ui.common.plainTextClipEntry
import com.nobody.poishelf.ui.common.comment.AdaptiveDetailLazyColumnCommentScaffold
import com.nobody.poishelf.ui.common.comment.CommentFeedState
import com.nobody.poishelf.ui.common.comment.CommentSectionInteraction
import com.nobody.poishelf.ui.comment.CommentDetailNavParam
import com.nobody.poishelf.ui.comment.HobbyCommentsNavParam
import com.nobody.poishelf.ui.home.components.shimmerBackground
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.ui.theme.LocalIsDarkTheme
import com.nobody.poishelf.util.CdnUrlUtils
import com.nobody.poishelf.util.ImageAspectRatioCache
import com.nobody.poishelf.ui.navigation.CollectEditNavParam
import com.nobody.poishelf.ui.navigation.ExHobbyListNavKey
import com.nobody.poishelf.ui.exhobby.ExHobbyPreviewSection
import com.nobody.poishelf.ui.vendor.VendorNavParam
import com.nobody.poishelf.ui.series.SeriesNavParam
import com.nobody.poishelf.ui.person.PersonNavParam
import com.nobody.poishelf.ui.works.WorksNavParam
import com.nobody.poishelf.ui.navigation.CharacterNavParam
import com.nobody.poishelf.ui.item.list.HobbyListNavParam
import com.nobody.poishelf.ui.navigation.PictureListNavParam
import com.nobody.poishelf.ui.navigation.HobbyAlbumListNavParam

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun HobbyDetailScreen(
    navParam: DetailNavParam,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    onNavigateToHobby: ((DetailNavParam) -> Unit)? = null,
    onNavigateToAlbum: ((Long) -> Unit)? = null,
    onNavigateToAlbumList: ((HobbyAlbumListNavParam) -> Unit)? = null,
    onNavigateToVendor: ((VendorNavParam) -> Unit)? = null,
    onNavigateToSeries: ((SeriesNavParam) -> Unit)? = null,
    onNavigateToPerson: ((PersonNavParam) -> Unit)? = null,
    onNavigateToWorks: ((WorksNavParam) -> Unit)? = null,
    onNavigateToCharacter: ((CharacterNavParam) -> Unit)? = null,
    onNavigateToHobbyList: ((HobbyListNavParam) -> Unit)? = null,
    onNavigateToCollect: ((CollectEditNavParam) -> Unit)? = null,
    onNavigateToCommentDetail: ((CommentDetailNavParam) -> Unit)? = null,
    onNavigateToHobbyComments: ((HobbyCommentsNavParam) -> Unit)? = null,
    onNavigateToPictureList: ((PictureListNavParam) -> Unit)? = null,
    onNavigateToExHobbyList: ((ExHobbyListNavKey) -> Unit)? = null,
    viewModel: HobbyDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current
    val messages = LocalAppMessages.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    // 页面初始化拉取数据
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

    val imageViewer = LocalImageViewer.current

    if (uiState.errorMessage != null && uiState.detail == null) {
        DetailErrorState(
            errorMessage = uiState.errorMessage ?: "加载失败",
            onRetry = { viewModel.loadDetail(navParam) }
        )
    } else {
        val detail = uiState.detail
        val initialSkeleton = uiState.isInitialLoading && detail == null
        val heroImages = buildList {
            val cover = detail?.cover ?: navParam.initialCover
            if (!cover.isNullOrBlank()) {
                add(CdnUrlUtils.getRawCoverUrl(cover))
            }
            uiState.officialPics.take(8).forEach { pic ->
                val url = pic.imagePath?.let { CdnUrlUtils.getRawPicUrl(it) }
                if (!url.isNullOrBlank() && !contains(url)) {
                    add(url)
                }
            }
        }

        val makerCompanyId = uiState.makerCompanyId
        val relateNodeId = uiState.detail?.actualNodeId ?: uiState.itemId
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

        val commentFeedState = remember(uiState, detail, initialSkeleton) {
            CommentFeedState(
                totalCommentCount = detail?.commentCount ?: (uiState.hotComments.size + uiState.latestComments.size),
                hotComments = uiState.hotComments,
                latestComments = uiState.latestComments,
                isCommentsLoading = uiState.isCommentsLoading || initialSkeleton,
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
            relateNodeId = relateNodeId,
            modifier = modifier,
            topBar = {
                DetailTopNavigation(
                    isScrolled = isScrolledPastHeader,
                    title = uiState.detail?.displayName?.ifBlank { null } ?: navParam.initialName ?: "手办详情",
                    subtitle = uiState.detail?.name?.takeIf { it.isNotBlank() && it != (uiState.detail?.displayName?.ifBlank { null } ?: navParam.initialName) } ?: uiState.makerName,
                    onBack = onBack,
                    showBackButton = showBackButton,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            floatingToolbar = {
                if (uiState.detail != null) {
                    HobbyFloatingActionBar(
                        detail = uiState.detail,
                        isFavorite = uiState.isFavorite,
                        collectDelta = uiState.collectCountDelta,
                        commentCount = uiState.detail?.commentCount ?: 0,
                        showCommentButton = !isWidePane,
                        onToggleFavorite = {
                            val curDetail = uiState.detail
                            val nodeId = curDetail?.actualNodeId ?: 0L
                            if (nodeId <= 0L) return@HobbyFloatingActionBar
                            val name = curDetail?.name ?: uiState.initialName
                            val cover = curDetail?.cover ?: uiState.initialCover
                            onNavigateToCollect?.invoke(
                                CollectEditNavParam(
                                    itemId = uiState.itemId,
                                    nodeId = nodeId,
                                    initialName = name,
                                    initialCover = cover
                                )
                            )
                        },
                        onCommentListClick = {
                            val curDetail = uiState.detail
                            val nodeId = curDetail?.actualNodeId ?: uiState.itemId
                            if (nodeId <= 0L) return@HobbyFloatingActionBar
                            onNavigateToHobbyComments?.invoke(
                                HobbyCommentsNavParam(
                                    nodeId = nodeId,
                                    itemType = curDetail?.itemType?.ifBlank { null } ?: uiState.itemType,
                                    title = curDetail?.displayName?.ifBlank { null } ?: "评论",
                                    initialCommentCount = curDetail?.commentCount ?: 0
                                )
                            )
                        }
                    )
                }
            }
        ) {

                    // ── 区块 0：官方经典双列 Header + 官图缩略预览栏（上下文由屏级下传）──
                    item(key = "section_hero") {
                        HobbyHeaderOfficialSection(
                            detail = detail,
                            initialName = navParam.initialName,
                            initialCover = navParam.initialCover,
                            officialPics = uiState.officialPics,
                            isDark = isDark,
                            makerName = uiState.makerName,
                            makerCover = uiState.makerCover,
                            onCoverClick = {
                                if (heroImages.isNotEmpty()) {
                                    imageViewer.show(heroImages, 0)
                                }
                            },
                            onPicClick = { index ->
                                val allPicUrls = uiState.officialPics.mapNotNull { it.imagePath?.let { p -> CdnUrlUtils.getRawPicUrl(p) } }
                                if (allPicUrls.isNotEmpty()) {
                                    imageViewer.show(allPicUrls, index)
                                }
                            },
                            onMakerClick = if (onNavigateToVendor != null && makerCompanyId > 0L) {
                                {
                                    onNavigateToVendor(
                                        VendorNavParam(
                                            companyId = makerCompanyId,
                                            initialName = uiState.makerName
                                        )
                                    )
                                }
                            } else null,
                            isPicsLoading = uiState.isOfficialPicsLoading || initialSkeleton,
                            onMoreClick = if (onNavigateToPictureList != null) {
                                {
                                    onNavigateToPictureList(
                                        PictureListNavParam(
                                            itemId = navParam.itemId,
                                            itemType = "hobby",
                                            subType = "official",
                                            title = "官图全集"
                                        )
                                    )
                                }
                            } else {
                                {
                                    messages.show("官图全集正在开发中")
                                }
                            }
                        )
                    }

                    // ── 区块 1：全量规格参数流 + 特典介绍 ──
                    item(key = "section_specs") {
                        HobbySpecsSection(
                            detail = detail,
                            infoList = uiState.infoList,
                            isLoading = uiState.isInfoListLoading || initialSkeleton,
                            isDark = isDark,
                            onNavigateToHobbyList = onNavigateToHobbyList,
                            onNavigateToHobby = onNavigateToHobby,
                            onNavigateToAlbum = onNavigateToAlbum,
                            onNavigateToCharacter = onNavigateToCharacter,
                            onEntryClick = { entry ->
                                if (entry.link?.startsWith("company/") == true || entry.displayKey == "制作") {
                                    val companyId = entry.link?.removePrefix("company/")?.substringBefore("?")?.substringBefore("/")?.toLongOrNull() ?: makerCompanyId
                                    if (onNavigateToVendor != null && companyId > 0L) {
                                        onNavigateToVendor(
                                            VendorNavParam(
                                                companyId = companyId,
                                                initialName = entry.displayValue
                                            )
                                        )
                                    } else {
                                        coroutineScope.launch { clipboard.setClipEntry(plainTextClipEntry(entry.displayValue)) }
                                        messages.show("已复制 ${entry.displayKey}：${entry.displayValue}")
                                    }
                                } else if (entry.link?.startsWith("series/") == true || entry.displayKey == "系列") {
                                    val seriesId = entry.link?.removePrefix("series/")?.substringBefore("?")?.substringBefore("/")?.toLongOrNull() ?: 0L
                                    if (onNavigateToSeries != null && seriesId > 0L) {
                                        onNavigateToSeries(
                                            SeriesNavParam(
                                                seriesId = seriesId,
                                                initialName = entry.displayValue
                                            )
                                        )
                                    } else {
                                        coroutineScope.launch { clipboard.setClipEntry(plainTextClipEntry(entry.displayValue)) }
                                        messages.show("已复制 ${entry.displayKey}：${entry.displayValue}")
                                    }
                                } else if (entry.link?.startsWith("person/") == true) {
                                    val personId = entry.link?.removePrefix("person/")?.substringBefore("?")?.substringBefore("/")?.toLongOrNull() ?: 0L
                                    if (onNavigateToPerson != null && personId > 0L) {
                                        onNavigateToPerson(
                                            PersonNavParam(
                                                personId = personId,
                                                initialName = entry.displayValue
                                            )
                                        )
                                    } else {
                                        coroutineScope.launch { clipboard.setClipEntry(plainTextClipEntry(entry.displayValue)) }
                                        messages.show("已复制 ${entry.displayKey}：${entry.displayValue}")
                                    }
                                } else if (entry.link?.startsWith("works/") == true) {
                                    val worksId = entry.link?.removePrefix("works/")?.substringBefore("?")?.substringBefore("/")?.toLongOrNull() ?: 0L
                                    if (onNavigateToWorks != null && worksId > 0L) {
                                        onNavigateToWorks(
                                            WorksNavParam(
                                                worksId = worksId,
                                                initialName = entry.displayValue
                                            )
                                        )
                                    } else {
                                        coroutineScope.launch { clipboard.setClipEntry(plainTextClipEntry(entry.displayValue)) }
                                        messages.show("已复制 ${entry.displayKey}：${entry.displayValue}")
                                    }
                                } else if (entry.link?.startsWith("charactar/") == true || entry.link?.startsWith("character/") == true || entry.displayKey == "角色") {
                                    val charId = entry.link?.removePrefix("charactar/")?.removePrefix("character/")
                                        ?.substringBefore("?")?.substringBefore("/")?.toLongOrNull() ?: 0L
                                    if (onNavigateToCharacter != null && charId > 0L) {
                                        onNavigateToCharacter(
                                            CharacterNavParam(
                                                characterId = charId,
                                                initialName = entry.displayValue
                                            )
                                        )
                                    } else {
                                        coroutineScope.launch { clipboard.setClipEntry(plainTextClipEntry(entry.displayValue)) }
                                        messages.show("已复制 ${entry.displayKey}：${entry.displayValue}")
                                    }
                                } else if (HobbyListNavParam.isHobbyListLink(entry.link)) {
                                    HobbyListNavParam.fromHobbyLink(entry.link ?: "", "周边列表")?.let {
                                        onNavigateToHobbyList?.invoke(it)
                                    }
                                }
                            }
                        )
                    }

                    // ── 区块 1.5：发售情报时间轴 ──
                    if (uiState.timeline.isNotEmpty()) {
                        item(key = "section_timeline") {
                            HobbyTimelineSection(
                                timeline = uiState.timeline
                            )
                        }
                    }

                    // ── 区块 2：玩家相册与随手拍（上下文由屏级下传）──
                    if (uiState.userAlbums.isNotEmpty() || uiState.isUserAlbumsLoading || initialSkeleton) {
                        item(key = "section_user_albums") {
                            HobbyUserAlbumsSection(
                                albums = uiState.userAlbums,
                                isLoading = uiState.isUserAlbumsLoading || initialSkeleton,
                                isDark = isDark,
                                onAlbumClick = { album ->
                                    if (onNavigateToAlbum != null && album.id > 0) {
                                        onNavigateToAlbum(album.id)
                                    } else {
                                        val coverUrl = CdnUrlUtils.getRawCoverUrl(album.cover)
                                        if (coverUrl.isNotBlank()) {
                                            imageViewer.show(coverUrl)
                                        }
                                    }
                                },
                                onViewMore = {
                                    val targetNodeId = (detail?.actualNodeId ?: 0L).takeIf { it > 0L } ?: navParam.itemId
                                    if (targetNodeId > 0L && onNavigateToAlbumList != null) {
                                        onNavigateToAlbumList(
                                            HobbyAlbumListNavParam(
                                                nodeId = targetNodeId,
                                                itemType = "hobby",
                                                title = "关联相册"
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }

                    if (uiState.isExHobbyEnabled && uiState.exHobbyPics.isNotEmpty()) {
                        item(key = "section_exhobby") {
                            val targetId = if ((detail?.actualNodeId ?: 0L) > 0L) detail!!.actualNodeId else navParam.itemId
                            ExHobbyPreviewSection(
                                pics = uiState.exHobbyPics,
                                onPicClick = { index: Int ->
                                    val allFullUrls = uiState.exHobbyPics.map { CdnUrlUtils.getExHobbyFullUrl(it.path) }
                                    if (allFullUrls.isNotEmpty()) {
                                        imageViewer.show(allFullUrls, index)
                                    }
                                },
                                onMoreClick = {
                                    if (onNavigateToExHobbyList != null) {
                                        onNavigateToExHobbyList(
                                            ExHobbyListNavKey(itemId = targetId, title = "ExHobby")
                                        )
                                    }
                                }
                            )
                        }
                    }

                    // ── 区块 2.8：相关手办推荐（同款/再版 + 推荐）──
                    if (uiState.relatedHobbies.isNotEmpty()) {
                        item(key = "section_related_hobbies") {
                            HobbyRelatedHobbiesSection(
                                hobbies = uiState.relatedHobbies,
                                isDark = isDark,
                                onHobbyClick = { item ->
                                    val targetId = item.actualItemId
                                    if (targetId > 0L) {
                                        onNavigateToHobby?.invoke(
                                            DetailNavParam(
                                                itemId = targetId,
                                                initialName = item.displayName,
                                                initialCover = item.cover
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }

        }
    }
}

// ─────────────────────────────────────────────────────────────
// 顶部官方经典双列 Header + 官图缩略预览栏 (HobbyHeaderOfficialSection)
// ─────────────────────────────────────────────────────────────

@Composable
private fun HobbyHeaderOfficialSection(
    detail: HobbyDetailData?,
    initialName: String?,
    initialCover: String?,
    officialPics: List<PicItem>,
    isDark: Boolean,
    makerName: String? = null,
    makerCover: String? = null,
    onCoverClick: () -> Unit,
    onPicClick: (Int) -> Unit,
    onMoreClick: () -> Unit,
    onMakerClick: (() -> Unit)? = null,
    isPicsLoading: Boolean = false
) {
    val cover = detail?.cover?.ifBlank { null } ?: initialCover?.ifBlank { null }
    val coverUrl = cover?.let { CdnUrlUtils.getCoverUrl(it) } ?: ""

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 1. 底层封面图高斯模糊背景 (Atmospheric Frosted Backdrop)
        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .matchParentSize()
                    .blur(28.dp)
            )
            // 渐变蒙层强化对比度与深浅色适配
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                if (isDark) Color(0xFF0D0E12).copy(alpha = 0.72f)
                                else Color(0xFFF5F6FA).copy(alpha = 0.68f),
                                if (isDark) Color(0xFF0D0E12).copy(alpha = 0.90f)
                                else Color(0xFFF5F6FA).copy(alpha = 0.88f),
                                if (isDark) Color(0xFF0D0E12)
                                else Color(0xFFF5F6FA)
                            )
                        )
                    )
            )
        }

        // 2. 前景内容区 (留出状态栏与悬浮返回按钮空间)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 64.dp, bottom = 10.dp)
        ) {
            // 上半部：左右双列卡片（左侧动态自适应全景封面，右侧品名与属性品牌）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 左侧封面卡片 (9:13 黄金手办封面比例，与列表网格卡片严格统一)
                Box(
                    modifier = Modifier
                        .width(135.dp)
                        .aspectRatio(9f / 13f)
                        .clip(ExpressiveTokens.CardShape)
                        .background(
                            if (isDark) Color.White.copy(alpha = 0.06f)
                            else Color.Black.copy(alpha = 0.04f)
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    if (isDark) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.85f),
                                    if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
                                )
                            ),
                            shape = ExpressiveTokens.CardShape
                        )
                        .clickable(onClick = onCoverClick)
                ) {
                    if (coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = "手办完整封面",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ShimmerHost {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shimmerBackground()
                            )
                        }
                    }
                }

                // 右侧关键信息面板
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 中文品名 / 主标题
                    val title = detail?.displayName?.ifBlank { null } ?: initialName ?: "手办详情"
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 22.sp
                    )

                    // 外文/日文原名 (如果有且不同)
                    val subName = detail?.name
                    if (!subName.isNullOrBlank() && subName != title) {
                        Text(
                            text = subName,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 15.sp
                        )
                    }

                    if (!makerName.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val logoUrl = makerCover?.let { CdnUrlUtils.getRawCoverUrl(it) }.orEmpty()
                            if (logoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = logoUrl,
                                    contentDescription = makerName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                            Text(
                                text = makerName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.then(
                                    if (onMakerClick != null) Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onMakerClick() }
                                    else Modifier
                                )
                            )
                        }
                    }

                    // 星级评分与评分人数
                    val score = detail?.ratingScore ?: 0.0
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RatingStars(score = score)
                        Text(
                            text = if (score > 0) String.format("%.1f", score) else "--",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        if (!detail?.ratingCountText.isNullOrBlank()) {
                            Text(
                                text = detail?.ratingCountText ?: "",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // ── 补齐：官方定价与发售日胶囊标签栏 (对齐原版 hobby_price / hobby_release) ──
                    val priceText = detail?.displayPriceText?.ifBlank { null }
                    val releaseText = detail?.displayReleaseDateText?.ifBlank { null }
                    if (priceText != null || releaseText != null) {
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (priceText != null) {
                                HeaderBadgeCapsule(text = priceText, isPrimary = true, isDark = isDark)
                            }
                            if (releaseText != null) {
                                HeaderBadgeCapsule(text = releaseText, isPrimary = false, isDark = isDark)
                            }
                        }
                    }
                }
            }

            // 下半部：官图横滑栏 (0 张整排消失；1~9 张全铺；超 9 张前 9 加第 10 格更多按钮)
            if (officialPics.isNotEmpty() || isPicsLoading) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (officialPics.isNotEmpty()) {
                            Text(
                                text = "官图",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            ShimmerHost {
                                Box(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .height(15.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .shimmerBackground()
                                )
                            }
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (officialPics.isEmpty()) {
                            // 官图骨架按固定类型复用，避免与正式官图条目混用复用池
                            items(
                                4,
                                key = { index -> "official_skeleton_$index" },
                                contentType = { "official_skeleton" }
                            ) {
                                ShimmerHost {
                                    Box(
                                        modifier = Modifier
                                            .size(130.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .shimmerBackground()
                                    )
                                }
                            }
                        } else {
                        // 官图按图片类型复用，键包含图片标识与下标防止重复
                        itemsIndexed(
                            officialPics.take(9),
                            key = { index, pic ->
                                val picKey = if (pic.id != 0L) "${pic.id}" else "${pic.imagePath}"
                                "official_${picKey}_$index"
                            },
                            contentType = { _, _ -> "official_pic" }
                        ) { index, pic ->
                            val url = pic.imagePath?.let { CdnUrlUtils.getPicSmallUrl(it) } ?: ""
                            Card(
                                onClick = { onPicClick(index) },
                                modifier = Modifier.size(130.dp),
                                shape = ExpressiveTokens.MediumCardShape,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalPlatformContext.current)
                                        .data(url)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "官方定妆照 $index",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        if (officialPics.size > 9) {
                            item(key = "official_pics_more") {
                                OfficialPicsMoreTile(
                                    onMoreClick = onMoreClick
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

@Composable
private fun OfficialPicsMoreTile(
    onMoreClick: () -> Unit
) {
    Surface(
        onClick = onMoreClick,
        modifier = Modifier.size(130.dp),
        shape = ExpressiveTokens.MediumCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "查看更多",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 全量规格参数与版本历程流 (HobbySpecsSection)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HobbySpecsSection(
    detail: HobbyDetailData?,
    infoList: List<InfoListEntry>,
    isLoading: Boolean,
    isDark: Boolean,
    onEntryClick: ((InfoListEntry) -> Unit)? = null,
    onNavigateToHobbyList: ((HobbyListNavParam) -> Unit)? = null,
    onNavigateToHobby: ((DetailNavParam) -> Unit)? = null,
    onNavigateToAlbum: ((Long) -> Unit)? = null,
    onNavigateToCharacter: ((CharacterNavParam) -> Unit)? = null
) {
    val clipboard = LocalClipboard.current
    val messages = LocalAppMessages.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    // 过滤有效条目（排除 key 和 value 都为空的）
    val validEntries = remember(infoList) {
        infoList.filter { it.displayKey.isNotBlank() && (it.displayValue.isNotBlank() || it.tags.isNotEmpty()) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 规格信息主卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = ExpressiveTokens.MediumCardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (validEntries.isNotEmpty()) {
                    // 全量动态参数键值流
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        validEntries.forEach { entry ->
                            SpecEntryRow(
                                entry = entry,
                                isDark = isDark,
                                onEntryClick = onEntryClick,
                                onNavigateToHobbyList = onNavigateToHobbyList,
                                onCopy = { text ->
                                    scope.launch { clipboard.setClipEntry(plainTextClipEntry(text)) }
                                    messages.show("已复制 ${entry.displayKey}：$text")
                                }
                            )
                        }
                    }
                } else if (!isLoading) {
                    // Fallback: 若 infoList 为空，使用 detail 中的字段兜底渲染
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val fallbackPrice = detail?.displayPriceText?.ifBlank { null }
                        fallbackPrice?.let { SpecFallbackRow("定价", it, isDark) }
                        val fallbackRelease = detail?.displayReleaseDateText?.ifBlank { null } ?: detail?.releaseDate
                        fallbackRelease?.let { SpecFallbackRow("发售日", it, isDark) }
                        detail?.scale?.let { SpecFallbackRow("比例", it, isDark) }
                        detail?.height?.let { SpecFallbackRow("尺寸", it, isDark) }
                        detail?.maker?.let {
                            SpecFallbackRow("制作", it, isDark, isHighlight = true) {
                                scope.launch { clipboard.setClipEntry(plainTextClipEntry(it)) }
                                messages.show("已复制制作商：$it")
                            }
                        }
                    }
                } else {
                    // 加载占位
                    ShimmerHost {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .shimmerBackground()
                        )
                    }
                }
            }
        }

        // 版本说明与特典介绍 (富文本卡片)
        val rawHtml = detail?.detail?.ifBlank { null }
        val cleanText = detail?.detailPlainText?.ifBlank { null }
        val hasDetailContent = !cleanText.isNullOrBlank()

        if (hasDetailContent) {
            val linkColor = MaterialTheme.colorScheme.primary
            val richText = remember(rawHtml, linkColor, onNavigateToHobby, onNavigateToAlbum, onNavigateToCharacter) {
                if (!rawHtml.isNullOrBlank()) {
                    HtmlRichTextParser.parse(
                        html = rawHtml,
                        linkColor = linkColor,
                        onLinkClick = { url ->
                            HpoiLinkDispatcher.dispatch(
                                url = url,
                                uriHandler = uriHandler,
                                onMessage = { messages.show(it) },
                                onNavigateToHobbyDetail = onNavigateToHobby,
                                onNavigateToAlbum = onNavigateToAlbum,
                                onNavigateToCharacter = onNavigateToCharacter
                            )
                        }
                    )
                } else null
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = ExpressiveTokens.MediumCardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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

private fun Modifier.noRippleClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    enabled = enabled,
    onClick = onClick
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpecEntryRow(
    entry: InfoListEntry,
    isDark: Boolean,
    onCopy: (String) -> Unit,
    onEntryClick: ((InfoListEntry) -> Unit)? = null,
    onNavigateToHobbyList: ((HobbyListNavParam) -> Unit)? = null
) {
    val isCompanyEntry = entry.link?.startsWith("company/") == true || entry.displayKey == "制作"
    val isSeriesEntry = entry.link?.startsWith("series/") == true || entry.displayKey == "系列"
    val isPersonEntry = entry.link?.startsWith("person/") == true
    val isWorksEntry = entry.link?.startsWith("works/") == true
    val isCharacterEntry = entry.link?.startsWith("charactar/") == true ||
        entry.link?.startsWith("character/") == true ||
        entry.displayKey == "角色"
    val isNavigatableEntry = isCompanyEntry || isSeriesEntry || isPersonEntry || isWorksEntry || isCharacterEntry
    val hobbyListParam = entry.link?.takeIf { HobbyListNavParam.isHobbyListLink(it) }
        ?.let { HobbyListNavParam.fromHobbyLink(it, "周边列表") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // 左侧 Key: 固定 74dp 宽度，次级颜色
        Text(
            text = entry.displayKey,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(74.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 右侧 Value: 根据不同类型呈现
        Box(modifier = Modifier.weight(1f)) {
            when {
                entry.isTagGroup && entry.tags.isNotEmpty() -> {
                    val tagItems = entry.tagLinks.ifEmpty { entry.tags.map { InfoListTag(value = it) } }
                    FlowRow {
                        tagItems.forEachIndexed { index, tag ->
                            val tagText = tag.value?.trim().orEmpty()
                            if (tagText.isBlank()) return@forEachIndexed
                            val tagLink = tag.link
                            val isTagNavigatable = tagLink?.let {
                                it.startsWith("company/") || it.startsWith("series/") ||
                                it.startsWith("person/") || it.startsWith("works/") ||
                                it.startsWith("charactar/") || it.startsWith("character/")
                            } == true
                            val tagListParam = tagLink?.takeIf { HobbyListNavParam.isHobbyListLink(it) }
                                ?.let { HobbyListNavParam.fromHobbyLink(it, "周边列表") }

                            Text(
                                text = tagText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                lineHeight = 18.sp,
                                modifier = Modifier.noRippleClickable {
                                    if (isTagNavigatable && onEntryClick != null) {
                                        onEntryClick(
                                            InfoListEntry(
                                                type = 2,
                                                key = entry.displayKey,
                                                title = entry.displayKey,
                                                link = tagLink,
                                                value = kotlinx.serialization.json.JsonPrimitive(tagText)
                                            )
                                        )
                                    } else if (tagListParam != null && onNavigateToHobbyList != null) {
                                        onNavigateToHobbyList(tagListParam)
                                    } else {
                                        onCopy(tagText)
                                    }
                                }
                            )
                            if (index < tagItems.lastIndex) {
                                Text(
                                    text = "、",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // 2. 多行文本（发售历史、再版历程等）
                entry.lines.size > 1 -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        entry.lines.forEach { line ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.noRippleClickable { onCopy(line) }
                            ) {
                                Text(
                                    text = "•",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = line,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // 3. 可点击/跳转实体 (制作商、系列、原型、原画、角色、作品等)
                entry.isNavigatable || entry.type == 2 -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(ExpressiveTokens.SmallCardShape)
                            .noRippleClickable {
                                if (isNavigatableEntry && onEntryClick != null) {
                                    onEntryClick(entry)
                                } else if (hobbyListParam != null && onEntryClick != null) {
                                    onEntryClick(entry)
                                } else if (hobbyListParam != null && onNavigateToHobbyList != null) {
                                    onNavigateToHobbyList(hobbyListParam)
                                } else {
                                    onCopy(entry.displayValue)
                                }
                            }
                    ) {
                        Text(
                            text = entry.displayValue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 18.sp
                        )
                    }
                }

                // 4. 标准文本 (定价、尺寸、比例、材质等)
                else -> {
                    Text(
                        text = entry.displayValue,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp,
                        modifier = Modifier.noRippleClickable { onCopy(entry.displayValue) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecFallbackRow(
    label: String,
    value: String,
    isDark: Boolean,
    isHighlight: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(74.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = if (isHighlight) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isHighlight) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .weight(1f)
                .noRippleClickable(enabled = onClick != null) { onClick?.invoke() }
        )
    }
}

/**
 * 手办头部关键信息胶囊徽章（对齐原版 Hpoi hobby_price / hobby_release 的胶囊设计）
 */
@Composable
private fun HeaderBadgeCapsule(
    text: String,
    isDark: Boolean,
    isPrimary: Boolean = false
) {
    Surface(
        shape = ExpressiveTokens.PillShape,
        color = if (isPrimary) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.45f else 0.85f)
        } else {
            if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
        },
        border = BorderStroke(
            0.5.dp,
            if (isPrimary) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            } else {
                if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.12f)
            }
        )
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 相关手办推荐卡片流 (HobbyRelatedHobbiesSection)
// ─────────────────────────────────────────────────────────────

@Composable
private fun HobbyRelatedHobbiesSection(
    hobbies: List<HobbyItemSummary>,
    isDark: Boolean,
    onHobbyClick: (HobbyItemSummary) -> Unit
) {
    if (hobbies.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "相关手办",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = hobbies,
                key = { item -> "${item.actualItemId}_${item.typeName ?: ""}" }
            ) { item ->
                HobbyRelatedHobbyCard(
                    item = item,
                    isDark = isDark,
                    onClick = { onHobbyClick(item) }
                )
            }
        }
    }
}

@Composable
private fun HobbyRelatedHobbyCard(
    item: HobbyItemSummary,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val coverUrl = CdnUrlUtils.getCoverUrl(item.cover)
    var loadFailed by remember(item.cover) { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier.width(136.dp),
        shape = ExpressiveTokens.MediumCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 手办封面（严格遵循 9:13 黄金比例）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 13f)
                    .clip(ExpressiveTokens.SmallCardShape)
                    .background(if (isDark) Color(0xFF22242E) else Color(0xFFEBEDF2)),
                contentAlignment = Alignment.Center
            ) {
                if (coverUrl.isNotBlank() && !loadFailed) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onError = { loadFailed = true }
                    )
                } else if (coverUrl.isBlank()) {
                    Text(
                        text = "暂无封面",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }

                // 分组标签角标（例如“再版”、“同款”、“异色”）
                if (!item.typeName.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                shape = ExpressiveTokens.PillShape
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.typeName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1
                        )
                    }
                }
            }

            // 中文品名（展示两行，固定最小两行高度保证底栏对齐）
            Text(
                text = item.displayName,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // 价格与发售时间
            val priceText = item.displayPriceText
            val releaseDate = item.displayReleaseDateText
            if (priceText.isNotBlank() || releaseDate.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (priceText.isNotBlank()) {
                        Text(
                            text = priceText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (releaseDate.isNotBlank()) {
                        Text(
                            text = releaseDate,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 玩家摄影相册卡片流 (HobbyUserAlbumsSection)
// ─────────────────────────────────────────────────────────────

@Composable
private fun HobbyUserAlbumsSection(
    albums: List<HobbyAlbumItem>,
    isLoading: Boolean,
    isDark: Boolean,
    onAlbumClick: (HobbyAlbumItem) -> Unit,
    onViewMore: () -> Unit
) {
    val displayAlbums = remember(albums) { albums.take(8) }
    val hasMore = albums.size > 8

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "相册",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (isLoading && albums.isEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(4) { index ->
                    val shimmerWidth = when (index % 3) {
                        0 -> 140.dp
                        1 -> 160.dp
                        else -> 120.dp
                    }
                    ShimmerHost {
                        Box(
                            modifier = Modifier
                                .height(160.dp)
                                .width(shimmerWidth)
                                .clip(ExpressiveTokens.MediumCardShape)
                                .shimmerBackground()
                        )
                    }
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = displayAlbums,
                    key = { it.id }
                ) { album ->
                    HobbyUserAlbumCard(
                        album = album,
                        onClick = { onAlbumClick(album) }
                    )
                }

                if (hasMore) {
                    item(key = "hobby_album_more_tile") {
                        HobbyAlbumMoreTile(
                            onMoreClick = onViewMore,
                            modifier = Modifier.size(160.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 手办相关相册「查看更多」卡片（等高且正方形比例，样式与 ExHobby 及角色相册保持一致）。
 */
@Composable
private fun HobbyAlbumMoreTile(
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onMoreClick,
        modifier = modifier,
        shape = ExpressiveTokens.MediumCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "查看更多",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun HobbyUserAlbumCard(
    album: HobbyAlbumItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = remember(album.cover) {
        album.cover?.let { CdnUrlUtils.getCoverSrUrl(it) }.orEmpty()
    }
    val ratioKey = remember(coverUrl) { "album_cover:$coverUrl" }
    val initialRatio = ImageAspectRatioCache[ratioKey] ?: 1.0f
    var aspectRatio by remember(ratioKey) { mutableFloatStateOf(initialRatio) }

    Card(
        onClick = onClick,
        modifier = modifier.height(160.dp),
        shape = ExpressiveTokens.MediumCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Box(
            modifier = Modifier
                .height(160.dp)
                .aspectRatio(aspectRatio)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = album.displayName,
                    contentScale = ContentScale.Crop,
                    onSuccess = { state ->
                        val size = state.painter.intrinsicSize
                        if (size.width > 0 && size.height > 0) {
                            val ratio = (size.width / size.height).coerceIn(0.6f, 2.2f)
                            aspectRatio = ratio
                            ImageAspectRatioCache[ratioKey] = ratio
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // 右上角半透明浮层点赞徽标（与相册列表页及角色详情页完全一致的 ExtraSmallShape 与 padding）
            val praiseCount = album.praiseCount ?: 0L
            if (praiseCount > 0L) {
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                    shape = ExpressiveTokens.ExtraSmallShape,
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
// 底部悬浮液态玻璃操作胶囊 (HobbyFloatingActionBar)
// ─────────────────────────────────────────────────────────────

@Composable
private fun HobbyFloatingActionBar(
    detail: HobbyDetailData?,
    isFavorite: Boolean,
    collectDelta: Int,
    commentCount: Int,
    showCommentButton: Boolean,
    onToggleFavorite: () -> Unit,
    onCommentListClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseCollect = detail?.collect ?: 0
    val displayCollect = (baseCollect + collectDelta).coerceAtLeast(0)
    val displayComment = commentCount.coerceAtLeast(0)

    ExpressiveFloatingToolbar(modifier = modifier) {
        // 1. 【关注收藏管理】只显示数字，无数字时只显示图标
        ExpressiveToolbarButton(
            onClick = onToggleFavorite,
            text = if (displayCollect > 0) "$displayCollect" else null,
            icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isFavorite) "已收藏" else "收藏",
            isProminent = isFavorite,
            containerColor = if (isFavorite) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (isFavorite) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            iconTint = if (isFavorite) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 2. 【评论】仅在小屏下展示独立全屏评论页入口，统一显示数字，没有则显示0；大屏右侧常驻评论区无需此按钮
        if (showCommentButton) {
            ExpressiveToolbarButton(
                onClick = onCommentListClick,
                text = "$displayComment",
                icon = Icons.AutoMirrored.Filled.Comment,
                contentDescription = "评论",
                isProminent = !isFavorite
            )
        }
    }
}


// ─────────────────────────────────────────────────────────────
// 五星评分组件
// ─────────────────────────────────────────────────────────────

@Composable
private fun RatingStars(score: Double) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..5) {
            val filled = score >= i
            Icon(
                imageVector = if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = if (filled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
            )
        }
    }
    }

// ─────────────────────────────────────────────────────────────
// 错误重试界面
// ─────────────────────────────────────────────────────────────

@Composable
private fun DetailErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Text(
                text = "条目加载失败",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onRetry,
                shape = ExpressiveTokens.ButtonShape
            ) {
                Text("点击重新加载")
            }
        }
    }
}
