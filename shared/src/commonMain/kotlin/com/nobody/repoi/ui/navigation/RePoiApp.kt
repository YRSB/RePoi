package com.nobody.repoi.ui.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import com.nobody.repoi.data.local.ThemeManager
import com.nobody.repoi.data.local.ThemeMode
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.platform.PoiShelfBackHandler
import com.nobody.repoi.platform.moveAppToBackground
import com.nobody.repoi.ui.common.ImageViewerHost
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.rememberImageViewerState
import com.nobody.repoi.ui.theme.PoiShelfTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * RePoi 根应用组件：双端统一入口（Android 与 Windows Desktop）。
 *
 * 对齐 PodAura 范式与 Navigation 3 规范：
 * 1. 采用 [RePoiNavigator] 统筹顶层 Tab 导航、历史回退与详情退栈；
 * 2. 消除深浅层返回手势死锁，实现流畅的 Tab 间历史回退与二级页面安全出栈；
 * 3. 响应式自适应脚手架与 Material 3 Expressive 规范。
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
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

/**
 * 向后兼容别名，便于平滑过渡。
 */
@Deprecated("请使用 RePoiApp 替代", ReplaceWith("RePoiApp()"))
@Composable
fun PoiShelfApp() = RePoiApp()

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
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

    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration {
            serializersModule = RePoiSerializersModule
        },
        HomeNavKey
    )

    // 统一导航控制器（对齐 PodAura 范式）
    val navigator = remember(backStack) {
        RePoiNavigator(
            backStack = backStack,
            onExitApp = { moveAppToBackground() },
            onShowExitMessage = { message ->
                rootScope.launch { appMessages.show(message) }
            }
        )
    }

    // 根 Snackbar 消费链路：各屏 messages.show() 在此落地为可见提示。
    LaunchedEffect(appMessages) {
        while (true) {
            val message = appMessages.take()
            rootSnackbarHostState.showSnackbar(message)
        }
    }

    // 处理系统返回手势：统一交由 RePoiNavigator 调度，彻底解决死锁
    PoiShelfBackHandler(enabled = true) {
        navigator.goBack()
    }

    val currentTopKey = backStack.lastOrNull()
    val currentDestination = RePoiNavigator.destinationOf(currentTopKey)
    val isTopLevelDestination = isTopLevelNavKey(currentTopKey)

    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val defaultLayoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    val isTabletOrFoldableExpanded = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) &&
        adaptiveInfo.windowSizeClass.isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND)

    val shouldShowNavSuite = isTabletOrFoldableExpanded || isTopLevelDestination
    val navSuiteState = rememberNavigationSuiteScaffoldState()
    LaunchedEffect(shouldShowNavSuite) {
        if (!shouldShowNavSuite) {
            navSuiteState.hide()
        } else {
            delay(300)
            navSuiteState.show()
        }
    }

    val itemColors = NavigationSuiteDefaults.itemColors()

    CompositionLocalProvider(
        LocalImageViewer provides imageViewerState,
        LocalAppMessages provides appMessages
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavigationSuiteScaffold(
                layoutType = defaultLayoutType,
                state = navSuiteState,
                navigationSuiteItems = {
                    AppDestinations.entries.forEach { destination ->
                        val isSelected = destination == currentDestination
                        item(
                            selected = isSelected,
                            onClick = { navigator.selectTab(destination) },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.label,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = destination.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = itemColors
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                AppNavDisplay(
                    backStack = backStack,
                    onBack = { navigator.goBack() },
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    onThemeModeChange = onThemeModeChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onOpenDebugScreen = {
                        rootScope.launch { appMessages.show("API 调试控制台在此版本中不可用") }
                    },
                    onLoginSuccess = {
                        backStack.removeAll { it is LoginNavKey }
                        navigator.selectTab(AppDestinations.PROFILE)
                    }
                )
            }

            SnackbarHost(
                hostState = rootSnackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // 全局全屏大图查看器宿主
            ImageViewerHost(imageViewerState)
        }
    }
}

enum class AppDestinations(
    val label: String,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME("动态", Icons.Outlined.Home, Icons.Filled.Home),
    ITEMS("条目", Icons.Outlined.Category, Icons.Filled.Category),
    ALBUM("相册", Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary),
    FOLLOW("关注", Icons.Outlined.People, Icons.Filled.People),
    PROFILE("我的", Icons.Outlined.Person, Icons.Filled.Person),
}
