package com.nobody.repoi.ui.common

import com.nobody.repoi.platform.FullscreenDialogEdgeToEdgeEffect
import com.nobody.repoi.ui.navigation.ImageViewerNavParam
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import coil3.ImageLoader
import kotlin.math.roundToInt

/**
 * 全屏大图画廊全屏页面（Navigation 3 二级页路由，由 [ImageViewerNavKey] 承载）。
 *
 * 彻底消灭 [Dialog] 独立浮层 Window 封装，全面回归 Navigation 3 单根栈，
 * 原生支持 [MaterialSharedAxis.Z] 进出场动效与 Android 14+ 系统级 Predictive Back 预测性返回预览。
 *
 * @param param 路由强类型参数
 * @param onBack 返回上一级页面（Navigation 3 出栈）
 * @param state 关联的大图控制器状态持有者（可动态响应底层列表续拉 [ImageViewerController.updateImages]）
 */
@Composable
fun ImageViewerScreen(
    param: ImageViewerNavParam,
    onBack: () -> Unit,
    state: ImageViewerStateHolder? = null,
    modifier: Modifier = Modifier
) {
    // 优先使用 state 中可能由 updateImages 动态更新的最新图片序列
    val currentUrls = state?.imageUrls?.takeIf { it.isNotEmpty() } ?: param.images
    val initialPage = (state?.currentIndex ?: param.initialIndex)
        .coerceIn(0, (currentUrls.size - 1).coerceAtLeast(0))

    DisposableEffect(Unit) {
        onDispose {
            state?.resetInternal()
        }
    }

    ImageViewerContent(
        imageUrls = currentUrls,
        initialIndex = initialPage,
        onBack = onBack,
        onLoadMore = state?.onLoadMoreCallback,
        onPageChange = { page -> state?.currentIndex = page },
        modifier = modifier
    )
}

/**
 * 全局大图画廊弹窗宿主（已废弃）。
 *
 * 大图画廊已全面迁移至 Navigation 3 原生单根栈路由 [ImageViewerNavKey]，
 * 原生支持 MaterialSharedAxis.Z 与预测性返回预览，无需在根脚手架外挂 Dialog 宿主。
 */
@Deprecated(
    message = "大图查看器已全面升级为 Navigation 3 原生单根栈路由 (ImageViewerNavKey)",
    level = DeprecationLevel.WARNING
)
@Composable
fun ImageViewerHost(
    state: ImageViewerStateHolder
) {
    // 兼容保留空实现：已由 Navigation 3 根栈自动承载渲染
}

/**
 * 独立 Dialog 形态的大图画廊（兼容保留）。
 */
@Deprecated(
    message = "请优先通过 Navigation 3 路由 (ImageViewerNavKey) 呈现以支持原生预测性返回动画",
    replaceWith = ReplaceWith("ImageViewerScreen(param, onBack)")
)
@Composable
fun ImageViewerDialog(
    imageUrls: List<String>,
    initialIndex: Int = 0,
    onDismissRequest: () -> Unit,
    onLoadMore: (() -> Unit)? = null,
    onPageChange: ((Int) -> Unit)? = null
) {
    if (imageUrls.isEmpty()) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        FullscreenDialogEdgeToEdgeEffect()
        ImageViewerContent(
            imageUrls = imageUrls,
            initialIndex = initialIndex,
            onBack = onDismissRequest,
            onLoadMore = onLoadMore,
            onPageChange = onPageChange
        )
    }
}

/**
 * 大图查看器核心画廊 UI 内容区：承载横向 Pager、捏合缩放、双向缓存预取与沉浸式顶栏。
 */
