package com.nobody.repoi.ui.profile

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.navigation.FriendListNavParam
import com.nobody.repoi.ui.navigation.UserCollectNavParam
import com.nobody.repoi.ui.navigation.UserCreationNavParam
import com.nobody.repoi.ui.profile.components.ProfileQuickAction
import com.nobody.repoi.ui.profile.navigation.ProfileCollectNavKey
import com.nobody.repoi.ui.profile.navigation.ProfileCreationNavKey
import com.nobody.repoi.ui.profile.navigation.ProfileFriendListNavKey
import com.nobody.repoi.ui.profile.navigation.ProfileHistoryNavKey
import com.nobody.repoi.ui.profile.navigation.ProfileListNavKey
import com.nobody.repoi.ui.profile.navigation.ProfileSubNavKey
import com.nobody.repoi.ui.profile.navigation.ProfileSubNavSerializersModule
import com.nobody.repoi.ui.profile.navigation.openProfileSubRoute
import com.nobody.repoi.ui.profile.navigation.popProfileSubRoute
import com.nobody.repoi.ui.profile.navigation.resetProfileSubStack
import com.nobody.repoi.ui.profile.navigation.resolveProfileSessionTracker
import com.nobody.repoi.ui.profile.navigation.toQuickAction
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileSubNavKeysTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun subNavKeysPolymorphicSerialization() {
        val keys: List<ProfileSubNavKey> = listOf(
            ProfileListNavKey,
            ProfileCollectNavKey(
                UserCollectNavParam(
                    userId = 1001L,
                    initialCollectState = "preorder",
                    initialCategory = 100
                )
            ),
            ProfileCreationNavKey(
                UserCreationNavParam(
                    userId = 1001L,
                    initialCategory = 70000
                )
            ),
            ProfileHistoryNavKey,
            ProfileFriendListNavKey(
                FriendListNavParam(
                    userId = 1001L,
                    initialType = 3
                )
            )
        )

        keys.forEach { key ->
            val encoded = json.encodeToString(ProfileSubNavKey.serializer(), key)
            val decoded = json.decodeFromString(ProfileSubNavKey.serializer(), encoded)
            assertEquals(key, decoded)
        }
    }

    @Test
    fun subNavKeysPolymorphicSerializationWithModule() {
        val jsonWithModule = Json {
            serializersModule = ProfileSubNavSerializersModule
            ignoreUnknownKeys = true
        }

        val keys: List<ProfileSubNavKey> = listOf(
            ProfileListNavKey,
            ProfileCollectNavKey(UserCollectNavParam(userId = 2002L, initialCollectState = "buy")),
            ProfileCreationNavKey(UserCreationNavParam(userId = 2002L, initialCategory = 70001)),
            ProfileHistoryNavKey,
            ProfileFriendListNavKey(FriendListNavParam(userId = 2002L, initialType = 1))
        )

        keys.forEach { key ->
            val navKeySerializer = PolymorphicSerializer(NavKey::class)
            val encodedNavKey = jsonWithModule.encodeToString(navKeySerializer, key)
            val decodedNavKey = jsonWithModule.decodeFromString(navKeySerializer, encodedNavKey)
            assertEquals(key, decodedNavKey)

            val subKeySerializer = PolymorphicSerializer(ProfileSubNavKey::class)
            val encodedSubKey = jsonWithModule.encodeToString(subKeySerializer, key)
            val decodedSubKey = jsonWithModule.decodeFromString(subKeySerializer, encodedSubKey)
            assertEquals(key, decodedSubKey)
        }
    }

    @Test
    fun toQuickActionMapsDetailKeysCorrectly() {
        assertEquals(
            ProfileQuickAction.COLLECT,
            ProfileCollectNavKey(UserCollectNavParam(userId = 1L)).toQuickAction()
        )
        assertEquals(
            ProfileQuickAction.CREATION,
            ProfileCreationNavKey(UserCreationNavParam(userId = 1L)).toQuickAction()
        )
        assertEquals(
            ProfileQuickAction.HISTORY,
            ProfileHistoryNavKey.toQuickAction()
        )
        assertNull(ProfileFriendListNavKey(FriendListNavParam(userId = 1L, initialType = 0)).toQuickAction())
        assertNull(ProfileListNavKey.toQuickAction())
        assertNull((null as ProfileSubNavKey?).toQuickAction())
    }

    @Test
    fun wideScreenReplacesTopAndUpdatesCollectParam() {
        val stack = NavBackStack<NavKey>(ProfileListNavKey)

        val preorderKey = ProfileCollectNavKey(UserCollectNavParam(userId = 42L, initialCollectState = "preorder"))
        val buyKey = ProfileCollectNavKey(UserCollectNavParam(userId = 42L, initialCollectState = "buy"))
        val creationKey = ProfileCreationNavKey(UserCreationNavParam(userId = 42L))
        val friendKey = ProfileFriendListNavKey(FriendListNavParam(userId = 42L, initialType = 1))

        openProfileSubRoute(stack, preorderKey, isWide = true)
        assertEquals(listOf<NavKey>(ProfileListNavKey, preorderKey), stack.toList())

        openProfileSubRoute(stack, preorderKey, isWide = true)
        assertEquals(listOf<NavKey>(ProfileListNavKey, preorderKey), stack.toList())

        openProfileSubRoute(stack, buyKey, isWide = true)
        assertEquals(listOf<NavKey>(ProfileListNavKey, buyKey), stack.toList())

        openProfileSubRoute(stack, creationKey, isWide = true)
        assertEquals(listOf<NavKey>(ProfileListNavKey, creationKey), stack.toList())

        openProfileSubRoute(stack, ProfileHistoryNavKey, isWide = true)
        assertEquals(listOf<NavKey>(ProfileListNavKey, ProfileHistoryNavKey), stack.toList())

        openProfileSubRoute(stack, friendKey, isWide = true)
        assertEquals(listOf<NavKey>(ProfileListNavKey, friendKey), stack.toList())
    }

    @Test
    fun narrowScreenPushesAvoidsDuplicateAndPopsToRoot() {
        val stack = NavBackStack<NavKey>(ProfileListNavKey)
        val collectKey = ProfileCollectNavKey(UserCollectNavParam(userId = 42L, initialCategory = 100))

        openProfileSubRoute(stack, collectKey, isWide = false)
        openProfileSubRoute(stack, collectKey, isWide = false)
        assertEquals(listOf<NavKey>(ProfileListNavKey, collectKey), stack.toList())

        assertTrue(popProfileSubRoute(stack))
        assertEquals(listOf<NavKey>(ProfileListNavKey), stack.toList())

        assertFalse(popProfileSubRoute(stack))
        assertEquals(listOf<NavKey>(ProfileListNavKey), stack.toList())
    }

    @Test
    fun resetOnLogoutOrUserSwitchClearsDetailStack() {
        val stack = NavBackStack<NavKey>(ProfileListNavKey, ProfileHistoryNavKey)

        val initialDecision = resolveProfileSessionTracker(
            lastAuthenticatedUserId = null,
            isLoggedIn = true,
            currentUserId = 100L
        )
        assertFalse(initialDecision.shouldResetSubStack)
        assertEquals(100L, initialDecision.nextAuthenticatedUserId)

        val sameUserDecision = resolveProfileSessionTracker(
            lastAuthenticatedUserId = 100L,
            isLoggedIn = true,
            currentUserId = 100L
        )
        assertFalse(sameUserDecision.shouldResetSubStack)
        assertEquals(100L, sameUserDecision.nextAuthenticatedUserId)

        val logoutDecision = resolveProfileSessionTracker(
            lastAuthenticatedUserId = 100L,
            isLoggedIn = false,
            currentUserId = 0L
        )
        assertTrue(logoutDecision.shouldResetSubStack)
        assertNull(logoutDecision.nextAuthenticatedUserId)

        val switchDecision = resolveProfileSessionTracker(
            lastAuthenticatedUserId = 100L,
            isLoggedIn = true,
            currentUserId = 200L
        )
        assertTrue(switchDecision.shouldResetSubStack)
        assertEquals(200L, switchDecision.nextAuthenticatedUserId)

        resetProfileSubStack(stack)
        assertEquals(listOf<NavKey>(ProfileListNavKey), stack.toList())
    }
}
