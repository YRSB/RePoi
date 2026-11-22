package com.nobody.repoi.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

@Composable
expect fun Modifier.poiDesktopZoomAndPan(
    enabled: Boolean,
    onZoom: (factor: Float, anchor: Offset) -> Unit,
    onPan: (delta: Offset) -> Unit,
    isZoomed: () -> Boolean
): Modifier
