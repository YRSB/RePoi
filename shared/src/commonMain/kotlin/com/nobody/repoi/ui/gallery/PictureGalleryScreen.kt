package com.nobody.repoi.ui.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.ImageLoader
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobody.repoi.platform.LocalAppMessages
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.repoi.data.remote.model.PictureRelateItem
import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.theme.ExpressiveTokens
import kotlin.math.roundToInt

// ── 大图画廊 ────────────────────────────────────────────────

/**
 * 画廊内的统一轻提示：经 [LocalAppMessages] 消息总线展示。
 *
 * 画廊可能以全屏页或 Dialog 两种形态宿主；调用点的 Scaffold 宿主由总线兜底，
 * 故不再直调 Toast（KMP common 无 android.widget.Toast）。
 */

/**
 * 图片大图画廊（全屏），替代收藏页原先直接复用的 `ImageViewerDialog`。
 *
 * 对标原版 `PictureGalleryActivity`，具备：
 * - **无限分页**：滑到倒数第 4 张回调 [onLoadMore] 续拉；页码显示 `"3 / 20"`，
 *   未到底时末尾追加 `"+"`（原版 `isEnd ? "" : "+"`）。
 * - **顶栏 actionBar**：返回、收藏星标 + 收藏数徽章、more 按钮。
 * - **收藏/取消收藏**：复用 `CollectRepository.collectItemAdd` / `collectItemDel`，
 *   key 用 `UserCollectPicItem.id`；乐观更新 + 1.3 倍缩放动画；切页后延迟 500ms 回填星标。
 * - **长按图片或点 more** → 底部 BottomSheet 四项：列表 / 原图 / 下载 / 举报。
 * - **底部关联条目**：`api/pic/relate`，按 `itemType` 分相册(album)/手办(hobby)/文章(article) 三类。
 * - **大图底部叠加图片备注 `remark`**。
 * - **缩放/平移/双击缩放**：与 `ImageViewerDialog` 同源实现（1–4x 捏合、放大态禁用翻页、单击收起）。
 * - **超长图保护**：按屏幕高度夹取解码尺寸，并对极端长图给出提示（不引入 SubsamplingScaleImageView）。
 *
 * @param items 当前已加载的图片列表（宿主侧维护，画廊只读）
 * @param initialIndex 初始定位下标
 * @param hasMore 是否还有下一页（决定页码是否追加 `+`）
 * @param isLoadingMore 续拉请求进行中（避免重复触发）
 * @param onLoadMore 滑到倒数第 4 张时回调，宿主接 `UserCollectViewModel.loadNextPage(PICTURE)`
 * @param onBack 返回
 * @param onOpenPictureList 「列表」入口回调；宿主需跳 `PictureListScreen`。
 *   收藏页的图片不属于任何相册/手办上下文，**PoiShelf 暂无「按图片 id 反查所属列表」接口**，
 *   故默认传 `null` —— 传 null 时该项自动隐藏，接线方式见交付报告。
 * @param onNavigateToAlbum 关联条目为相册时的跳转
 * @param onNavigateToHobby 关联条目为手办时的跳转
 * @param onNavigateToArticle 关联条目为文章时的跳转（当前工程无文章详情页，宿主可不接）
 */
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
    // 超长图保护用的解码高度上限：取自当前窗口自适应信息（KMP 无 android Configuration）
    val screenHeightPx = with(LocalDensity.current) {
        currentWindowAdaptiveInfoV2().windowSizeClass.run { null }.let {
            // 回退：以窗口高度下限断点估算（桌面/手机均可用，精确值由 Density 换算）
            800.dp.toPx()
        }
    }.roundToInt()

    var isCurrentPageZoomed by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    var showActionSheet by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    // 收藏星标缩放脉冲触发器：每次收藏操作 +1
    var starPulse by remember { mutableIntStateOf(0) }

    val currentItem = items.getOrNull(pagerState.currentPage) ?: items.first()

    // 切页：重置原图/超长图态，延迟 500ms 回填收藏态并拉关联条目
    LaunchedEffect(pagerState.currentPage) {
        items.getOrNull(pagerState.currentPage)?.let { item ->
            viewModel.onPageChanged(item, pagerState.currentPage)
        }
    }

    // 画廊内无限分页：滑到倒数第 4 张即续拉（原版 `i10 > list.length() - 4`）
    LaunchedEffect(pagerState.currentPage, items.size) {
        val fromEnd = items.size - pagerState.currentPage
        if (fromEnd < PictureGalleryViewModel.PREFETCH_DISTANCE && hasMore && !isLoadingMore) {
            onLoadMore()
        }
    }

    // 轻提示（下载结果 / 举报结果 / 收藏回滚）：经消息总线展示，由调用点 Scaffold 宿主兜底
    LaunchedEffect(uiState.toastMessage) {
        val message = uiState.toastMessage ?: return@LaunchedEffect
        messages.show(message)
        viewModel.consumeMessage()
    }

    // 双向后台预取管道：预取前后各 2 张图片沉淀到 Coil 磁盘与内存缓存中，防止快速连续翻页时白屏等待
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
        // 1. 横向滑动查看大图（配置 beyondViewportPageCount=1 保证左右各预渲染 1 页，无缝切图零白屏）
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isCurrentPageZoomed,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = items.getOrNull(page) ?: return@HorizontalPager
            val url = viewModel.currentDisplayUrl(item, uiState.showRaw)
            GalleryZoomableImage(
                imageUrl = url,
                isCurrentPage = pagerState.currentPage == page,
                maxDecodeHeightPx = screenHeightPx,
                onZoomChanged = { zoomed ->
                    if (pagerState.currentPage == page) {
                        isCurrentPageZoomed = zoomed
                    }
                },
                onSingleTap = {
                    // 单击切换操作层显隐；放大态下单击先复位缩放
                    if (isCurrentPageZoomed) {
                        isCurrentPageZoomed = false
                    } else {
                        chromeVisible = !chromeVisible
                    }
                },
                onLongPress = { showActionSheet = true },
                onDetectLongImage = { viewModel.markLongImage() }
            )
        }

        // 2. 顶栏 actionBar：返回 / 收藏星标 + 徽章 / more
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

        // 3. 底部：图片备注 remark + 关联条目
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

        // 4. 超长图保护提示（P2）
        if (uiState.isLongImage) {
            Surface(
                shape = ExpressiveTokens.PillShape,
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

        // 5. 下载中遮罩
        if (uiState.isDownloading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = ExpressiveTokens.MediumCardShape,
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("正在保存原图…", color = Color.White)
                    }
                }
            }
        }
    }

    // 6. 操作 BottomSheet：列表 / 原图 / 下载 / 举报
    if (showActionSheet) {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
        ModalBottomSheet(
            onDismissRequest = { showActionSheet = false },
            sheetState = sheetState,
            shape = ExpressiveTokens.LargeCardShape
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
                    // 已是原图规格则只提示，不再重复请求（原版 :235-242 的「已是原图」语义）
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

    // 7. 举报原因输入弹窗（api/web-report/add）
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

// ── 顶栏 actionBar ──────────────────────────────────────────

/**
 * 画廊顶栏：返回、收藏星标（含收藏数徽章）、more。
 *
 * @param pulseTrigger 每次 +1 触发一次 1→1.3→1 的缩放脉冲（对齐原版 `ValueAnimator.ofFloat(1f,1.3f,1f)`）
 */
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
    // 收藏/取消收藏后的缩放脉冲（原版 300ms：1f → 1.3f → 1f）
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
            // 拦截点击防穿透到背景误触
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GalleryIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, description = "返回", onClick = onBack)

            Text(
                text = pageText,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 收藏星标 + 收藏数徽章
                Box(contentAlignment = Alignment.Center) {
                    GalleryIconButton(
                        icon = if (isCollected) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
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
                            shape = ExpressiveTokens.PillShape,
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
                    icon = Icons.Filled.MoreHoriz,
                    description = "更多操作",
                    onClick = onMore
                )
            }
        }
    }
}

