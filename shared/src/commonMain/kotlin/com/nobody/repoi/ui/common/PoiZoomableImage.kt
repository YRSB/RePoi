package com.nobody.repoi.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

internal const val POI_ZOOMED_THRESHOLD: Float = 1.02f

internal const val POI_ZOOM_DAMPING: Float = 0.25f

internal const val POI_WHEEL_ZOOM_SENSITIVITY: Float = 0.12f

internal const val POI_WHEEL_ZOOM_MIN_FACTOR: Float = 0.85f

internal const val POI_WHEEL_ZOOM_MAX_FACTOR: Float = 1.18f

internal const val POI_ZOOM_ANIMATION_MILLIS: Int = 220

internal const val POI_GESTURE_SETTLE_DELAY_MILLIS: Long = 140L

internal const val POI_LONG_IMAGE_THRESHOLD: Float = 3f

internal const val POI_DEFAULT_MAX_DECODE_WIDTH_PX: Int = 4096

internal fun wheelZoomFactor(scrollDeltaY: Float): Float {
    if (scrollDeltaY.isNaN() || scrollDeltaY == 0f) return 1f
    val raw = 1f - scrollDeltaY * POI_WHEEL_ZOOM_SENSITIVITY
    if (raw.isNaN()) return 1f
    return raw.coerceIn(POI_WHEEL_ZOOM_MIN_FACTOR, POI_WHEEL_ZOOM_MAX_FACTOR)
}

internal fun dampedScale(
    raw: Float,
    current: Float,
    minScale: Float,
    maxScale: Float,
    damping: Float = POI_ZOOM_DAMPING
): Float {
    if (current.isNaN()) return minScale
    if (raw.isNaN()) return current
    val eased = if (raw > maxScale) current + (raw - current) * damping else raw
    return eased.coerceAtLeast(minScale)
}

internal fun anchorAdjustedOffset(offset: Offset, factor: Float, anchor: Offset): Offset {
    if (factor.isNaN() || offset.x.isNaN() || offset.y.isNaN() ||
        anchor.x.isNaN() || anchor.y.isNaN()
    ) {
        return offset
    }
    return offset * factor + anchor * (1f - factor)
}

internal fun isLongImageRatio(
    width: Float,
    height: Float,
    threshold: Float = POI_LONG_IMAGE_THRESHOLD
): Boolean {
    if (width.isNaN() || height.isNaN()) return false
    if (width <= 0f || height <= 0f) return false
    val ratio = height / width
    return ratio.isFinite() && ratio > threshold
}

internal enum class PanContentScale { FIT, FILL_WIDTH }

internal enum class LongImageAnchor { TOP, BOTTOM }

internal fun longImageAnchorOffset(
    offset: Offset,
    maxY: Float,
    target: LongImageAnchor
): Offset {
    if (maxY.isNaN() || maxY <= 0f) return Offset.Zero
    return when (target) {
        LongImageAnchor.TOP -> Offset(0f, maxY)
        LongImageAnchor.BOTTOM -> Offset(0f, -maxY)
    }
}

internal fun nextLongImageAnchor(offset: Offset): LongImageAnchor =
    if (offset.y > 0f) LongImageAnchor.BOTTOM else LongImageAnchor.TOP

internal fun maxVerticalPan(
    scale: Float,
    viewport: Size,
    contentSize: Size,
    panContentScale: PanContentScale = PanContentScale.FIT
): Float {
    if (scale.isNaN() || scale <= 0f) return 0f
    if (viewport.width <= 0f || viewport.height <= 0f) return 0f
    if (contentSize.width <= 0f || contentSize.height <= 0f) return 0f

    val fitScale = when (panContentScale) {
        PanContentScale.FIT -> min(
            viewport.width / contentSize.width,
            viewport.height / contentSize.height
        )

        PanContentScale.FILL_WIDTH -> viewport.width / contentSize.width
    }
    if (fitScale.isNaN() || fitScale <= 0f) return 0f

    val overflow = (contentSize.height * fitScale * scale - viewport.height) / 2f
    return if (overflow.isNaN() || overflow <= 0f) 0f else overflow
}

internal fun clampPan(
    offset: Offset,
    scale: Float,
    viewport: Size,
    contentSize: Size,
    panContentScale: PanContentScale = PanContentScale.FIT
): Offset {
    if (offset.x.isNaN() || offset.y.isNaN() || scale.isNaN()) return Offset.Zero
    if (viewport.width <= 0f || viewport.height <= 0f) return Offset.Zero
    if (contentSize.width <= 0f || contentSize.height <= 0f) return Offset.Zero

    val fitScale = when (panContentScale) {
        PanContentScale.FIT -> min(
            viewport.width / contentSize.width,
            viewport.height / contentSize.height
        )

        PanContentScale.FILL_WIDTH -> viewport.width / contentSize.width
    }
    if (fitScale.isNaN() || fitScale <= 0f) return Offset.Zero

    val displayedWidth = contentSize.width * fitScale * scale
    val displayedHeight = contentSize.height * fitScale * scale
    val maxX = ((displayedWidth - viewport.width) / 2f).coerceAtLeast(0f)
    val maxY = ((displayedHeight - viewport.height) / 2f).coerceAtLeast(0f)
    if (maxX.isNaN() || maxY.isNaN()) return Offset.Zero

    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY)
    )
}

