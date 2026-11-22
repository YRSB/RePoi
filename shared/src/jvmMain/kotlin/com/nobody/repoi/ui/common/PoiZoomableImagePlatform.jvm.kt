package com.nobody.repoi.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

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

                    PointerEventType.Press -> {
                        val pressed = event.changes.firstOrNull()
                        if (pressed != null && isZoomed()) {
                            var lastPosition = pressed.position
                            var isPointerDown = true
                            while (isPointerDown) {
                                val moveEvent = awaitPointerEvent()
                                isPointerDown = moveEvent.changes.any { it.pressed }
                                val moveChange = moveEvent.changes.firstOrNull()
                                if (moveChange != null) {
                                    val delta = moveChange.position - lastPosition
                                    lastPosition = moveChange.position
                                    if (delta != Offset.Zero) {
                                        onPan(delta)
                                        moveEvent.changes.forEach { it.consume() }
                                    }
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
