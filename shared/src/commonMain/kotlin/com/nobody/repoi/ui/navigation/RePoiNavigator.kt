package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

class RePoiNavigator(
    val rootBackStack: NavBackStack<NavKey>,
    val tabState: RePoiNavigationState,
) {
    val currentBackStack: NavBackStack<NavKey>
        get() = rootBackStack

    fun navigate(key: NavKey) {
        rootBackStack.add(key)
    }

    fun pop(): Boolean {
        if (rootBackStack.size <= 1) return false
        rootBackStack.removeLastOrNull()
        return true
    }

    fun selectTab(destination: AppDestinations) {
        tabState.currentDestination = destination
    }
}
