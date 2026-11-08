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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
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

/** 判定「已放大」的缩放阈值：不高于该值视为未放大（单指滑动交还外层 `HorizontalPager`）。 */
internal const val POI_ZOOMED_THRESHOLD: Float = 1.02f

/** 捏合超上界时的阻尼系数（越小越「硬」，松手后回弹到合法区间）。 */
internal const val POI_ZOOM_DAMPING: Float = 0.25f

/** 桌面滚轮灵敏度：每格 `scrollDelta.y` 换算的缩放增量系数。 */
internal const val POI_WHEEL_ZOOM_SENSITIVITY: Float = 0.12f

/** 桌面滚轮单格缩放倍率下限（防止触控板惯性一次放大到离谱倍率）。 */
internal const val POI_WHEEL_ZOOM_MIN_FACTOR: Float = 0.85f

/** 桌面滚轮单格缩放倍率上限。 */
internal const val POI_WHEEL_ZOOM_MAX_FACTOR: Float = 1.18f

/** 双击切换与回弹动画时长（毫秒）。 */
internal const val POI_ZOOM_ANIMATION_MILLIS: Int = 220

/** 手势静止多久（毫秒）后视为松手并触发回弹收敛。 */
internal const val POI_GESTURE_SETTLE_DELAY_MILLIS: Long = 140L

/** 超长图判定阈值：解码后图片的**高宽比**超过该值即视为超长图（与规格原文「高宽比 > 3」一致）。 */
internal const val POI_LONG_IMAGE_THRESHOLD: Float = 3f

/**
 * 默认解码宽度上限：不按屏幕宽度夹取（避免竖屏手机把横图压糊），
 * 只在高度侧配合 `maxDecodeHeightPx` 限制，防止超长图 OOM。
 */
internal const val POI_DEFAULT_MAX_DECODE_WIDTH_PX: Int = 4096

/**
 * 滚轮增量 → 缩放倍率（纯函数）。
 *
 * 约定 `scrollDelta.y` 向上滚为负、向下滚为正（与 Compose Desktop 一致），
 * 故倍率取 `1 - delta * 灵敏度`，并夹取在
 * [POI_WHEEL_ZOOM_MIN_FACTOR]..[POI_WHEEL_ZOOM_MAX_FACTOR]。
 * 对 `0` / `NaN` 增量返回 `1f`（表示不缩放）。
 */
internal fun wheelZoomFactor(scrollDeltaY: Float): Float {
    if (scrollDeltaY.isNaN() || scrollDeltaY == 0f) return 1f
    val raw = 1f - scrollDeltaY * POI_WHEEL_ZOOM_SENSITIVITY
    if (raw.isNaN()) return 1f
    return raw.coerceIn(POI_WHEEL_ZOOM_MIN_FACTOR, POI_WHEEL_ZOOM_MAX_FACTOR)
}

/**
 * 捏合缩放的目标值（带橡皮筋阻尼）。
 *
 * 未超上界时返回原始倍率；超上界时只走一部分
 *（`current + (raw - current) * damping`），把「继续放大」收敛成轻微拉伸，
 * 松手后由回弹动画拉回 [maxScale]。下界直接夹取到 [minScale]。
 * `NaN` 防护：[raw] 为 `NaN` 时保持 [current]，[current] 为 `NaN` 时退回 [minScale]。
 */
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

/**
 * 以指针为锚点的偏移换算（纯函数）：缩放倍率为 [factor] 时让 [anchor]
 *（相对视口中心的坐标）下方的画面点保持不动。
 *
 * 渲染模型为「以中心为原点、先缩放 `s`、再平移 `t`」，锚点 `a` 对应画面点
 * `c = (a - t) / s`；要求缩放后 `c` 仍落在 `a`，解得 `t' = factor * t + (1 - factor) * a`。
 * 任一分量 `NaN` 时原样返回 [offset]。
 */
internal fun anchorAdjustedOffset(offset: Offset, factor: Float, anchor: Offset): Offset {
    if (factor.isNaN() || offset.x.isNaN() || offset.y.isNaN() ||
        anchor.x.isNaN() || anchor.y.isNaN()
    ) {
        return offset
    }
    return offset * factor + anchor * (1f - factor)
}

