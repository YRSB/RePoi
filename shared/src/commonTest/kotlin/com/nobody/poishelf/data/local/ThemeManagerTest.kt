package com.nobody.poishelf.data.local

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
}
