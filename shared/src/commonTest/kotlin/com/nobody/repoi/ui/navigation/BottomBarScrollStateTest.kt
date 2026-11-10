package com.nobody.repoi.ui.navigation

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 底栏尺寸状态与自适应避让内边距纯逻辑测试（KMP commonTest）。
 */
class BottomBarScrollStateTest {

    @Test
    fun calculateBottomBarContentPadding_whenBottomBarExists_addsExtraPaddingToBarHeight() {
        val result = calculateBottomBarContentPadding(
            bottomBarHeightDp = 80.dp,
            navBarBottomPadding = 24.dp,
            extraPadding = 16.dp
        )
        // 紧凑屏常驻底栏存在时，以实际测量的底栏高度为基准避让
        assertEquals(96.dp, result)
    }

    @Test
    fun calculateBottomBarContentPadding_whenBottomBarDoesNotExist_fallbacksToNavBarPadding() {
        val result = calculateBottomBarContentPadding(
            bottomBarHeightDp = 0.dp,
            navBarBottomPadding = 24.dp,
            extraPadding = 16.dp
        )
        // 侧边栏/平板等无底栏时，回退避让系统手势导航栏
        assertEquals(40.dp, result)
    }

    @Test
    fun calculateBottomBarContentPadding_onDesktop_returnsExtraPadding() {
        val result = calculateBottomBarContentPadding(
            bottomBarHeightDp = 0.dp,
            navBarBottomPadding = 0.dp,
            extraPadding = 20.dp
        )
        // 桌面平台无系统手势栏与底栏，仅保留内容呼吸留白
        assertEquals(20.dp, result)
    }

    @Test
    fun calculateBottomBarFabPadding_whenBottomBarExists_usesCompactPadding() {
        val result = calculateBottomBarFabPadding(
            bottomBarHeightDp = 80.dp,
            navBarBottomPadding = 24.dp,
            compactPadding = 16.dp,
            wideScreenPadding = 24.dp
        )
        // 紧凑屏 FAB 停留在底栏上方 16.dp
        assertEquals(96.dp, result)
    }

    @Test
    fun calculateBottomBarFabPadding_whenBottomBarDoesNotExist_usesWideScreenPadding() {
        val result = calculateBottomBarFabPadding(
            bottomBarHeightDp = 0.dp,
            navBarBottomPadding = 18.dp,
            compactPadding = 16.dp,
            wideScreenPadding = 24.dp
        )
        // 侧边栏/大屏模式 FAB 停留在屏幕底部安全区上方 24.dp
        assertEquals(42.dp, result)
    }
}
