package com.nobody.repoi.ui.settings

import androidx.navigation3.runtime.NavBackStack
import com.nobody.repoi.ui.settings.navigation.SettingsAccountNavKey
import com.nobody.repoi.ui.settings.navigation.SettingsAppearanceNavKey
import com.nobody.repoi.ui.settings.navigation.SettingsContentNavKey
import com.nobody.repoi.ui.settings.navigation.SettingsGroup
import com.nobody.repoi.ui.settings.navigation.SettingsListNavKey
import com.nobody.repoi.ui.settings.navigation.SettingsSubNavKey
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SettingsSubNavKeysTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun testSettingsGroupFromNavKey() {
        assertEquals(SettingsGroup.Appearance, SettingsGroup.fromNavKey(SettingsAppearanceNavKey))
        assertEquals(SettingsGroup.Content, SettingsGroup.fromNavKey(SettingsContentNavKey))
        assertEquals(SettingsGroup.Account, SettingsGroup.fromNavKey(SettingsAccountNavKey))
        assertNull(SettingsGroup.fromNavKey(SettingsListNavKey))
        assertNull(SettingsGroup.fromNavKey(null))
    }

    @Test
    fun testSettingsGroupNavKeyBinding() {
        assertEquals(SettingsAppearanceNavKey, SettingsGroup.Appearance.navKey)
        assertEquals(SettingsContentNavKey, SettingsGroup.Content.navKey)
        assertEquals(SettingsAccountNavKey, SettingsGroup.Account.navKey)
    }

    @Test
    fun testWideScreenReplaceTopBehavior() {
        val stack = NavBackStack<SettingsSubNavKey>(SettingsListNavKey, SettingsAppearanceNavKey)
        assertEquals(2, stack.size)
        assertEquals(SettingsAppearanceNavKey, stack.last())

        // 切换到 Content：替换栈顶
        val target1 = SettingsContentNavKey
        if (stack.lastOrNull() != target1) {
            if (stack.size > 1) {
                stack.removeLastOrNull()
            }
            stack.add(target1)
        }
        assertEquals(2, stack.size)
        assertEquals(SettingsContentNavKey, stack.last())
        assertEquals(SettingsListNavKey, stack.first())

        // 重复点击 Content：无操作
        if (stack.lastOrNull() != target1) {
            if (stack.size > 1) {
                stack.removeLastOrNull()
            }
            stack.add(target1)
        }
        assertEquals(2, stack.size)
        assertEquals(SettingsContentNavKey, stack.last())

        // 切换到 Account：替换栈顶
        val target2 = SettingsAccountNavKey
        if (stack.lastOrNull() != target2) {
            if (stack.size > 1) {
                stack.removeLastOrNull()
            }
            stack.add(target2)
        }
        assertEquals(2, stack.size)
        assertEquals(SettingsAccountNavKey, stack.last())
        assertEquals(SettingsListNavKey, stack.first())
    }

    @Test
    fun testNarrowScreenPushAndPopBehavior() {
        val stack = NavBackStack<SettingsSubNavKey>(SettingsListNavKey)
        assertEquals(1, stack.size)
        assertEquals(SettingsListNavKey, stack.single())

        // 点击进入 Appearance：压栈
        stack.add(SettingsAppearanceNavKey)
        assertEquals(2, stack.size)
        assertEquals(SettingsAppearanceNavKey, stack.last())

        // 返回：出栈回退到 List
        val popped = stack.removeLastOrNull()
        assertEquals(SettingsAppearanceNavKey, popped)
        assertEquals(1, stack.size)
        assertEquals(SettingsListNavKey, stack.single())
    }

    @Test
    fun testSubNavKeysPolymorphicSerialization() {
        val keys: List<SettingsSubNavKey> = listOf(
            SettingsListNavKey,
            SettingsAppearanceNavKey,
            SettingsContentNavKey,
            SettingsAccountNavKey
        )

        keys.forEach { key ->
            val encoded = json.encodeToString(SettingsSubNavKey.serializer(), key)
            val decoded = json.decodeFromString(SettingsSubNavKey.serializer(), encoded)
            assertEquals(key, decoded)
        }
    }
}
