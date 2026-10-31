package com.nobody.repoi.ui.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import com.nobody.repoi.data.local.ThemeManager
import com.nobody.repoi.data.local.ThemeMode
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.ui.common.ImageViewerHost
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.rememberImageViewerState
import com.nobody.repoi.ui.theme.PoiShelfTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * RePoi 根应用组件：双端统一入口（Android 与 Windows Desktop）。
 *
 * 对齐 PodAura 范式与 Navigation 3 规范：
 * 1. 全局单根栈 `[MainNavKey, 二级页...]`，主界面常驻栈底，二级页整屏覆盖；
 * 2. Tab 状态由 [RePoiNavigationState] 独立维持，切换时 ViewModel 与 UI 状态全量保留；
 * 3. 返回完全交由 Nav3 原生出栈与系统 Activity 退出，无返回状态机与显隐补丁。
 */
@Composable
fun RePoiApp() {
    val imageLoader: ImageLoader = koinInject()
    setSingletonImageLoaderFactory { imageLoader }
    val themeManager: ThemeManager = koinInject()
    val themeMode by themeManager.themeModeFlow.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val dynamicColor by themeManager.dynamicColorFlow.collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()
    val onThemeModeChange: (ThemeMode) -> Unit = { mode ->
        scope.launch { themeManager.setThemeMode(mode) }
    }
    val onDynamicColorChange: (Boolean) -> Unit = { enabled ->
        scope.launch { themeManager.setDynamicColor(enabled) }
    }

    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    PoiShelfTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        RePoiNavHost(
            themeMode = themeMode,
            dynamicColor = dynamicColor,
            onThemeModeChange = onThemeModeChange,
            onDynamicColorChange = onDynamicColorChange
        )
    }
}

@Composable
private fun RePoiNavHost(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit
) {
    val imageViewerState = rememberImageViewerState()
    val appMessages = remember { RootMessageBus() }
    val rootSnackbarHostState = remember { SnackbarHostState() }
    val rootScope = rememberCoroutineScope()

    val savedStateConfiguration = remember {
        SavedStateConfiguration { serializersModule = RePoiSerializersModule }
    }

    val rootBackStack = rememberNavBackStack(savedStateConfiguration, MainNavKey)
    val tabState = rememberRePoiNavigationState(
        startDestination = AppDestinations.HOME,
        configuration = savedStateConfiguration
    )
    val navigator = remember(rootBackStack, tabState) {
        RePoiNavigator(rootBackStack = rootBackStack, tabState = tabState)
    }
    val tabEntryProvider: (NavKey) -> NavEntry<NavKey> = remember(navigator) {
        entryProvider<NavKey> { topLevelEntries(navigator) }
    }
    // Tab 装饰器必须常驻根层（覆盖期间不丢滚动位置与 ViewModel），且 NavDisplay 以
    // remember(backStack.toList()) 记忆条目、Tab 切换不改根栈，故经 State 下传当前 Tab 的 entries。
    val tabEntries = rememberUpdatedState(tabState.toDecoratedEntries(tabEntryProvider))

    LaunchedEffect(appMessages) {
        while (true) {
            val message = appMessages.take()
            rootSnackbarHostState.showSnackbar(message)
        }
    }

    CompositionLocalProvider(
        LocalImageViewer provides imageViewerState,
        LocalAppMessages provides appMessages
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val atMainRoot = rootBackStack.size <= 1
            AppNavDisplay(
                rootBackStack = rootBackStack,
                navigator = navigator,
                tabEntries = tabEntries,
                snackbarHostState = rootSnackbarHostState,
                showSnackbarHost = atMainRoot,
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                onThemeModeChange = onThemeModeChange,
                onDynamicColorChange = onDynamicColorChange,
                onOpenDebugScreen = {
                    rootScope.launch { appMessages.show("API 调试控制台在此版本中不可用") }
                },
                onLoginSuccess = {
                    while (rootBackStack.size > 1) rootBackStack.removeLastOrNull()
                    navigator.selectTab(AppDestinations.PROFILE)
                }
            )

            if (!atMainRoot) {
                SnackbarHost(
                    hostState = rootSnackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                )
            }

            ImageViewerHost(imageViewerState)
        }
    }
}
