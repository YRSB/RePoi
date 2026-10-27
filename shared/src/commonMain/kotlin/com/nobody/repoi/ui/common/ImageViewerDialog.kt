package com.nobody.repoi.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.CircularProgressIndicator

/**
 * 全局大图画廊弹窗宿主，统一挂载在根部脚手架 (PoiShelfApp) 中。
 */
@Composable
internal fun ImageViewerHost(
    state: ImageViewerStateHolder
) {
    if (state.isVisible) {
        ImageViewerDialog(
            imageUrls = state.imageUrls,
            initialIndex = state.currentIndex,
            onDismissRequest = { state.dismiss() },
            onLoadMore = state.onLoadMoreCallback,
            onPageChange = { page -> state.currentIndex = page }
        )
    }
}

@Composable
internal fun ImageViewerDialog(
    imageUrls: List<String>,
    initialIndex: Int = 0,
    onDismissRequest: () -> Unit,
    onLoadMore: (() -> Unit)? = null,
    onPageChange: ((Int) -> Unit)? = null
) {
    if (imageUrls.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, imageUrls.size - 1),
        pageCount = { imageUrls.size }
    )

    val context = LocalPlatformContext.current
    val coroutineScope = rememberCoroutineScope()

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
        val imageLoader = SingletonImageLoader.get(context)
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
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                    imageLoader.enqueue(request)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 1. 横向滑动查看大图（配置 beyondViewportPageCount=1 保证左右各预渲染 1 页，无缝切图零白屏）
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isCurrentPageZoomed,
                beyondViewportPageCount = 1
            ) { page ->
                ZoomableImage(
                    imageUrl = imageUrls[page],
                    isCurrentPage = pagerState.currentPage == page,
                    onZoomChanged = { isZoomed ->
                        if (pagerState.currentPage == page) {
                            isCurrentPageZoomed = isZoomed
                        }
                    },
                    onSingleTap = onDismissRequest
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

                    IconButton(onClick = onDismissRequest) {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomableImage(
    imageUrl: String,
    isCurrentPage: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onSingleTap: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // 页面切走时重置缩放和偏移
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            scale = 1f
            offset = Offset.Zero
            onZoomChanged(false)
        }
    }

    // 双指捏合缩放与平移 (transformable 原生仅监听多指手势，不拦截单指滑动，让单指滑动流畅交由 HorizontalPager 翻页)
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
            // 放大状态下：启用单指拖拽自由平移以浏览放大细节；未放大时不拦截滑动事件以保留 Pager 左右滑动手势
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
            .transformable(
                state = transformableState,
                canPan = { scale > 1.05f }
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    onSingleTap()
                },
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
            onSuccess = { isImageLoading = false },
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
