package com.nobody.repoi.ui.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.ImageLoader
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobody.repoi.platform.LocalAppMessages
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.repoi.data.remote.model.PictureRelateItem
import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.common.POI_DEFAULT_MAX_DECODE_WIDTH_PX
import com.nobody.repoi.ui.common.PoiZoomableImage
import kotlin.math.roundToInt
import com.nobody.repoi.ui.theme.largeCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PictureGalleryScreen(
    items: List<UserCollectPicItem>,
    initialIndex: Int = 0,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPictureList: ((UserCollectPicItem) -> Unit)? = null,
    onNavigateToAlbum: ((AlbumNavParam) -> Unit)? = null,
    onNavigateToHobby: ((DetailNavParam) -> Unit)? = null,
    onNavigateToArticle: ((PictureRelateItem) -> Unit)? = null,
    viewModel: PictureGalleryViewModel = koinViewModel()
) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, items.size - 1),
        pageCount = { items.size }
    )
    val platformContext = LocalPlatformContext.current
    val messages = LocalAppMessages.current
    val imageLoader: ImageLoader = koinInject()
    val coroutineScope = rememberCoroutineScope()

    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val screenHeightPx = remember(windowSize, density) {
        windowSize.height.takeIf { it > 0 } ?: with(density) { 800.dp.toPx().roundToInt() }
    }

    var isCurrentPageZoomed by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    var starPulse by remember { mutableIntStateOf(0) }

    val currentItem = items.getOrNull(pagerState.currentPage) ?: items.first()

    LaunchedEffect(pagerState.currentPage) {
        items.getOrNull(pagerState.currentPage)?.let { item ->
            viewModel.onPageChanged(item, pagerState.currentPage)
        }
    }

    LaunchedEffect(pagerState.currentPage, items.size) {
        val fromEnd = items.size - pagerState.currentPage
        if (fromEnd < PictureGalleryViewModel.PREFETCH_DISTANCE && hasMore && !isLoadingMore) {
            onLoadMore()
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        val message = uiState.toastMessage ?: return@LaunchedEffect
        messages.show(message)
        viewModel.consumeMessage()
    }

    LaunchedEffect(pagerState.currentPage, items, uiState.showRaw) {
        val currentPage = pagerState.currentPage
        val preloadIndices = listOf(
            currentPage + 1,
            currentPage - 1,
            currentPage + 2,
            currentPage - 2
        )
        for (idx in preloadIndices) {
            val item = items.getOrNull(idx) ?: continue
            val url = viewModel.currentDisplayUrl(item, uiState.showRaw)
            if (url.isNotBlank()) {
                val request = ImageRequest.Builder(platformContext)
                    .data(url)
                    .size(POI_DEFAULT_MAX_DECODE_WIDTH_PX, screenHeightPx)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                imageLoader.enqueue(request)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isCurrentPageZoomed,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            val url = viewModel.currentDisplayUrl(item, uiState.showRaw)
            PoiZoomableImage(
                imageUrl = url,
                isActive = pagerState.currentPage == page,
                maxDecodeHeightPx = screenHeightPx,
                onZoomChanged = { zoomed ->
                    if (pagerState.currentPage == page) {
                        isCurrentPageZoomed = zoomed
                    }
                },

                onSingleTap = { chromeVisible = !chromeVisible },
                onLongPress = { showActionSheet = true },
                onDetectLongImage = { viewModel.markLongImage() }
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            GalleryActionBar(
                pageText = buildString {
                    append(pagerState.currentPage + 1)
                    append(" / ")
                    append(items.size)
                    if (hasMore) append("+")
                },
                isCollected = uiState.isCollected,
                collectCount = uiState.collectCount,
                pulseTrigger = starPulse,
                onBack = onBack,
                onToggleCollect = {
                    starPulse++
                    viewModel.toggleCollect(currentItem)
                },
                onMore = { showActionSheet = true }
            )
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            GalleryBottomPanel(
                remark = currentItem.actualRemark,
                relateItems = uiState.relateItems,
                isRelateLoading = uiState.isRelateLoading,
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToHobby = onNavigateToHobby,
                onNavigateToArticle = onNavigateToArticle
            )
        }

        if (uiState.isLongImage) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp)
            ) {
                Text(
                    text = "这是一张超长图，已按屏幕高度优化显示，可长按查看原图",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        if (uiState.isDownloading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("正在保存原图…", color = Color.White)
                    }
                }
            }
        }
    }

    if (showActionSheet) {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
        ModalBottomSheet(
            onDismissRequest = { showActionSheet = false },
            sheetState = sheetState,
            shape = MaterialTheme.shapes.largeCard
        ) {
            GalleryActionSheet(
                showListEntry = onOpenPictureList != null,
                isDownloading = uiState.isDownloading,
                onOpenPictureList = {
                    showActionSheet = false
                    onOpenPictureList?.invoke(currentItem)
                },
                onToggleRaw = {
                    showActionSheet = false

                    if (uiState.showRaw) {
                        coroutineScope.launch { messages.show("已是原图") }
                    } else {
                        viewModel.toggleRawSpec()
                        coroutineScope.launch { messages.show("已切换到原图") }
                    }
                },
                onDownload = {
                    showActionSheet = false
                    viewModel.downloadToGallery(currentItem, uiState.showRaw)
                },
                onReport = {
                    showActionSheet = false
                    showReportDialog = true
                }
            )
        }
    }

    if (showReportDialog) {
        ReportReasonDialog(
            onDismiss = { showReportDialog = false },
            onSubmit = { reason ->
                showReportDialog = false
                viewModel.submitReport(currentItem, reason)
            }
        )
    }
}

