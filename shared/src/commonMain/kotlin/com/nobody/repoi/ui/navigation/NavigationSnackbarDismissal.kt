package com.nobody.repoi.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop

/**
 * 监听应用导航目的地变更，并在页面切换（Tab 切换或二级页面入栈/出栈）时自动消除当前悬浮的 SnackBar。
 *
 * 解决签到等轻提示在导航到其他页面（如首页、设置页）后依然跟随滞留的问题（Issue #209）。
 */
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

/**
 * 监听应用导航目的地变更的底层实现重载。
 */
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

/**
 * 提取当前导航可视目标的冷流（由当前 Tab 目的地与根栈顶层页面构成）。
 */
fun navigationDestinationFlow(
    tabState: RePoiNavigationState,
    rootBackStack: NavBackStack<NavKey>,
): Flow<Pair<AppDestinations, NavKey?>> = snapshotFlow {
    tabState.currentDestination to rootBackStack.lastOrNull()
}
