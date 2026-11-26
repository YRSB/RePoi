package com.nobody.repoi.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal fun calculateBannerHeight(
    containerWidth: Dp,
    ratio: Float = 2f,
    maxHeight: Dp = 240.dp
): Dp = minOf(containerWidth / ratio, maxHeight)

internal fun Modifier.bannerAspectRatio(
    ratio: Float = 2f,
    maxHeight: Dp = 240.dp
): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    } else {
        val width = constraints.maxWidth
        val maxHeightPx = maxHeight.roundToPx()
        val targetHeight = (width / ratio)
            .roundToInt()
            .coerceAtMost(maxHeightPx)
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = width,
                maxWidth = width,
                minHeight = targetHeight,
                maxHeight = targetHeight
            )
        )
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
}
