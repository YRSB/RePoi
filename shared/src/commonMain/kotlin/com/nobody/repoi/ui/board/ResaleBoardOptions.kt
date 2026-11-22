package com.nobody.repoi.ui.board

import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.nobody.repoi.platform.isLandscapeOrientation

object ResaleBoardOptions {

    const val DEFAULT_TYPE: Int = 1

    const val PAGE_SIZE: Int = 24

    val TYPES: List<Pair<Int, String>> = listOf(
        1 to "出售",
        2 to "求购"
    )

    val PURITIES: List<Pair<String?, String>> = listOf(
        null to "全部成色",
        "11" to "全新未拆",
        "9" to "仅拆封",
        "8" to "二手好相",
        "7" to "瑕疵品"
    )

    val TYPE_KEYS: Set<Int> = TYPES.map { it.first }.toSet()

    val PURITY_KEYS: Set<String> = PURITIES.mapNotNull { it.first }.toSet()

    fun isValidType(type: Int): Boolean = type in TYPE_KEYS

    fun isValidPurity(purity: String?): Boolean = purity == null || purity in PURITY_KEYS
}

@Composable
fun rememberResaleStaggeredGridColumns(): StaggeredGridCells {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val isLandscape = isLandscapeOrientation()
    return remember(windowSizeClass, isLandscape) {
        when {
            !isLandscape -> StaggeredGridCells.Fixed(1)
            windowSizeClass.isWidthAtLeastBreakpoint(1200) -> StaggeredGridCells.Fixed(3)
            else -> StaggeredGridCells.Fixed(2)
        }
    }
}
