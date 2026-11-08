package com.nobody.repoi.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

/**
 * 桌面指针增强（android）：空实现。
 *
 * 触控设备上的捏合、单指平移、双击、单击已由 `commonMain` 的 `transformable` /
 * `detectDragGestures` / `combinedClickable` 覆盖；Android 无鼠标滚轮语义，
 * 原样返回 `Modifier` 即可保证编译通过且行为不变。
 */
@Composable
actual fun Modifier.poiDesktopZoomAndPan(
    enabled: Boolean,
    onZoom: (factor: Float, anchor: Offset) -> Unit,
    onPan: (delta: Offset) -> Unit,
    isZoomed: () -> Boolean
): Modifier = this
