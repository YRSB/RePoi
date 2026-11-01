package com.nobody.repoi.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import com.nobody.repoi.ui.navigation.LocalBottomBarScrollState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncedBottomBarCollapse(
    scrollBehavior: TopAppBarScrollBehavior,
    driverHeightPx: Float,
    enabled: Boolean = true,
) {
    val bottomBarState = LocalBottomBarScrollState.current ?: return

    DisposableEffect(bottomBarState, enabled) {
        if (enabled) {
            bottomBarState.acquireSelfDriven()
        }
        onDispose {
            if (enabled) {
                bottomBarState.releaseSelfDriven()
            }
        }
    }

    LaunchedEffect(scrollBehavior.state, bottomBarState, driverHeightPx, enabled) {
        if (!enabled || driverHeightPx <= 0f) return@LaunchedEffect
        snapshotFlow { scrollBehavior.state.heightOffset }
            .collect { offset ->
                bottomBarState.collapseFraction =
                    (-offset / driverHeightPx).coerceIn(0f, 1f)
            }
    }
}
