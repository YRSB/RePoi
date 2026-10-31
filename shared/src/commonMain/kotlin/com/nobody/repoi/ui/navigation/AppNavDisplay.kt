package com.nobody.repoi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.nobody.repoi.data.local.ThemeMode

/**
 * 全局导航分发器：封装 Navigation 3 强类型路由拓扑与页面映射。
 *
 * 底层基于通用 [RePoiNavDisplay] 容器构建，组合 8 大 DDD 领域 Entries 模块。
 * 在多栈架构（Multiple Back Stacks）下，通过 [RePoiNavigationState] 独立维护 5 个 Tab 的状态。
 */
@Composable
fun AppNavDisplay(
    navigationState: RePoiNavigationState,
    navigator: RePoiNavigator,
    modifier: Modifier = Modifier,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onOpenDebugScreen: () -> Unit,
    onLoginSuccess: () -> Unit,
) {
    val currentBackStack = navigationState.currentBackStack
    val onBack = { navigator.goBack() }

    val entryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
        topLevelEntries(currentBackStack)
        detailEntries(currentBackStack, onBack)
        communityEntries(currentBackStack, onBack)
        marketEntries(currentBackStack, onBack)
        userEntries(currentBackStack, onBack)
        messageEntries(currentBackStack, onBack)
        interactionEntries(currentBackStack, onBack)
        systemEntries(
            onBack = onBack,
            themeMode = themeMode,
            dynamicColor = dynamicColor,
            onThemeModeChange = onThemeModeChange,
            onDynamicColorChange = onDynamicColorChange,
            onOpenDebugScreen = onOpenDebugScreen,
            onLoginSuccess = onLoginSuccess
        )
    }

    CompositionLocalProvider(LocalNavBackStack provides currentBackStack) {
        RePoiNavDisplay(
            entries = navigationState.toDecoratedEntries(entryProvider),
            onBack = { onBack() },
            modifier = modifier,
            transitionSpec = {
                val fromKey = initialState.key as? NavKey
                val toKey = targetState.key as? NavKey
                if (fromKey != null && toKey != null && isTopLevelNavKey(fromKey) && isTopLevelNavKey(toKey)) {
                    MaterialSharedAxis.Fade.TransitionSpec
                } else {
                    MaterialSharedAxis.Z.TransitionSpec
                }
            },
            popTransitionSpec = {
                val fromKey = initialState.key as? NavKey
                val toKey = targetState.key as? NavKey
                if (fromKey != null && toKey != null && isTopLevelNavKey(fromKey) && isTopLevelNavKey(toKey)) {
                    MaterialSharedAxis.Fade.TransitionSpec
                } else {
                    MaterialSharedAxis.Z.PopTransitionSpec
                }
            },
            predictivePopTransitionSpec = { _ ->
                MaterialSharedAxis.Z.PopTransitionSpec
            }
        )
    }
}

/**
 * 单栈兼容重载（供轻量使用或单测场景）。
 */
@Composable
fun AppNavDisplay(
    backStack: NavBackStack<NavKey>,
    onBack: () -> Unit = { backStack.safePop() },
    modifier: Modifier = Modifier,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onOpenDebugScreen: () -> Unit,
    onLoginSuccess: () -> Unit,
) {
    CompositionLocalProvider(LocalNavBackStack provides backStack) {
        RePoiNavDisplay(
            backStack = backStack,
            onBack = onBack,
            modifier = modifier,
            transitionSpec = {
                val fromKey = initialState.key as? NavKey
                val toKey = targetState.key as? NavKey
                if (fromKey != null && toKey != null && isTopLevelNavKey(fromKey) && isTopLevelNavKey(toKey)) {
                    MaterialSharedAxis.Fade.TransitionSpec
                } else {
                    MaterialSharedAxis.Z.TransitionSpec
                }
            },
            popTransitionSpec = {
                val fromKey = initialState.key as? NavKey
                val toKey = targetState.key as? NavKey
                if (fromKey != null && toKey != null && isTopLevelNavKey(fromKey) && isTopLevelNavKey(toKey)) {
                    MaterialSharedAxis.Fade.TransitionSpec
                } else {
                    MaterialSharedAxis.Z.PopTransitionSpec
                }
            },
            predictivePopTransitionSpec = { _ ->
                MaterialSharedAxis.Z.PopTransitionSpec
            },
            entryProvider = entryProvider {
                topLevelEntries(backStack)
                detailEntries(backStack, onBack)
                communityEntries(backStack, onBack)
                marketEntries(backStack, onBack)
                userEntries(backStack, onBack)
                messageEntries(backStack, onBack)
                interactionEntries(backStack, onBack)
                systemEntries(
                    onBack = onBack,
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    onThemeModeChange = onThemeModeChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onOpenDebugScreen = onOpenDebugScreen,
                    onLoginSuccess = onLoginSuccess
                )
            }
        )
    }
}
