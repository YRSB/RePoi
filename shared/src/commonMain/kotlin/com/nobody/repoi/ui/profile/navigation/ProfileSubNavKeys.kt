package com.nobody.repoi.ui.profile.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.navigation.FriendListNavParam
import com.nobody.repoi.ui.navigation.UserCollectNavParam
import com.nobody.repoi.ui.navigation.UserCreationNavParam
import com.nobody.repoi.ui.profile.components.ProfileQuickAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable
internal sealed interface ProfileSubNavKey : NavKey

@Serializable
internal data object ProfileListNavKey : ProfileSubNavKey

@Serializable
internal data class ProfileCollectNavKey(
    val param: UserCollectNavParam,
) : ProfileSubNavKey

@Serializable
internal data class ProfileCreationNavKey(
    val param: UserCreationNavParam,
) : ProfileSubNavKey

@Serializable
internal data object ProfileHistoryNavKey : ProfileSubNavKey

@Serializable
internal data class ProfileFriendListNavKey(
    val param: FriendListNavParam,
) : ProfileSubNavKey

internal val ProfileSubNavSerializersModule: SerializersModule = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(ProfileListNavKey::class)
        subclass(ProfileCollectNavKey::class)
        subclass(ProfileCreationNavKey::class)
        subclass(ProfileHistoryNavKey::class)
        subclass(ProfileFriendListNavKey::class)
    }
    polymorphic(ProfileSubNavKey::class) {
        subclass(ProfileListNavKey::class)
        subclass(ProfileCollectNavKey::class)
        subclass(ProfileCreationNavKey::class)
        subclass(ProfileHistoryNavKey::class)
        subclass(ProfileFriendListNavKey::class)
    }
}

internal data class ProfileSessionTrackDecision(
    val shouldResetSubStack: Boolean,
    val nextAuthenticatedUserId: Long?
)

internal fun ProfileSubNavKey?.toQuickAction(): ProfileQuickAction? = when (this) {
    is ProfileCollectNavKey -> ProfileQuickAction.COLLECT
    is ProfileCreationNavKey -> ProfileQuickAction.CREATION
    is ProfileHistoryNavKey -> ProfileQuickAction.HISTORY
    else -> null
}

internal fun openProfileSubRoute(
    backStack: NavBackStack<NavKey>,
    target: ProfileSubNavKey,
    isWide: Boolean
) {
    if (backStack.isEmpty()) {
        backStack.add(ProfileListNavKey)
    }
    if (backStack.lastOrNull() == target) return
    if (isWide) {
        while (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }
    backStack.add(target)
}

internal fun popProfileSubRoute(backStack: NavBackStack<NavKey>): Boolean {
    if (backStack.size > 1) {
        backStack.removeLastOrNull()
        return true
    }
    return false
}

internal fun resetProfileSubStack(backStack: NavBackStack<NavKey>) {
    while (backStack.size > 1) {
        backStack.removeLastOrNull()
    }
    if (backStack.isEmpty()) {
        backStack.add(ProfileListNavKey)
    } else if (backStack.firstOrNull() != ProfileListNavKey) {
        backStack.clear()
        backStack.add(ProfileListNavKey)
    }
}

internal fun resolveProfileSessionTracker(
    lastAuthenticatedUserId: Long?,
    isLoggedIn: Boolean,
    currentUserId: Long
): ProfileSessionTrackDecision {
    if (!isLoggedIn) {
        return ProfileSessionTrackDecision(
            shouldResetSubStack = lastAuthenticatedUserId != null,
            nextAuthenticatedUserId = null
        )
    }
    if (currentUserId <= 0L) {
        return ProfileSessionTrackDecision(
            shouldResetSubStack = false,
            nextAuthenticatedUserId = lastAuthenticatedUserId
        )
    }
    val switchedUser = lastAuthenticatedUserId != null && lastAuthenticatedUserId != currentUserId
    return ProfileSessionTrackDecision(
        shouldResetSubStack = switchedUser,
        nextAuthenticatedUserId = currentUserId
    )
}
