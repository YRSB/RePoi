package com.nobody.repoi.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nobody.repoi.platform.isDynamicSeedAvailable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 主题管理器测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `ThemeManagerTest`（逐用例；JUnit4 → kotlin.test，
 * Robolectric → [TestDataStore] 真实 DataStore 隔离目录；
 * legacy 字节扫描已在 shared 删除，不在此覆盖）。
 */
class ThemeManagerTest {

    private fun manager(): ThemeManager = ThemeManager(TestDataStore.themePrefs())

    @Test
    fun setThemeMode_updatesMemoryFlowAndInitialValue() = runTest {
        val manager = manager()
        manager.themeModeFlow.first()
        manager.dynamicColorFlow.first()
        manager.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, manager.themeMode)
        assertEquals(ThemeMode.DARK, manager.getInitialThemeMode())
        assertEquals(ThemeMode.DARK, manager.themeModeFlow.first())
    }

    @Test
    fun setDynamicColor_updatesMemoryFlowAndInitialValue() = runTest {
        val manager = manager()
        manager.themeModeFlow.first()
        manager.dynamicColorFlow.first()
        manager.setDynamicColor(false)

        assertFalse(manager.dynamicColor)
        assertFalse(manager.getInitialDynamicColor())
        assertFalse(manager.dynamicColorFlow.first())
    }

    @Test
    fun lightModeAndDynamicColor_roundTrip() = runTest {
        val manager = manager()
        manager.themeModeFlow.first()
        manager.dynamicColorFlow.first()
        manager.setThemeMode(ThemeMode.LIGHT)
        manager.setDynamicColor(true)

        assertEquals(ThemeMode.LIGHT, manager.themeModeFlow.first())
        assertTrue(manager.dynamicColorFlow.first())
    }

    @Test
    fun setThemeSeed_updatesMemoryFlowAndInitialValue() = runTest {
        val manager = manager()
        manager.themeSeedFlow.first()
        manager.setThemeSeed(ThemeSeed.Amber)

        assertEquals(ThemeSeed.Amber, manager.themeSeed)
        assertEquals(ThemeSeed.Amber, manager.getInitialThemeSeed())
        assertEquals(ThemeSeed.Amber, manager.themeSeedFlow.first())
    }

    @Test
    fun setAmoled_updatesMemoryFlowAndInitialValue() = runTest {
        val manager = manager()
        manager.amoledFlow.first()
        manager.setAmoled(true)

        assertTrue(manager.amoled)
        assertTrue(manager.getInitialAmoled())
        assertTrue(manager.amoledFlow.first())
    }

    @Test
    fun setExpressiveColor_updatesMemoryFlowAndInitialValue() = runTest {
        val manager = manager()
        manager.expressiveColorFlow.first()
        manager.setExpressiveColor(true)

        assertTrue(manager.expressiveColor)
        assertTrue(manager.getInitialExpressiveColor())
        assertTrue(manager.expressiveColorFlow.first())
    }

    @Test
    fun themeSeed_toColors_returnsExpectedPalette() {
        val (lotusPrimary, lotusSecondary, lotusTertiary) = ThemeSeed.Lotus.toColors()
        assertEquals(androidx.compose.ui.graphics.Color(0xFF006EBE), lotusPrimary)
        assertEquals(null, lotusSecondary)
        assertEquals(null, lotusTertiary)

        val (triPrimary, triSecondary, triTertiary) = ThemeSeed.Tricolor.toColors()
        assertEquals(androidx.compose.ui.graphics.Color(0xFFEAD4CE), triPrimary)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF7D859D), triSecondary)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFEC9CA8), triTertiary)
    }

    @Test
    fun unknownSeedName_fallsBackToDefault() = runTest {
        val dataStore = TestDataStore.themePrefs()
        dataStore.edit { prefs -> prefs[stringPreferencesKey("app_theme_seed")] = "NotASeed" }
        val manager = ThemeManager(dataStore)

        val expected = if (isDynamicSeedAvailable) ThemeSeed.Dynamic else ThemeSeed.Pink
        assertEquals(expected, manager.themeSeedFlow.first())
    }

    @Test
    fun dynamicSeed_orAvailableRespectsPlatform() {
        val expected = if (isDynamicSeedAvailable) ThemeSeed.Dynamic else ThemeSeed.Pink
        assertEquals(expected, ThemeSeed.Dynamic.orAvailable())
        assertEquals(ThemeSeed.Amber, ThemeSeed.Amber.orAvailable())
    }
}