/**
 * 是否属于「超长图」（纯函数）：解码后**高宽比**大于 [threshold] 即命中。
 *
 * 不能用「高度超过高度上限的倍数」判定：`ImageRequest.size()` 已把解码结果夹取到
 * `maxDecodeWidthPx × maxDecodeHeightPx`，`intrinsicSize.height` 恒不超过上限，那个判据永不成立。
 * 宽/高任一非正或为 `NaN` 时返回 `false`（矢量图与未指定尺寸的 painter）。
 */
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

/**
 * 平移钳制时使用的拟合语义。
 *
 * - [FIT]：与 `ContentScale.Fit` 一致，等比塞进视口（常规大图）；
 * - [FILL_WIDTH]：与 `ContentScale.FillWidth` 一致，宽度撑满、高度自然溢出（超长图纵向浏览）。
 */
internal enum class PanContentScale { FIT, FILL_WIDTH }

/**
 * 平移边界钳制（纯函数）：把 [offset] 夹到「放大后的图片不露黑边」的合法范围。
 *
 * 先按 [panContentScale] 语义把 [contentSize] 等比塞进 [viewport]，再乘 [scale]
 * 得到实际显示尺寸；两轴允许的最大位移即溢出量的一半。
 * 尺寸非正、[scale] 非正或出现 `NaN` 时退化为 `Offset.Zero`。
 */
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

