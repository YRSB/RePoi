package com.nobody.repoi.ui.navigation

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class BottomBarScrollStateTest {

    @Test
    fun calculateBottomBarContentPadding_whenBottomBarExists_addsExtraPaddingToBarHeight() {
        val result = calculateBottomBarContentPadding(
            bottomBarHeightDp = 80.dp,
            navBarBottomPadding = 24.dp,
            extraPadding = 16.dp
        )

        assertEquals(96.dp, result)
    }

    @Test
    fun calculateBottomBarContentPadding_whenBottomBarDoesNotExist_fallbacksToNavBarPadding() {
        val result = calculateBottomBarContentPadding(
            bottomBarHeightDp = 0.dp,
            navBarBottomPadding = 24.dp,
            extraPadding = 16.dp
        )

        assertEquals(40.dp, result)
    }

    @Test
    fun calculateBottomBarContentPadding_onDesktop_returnsExtraPadding() {
        val result = calculateBottomBarContentPadding(
            bottomBarHeightDp = 0.dp,
            navBarBottomPadding = 0.dp,
            extraPadding = 20.dp
        )

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

        assertEquals(42.dp, result)
    }
}
