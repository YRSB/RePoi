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
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

@Composable
fun ImageViewerScreen(
    param: ImageViewerNavParam,
    onBack: () -> Unit,
    state: ImageViewerStateHolder? = null,
    modifier: Modifier = Modifier
) {

    val activeState = state?.takeIf { it.isVisible }
    val currentUrls = activeState?.imageUrls ?: param.images
    val initialPage = (activeState?.currentIndex ?: param.initialIndex)
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

@Deprecated(
    message = "请优先通过 Navigation 3 路由 (ImageViewerNavKey) 呈现以支持原生预测性返回动画",
    replaceWith = ReplaceWith(
        "ImageViewerContent(imageUrls, initialIndex, onBack = onDismissRequest, " +
            "onLoadMore = onLoadMore, onPageChange = onPageChange)"
    )
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

    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val decodeHeightPx = remember(windowSize, density) {
        windowSize.height.takeIf { it > 0 } ?: with(density) { 800.dp.toPx().roundToInt() }
    }

    var isCurrentPageZoomed by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage, imageUrls.size) {
        isCurrentPageZoomed = false
        onPageChange?.invoke(pagerState.currentPage)
        if (onLoadMore != null && pagerState.currentPage >= imageUrls.size - 2) {
            onLoadMore()
        }
    }

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

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !isCurrentPageZoomed,
            beyondViewportPageCount = 1
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
                    detectTapGestures {  }
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
                        painter = painterResource(PoiIcons.Filled.Close),
                        contentDescription = "关闭",
                        tint = Color.White
                    )
                }
            }
        }

        if (imageUrls.size > 1) {

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
                    painter = painterResource(PoiIcons.AutoMirrored.Filled.ArrowBack),
                    contentDescription = contentDescription,
                    tint = Color.White,
                    modifier = if (iconRotation != 0f) Modifier.graphicsLayer(rotationZ = iconRotation) else Modifier
                )
            }
        }
    }
}
