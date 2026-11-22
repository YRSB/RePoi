package com.nobody.repoi.ui.detail

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
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import com.nobody.repoi.util.HtmlRichTextParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.repoi.platform.LocalAppMessages
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.remote.model.ExHobbyImageItem
import com.nobody.repoi.data.remote.model.HobbyAlbumItem
import com.nobody.repoi.ui.album.components.AlbumTextCover
import com.nobody.repoi.ui.common.PoiAsyncImage
import com.nobody.repoi.data.remote.model.HobbyDetailData
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.InfoListEntry
import com.nobody.repoi.data.remote.model.InfoListTag
import com.nobody.repoi.data.remote.model.PicItem
import com.nobody.repoi.ui.detail.components.HobbyTimelineSection
import com.nobody.repoi.ui.common.DetailTopNavigation
import com.nobody.repoi.ui.common.ExpressiveFloatingToolbar
import com.nobody.repoi.ui.common.ExpressiveToolbarButton
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.plainTextClipEntry
import com.nobody.repoi.ui.common.comment.AdaptiveDetailLazyColumnCommentScaffold
import com.nobody.repoi.ui.common.comment.CommentFeedState
import com.nobody.repoi.ui.common.comment.CommentSectionInteraction
import com.nobody.repoi.ui.comment.CommentDetailNavParam
import com.nobody.repoi.ui.comment.HobbyCommentsNavParam
import com.nobody.repoi.ui.theme.LocalIsDarkTheme
import com.nobody.repoi.ui.navigation.CollectEditNavParam
import com.nobody.repoi.ui.navigation.ExHobbyListNavKey
import com.nobody.repoi.ui.exhobby.ExHobbyPreviewSection
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.works.WorksNavParam
import com.nobody.repoi.ui.navigation.CharacterNavParam
import com.nobody.repoi.ui.item.list.HobbyListNavParam
import com.nobody.repoi.ui.navigation.PictureListNavParam
import com.nobody.repoi.ui.navigation.HobbyAlbumListNavParam
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

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
    onUrlClick: ((String) -> Unit)? = null,
    viewModel: HobbyDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current
    val messages = LocalAppMessages.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

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

    val imageViewer = LocalImageViewer.current

    if (uiState.errorMessage != null && uiState.detail == null) {
        DetailErrorState(
            errorMessage = uiState.errorMessage ?: "加载失败",
            onRetry = { viewModel.loadDetail(navParam) }
        )
    } else {
        val detail = uiState.detail
        val isInitialLoading = uiState.isInitialLoading && detail == null

        val heroImages = buildList {
            val rawCover = detail?.cover ?: navParam.initialCover
            if (!MediaPipeline.Default.isPlaceholder(rawCover)) {
                add(MediaPipeline.Default.resolveRawUrl(rawCover, MediaCategory.HOBBY))
            }
            uiState.officialPics.take(8).forEach { pic ->
                val url = pic.imagePath?.let {
                    MediaPipeline.Default.resolvePictureUrl(it, ImagePurpose.FULLSCREEN_RAW)
                }
                if (!url.isNullOrBlank() && !contains(url)) {
                    add(url)
                }
            }
        }

        val makerCompanyId = uiState.makerCompanyId
        val relateNodeId = uiState.detail?.actualNodeId ?: uiState.itemId
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

        val commentFeedState = remember(uiState, detail, isInitialLoading) {
            CommentFeedState(
                totalCommentCount = detail?.commentCount ?: (uiState.hotComments.size + uiState.latestComments.size),
                hotComments = uiState.hotComments,
                latestComments = uiState.latestComments,
                isCommentsLoading = uiState.isCommentsLoading || isInitialLoading,
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
                        collectCountBase = uiState.collectCountBase,
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
            if (isInitialLoading) {
                item(key = "hobby_detail_initial_loading") {
                    PoiLoadingView(modifier = Modifier.fillParentMaxSize())
                }
            } else {

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
                                val allPicUrls = uiState.officialPics.mapNotNull { pic ->
                                    pic.imagePath?.let {
                                        MediaPipeline.Default.resolvePictureUrl(it, ImagePurpose.FULLSCREEN_RAW)
                                    }
                                }
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
                            isPicsLoading = uiState.isOfficialPicsLoading,
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

                    item(key = "section_specs") {
                        HobbySpecsSection(
                            detail = detail,
                            infoList = uiState.infoList,
                            isLoading = uiState.isInfoListLoading,
                            isDark = isDark,
                            onNavigateToHobbyList = onNavigateToHobbyList,
                            onUrlClick = onUrlClick,
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
                                    val personId = entry.link.removePrefix("person/").substringBefore("?").substringBefore("/").toLongOrNull() ?: 0L
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
                                    val worksId = entry.link.removePrefix("works/").substringBefore("?").substringBefore("/").toLongOrNull() ?: 0L
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

                    if (uiState.timeline.isNotEmpty()) {
                        item(key = "section_timeline") {
                            HobbyTimelineSection(
                                timeline = uiState.timeline
                            )
                        }
                    }

                    if (uiState.userAlbums.isNotEmpty() || uiState.isUserAlbumsLoading) {
                        item(key = "section_user_albums") {
                            HobbyUserAlbumsSection(
                                albums = uiState.userAlbums,
                                isLoading = uiState.isUserAlbumsLoading,
                                isDark = isDark,
                                onAlbumClick = { album ->
                                    if (onNavigateToAlbum != null && album.id > 0) {
                                        onNavigateToAlbum(album.id)
                                    } else {

                                        val coverUrl = MediaPipeline.Default.resolveRawUrl(album.cover, MediaCategory.ALBUM)
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
                                            ExHobbyListNavKey(itemId = targetId, title = "ExHobby")
                                        )
                                    }
                                }
                            )
                        }
                    }

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
}

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
    val rawCover = detail?.cover?.ifBlank { null } ?: initialCover?.ifBlank { null }

    val hasCover = !(detail == null && initialCover.isNullOrBlank()) && !MediaPipeline.Default.isPlaceholder(rawCover)

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {

        if (hasCover) {
            PoiAsyncImage(
                path = rawCover,
                category = MediaCategory.HOBBY,
                purpose = ImagePurpose.CARD_STANDARD,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .matchParentSize()
                    .blur(28.dp)
            )

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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 64.dp, bottom = 10.dp)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Box(
                    modifier = Modifier
                        .width(135.dp)
                        .aspectRatio(9f / 13f)
                        .clip(MaterialTheme.shapes.large)
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
                            shape = MaterialTheme.shapes.large
                        )
                        .clickable(onClick = onCoverClick)
                ) {

                    when {
                        hasCover -> {
                            PoiAsyncImage(
                                path = rawCover,
                                category = MediaCategory.HOBBY,
                                purpose = ImagePurpose.CARD_STANDARD,
                                contentDescription = "手办完整封面",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        detail == null -> {
                            PoiLoadingView()
                        }
                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(PoiIcons.Filled.PhotoLibrary),
                                    contentDescription = "暂无封面",
                                    tint = if (isDark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.3f),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "暂无封面",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.45f)
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

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
                            if (!makerCover.isNullOrBlank()) {

                                PoiAsyncImage(
                                    path = makerCover,
                                    category = MediaCategory.HOBBY,
                                    purpose = ImagePurpose.CARD_STANDARD,
                                    contentDescription = makerName,
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
                                text = detail.ratingCountText.orEmpty(),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

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
                        if (officialPics.isNotEmpty() || !isPicsLoading) {
                            Text(
                                text = "官图",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (officialPics.isEmpty()) {
                            if (isPicsLoading) {
                                item(
                                    key = "official_loading",
                                    contentType = "official_loading"
                                ) {
                                    PoiLoadingView(modifier = Modifier.size(130.dp))
                                }
                            }
                        } else {

                        itemsIndexed(
                            officialPics.take(9),
                            key = { index, pic ->
                                val picKey = if (pic.id != 0L) "${pic.id}" else "${pic.imagePath}"
                                "official_${picKey}_$index"
                            },
                            contentType = { _, _ -> "official_pic" }
                        ) { index, pic ->
                            Card(
                                onClick = { onPicClick(index) },
                                modifier = Modifier.size(130.dp),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {

                                PoiAsyncImage(
                                    model = MediaPipeline.Default.resolvePictureUrl(
                                        pic.imagePath,
                                        ImagePurpose.THUMBNAIL
                                    ),
                                    contentDescription = "官方定妆照 $index",
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
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(PoiIcons.Filled.PhotoLibrary),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HobbySpecsSection(
    detail: HobbyDetailData?,
    infoList: List<InfoListEntry>,
    isLoading: Boolean,
    isDark: Boolean,
    onEntryClick: ((InfoListEntry) -> Unit)? = null,
    onNavigateToHobbyList: ((HobbyListNavParam) -> Unit)? = null,
    onUrlClick: ((String) -> Unit)? = null
) {
    val clipboard = LocalClipboard.current
    val messages = LocalAppMessages.current
    val scope = rememberCoroutineScope()

    val validEntries = remember(infoList) {
        infoList.filter { it.displayKey.isNotBlank() && (it.displayValue.isNotBlank() || it.tags.isNotEmpty()) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (validEntries.isNotEmpty()) {

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

                    PoiLoadingView(modifier = Modifier.fillMaxWidth().padding(32.dp))
                }
            }
        }

        val rawHtml = detail?.detail?.ifBlank { null }
        val cleanText = detail?.detailPlainText?.ifBlank { null }
        val hasDetailContent = !cleanText.isNullOrBlank()

        if (hasDetailContent) {
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

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
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

        Text(
            text = entry.displayKey,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(74.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

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

                entry.isNavigatable || entry.type == 2 -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
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

@Composable
private fun HeaderBadgeCapsule(
    text: String,
    isDark: Boolean,
    isPrimary: Boolean = false
) {
    Surface(
        shape = CircleShape,
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
    val hasCover = !MediaPipeline.Default.isPlaceholder(item.cover)

    Card(
        onClick = onClick,
        modifier = Modifier.width(136.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 13f)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (isDark) Color(0xFF22242E) else Color(0xFFEBEDF2)),
                contentAlignment = Alignment.Center
            ) {
                if (hasCover) {
                    PoiAsyncImage(
                        path = item.cover,
                        category = MediaCategory.HOBBY,
                        purpose = ImagePurpose.CARD_STANDARD,
                        contentDescription = item.displayName,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "暂无封面",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (!item.typeName.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                                shape = CircleShape
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
            PoiLoadingView(modifier = Modifier.fillMaxWidth().padding(32.dp))
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

@Composable
private fun HobbyAlbumMoreTile(
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onMoreClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(PoiIcons.Filled.PhotoLibrary),
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
                AlbumTextCover(
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
private fun HobbyFloatingActionBar(
    detail: HobbyDetailData?,
    isFavorite: Boolean,
    collectDelta: Int,
    collectCountBase: Long?,
    commentCount: Int,
    showCommentButton: Boolean,
    onToggleFavorite: () -> Unit,
    onCommentListClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    val baseCollect = collectCountBase ?: (detail?.collect ?: 0).toLong()
    val displayCollect = (baseCollect + collectDelta).coerceAtLeast(0L)
    val displayComment = commentCount.coerceAtLeast(0)

    ExpressiveFloatingToolbar(modifier = modifier) {

        ExpressiveToolbarButton(
            onClick = onToggleFavorite,
            text = if (displayCollect > 0) "$displayCollect" else null,
            icon = if (isFavorite) PoiIcons.Filled.Favorite else PoiIcons.Outlined.FavoriteBorder,
            contentDescription = if (isFavorite) "已收藏" else "收藏",
            isProminent = isFavorite,
            containerColor = if (isFavorite) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (isFavorite) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            iconTint = if (isFavorite) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (showCommentButton) {
            ExpressiveToolbarButton(
                onClick = onCommentListClick,
                text = "$displayComment",
                icon = PoiIcons.AutoMirrored.Filled.Comment,
                contentDescription = "评论",

                isProminent = false
            )
        }
    }
}

@Composable
private fun RatingStars(score: Double) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 1..5) {
            val filled = score >= i
            Icon(
                painter = painterResource(if (filled) PoiIcons.Filled.Star else PoiIcons.Filled.StarBorder),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = if (filled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
            )
        }
    }
    }

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
                        painter = painterResource(PoiIcons.Filled.Close),
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
                shape = CircleShape
            ) {
                Text("点击重新加载")
            }
        }
    }
}
