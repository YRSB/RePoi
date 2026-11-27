package com.nobody.repoi.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class BottomBarScrollState(
    internal val isVisibleProvider: () -> Boolean = { true }
) {
    var barHeightPx by mutableFloatStateOf(0f)
    val isBottomBarVisible: Boolean
        get() = isVisibleProvider()
}

val LocalBottomBarScrollState = staticCompositionLocalOf<BottomBarScrollState?> { null }

internal fun calculateBottomBarContentPadding(
    bottomBarHeightDp: Dp,
    navBarBottomPadding: Dp,
    extraPadding: Dp
): Dp {
    return if (bottomBarHeightDp > 0.dp) {
        bottomBarHeightDp + extraPadding
    } else {
        navBarBottomPadding + extraPadding
    }
}

internal fun calculateBottomBarFabPadding(
    bottomBarHeightDp: Dp,
    navBarBottomPadding: Dp,
    compactPadding: Dp,
    wideScreenPadding: Dp
): Dp {
    return if (bottomBarHeightDp > 0.dp) {
        bottomBarHeightDp + compactPadding
    } else {
        navBarBottomPadding + wideScreenPadding
    }
}

@Composable
private fun rememberEffectiveBottomBarHeightDp(): Dp {
    val bottomBarState = LocalBottomBarScrollState.current
    val density = LocalDensity.current
    if (bottomBarState == null || !bottomBarState.isBottomBarVisible) {
        return 0.dp
    }
    return with(density) { bottomBarState.barHeightPx.toDp() }
}

@Composable
fun rememberBottomBarContentPadding(extraPadding: Dp = 16.dp): Dp {
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = rememberEffectiveBottomBarHeightDp()
    return remember(bottomBarHeightDp, navBarBottomPadding, extraPadding) {
        calculateBottomBarContentPadding(bottomBarHeightDp, navBarBottomPadding, extraPadding)
    }
}

@Composable
fun rememberBottomBarFabPadding(
    compactPadding: Dp = 16.dp,
    wideScreenPadding: Dp = 24.dp
): Dp {
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = rememberEffectiveBottomBarHeightDp()
    return remember(bottomBarHeightDp, navBarBottomPadding, compactPadding, wideScreenPadding) {
        calculateBottomBarFabPadding(bottomBarHeightDp, navBarBottomPadding, compactPadding, wideScreenPadding)
    }
}

@Composable
fun rememberBottomBarExtraPadding(): Dp {
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = rememberEffectiveBottomBarHeightDp()
    return remember(bottomBarHeightDp, navBarBottomPadding) {
        if (bottomBarHeightDp > 0.dp) {
            (bottomBarHeightDp - navBarBottomPadding).coerceAtLeast(0.dp)
        } else {
            0.dp
        }
    }
}
