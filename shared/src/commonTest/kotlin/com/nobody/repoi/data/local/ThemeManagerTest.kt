package com.nobody.repoi.data.local

import com.nobody.repoi.test.TestDataStores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeManagerTest {

    private val stores = TestDataStores()
    private lateinit var manager: ThemeManager

    @BeforeTest
    fun setUp() = runTest {
        manager = ThemeManager(stores.createDataStore("theme_prefs"))
        manager.themeModeFlow.first()
        manager.dynamicColorFlow.first()
    }

    @AfterTest
    fun tearDown() {
        stores.cleanUp()
    }

    @Test
    fun setThemeMode_updatesMemoryFlowAndInitialValue() = runTest {
        manager.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, manager.themeMode)
        assertEquals(ThemeMode.DARK, manager.getInitialThemeMode())
        assertEquals(ThemeMode.DARK, manager.themeModeFlow.first())
    }

    @Test
    fun setDynamicColor_updatesMemoryFlowAndInitialValue() = runTest {
        manager.setDynamicColor(false)

        assertFalse(manager.dynamicColor)
        assertFalse(manager.getInitialDynamicColor())
        assertFalse(manager.dynamicColorFlow.first())
    }

    @Test
    fun lightModeAndDynamicColor_roundTrip() = runTest {
        manager.setThemeMode(ThemeMode.LIGHT)
        manager.setDynamicColor(true)

        assertEquals(ThemeMode.LIGHT, manager.themeModeFlow.first())
        assertTrue(manager.dynamicColorFlow.first())
    }
}
