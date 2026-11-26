package com.nobody.repoi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.savedstate.serialization.SavedStateConfiguration

class RePoiNavigationState(
    val startDestination: AppDestinations = AppDestinations.HOME,
    private val currentDestinationState: MutableState<AppDestinations>,
    val backStacks: Map<AppDestinations, NavBackStack<NavKey>>,
    private val profileSubRouteActiveState: MutableState<Boolean> = mutableStateOf(false)
) {
    var currentDestination: AppDestinations by currentDestinationState
    var isProfileSubRouteActive: Boolean by profileSubRouteActiveState

    @Composable
    fun toDecoratedEntries(
        entryProvider: (NavKey) -> NavEntry<NavKey>
    ): List<NavEntry<NavKey>> {
        val decoratedEntries = backStacks.mapValues { (_, stack) ->
            val decorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>()
            )
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators = decorators,
                entryProvider = entryProvider
            )
        }

        return decoratedEntries[currentDestination] ?: emptyList()
    }
}

@Composable
fun rememberRePoiNavigationState(
    startDestination: AppDestinations = AppDestinations.HOME,
    configuration: SavedStateConfiguration = SavedStateConfiguration {
        serializersModule = RePoiSerializersModule
    }
): RePoiNavigationState {
    val currentDestinationState = rememberSaveable { mutableStateOf(startDestination) }
    val profileSubRouteActiveState = rememberSaveable { mutableStateOf(false) }

    val homeStack = rememberNavBackStack(configuration, HomeNavKey)
    val itemsStack = rememberNavBackStack(configuration, ItemsNavKey)
    val albumStack = rememberNavBackStack(configuration, AlbumNavKey)
    val followStack = rememberNavBackStack(configuration, FollowNavKey)
    val profileStack = rememberNavBackStack(configuration, ProfileNavKey)

    val backStacks = remember(homeStack, itemsStack, albumStack, followStack, profileStack) {
        mapOf(
            AppDestinations.HOME to homeStack,
            AppDestinations.ITEMS to itemsStack,
            AppDestinations.ALBUM to albumStack,
            AppDestinations.FOLLOW to followStack,
            AppDestinations.PROFILE to profileStack,
        )
    }

    return remember(startDestination, currentDestinationState, profileSubRouteActiveState, backStacks) {
        RePoiNavigationState(
            startDestination = startDestination,
            currentDestinationState = currentDestinationState,
            backStacks = backStacks,
            profileSubRouteActiveState = profileSubRouteActiveState
        )
    }
}
