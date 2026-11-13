package com.nobody.repoi.ui.search

import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import kotlin.math.abs

/** 搜索分类页签指示器的测试句柄。 */
internal const val SearchTabIndicatorTestTag = "search-tab-indicator"

/**
 * 搜索分类页签指示器：随 `HorizontalPager` 拖拽在相邻页签的 `TabPosition` 间线性插值。
 *
 * 防坑契约：Material 3 的 `ScrollableTabRow` 以指示器 Placeable 的**宽度**计算页签内居中偏移
 * `(tabWidth - placeable.width) / 2`，这一偏移允许为负且无下限保护。因此指示器自身的布局宽度
 * 必须等于页签内容宽，绘制位置只平移 `TabPosition.left`；若返回整行宽度，居中偏移会变成大幅
 * 负数，把指示器整体推出屏幕（表现为下划线停留在首 tab 甚至贴左边框）。
 *
 * @param page 当前分页索引（拖拽过程中取 `PagerState.currentPage`）
 * @param pageOffsetFraction 当前分页的偏移比例，向高页拖动为正、向低页拖动为负
 */
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
                    // 在相邻页签的 left / contentWidth 间线性插值，拖拽过程中指示器连续跟随
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
                    // 布局宽度固定为内容宽（居中偏移由 TabRow 依选中页叠加），绘制位置只平移 left
                    layout(indicatorWidthPx, placeable.height) { placeable.place(x = tabLeftPx, y = 0) }
                },
        width = Dp.Unspecified
    )
}
