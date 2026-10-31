package com.nobody.repoi.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.nobody.repoi.data.local.ThemeMode

/**
 * 全局导航根容器：单根栈 `[MainNavKey, 二级页...]` 的页面分发。
 *
 * 栈底 [MainNavKey] 承载主界面（底栏 + 5 个 Tab 首屏，其 decorated entries 由调用方计算后传入），
 * 二级页面平铺压入同一栈并整屏覆盖主界面；返回完全交由 Nav3 原生出栈。
 */
@Composable
fun AppNavDisplay(
    rootBackStack: NavBackStack<NavKey>,
    navigator: RePoiNavigator,
    tabEntries: State<List<NavEntry<NavKey>>>,
    snackbarHostState: SnackbarHostState,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onOpenDebugScreen: () -> Unit,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onBack: () -> Unit = { navigator.pop() }

    val entryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
        entry<MainNavKey> {
            MainScreen(
                navigator = navigator,
                tabEntries = tabEntries,
                snackbarHostState = snackbarHostState
            )
        }
        detailEntries(navigator, onBack)
        communityEntries(navigator, onBack)
        marketEntries(navigator, onBack)
        userEntries(navigator, onBack)
        messageEntries(navigator, onBack)
        interactionEntries(navigator, onBack)
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

    RePoiNavDisplay(
        backStack = rootBackStack,
        onBack = onBack,
        modifier = modifier,
        transitionSpec = { MaterialSharedAxis.Z.TransitionSpec },
        popTransitionSpec = { MaterialSharedAxis.Z.PopTransitionSpec },
        predictivePopTransitionSpec = { MaterialSharedAxis.Z.PopTransitionSpec },
        entryProvider = entryProvider
    )
}
