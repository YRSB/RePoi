package com.nobody.repoi.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 底栏尺寸状态。
 *
 * 底栏只在紧凑屏以 [androidx.compose.material3.ShortNavigationBar] 呈现，折叠已取消，
 * 页面只需读取 [barHeightPx] 来避让悬浮操作按钮。
 */
class BottomBarScrollState {
    var barHeightPx by mutableFloatStateOf(0f)
}

val LocalBottomBarScrollState = staticCompositionLocalOf<BottomBarScrollState?> { null }
