package com.nobody.repoi.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.nobody.repoi.platform.isDynamicSeedAvailable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 主题管理器测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `ThemeManagerTest`（逐用例；JUnit4 → kotlin.test，
 * Robolectric → [TestDataStore] 真实 DataStore 隔离目录；
 * legacy 字节扫描已在 shared 删除，不在此覆盖）。
 *
 * 六维配置已合并为单一 `settings: StateFlow<ThemeSettings?>`：
 * `null` 表示首次读盘未完成，因此每个用例都先 [awaitSettings] 再断言。
 */
class ThemeManagerTest {

    private fun manager(): ThemeManager = ThemeManager(TestDataStore.themePrefs())

    /** 等首次读盘落定（成功或回落默认值）。 */
    private suspend fun ThemeManager.awaitSettings(): ThemeSettings =
        assertNotNull(settings.first { it != null }, "主题配置应在一轮读盘后落定")

    @Test
    fun settings_resolvesFromDiskAndNeverStaysNull() = runTest {
        val manager = manager()

        val resolved = manager.awaitSettings()

        // 落定后所有字段都必须有值，且与无持久化配置时的平台默认值一致
        assertEquals(defaultThemeSettings(), resolved)
    }

    @Test
    fun persistedValues_areLoadedIntoSnapshot() = runTest {
        val dataStore = TestDataStore.themePrefs()
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("app_theme_mode")] = ThemeMode.DARK.name
            prefs[booleanPreferencesKey("app_dynamic_color")] = false
            prefs[stringPreferencesKey("app_theme_seed")] = ThemeSeed.Amber.name
            prefs[booleanPreferencesKey("app_amoled")] = true
            prefs[stringPreferencesKey("app_palette_style")] = PaletteStyle.Vibrant.name
            prefs[stringPreferencesKey("app_spec_version")] = ColorSpec.SpecVersion.SPEC_2021.name
        }
        val manager = ThemeManager(dataStore)

        val resolved = manager.awaitSettings()

        assertEquals(ThemeMode.DARK, resolved.themeMode)
        assertFalse(resolved.dynamicColor)
        assertEquals(ThemeSeed.Amber, resolved.themeSeed)
        assertTrue(resolved.amoled)
        assertEquals(PaletteStyle.Vibrant, resolved.paletteStyle)
        assertEquals(ColorSpec.SpecVersion.SPEC_2021, resolved.specVersion)
    }

    @Test
    fun setThemeMode_updatesStateAndPersists() = runTest {
        val manager = manager()
        manager.awaitSettings()

        manager.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, manager.awaitSettings().themeMode)
    }

    @Test
    fun setDynamicColor_updatesStateAndPersists() = runTest {
        val manager = manager()
        manager.awaitSettings()

        manager.setDynamicColor(false)

        assertFalse(manager.awaitSettings().dynamicColor)
    }

    @Test
    fun lightModeAndDynamicColor_roundTrip() = runTest {
        val manager = manager()
        manager.awaitSettings()

        manager.setThemeMode(ThemeMode.LIGHT)
        manager.setDynamicColor(true)

        val resolved = manager.awaitSettings()
        assertEquals(ThemeMode.LIGHT, resolved.themeMode)
        assertTrue(resolved.dynamicColor)
    }

    @Test
    fun setThemeSeed_updatesStateAndPersists() = runTest {
        val manager = manager()
        manager.awaitSettings()

        manager.setThemeSeed(ThemeSeed.Amber)

        assertEquals(ThemeSeed.Amber, manager.awaitSettings().themeSeed)
    }

    @Test
    fun selectTheme_static_updatesSeedAndDynamicColorAtomically() = runTest {
        val manager = manager()
        manager.awaitSettings()
        manager.setDynamicColor(true)
        assertTrue(manager.awaitSettings().dynamicColor)

        manager.selectTheme(ThemeSeed.Lotus)

        val resolved = manager.awaitSettings()
        assertEquals(ThemeSeed.Lotus, resolved.themeSeed)
        assertFalse(resolved.dynamicColor)
    }

    @Test
    fun setDynamicColor_falseWhenDynamic_fallsBackToLotusAtomically() = runTest {
        if (!isDynamicSeedAvailable) return@runTest
        val manager = manager()
        manager.awaitSettings()
        manager.setThemeSeed(ThemeSeed.Dynamic)
        assertTrue(manager.awaitSettings().dynamicColor)

        manager.setDynamicColor(false)

        val resolved = manager.awaitSettings()
        assertFalse(resolved.dynamicColor)
        assertEquals(ThemeSeed.Lotus, resolved.themeSeed)
    }

    @Test
    fun setAmoled_updatesStateAndPersists() = runTest {
        val manager = manager()
        manager.awaitSettings()

        manager.setAmoled(true)

        assertTrue(manager.awaitSettings().amoled)
    }

    @Test
    fun setPaletteStyle_updatesStateAndPersists() = runTest {
        val manager = manager()
        manager.awaitSettings()

        manager.setPaletteStyle(PaletteStyle.Vibrant)

        assertEquals(PaletteStyle.Vibrant, manager.awaitSettings().paletteStyle)
    }

    @Test
    fun setSpecVersion_updatesStateAndPersists() = runTest {
        val manager = manager()
        manager.awaitSettings()

        manager.setSpecVersion(ColorSpec.SpecVersion.SPEC_2021)

        assertEquals(ColorSpec.SpecVersion.SPEC_2021, manager.awaitSettings().specVersion)
    }

    @Test
    fun legacyExpressiveColor_migratesToExpressiveStyle() = runTest {
        val dataStore = TestDataStore.themePrefs()
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey("app_expressive_color")] = true
        }
        val manager = ThemeManager(dataStore)

        // 遗留布尔标志只作为一次性迁移来源，统一收敛到 paletteStyle（issue #82 顺带清理）
        assertEquals(PaletteStyle.Expressive, manager.awaitSettings().paletteStyle)
    }

    @Test
    fun setPaletteStyle_clearsLegacyExpressiveKey() = runTest {
        val dataStore = TestDataStore.themePrefs()
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey("app_expressive_color")] = true
        }
        val manager = ThemeManager(dataStore)
        manager.awaitSettings()

        manager.setPaletteStyle(PaletteStyle.Vibrant)

        val prefs = dataStore.data.first()
        assertEquals(null, prefs[booleanPreferencesKey("app_expressive_color")])
        assertEquals(PaletteStyle.Vibrant, manager.awaitSettings().paletteStyle)
    }

    @Test
    fun themeSeed_toColors_returnsExpectedPalette() {
        val (lotusPrimary, lotusSecondary, lotusTertiary) = ThemeSeed.Lotus.toColors()
        assertEquals(Color(0xFF006EBE), lotusPrimary)
        assertEquals(null, lotusSecondary)
        assertEquals(null, lotusTertiary)

        val (triPrimary, triSecondary, triTertiary) = ThemeSeed.Tricolor.toColors()
        assertEquals(Color(0xFFEAD4CE), triPrimary)
        assertEquals(Color(0xFF7D859D), triSecondary)
        assertEquals(Color(0xFFEC9CA8), triTertiary)
    }

    @Test
    fun unknownSeedName_fallsBackToDefault() = runTest {
        val dataStore = TestDataStore.themePrefs()
        dataStore.edit { prefs -> prefs[stringPreferencesKey("app_theme_seed")] = "NotASeed" }
        val manager = ThemeManager(dataStore)

        val expected = if (isDynamicSeedAvailable) ThemeSeed.Dynamic else ThemeSeed.Pink
        assertEquals(expected, manager.awaitSettings().themeSeed)
    }

    @Test
    fun dynamicSeed_orAvailableRespectsPlatform() {
        val expected = if (isDynamicSeedAvailable) ThemeSeed.Dynamic else ThemeSeed.Pink
        assertEquals(expected, ThemeSeed.Dynamic.orAvailable())
        assertEquals(ThemeSeed.Amber, ThemeSeed.Amber.orAvailable())
    }
}