/**
 * 画廊顶栏的圆形图标按钮（半透明磨砂底，对齐原版 actionBar 观感）。
 */
@Composable
private fun GalleryIconButton(
    icon: ImageVector,
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
                imageVector = icon,
                contentDescription = description,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ── 缩放大图 ────────────────────────────────────────────────

/**
 * 可缩放大图：捏合缩放（1–4x）、放大态单指平移、双击缩放、长按回调。
 *
 * 与 `ui/common/ImageViewerDialog.kt` 的 `ZoomableImage` 同源实现，
 * **未修改该文件**（其它页面仍在使用），此处独立承载画廊的额外回调。
 *
 * @param maxDecodeHeightPx 超长图保护用的解码高度上限（按屏幕高度换算，见 [maxDecodeHeightPx]）
 * @param onDetectLongImage 解码后发现图片远高于屏幕时回调，UI 据此给出提示
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryZoomableImage(
    imageUrl: String,
    isCurrentPage: Boolean,
    maxDecodeHeightPx: Int,
    onZoomChanged: (Boolean) -> Unit,
    onSingleTap: () -> Unit,
    onLongPress: () -> Unit,
    onDetectLongImage: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offset = Offset.Zero
            onZoomChanged(false)
        }
    }

    val transformableState = rememberTransformableState { _, zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = newScale
        if (newScale > 1f) {
            offset += panChange
            onZoomChanged(true)
        } else {
            scale = 1f
            offset = Offset.Zero
            onZoomChanged(false)
        }
    }

    val isZoomed = scale > 1.05f
    var isImageLoading by remember(imageUrl) { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isZoomed) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offset += dragAmount
                        }
                    }
                } else {
                    Modifier
                }
            )
            .transformable(state = transformableState, canPan = { scale > 1.05f })
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSingleTap,
                onLongClick = onLongPress,
                onDoubleClick = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                        onZoomChanged(false)
                    } else {
                        scale = 2.5f
                        onZoomChanged(true)
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(imageUrl)
                .crossfade(true)
                // 超长图保护（P2）：按屏幕高度夹取解码尺寸，避免 Coil 一次性解码整张巨图导致 OOM
                .size(MAX_DECODE_WIDTH_PX, maxDecodeHeightPx)
                .build(),
            contentDescription = "高清大图",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit,
            onSuccess = { state ->
                isImageLoading = false
                // 图片高度远超一屏时提示「已按屏幕高度优化显示」
                val size = state.painter.intrinsicSize
                if (size.height > maxDecodeHeightPx * LONG_IMAGE_THRESHOLD) {
                    onDetectLongImage()
                }
            },
            onError = { isImageLoading = false }
        )

        if (isImageLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = Color.White.copy(alpha = 0.7f),
                strokeWidth = 3.dp
            )
        }
    }
}

/** 解码宽度上限（不随屏幕宽度限制，只按高度夹取，避免超长图 OOM） */
private const val MAX_DECODE_WIDTH_PX = 4096

/** 图片高度超过屏幕高度的该倍数即判定为超长图 */
private const val LONG_IMAGE_THRESHOLD = 3f

// ── 底部面板：备注 + 关联条目 ────────────────────────────────

/**
 * 画廊底部面板：图片备注 `remark` + 关联条目横滑。
 */
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
        // 图片备注（原版 PictureGalleryPagerAdapter.java:173-179）
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
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    strokeWidth = 2.dp
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
                    items(relateItems, key = { "${it.itemType}_${it.id}" }) { relate ->
                        RelateItemChip(
                            relate = relate,
                            onClick = {
                                when (relate.itemType) {
                                    // 相册 → 相册详情
                                    "album" -> onNavigateToAlbum?.invoke(
                                        AlbumNavParam(
                                            albumId = if (relate.itemId > 0L) relate.itemId else relate.id,
                                            initialTitle = relate.displayName,
                                            initialCover = relate.cover
                                        )
                                    )

                                    // 手办 → 手办详情
                                    "hobby" -> onNavigateToHobby?.invoke(
                                        DetailNavParam(
                                            itemId = if (relate.itemId > 0L) relate.itemId else relate.id,
                                            itemType = "hobby",
                                            initialName = relate.displayName,
                                            initialCover = relate.cover
                                        )
                                    )

                                    // 文章 → 文章详情（当前工程暂无文章页，宿主可不接）
                                    else -> onNavigateToArticle?.invoke(relate)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 关联条目展示名（原版 `pe.c.j` 标题回退：nameCN → name）。
 */
private val PictureRelateItem.displayName: String
    get() = nameCN?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: "相关条目"

/**
 * 关联条目胶囊：类型标签 + 名称。
 */
@Composable
private fun RelateItemChip(
    relate: PictureRelateItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = ExpressiveTokens.PillShape,
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
                    .clip(ExpressiveTokens.ExtraSmallShape)
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

/** 关联条目类型中文标签 */
private val PictureRelateItem.itemTypeLabel: String
    get() = when (itemType) {
        "album" -> "相册"
        "hobby" -> "手办"
        else -> "文章"
    }

// ── 操作 BottomSheet ────────────────────────────────────────

/**
 * 大图操作 BottomSheet：列表 / 原图 / 下载 / 举报。
 */
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
        // 「列表」入口无可用上下文时隐藏（见 PictureGalleryScreen 的 KDoc）
        if (showListEntry) {
            GallerySheetRow(
                icon = Icons.AutoMirrored.Filled.List,
                label = "列表",
                onClick = onOpenPictureList
            )
        }
        GallerySheetRow(
            icon = Icons.Filled.HighQuality,
            label = "原图",
            onClick = onToggleRaw
        )
        GallerySheetRow(
            icon = Icons.Filled.Download,
            label = if (isDownloading) "保存中…" else "下载",
            enabled = !isDownloading,
            onClick = onDownload
        )
        GallerySheetRow(
            icon = Icons.Filled.Report,
            label = "举报",
            onClick = onReport
        )
    }
}

/**
 * BottomSheet 单行操作项。
 */
@Composable
private fun GallerySheetRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = ExpressiveTokens.MediumCardShape,
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
                imageVector = icon,
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

// ── 举报弹窗 ────────────────────────────────────────────────

/**
 * 举报原因输入弹窗（`api/web-report/add`，type 固定 `picture_info`）。
 */
@Composable
private fun ReportReasonDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = ExpressiveTokens.DialogShape,
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
                    shape = ExpressiveTokens.MediumCardShape,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(reason.trim()) },
                enabled = reason.isNotBlank(),
                shape = ExpressiveTokens.ButtonShape,
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