@Composable
fun ImageViewerContent(
    imageUrls: List<String>,
    initialIndex: Int = 0,
    onBack: () -> Unit,
    onLoadMore: (() -> Unit)? = null,
    onPageChange: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (imageUrls.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, imageUrls.size - 1),
        pageCount = { imageUrls.size }
    )

    val platformContext = LocalPlatformContext.current
    val imageLoader: ImageLoader = koinInject()
    val coroutineScope = rememberCoroutineScope()
    // 与 PictureGalleryScreen 同源的解码高度上限估算：窗口像素高度优先，取不到回落 800dp，
    // 避免巨图全尺寸解码导致 OOM（此前这里完全未设上限）
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val decodeHeightPx = remember(windowSize, density) {
        windowSize.height.takeIf { it > 0 } ?: with(density) { 800.dp.toPx().roundToInt() }
    }

    // 追踪当前页面是否处于放大状态，放大状态下禁用 Pager 滑动以支持拖拽平移
    var isCurrentPageZoomed by remember { mutableStateOf(false) }

    // 页面切页时自动重置放大状态、回写当前页码给宿主，并在翻页临近末尾（倒数第 2 张）时自动联动加载更多
    LaunchedEffect(pagerState.currentPage, imageUrls.size) {
        isCurrentPageZoomed = false
        onPageChange?.invoke(pagerState.currentPage)
        if (onLoadMore != null && pagerState.currentPage >= imageUrls.size - 2) {
            onLoadMore()
        }
    }

    // 双向后台预取管道：预取前后各 2 张图片沉淀到 Coil 磁盘与内存缓存中，防止快速连续翻页时白屏等待
    LaunchedEffect(pagerState.currentPage, imageUrls) {
        val currentPage = pagerState.currentPage
        val preloadIndices = listOf(
            currentPage + 1,
            currentPage - 1,
            currentPage + 2,
            currentPage - 2
        )
        for (idx in preloadIndices) {
            if (idx in imageUrls.indices) {
                val url = imageUrls[idx]
                if (url.isNotBlank()) {
                    val request = ImageRequest.Builder(platformContext)
                        .data(url)
                        // 与 [PoiZoomableImage] 的解码尺寸保持一致：既避免巨图全尺寸解码导致 OOM，
                        // 也让预取结果与主图请求命中同一份内存缓存（否则尺寸不同会缓存串味、失去秒开）
                        .size(POI_DEFAULT_MAX_DECODE_WIDTH_PX, decodeHeightPx)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                    imageLoader.enqueue(request)
                }
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
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isCurrentPageZoomed,
            beyondViewportPageCount = 1,
            key = { page -> imageUrls.getOrNull(page) ?: page }
        ) { page ->
            PoiZoomableImage(
                imageUrl = imageUrls[page],
                isActive = pagerState.currentPage == page,
                maxDecodeHeightPx = decodeHeightPx,
                onZoomChanged = { isZoomed ->
                    if (pagerState.currentPage == page) {
                        isCurrentPageZoomed = isZoomed
                    }
                },
                onSingleTap = onBack
            )
        }

        // 2. 顶部工具栏 (页码 + 关闭按钮，半透明渐变防白底，拦截点击防误关)
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
                .padding(statusBarPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .pointerInput(Unit) {
                    detectTapGestures { /* 拦截点击防穿透到背景关闭 */ }
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${imageUrls.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = Color.White
                    )
                }
            }
        }

        // 3. 左右悬浮切换按钮 (上一张 / 下一张，仅在多图且未放大状态下展示)
        if (imageUrls.size > 1) {
            // 左切换按钮 (上一张)
            ViewerNavigationButton(
                visible = pagerState.currentPage > 0 && !isCurrentPageZoomed,
                alignment = Alignment.CenterStart,
                contentDescription = "上一张",
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                }
            )

            // 右切换按钮 (下一张)
            ViewerNavigationButton(
                visible = pagerState.currentPage < imageUrls.size - 1 && !isCurrentPageZoomed,
                alignment = Alignment.CenterEnd,
                contentDescription = "下一张",
                iconRotation = 180f,
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            )
        }
    }
}

/**
 * 全屏画廊悬浮切换按钮（上一张 / 下一张），复用磨砂背景与入场/退场动画规范。
 */
@Composable
private fun BoxScope.ViewerNavigationButton(
    visible: Boolean,
    alignment: Alignment,
    contentDescription: String,
    iconRotation: Float = 0f,
    onClick: () -> Unit
) {
    val paddingModifier = if (alignment == Alignment.CenterStart) {
        Modifier.padding(start = 16.dp)
    } else {
        Modifier.padding(end = 16.dp)
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(alignment)
            .then(paddingModifier)
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = contentDescription,
                    tint = Color.White,
                    modifier = if (iconRotation != 0f) Modifier.graphicsLayer(rotationZ = iconRotation) else Modifier
                )
            }
        }
    }
}
