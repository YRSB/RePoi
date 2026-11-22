package com.nobody.repoi.ui.settings

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.settings.navigation.SettingsAccountNavKey
import com.nobody.repoi.ui.settings.navigation.SettingsAppearanceNavKey
import com.nobody.repoi.ui.settings.navigation.SettingsContentNavKey
import com.nobody.repoi.ui.settings.navigation.SettingsGroup
import com.nobody.repoi.ui.settings.navigation.SettingsListNavKey
import com.nobody.repoi.ui.settings.navigation.SettingsSubNavKey
import com.nobody.repoi.ui.settings.navigation.SettingsSubNavSerializersModule
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val stack = NavBackStack<SettingsSubNavKey>(SettingsListNavKey)
        stack.add(SettingsAppearanceNavKey)
        assertEquals(2, stack.size)
        assertEquals(SettingsAppearanceNavKey, stack.last())

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

        if (stack.lastOrNull() != target1) {
            if (stack.size > 1) {
                stack.removeLastOrNull()
            }
            stack.add(target1)
        }
        assertEquals(2, stack.size)
        assertEquals(SettingsContentNavKey, stack.last())

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

        stack.add(SettingsAppearanceNavKey)
        assertEquals(2, stack.size)
        assertEquals(SettingsAppearanceNavKey, stack.last())

        val popped = stack.removeLastOrNull()
        assertEquals(SettingsAppearanceNavKey, popped)
        assertEquals(1, stack.size)
        assertEquals(SettingsListNavKey, stack.single())
    }

    @Test
    fun testNarrowScreenPopFromSingleKeyExits() {
        val stack = NavBackStack<NavKey>(SettingsListNavKey)
        var exitCalled = false
        val onBack = { exitCalled = true }
        val localOnBack = {
            if (stack.size > 1) {
                stack.removeLastOrNull()
            } else {
                onBack()
            }
        }

        localOnBack()
        assertEquals(true, exitCalled)
        assertEquals(1, stack.size)
        assertEquals(SettingsListNavKey, stack.single())

        exitCalled = false
        stack.add(SettingsAppearanceNavKey)
        assertEquals(2, stack.size)

        localOnBack()
        assertEquals(false, exitCalled)
        assertEquals(1, stack.size)
        assertEquals(SettingsListNavKey, stack.single())

        localOnBack()
        assertEquals(true, exitCalled)
        assertEquals(1, stack.size)
    }

    @Test
    fun testNarrowScreenRapidClickNoDuplicate() {
        val stack = NavBackStack<NavKey>(SettingsListNavKey)
        val onSelectGroup: (SettingsGroup) -> Unit = { group ->
            val targetKey = group.navKey
            if (stack.lastOrNull() != targetKey) {
                stack.add(targetKey)
            }
        }

        onSelectGroup(SettingsGroup.Appearance)
        assertEquals(2, stack.size)
        assertEquals(SettingsAppearanceNavKey, stack.last())

        onSelectGroup(SettingsGroup.Appearance)
        assertEquals(2, stack.size)
        assertEquals(SettingsAppearanceNavKey, stack.last())
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

    @Test
    fun testSubNavKeysPolymorphicSerializationWithModule() {
        val jsonWithModule = Json {
            serializersModule = SettingsSubNavSerializersModule
            ignoreUnknownKeys = true
        }

        val keys: List<SettingsSubNavKey> = listOf(
            SettingsListNavKey,
            SettingsAppearanceNavKey,
            SettingsContentNavKey,
            SettingsAccountNavKey
        )

        keys.forEach { key ->

            val navKeySerializer = PolymorphicSerializer(NavKey::class)
            val encodedNavKey = jsonWithModule.encodeToString(navKeySerializer, key)
            val decodedNavKey = jsonWithModule.decodeFromString(navKeySerializer, encodedNavKey)
            assertEquals(key, decodedNavKey)

            val subKeySerializer = PolymorphicSerializer(SettingsSubNavKey::class)
            val encodedSubKey = jsonWithModule.encodeToString(subKeySerializer, key)
            val decodedSubKey = jsonWithModule.decodeFromString(subKeySerializer, encodedSubKey)
            assertEquals(key, decodedSubKey)
        }
    }
}
