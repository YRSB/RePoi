package com.nobody.repoi.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 桌面指针增强（jvm）：滚轮缩放 + 放大态左键拖拽平移。
 *
 * 实现要点：
 * - 用 `awaitPointerEventScope` 逐帧读取原始指针事件：`PointerEventType.Scroll` 时把
 *   `scrollDelta.y` 交给 [wheelZoomFactor] 换算倍率，并携带**指针在组件内的局部坐标**作为锚点，
 *   由调用方用 [anchorAdjustedOffset] 保证指针下的画面点不动；
 * - 放大态（[isZoomed] 为真）按下左键后进入拖拽，按 `positionChange()` 上报增量并消费事件，
 *   避免外层 `HorizontalPager` 抢走拖拽；
 * - 未放大时不消费事件，单击/双击仍由 `commonMain` 的 `combinedClickable` 处理。
 *
 * 本文件是唯一允许出现 JVM-only API 的位置，`commonMain` 只持有 `expect` 声明。
 */
@Composable
actual fun Modifier.poiDesktopZoomAndPan(
    enabled: Boolean,
    onZoom: (factor: Float, anchor: Offset) -> Unit,
    onPan: (delta: Offset) -> Unit,
    isZoomed: () -> Boolean
): Modifier {
    if (!enabled) return this
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                    // 滚轮：以指针位置为锚点缩放（向上滚 scrollDelta.y 为负 → 放大）
                    PointerEventType.Scroll -> {
                        val change = event.changes.firstOrNull()
                        if (change != null) {
                            val factor = wheelZoomFactor(change.scrollDelta.y)
                            if (factor != 1f) {
                                onZoom(factor, change.position)
                                change.consume()
                            }
                        }
                    }

                    // 左键按下：放大态进入拖拽平移，直到整组指针抬起
                    PointerEventType.Press -> {
                        val change = event.changes.firstOrNull()
                        if (change != null && isZoomed()) {
                            change.consume()
                            var isPointerDown = true
                            while (isPointerDown) {
                                val moveEvent = awaitPointerEvent()
                                isPointerDown = moveEvent.changes.any { it.pressed }
                                val delta = moveEvent.changes.firstOrNull()?.positionChange()
                                if (delta != null && delta != Offset.Zero) {
                                    onPan(delta)
                                    moveEvent.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}
