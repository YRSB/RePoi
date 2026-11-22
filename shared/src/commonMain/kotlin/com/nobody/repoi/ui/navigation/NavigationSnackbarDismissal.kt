package com.nobody.repoi.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop

@Composable
fun NavigationSnackbarDismissalEffect(
    navigator: RePoiNavigator,
    snackbarHostState: SnackbarHostState,
) {
    NavigationSnackbarDismissalEffect(
        tabState = navigator.tabState,
        rootBackStack = navigator.rootBackStack,
        snackbarHostState = snackbarHostState
    )
}

@Composable
fun NavigationSnackbarDismissalEffect(
    tabState: RePoiNavigationState,
    rootBackStack: NavBackStack<NavKey>,
    snackbarHostState: SnackbarHostState,
) {
    LaunchedEffect(tabState, rootBackStack, snackbarHostState) {
        navigationDestinationFlow(tabState, rootBackStack)
            .drop(1)
            .collect {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
    }
}

fun navigationDestinationFlow(
    tabState: RePoiNavigationState,
    rootBackStack: NavBackStack<NavKey>,
): Flow<Pair<AppDestinations, NavKey?>> = snapshotFlow {
    tabState.currentDestination to rootBackStack.lastOrNull()
}
