package com.nobody.repoi.ui.album

import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND

/**
 * 相册瀑布流自适应列数策略：
 * - 超大屏/桌面端 (≥ 1400dp)：5 列；
 * - 大平板横竖屏/展开态大屏 (840dp ~ 1400dp EXPANDED)：4 列；
 * - 折叠屏内屏/中屏 (600dp ~ 840dp MEDIUM)：3 列；
 * - 手机端 (< 600dp COMPACT)：稳定 2 列。
 *
 * Wave 6 自 `AlbumScreen.kt` 前移，供 search / detail-album / picture 共用。
 */
@Composable
fun rememberAlbumStaggeredGridColumns(): StaggeredGridCells {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val windowSizeClass = adaptiveInfo.windowSizeClass
    return remember(windowSizeClass) {
        when {
            windowSizeClass.isWidthAtLeastBreakpoint(1400) -> StaggeredGridCells.Fixed(5)
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) -> StaggeredGridCells.Fixed(4)
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) -> StaggeredGridCells.Fixed(3)
            else -> StaggeredGridCells.Fixed(2)
        }
    }
}
