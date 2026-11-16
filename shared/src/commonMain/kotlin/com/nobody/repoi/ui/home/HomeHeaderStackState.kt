package com.nobody.repoi.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 首页可配置头部高度栈（顶栏 + 二级板块 Tab 行 + 可折叠筛选行），高度栈的唯一真相来源。
 *
 * 背景（issue #155）：首页头部从「顶栏 + 筛选行」两层扩展为三层，若继续用散落常量拼
 * `totalHeaderHeight`，加一层就会漏改某处（下拉指示器 / 内容 padding / 首屏各态卡片 / 折叠分母），
 * 因此把三个高度收敛到一个状态对象，所有依赖高度的地方只读它。
 *
 * 分层语义：
 * - [topBarHeight]：顶栏高度（含状态栏内边距），由宿主常量给出；
 * - [tabRowHeightPx]：二级 Tab 行实测高度（常驻吸顶、不随滚动折叠）；
 * - [collapsibleHeightPx]：三级筛选行实测高度，既是折叠位移上限也是折叠比例的分母。
 *
 * 首帧测量前用 [estimatedRowHeight] 兜底，避免 measurement 前的内容位移抖动；
 * 实测值只增不降到 0（`onSizeChanged` 在动画中间态可能报 0，忽略之）。
 */
@Stable
class HomeHeaderStackState internal constructor(
    /** 顶栏高度（含状态栏内边距）。 */
    val topBarHeight: Dp,
    private val density: Density,
    estimatedRowHeight: Dp
) {
    /** 二级 Tab 行实测高度（px）。 */
    var tabRowHeightPx by mutableFloatStateOf(with(density) { estimatedRowHeight.toPx() })
        private set

    /** 三级筛选行实测高度（px），折叠驱动的分母。 */
    var collapsibleHeightPx by mutableFloatStateOf(with(density) { estimatedRowHeight.toPx() })
        private set

    /** 二级 Tab 行高度（Dp）。 */
    val tabRowHeight: Dp
        get() = with(density) { tabRowHeightPx.toDp() }

    /** 三级筛选行高度（Dp）。 */
    val collapsibleHeight: Dp
        get() = with(density) { collapsibleHeightPx.toDp() }

    /** 常驻吸顶层高度（顶栏 + 二级 Tab），折叠筛选行的位移锚点。 */
    val pinnedHeight: Dp
        get() = topBarHeight + tabRowHeight

    /** 头部总高度：内容 `contentPadding(top)`、下拉指示器 padding、首屏各态卡片统一读它。 */
    val totalHeaderHeight: Dp
        get() = pinnedHeight + collapsibleHeight

    /** 回填二级 Tab 行实测高度。 */
    fun onTabRowMeasured(heightPx: Int) {
        if (heightPx > 0) tabRowHeightPx = heightPx.toFloat()
    }

    /** 回填三级筛选行实测高度。 */
    fun onCollapsibleMeasured(heightPx: Int) {
        if (heightPx > 0) collapsibleHeightPx = heightPx.toFloat()
    }

    /**
     * 当前板块没有可折叠筛选行（实体 5 类）时归零。
     *
     * 此时筛选行不参与布局，`onSizeChanged` 不会再回填，必须显式清零，否则会沿用上一板块
     * 约 48–56dp 的旧高度，造成内容顶部留白与折叠位移错误。
     */
    fun resetCollapsibleHeight() {
        collapsibleHeightPx = 0f
    }

    /** 无可用板块（不渲染 Tab 行与筛选行）时把两层实测高度归零，避免内容被空档顶开。 */
    fun clearMeasuredRows() {
        tabRowHeightPx = 0f
        collapsibleHeightPx = 0f
    }
}

/**
 * 创建首页头部高度栈状态，并在密度变化（字体缩放）时重建以保持 Dp/px 换算一致。
 *
 * @param topBarHeight 顶栏高度（含状态栏内边距）
 * @param estimatedRowHeight 两层行高在首帧实测前的兜底估值（默认 48dp）
 */
@Composable
fun rememberHomeHeaderStack(
    topBarHeight: Dp,
    estimatedRowHeight: Dp = 48.dp
): HomeHeaderStackState {
    val density = LocalDensity.current
    return remember(topBarHeight, density.density, density.fontScale) {
        HomeHeaderStackState(
            topBarHeight = topBarHeight,
            density = density,
            estimatedRowHeight = estimatedRowHeight
        )
    }
}
