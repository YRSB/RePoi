package com.nobody.repoi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.nobody.repoi.data.local.ThemeMode

/**
 * 全局导航分发器：封装 Navigation 3 强类型路由拓扑与页面映射。
 *
 * 通过组合 8 大 DDD 领域 Entries 模块，实现路由映射逻辑自治与解耦。
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
    val entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
        rememberViewModelStoreNavEntryDecorator<NavKey>()
    )

    CompositionLocalProvider(LocalNavBackStack provides backStack) {
        NavDisplay(
            backStack = backStack,
            onBack = onBack,
            modifier = modifier,
            entryDecorators = entryDecorators,
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
