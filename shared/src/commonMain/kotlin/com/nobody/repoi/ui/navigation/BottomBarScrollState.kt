package com.nobody.repoi.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

class BottomBarScrollState {
    var collapseFraction by mutableFloatStateOf(0f)
}

val LocalBottomBarScrollState = staticCompositionLocalOf<BottomBarScrollState?> { null }