@Composable
private fun GalleryActionBar(
    pageText: String,
    isCollected: Boolean,
    collectCount: Int,
    pulseTrigger: Int,
    onBack: () -> Unit,
    onToggleCollect: () -> Unit,
    onMore: () -> Unit
) {

    val starScale = remember { Animatable(1f) }
    LaunchedEffect(pulseTrigger) {
        if (pulseTrigger == 0) return@LaunchedEffect
        starScale.animateTo(1.3f, animationSpec = tween(durationMillis = 150))
        starScale.animateTo(1f, animationSpec = tween(durationMillis = 150))
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                )
            )
            .padding(statusBarPadding)
            .padding(horizontal = 8.dp, vertical = 8.dp)

            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GalleryIconButton(icon = PoiIcons.ArrowBack, description = "返回", onClick = onBack)

            Text(
                text = pageText,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {

                Box(contentAlignment = Alignment.Center) {
                    GalleryIconButton(
                        icon = if (isCollected) PoiIcons.Favorite else PoiIcons.FavoriteBorder,
                        description = if (isCollected) "取消收藏" else "收藏",
                        tint = if (isCollected) MaterialTheme.colorScheme.error else Color.White,
                        onClick = onToggleCollect,
                        modifier = Modifier.graphicsLayer {
                            scaleX = starScale.value
                            scaleY = starScale.value
                        }
                    )
                    if (collectCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 2.dp)
                        ) {
                            Text(
                                text = collectCount.toString(),
                                color = MaterialTheme.colorScheme.onError,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                GalleryIconButton(
                    icon = PoiIcons.MoreHoriz,
                    description = "更多操作",
                    onClick = onMore
                )
            }
        }
    }
}

@Composable
private fun GalleryIconButton(
    icon: DrawableResource,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.35f),
        modifier = modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun GalleryBottomPanel(
    remark: String?,
    relateItems: List<PictureRelateItem>,
    isRelateLoading: Boolean,
    onNavigateToAlbum: ((AlbumNavParam) -> Unit)?,
    onNavigateToHobby: ((DetailNavParam) -> Unit)?,
    onNavigateToArticle: ((PictureRelateItem) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                )
            )
            .padding(top = 24.dp)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {

        if (!remark.isNullOrBlank()) {
            Text(
                text = remark,
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        when {
            isRelateLoading -> {
                LoadingIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            relateItems.isEmpty() -> Unit

            else -> {
                Text(
                    text = "相关条目",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        items = relateItems,
                        key = { "${it.itemType}_${it.id}" },
                        contentType = { "relate_chip" }
                    ) { relate ->
                        RelateItemChip(
                            relate = relate,
                            onClick = {
                                when (relate.kind) {

                                    RelateKind.ALBUM -> onNavigateToAlbum?.invoke(
                                        AlbumNavParam(
                                            albumId = relate.actualAlbumId,
                                            initialTitle = relate.displayName,
                                            initialCover = relate.cover
                                        )
                                    )

                                    RelateKind.HOBBY -> onNavigateToHobby?.invoke(
                                        DetailNavParam(
                                            itemId = relate.actualHobbyId,
                                            itemType = "hobby",
                                            initialName = relate.displayName,
                                            initialCover = relate.cover
                                        )
                                    )

                                    RelateKind.ARTICLE -> onNavigateToArticle?.invoke(relate)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private val PictureRelateItem.displayName: String
    get() = nameCN?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: "相关条目"

@Composable
private fun RelateItemChip(
    relate: PictureRelateItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.16f),
        modifier = Modifier.heightIn(min = 36.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = relate.itemTypeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(Color.White.copy(alpha = 0.14f))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = relate.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private enum class RelateKind { ALBUM, HOBBY, ARTICLE }

private val PictureRelateItem.kind: RelateKind
    get() = when {
        itemType?.trim().equals("album", ignoreCase = true) -> RelateKind.ALBUM
        itemType?.trim().equals("hobby", ignoreCase = true) -> RelateKind.HOBBY
        categoryId in 60001..70000 -> RelateKind.ALBUM
        else -> RelateKind.ARTICLE
    }

private val PictureRelateItem.itemTypeLabel: String
    get() = when (kind) {
        RelateKind.ALBUM -> "相册"
        RelateKind.HOBBY -> "手办"
        RelateKind.ARTICLE -> "文章"
    }

@Composable
private fun GalleryActionSheet(
    showListEntry: Boolean,
    isDownloading: Boolean,
    onOpenPictureList: () -> Unit,
    onToggleRaw: () -> Unit,
    onDownload: () -> Unit,
    onReport: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {

        if (showListEntry) {
            GallerySheetRow(
                icon = PoiIcons.List,
                label = "列表",
                onClick = onOpenPictureList
            )
        }
        GallerySheetRow(
            icon = PoiIcons.HighQuality,
            label = "原图",
            onClick = onToggleRaw
        )
        GallerySheetRow(
            icon = PoiIcons.Download,
            label = if (isDownloading) "保存中…" else "下载",
            enabled = !isDownloading,
            onClick = onDownload
        )
        GallerySheetRow(
            icon = PoiIcons.Report,
            label = "举报",
            onClick = onReport
        )
    }
}

@Composable
private fun GallerySheetRow(
    icon: DrawableResource,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun ReportReasonDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("举报图片") },
        text = {
            Column {
                Text(
                    text = "请填写举报原因，我们会尽快处理",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = { Text("例如：含有不适宜内容") },
                    shape = MaterialTheme.shapes.medium,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(reason.trim()) },
                enabled = reason.isNotBlank(),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("提交")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
