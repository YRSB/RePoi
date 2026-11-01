package com.nobody.repoi.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

class BottomBarScrollState {
    var collapseFraction by mutableFloatStateOf(0f)
    var barHeightPx by mutableFloatStateOf(0f)

    private var selfDrivenCount by mutableIntStateOf(0)
    val selfDriven: Boolean get() = selfDrivenCount > 0

    fun acquireSelfDriven() {
        selfDrivenCount += 1
    }

    fun releaseSelfDriven() {
        selfDrivenCount = (selfDrivenCount - 1).coerceAtLeast(0)
    }
}

val LocalBottomBarScrollState = staticCompositionLocalOf<BottomBarScrollState?> { null }