/**
 * 可缩放大图基元（全屏查看器与图库共用）。
 *
 * 行为契约：
 * - **捏合缩放**：范围 `minScale..maxScale`；超上界按 [dampedScale] 阻尼收敛，
 *   松手（[POI_GESTURE_SETTLE_DELAY_MILLIS] 内无新事件）回弹到合法区间；
 *   未放大到阈值以上时偏移归零。
 * - **双击**：在 [minScale] 与 [doubleTapScale] 之间以 [tween]（[POI_ZOOM_ANIMATION_MILLIS]）
 *   动画切换；放大态双击复原并清零偏移。
 * - **单指平移**：仅放大态（`scale > `[POI_ZOOMED_THRESHOLD]`）拦截拖拽，
 *   未放大时不拦截滑动，交还外层 `HorizontalPager` 翻页；平移量按 [clampPan] 钳制。
 * - **单击语义**：放大态单击**只复位缩放与偏移、不冒泡** [onSingleTap]；
 *   未放大时单击冒泡 [onSingleTap]（宿主据此切换 chrome 显隐或关闭弹窗）。
 * - **切页复位**：[isActive] 变 `false` 时复位缩放与偏移并回调 `onZoomChanged(false)`。
 * - **桌面端**：滚轮缩放（以指针为锚点）与放大态左键拖拽平移经
 *   [poiDesktopZoomAndPan] 的 `expect/actual` 平台基元提供，`commonMain` 不出现 JVM-only API。
 * - **超长图保护**：解码尺寸按 [maxDecodeWidthPx] × [maxDecodeHeightPx] 夹取；
 *   [maxDecodeHeightPx] 非空且解码后**高宽比**超过 [POI_LONG_IMAGE_THRESHOLD] 时回调
 *   [onDetectLongImage]（不能按「高度超过上限倍数」判定，解码已被夹取，那个判据永不成立）。
 * - **长图纵向浏览**：命中超长图后改用 `ContentScale.FillWidth`（宽度撑满、高度自然溢出），
 *   未放大也允许**纵向**拖拽平移（横向拖拽不拦截、不消费，交还外层 `HorizontalPager` 翻页），
 *   平移量仍按 [clampPan] 的 `FILL_WIDTH` 语义钳制。
 *
 * @param imageUrl 绝对图片 URL；为 `null` 或空白时只显示破损占位，不发网络请求
 * @param contentDescription 无障碍描述，默认「高清大图」
 * @param modifier 外层修饰符
 * @param isActive 是否为当前页；`false` 时复位缩放与偏移
 * @param maxDecodeWidthPx 解码宽度上限
 * @param maxDecodeHeightPx 解码高度上限；`null` 表示不夹取高度
 * @param contentScale 图片填充方式；命中超长图时会自动切换为 `ContentScale.FillWidth`
 * @param minScale 未放大基准缩放（最小值）
 * @param maxScale 最大缩放
 * @param doubleTapScale 双击放大目标倍率
 * @param onZoomChanged 跨越放大阈值的状态回调（仅在状态翻转时触发，非每帧）
 * @param onSingleTap 未放大态单击回调；放大态单击只复位缩放、不触发该回调
 * @param onLongPress 长按回调；为 `null` 时不触发长按
 * @param onDetectLongImage 解码后发现超长图时回调
 */
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
    // 超长图态：开启纵向浏览（FillWidth + 未放大也可纵向拖拽）；换图时随 URL 重置
    var isLongImage by remember(imageUrl) { mutableStateOf(false) }

    val isZoomed by remember { derivedStateOf { scale.value > POI_ZOOMED_THRESHOLD } }
    // 长图用 FillWidth 拟合（宽度撑满、高度溢出才能滚动），其余沿用调用方给的 Fit
    val effectiveContentScale = if (isLongImage) ContentScale.FillWidth else contentScale
    val panContentScale = if (isLongImage) PanContentScale.FILL_WIDTH else PanContentScale.FIT
    // 未放大也可拖拽：放大态或长图态
    val isPanEnabled = isZoomed || isLongImage

    /** 把缩放与偏移复位到初始态（切页、放大态单击、双击复原共用）。 */
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

    // 手势静止后回弹到合法区间（超上界阻尼值、越界平移量收敛）
    var settleJob by remember { mutableStateOf<Job?>(null) }
    fun scheduleSettle() {
        settleJob?.cancel()
        settleJob = scope.launch {
            delay(POI_GESTURE_SETTLE_DELAY_MILLIS)
            val legalScale = scale.value.coerceIn(minScale, maxScale)
            if (legalScale != scale.value) {
                scale.animateTo(legalScale, animationSpec = tween(POI_ZOOM_ANIMATION_MILLIS))
            }
            // 长图在未放大时同样合法持有纵向偏移，不能像常规图那样强制归零
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

    // 仅在跨越放大阈值时回调，避免每帧触发外层重组
    LaunchedEffect(Unit) {
        snapshotFlow { isZoomed }.collect { zoomed -> currentOnZoomChanged(zoomed) }
    }

    // 切页复位：离开当前页时回到基准缩放并清零偏移
    LaunchedEffect(isActive) {
        if (!isActive) {
            scale.snapTo(minScale)
            offset.snapTo(Offset.Zero)
            currentOnZoomChanged(false)
        }
    }

    // 长图态退出（换图后重新判定为常规图）时清掉残留偏移，避免新图带着旧偏移出现
    LaunchedEffect(isLongImage) {
        if (!isLongImage && offset.value != Offset.Zero) {
            offset.snapTo(Offset.Zero)
        }
    }

    // 双指捏合缩放：transformable 只响应多指，不拦截单指滑动，翻页仍交给外层 Pager
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
            // 放大态或长图态：单指拖拽平移；其余情况不挂载，滑动交还外层 Pager。
            // 长图未放大时只处理「更偏纵向」的拖拽，横向拖拽既不消费也不平移，保证翻页手感。
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
            // canPan 仍只认放大态：长图的单指平移交给上面的 detectDragGestures（可按方向筛选），
            // 否则 transformable 会吃掉横向拖拽、抢走 Pager 翻页
            .transformable(state = transformableState, canPan = { isZoomed })
            // 桌面端：滚轮缩放 + 放大态左键拖拽（android 为空实现）
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
                    // 放大态单击：只复位缩放与偏移，不冒泡（宿主据此不做 chrome 切换）
                    if (isZoomed) {
                        resetZoom(animated = true)
                    } else {
                        currentOnSingleTap?.invoke()
                    }
                },
                onDoubleClick = {
                    if (scale.value > minScale) {
                        resetZoom(animated = true)
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
                imageVector = Icons.Filled.BrokenImage,
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
                    // 纵横比判定（解码已被 size() 夹取，高度倍数判据永不成立）；
                    // maxDecodeHeightPx == null 表示调用方明确不限制高度，也就无需长图保护
                    if (maxDecodeHeightPx != null &&
                        isLongImageRatio(intrinsic.width, intrinsic.height) &&
                        !isLongImage
                    ) {
                        isLongImage = true
                        currentOnDetectLongImage?.invoke()
                    }
                },
                onError = {
                    isImageLoading = false
                    hasImageError = true
                }
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
}
