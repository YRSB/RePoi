package com.nobody.repoi.ui.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 底栏尺寸状态。
 *
 * 底栏在紧凑屏以 [androidx.compose.material3.ShortNavigationBar] 呈现，常驻展示不折叠，
 * 页面通过 [rememberBottomBarContentPadding] / [rememberBottomBarFabPadding] 统一自适应避让。
 */
class BottomBarScrollState {
    var barHeightPx by mutableFloatStateOf(0f)
}

val LocalBottomBarScrollState = staticCompositionLocalOf<BottomBarScrollState?> { null }

/**
 * 纯逻辑计算顶层主屏内容在底部的避让内边距。
 */
internal fun calculateBottomBarContentPadding(
    bottomBarHeightDp: Dp,
    navBarBottomPadding: Dp,
    extraPadding: Dp
): Dp {
    return if (bottomBarHeightDp > 0.dp) {
        bottomBarHeightDp + extraPadding
    } else {
        navBarBottomPadding + extraPadding
    }
}

/**
 * 纯逻辑计算顶层主屏悬浮操作按钮（FAB）在底部的避让内边距。
 */
internal fun calculateBottomBarFabPadding(
    bottomBarHeightDp: Dp,
    navBarBottomPadding: Dp,
    compactPadding: Dp,
    wideScreenPadding: Dp
): Dp {
    return if (bottomBarHeightDp > 0.dp) {
        bottomBarHeightDp + compactPadding
    } else {
        navBarBottomPadding + wideScreenPadding
    }
}

/**
 * 计算顶层主屏内容区域在底部的自适应避让内边距。
 *
 * 统一避让规则（无魔法数字）：
 * - 紧凑屏且常驻底栏存在时（[bottomBarHeightDp] > 0）：避让完整底栏高度（含系统导航条）+ [extraPadding]；
 * - 侧边栏/桌面端等无底栏时：避让系统底部导航栏（[WindowInsets.navigationBars]）+ [extraPadding]。
 *
 * @param extraPadding 内容最底端与底栏或系统栏之间的安全留白（默认 16.dp）
 */
@Composable
fun rememberBottomBarContentPadding(extraPadding: Dp = 16.dp): Dp {
    val bottomBarState = LocalBottomBarScrollState.current
    val density = LocalDensity.current
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = with(density) { (bottomBarState?.barHeightPx ?: 0f).toDp() }
    return remember(bottomBarHeightDp, navBarBottomPadding, extraPadding) {
        calculateBottomBarContentPadding(bottomBarHeightDp, navBarBottomPadding, extraPadding)
    }
}

/**
 * 计算顶层主屏悬浮操作按钮（FAB）在底部的自适应避让内边距。
 *
 * 统一避让规则：
 * - 紧凑屏常驻底栏存在时：FAB 悬浮于底栏顶部之上 [compactPadding]（默认 16.dp）；
 * - 侧边栏/桌面端等无底栏时：FAB 悬浮于屏幕底部（含系统导航条）之上 [wideScreenPadding]（默认 24.dp）。
 *
 * @param compactPadding 紧凑屏底栏存在时的留白（默认 16.dp）
 * @param wideScreenPadding 大屏或无底栏时的留白（默认 24.dp）
 */
@Composable
fun rememberBottomBarFabPadding(
    compactPadding: Dp = 16.dp,
    wideScreenPadding: Dp = 24.dp
): Dp {
    val bottomBarState = LocalBottomBarScrollState.current
    val density = LocalDensity.current
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = with(density) { (bottomBarState?.barHeightPx ?: 0f).toDp() }
    return remember(bottomBarHeightDp, navBarBottomPadding, compactPadding, wideScreenPadding) {
        calculateBottomBarFabPadding(bottomBarHeightDp, navBarBottomPadding, compactPadding, wideScreenPadding)
    }
}