@Composable
fun PoiZoomableImage(
    imageUrl: String?,
    contentDescription: String? = "高清大图",
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    maxDecodeWidthPx: Int = POI_DEFAULT_MAX_DECODE_WIDTH_PX,
    maxDecodeHeightPx: Int? = null,
    contentScale: ContentScale = ContentScale.Fit,
    minScale: Float = 1f,
    maxScale: Float = 4f,
    doubleTapScale: Float = 2.5f,
    onZoomChanged: (Boolean) -> Unit = {},
    onSingleTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onDetectLongImage: (() -> Unit)? = null
) {
    val scale = remember { Animatable(minScale) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()

    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnDetectLongImage by rememberUpdatedState(onDetectLongImage)
    val platformContext = LocalPlatformContext.current

    val isBlankUrl = imageUrl.isNullOrBlank()
    var isImageLoading by remember(imageUrl) { mutableStateOf(!isBlankUrl) }
    var hasImageError by remember(imageUrl) { mutableStateOf(isBlankUrl) }
    var viewportSize by remember { mutableStateOf(Size.Zero) }
    var imageIntrinsicSize by remember(imageUrl) { mutableStateOf(Size.Zero) }

    var isLongImage by remember(imageUrl) { mutableStateOf(false) }

    val isZoomed by remember { derivedStateOf { scale.value > POI_ZOOMED_THRESHOLD } }

    val effectiveContentScale = if (isLongImage) ContentScale.FillWidth else contentScale
    val panContentScale = if (isLongImage) PanContentScale.FILL_WIDTH else PanContentScale.FIT

    val isPanEnabled = isZoomed || isLongImage

    fun resetZoom(animated: Boolean) {
        scope.launch {
            if (animated) {
                scale.animateTo(minScale, animationSpec = tween(POI_ZOOM_ANIMATION_MILLIS))
                if (offset.value != Offset.Zero) {
                    offset.animateTo(Offset.Zero, animationSpec = tween(POI_ZOOM_ANIMATION_MILLIS))
                }
            } else {
                scale.snapTo(minScale)
                offset.snapTo(Offset.Zero)
            }
        }
    }

    var settleJob by remember { mutableStateOf<Job?>(null) }
    fun scheduleSettle() {
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(POI_GESTURE_SETTLE_DELAY_MILLIS)
            val legalScale = scale.value.coerceIn(minScale, maxScale)
            if (legalScale != scale.value) {
                scale.animateTo(legalScale, animationSpec = tween(POI_ZOOM_ANIMATION_MILLIS))
            }

            val targetOffset = if (legalScale <= POI_ZOOMED_THRESHOLD && !isLongImage) {
                Offset.Zero
            } else {
                clampPan(offset.value, legalScale, viewportSize, imageIntrinsicSize, panContentScale)
            }
            if (targetOffset != offset.value) {
                offset.animateTo(targetOffset, animationSpec = tween(POI_ZOOM_ANIMATION_MILLIS))
            }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { isZoomed }.collect { zoomed -> currentOnZoomChanged(zoomed) }
    }

    LaunchedEffect(isActive) {
        if (!isActive) {
            scale.snapTo(minScale)
            offset.snapTo(Offset.Zero)
            currentOnZoomChanged(false)
        }
    }

    LaunchedEffect(isLongImage) {
        if (!isLongImage && offset.value != Offset.Zero) {
            offset.snapTo(Offset.Zero)
        }
    }

    val transformableState = rememberTransformableState { _, zoomChange, panChange, _ ->
        val newScale = dampedScale(
            raw = scale.value * zoomChange,
            current = scale.value,
            minScale = minScale,
            maxScale = maxScale
        )
        val targetOffset = if (newScale > POI_ZOOMED_THRESHOLD || isLongImage) {
            clampPan(
                offset = offset.value + panChange,
                scale = newScale,
                viewport = viewportSize,
                contentSize = imageIntrinsicSize,
                panContentScale = panContentScale
            )
        } else {
            Offset.Zero
        }
        scope.launch {
            scale.snapTo(newScale)
            offset.snapTo(targetOffset)
        }
        scheduleSettle()
    }

    val request = remember(platformContext, imageUrl, maxDecodeWidthPx, maxDecodeHeightPx) {
        val builder = ImageRequest.Builder(platformContext)
            .data(imageUrl)
            .crossfade(true)
        val decodeHeight = maxDecodeHeightPx
        if (decodeHeight != null) {
            builder.size(maxDecodeWidthPx, decodeHeight)
        }
        builder.build()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = Size(it.width.toFloat(), it.height.toFloat()) }

            .then(
                if (isPanEnabled) {
                    Modifier.pointerInput(isPanEnabled, isLongImage) {
                        detectDragGestures { change, dragAmount ->
                            if (!isZoomed && abs(dragAmount.y) <= abs(dragAmount.x)) {
                                return@detectDragGestures
                            }
                            change.consume()
                            val target = clampPan(
                                offset = offset.value + dragAmount,
                                scale = scale.value,
                                viewport = viewportSize,
                                contentSize = imageIntrinsicSize,
                                panContentScale = panContentScale
                            )
                            scope.launch { offset.snapTo(target) }
                        }
                    }
                } else {
                    Modifier
                }
            )

            .transformable(state = transformableState, canPan = { isZoomed })

            .poiDesktopZoomAndPan(
                enabled = true,
                onZoom = { factor, anchor ->
                    val currentScale = scale.value
                    if (!factor.isNaN() && factor != 1f) {
                        val targetScale = (currentScale * factor).coerceIn(minScale, maxScale)
                        if (targetScale != currentScale) {
                            val anchorFromCenter = anchor - Offset(
                                viewportSize.width / 2f,
                                viewportSize.height / 2f
                            )
                            val movedOffset = anchorAdjustedOffset(
                                offset = offset.value,
                                factor = targetScale / currentScale,
                                anchor = anchorFromCenter
                            )
                            val targetOffset =
                                if (targetScale <= POI_ZOOMED_THRESHOLD && !isLongImage) {
                                    Offset.Zero
                                } else {
                                    clampPan(
                                        offset = movedOffset,
                                        scale = targetScale,
                                        viewport = viewportSize,
                                        contentSize = imageIntrinsicSize,
                                        panContentScale = panContentScale
                                    )
                                }
                            scope.launch {
                                scale.snapTo(targetScale)
                                offset.snapTo(targetOffset)
                            }
                        }
                    }
                },
                onPan = { delta ->
                    if (isZoomed) {
                        val target = clampPan(
                            offset = offset.value + delta,
                            scale = scale.value,
                            viewport = viewportSize,
                            contentSize = imageIntrinsicSize,
                            panContentScale = panContentScale
                        )
                        scope.launch { offset.snapTo(target) }
                    }
                },
                isZoomed = { isZoomed }
            )
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {

                    if (isZoomed) {
                        resetZoom(animated = true)
                    } else {
                        currentOnSingleTap?.invoke()
                    }
                },
                onDoubleClick = {
                    if (scale.value > minScale) {
                        resetZoom(animated = true)
                    } else if (isLongImage) {

                        val maxY = maxVerticalPan(
                            scale = scale.value,
                            viewport = viewportSize,
                            contentSize = imageIntrinsicSize,
                            panContentScale = panContentScale
                        )
                        val target = longImageAnchorOffset(
                            offset = offset.value,
                            maxY = maxY,
                            target = nextLongImageAnchor(offset.value)
                        )
                        scope.launch {
                            offset.animateTo(target, animationSpec = tween(POI_ZOOM_ANIMATION_MILLIS))
                        }
                    } else {
                        scope.launch {
                            scale.animateTo(
                                doubleTapScale,
                                animationSpec = tween(POI_ZOOM_ANIMATION_MILLIS)
                            )
                            offset.snapTo(Offset.Zero)
                        }
                    }
                },
                onLongClick = currentOnLongPress?.let { longPress -> { longPress() } }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isBlankUrl || hasImageError) {
            Icon(
                painter = painterResource(PoiIcons.Filled.BrokenImage),
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp)
            )
        } else {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offset.value.x,
                        translationY = offset.value.y
                    ),
                contentScale = effectiveContentScale,
                onSuccess = { state ->
                    isImageLoading = false
                    hasImageError = false
                    val intrinsic = state.painter.intrinsicSize
                    imageIntrinsicSize = intrinsic

                    if (maxDecodeHeightPx != null &&
                        isLongImageRatio(intrinsic.width, intrinsic.height) &&
                        !isLongImage
                    ) {
                        isLongImage = true

                        val maxY = maxVerticalPan(
                            scale = scale.value,
                            viewport = viewportSize,
                            contentSize = imageIntrinsicSize,
                            panContentScale = PanContentScale.FILL_WIDTH
                        )
                        scope.launch { offset.snapTo(Offset(0f, maxY)) }
                        currentOnDetectLongImage?.invoke()
                    }
                },
                onError = {
                    isImageLoading = false
                    hasImageError = true
                }
            )

            if (isImageLoading) {
                LoadingIndicator(
                    modifier = Modifier.size(36.dp),
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}
