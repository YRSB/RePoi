package com.nobody.repoi.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
class HomeHeaderStackState internal constructor(

    val topBarHeight: Dp,
    private val density: Density,
    estimatedRowHeight: Dp
) {

    var tabRowHeightPx by mutableFloatStateOf(with(density) { estimatedRowHeight.toPx() })
        private set

    var collapsibleHeightPx by mutableFloatStateOf(with(density) { estimatedRowHeight.toPx() })
        private set

    val tabRowHeight: Dp
        get() = with(density) { tabRowHeightPx.toDp() }

    val collapsibleHeight: Dp
        get() = with(density) { collapsibleHeightPx.toDp() }

    val pinnedHeight: Dp
        get() = topBarHeight + tabRowHeight

    val totalHeaderHeight: Dp
        get() = pinnedHeight + collapsibleHeight

    fun onTabRowMeasured(heightPx: Int) {
        if (heightPx > 0) tabRowHeightPx = heightPx.toFloat()
    }

    fun onCollapsibleMeasured(heightPx: Int) {
        if (heightPx > 0) collapsibleHeightPx = heightPx.toFloat()
    }

    fun resetCollapsibleHeight() {
        collapsibleHeightPx = 0f
    }

    fun clearMeasuredRows() {
        tabRowHeightPx = 0f
        collapsibleHeightPx = 0f
    }
}

@Composable
fun rememberHomeHeaderStack(
    topBarHeight: Dp,
    estimatedRowHeight: Dp = 48.dp
): HomeHeaderStackState {
    val density = LocalDensity.current
    return remember(topBarHeight, density.density, density.fontScale) {
        HomeHeaderStackState(
            topBarHeight = topBarHeight,
            density = density,
            estimatedRowHeight = estimatedRowHeight
        )
    }
}
