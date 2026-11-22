package com.nobody.repoi.ui.search

import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import kotlin.math.abs

internal const val SearchTabIndicatorTestTag = "search-tab-indicator"

@Composable
internal fun TabIndicatorScope.SearchTabIndicator(
    page: () -> Int,
    pageOffsetFraction: () -> Float,
    modifier: Modifier = Modifier
) {
    TabRowDefaults.PrimaryIndicator(
        modifier =
            modifier
                .testTag(SearchTabIndicatorTestTag)
                .tabIndicatorLayout { measurable, constraints, tabPositions ->
                    if (tabPositions.isEmpty()) {
                        return@tabIndicatorLayout layout(0, 0) {}
                    }
                    val currentPage = page().coerceIn(0, tabPositions.lastIndex)
                    val fraction = pageOffsetFraction().coerceIn(-1f, 1f)
                    val neighborPage =
                        if (fraction >= 0f) {
                            (currentPage + 1).coerceAtMost(tabPositions.lastIndex)
                        } else {
                            (currentPage - 1).coerceAtLeast(0)
                        }
                    val progress = abs(fraction)
                    val currentPosition = tabPositions[currentPage]
                    val neighborPosition = tabPositions[neighborPage]

                    val tabLeftPx =
                        lerp(currentPosition.left, neighborPosition.left, progress).roundToPx()
                    val indicatorWidthPx =
                        lerp(currentPosition.contentWidth, neighborPosition.contentWidth, progress)
                            .roundToPx()
                            .coerceAtLeast(1)
                    val placeable =
                        measurable.measure(
                            constraints.copy(minWidth = indicatorWidthPx, maxWidth = indicatorWidthPx)
                        )

                    layout(indicatorWidthPx, placeable.height) { placeable.place(x = tabLeftPx, y = 0) }
                },
        width = Dp.Unspecified
    )
}
