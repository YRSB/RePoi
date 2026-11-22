package com.nobody.repoi.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND

@Composable
fun <T> AdaptiveChunkedGrid(
    items: List<T>,
    modifier: Modifier = Modifier,
    maxRows: Int = 3,
    mediumColumns: Int = 4,
    wideColumns: Int = 6,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    keyProvider: ((T) -> Any)? = null,
    itemContent: @Composable BoxScope.(item: T) -> Unit
) {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val columns = when {
        windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) -> wideColumns
        windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) -> mediumColumns
        else -> 3
    }
    val displayItems = remember(items, columns, maxRows) { items.take(columns * maxRows) }
    val rows = remember(displayItems, columns) { displayItems.chunked(columns) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        rows.forEachIndexed { rowIndex, rowItems ->
            key(rowIndex) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
                ) {
                    rowItems.forEach { item ->
                        val itemKey = keyProvider?.invoke(item)
                        if (itemKey != null) {
                            key(itemKey) {
                                Box(modifier = Modifier.weight(1f)) {
                                    itemContent(item)
                                }
                            }
                        } else {
                            Box(modifier = Modifier.weight(1f)) {
                                itemContent(item)
                            }
                        }
                    }
                    val remaining = columns - rowItems.size
                    if (remaining > 0) {
                        Spacer(modifier = Modifier.weight(remaining.toFloat()))
                    }
                }
            }
        }
    }
}
