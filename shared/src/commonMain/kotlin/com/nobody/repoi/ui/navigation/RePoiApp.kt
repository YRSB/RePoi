package com.nobody.repoi.ui.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 * 1. 采用多栈模型 [RePoiNavigationState]，独立维护 5 个主目的地回退栈，保证切换时 ViewModel 与 UI 状态全量持久保留；
 * 2. 采用 [RePoiNavigator] 统筹顶层 Tab 导航、历史回退与详情退栈，消除死锁；
 * 3. 规范嵌套 Scaffold 布局，消除小屏下 Snackbar 悬浮与间距错误；
 * 4. 响应式自适应脚手架与 Material 3 Expressive 规范。
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

    // 多栈导航状态持有者（5 个主 Tab 拥有独立状态与 ViewModel 作用域）
    val navigationState = rememberRePoiNavigationState(
        startDestination = AppDestinations.HOME,
        configuration = SavedStateConfiguration {
            serializersModule = RePoiSerializersModule
        }
    )

    // 统一导航控制器（对齐 PodAura 范式与 Nav3 规范）
    val navigator = remember(navigationState) {
        RePoiNavigator(
            state = navigationState,
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

    val currentTopKey = navigationState.currentBackStack.lastOrNull()
    val currentDestination = navigationState.currentDestination
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
                // 内部嵌套标准 Scaffold：将 Snackbar 槽位锚定在自适应内容区底端（导航栏正上方）
                Scaffold(
                    snackbarHost = {
                        SnackbarHost(hostState = rootSnackbarHostState)
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.fillMaxSize()
                ) { _ ->
                    AppNavDisplay(
                        navigationState = navigationState,
                        navigator = navigator,
                        themeMode = themeMode,
                        dynamicColor = dynamicColor,
                        onThemeModeChange = onThemeModeChange,
                        onDynamicColorChange = onDynamicColorChange,
                        onOpenDebugScreen = {
                            rootScope.launch { appMessages.show("API 调试控制台在此版本中不可用") }
                        },
                        onLoginSuccess = {
                            navigationState.backStacks[AppDestinations.PROFILE]?.removeAll { it is LoginNavKey }
                            navigator.selectTab(AppDestinations.PROFILE)
                        }
                    )
                }
            }

            // 全局全屏大图查看器宿主
            ImageViewerHost(imageViewerState)
        }
    }
}

enum class AppDestinations(
    val label: String,
    val unselectedIcon: ImageVector,
    val selectedIcon: ImageVector,
    val route: AppNavKey,
) {
    HOME("动态", Icons.Outlined.Home, Icons.Filled.Home, HomeNavKey),
    ITEMS("条目", Icons.Outlined.Category, Icons.Filled.Category, ItemsNavKey),
    ALBUM("相册", Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary, AlbumNavKey),
    FOLLOW("关注", Icons.Outlined.People, Icons.Filled.People, FollowNavKey),
    PROFILE("我的", Icons.Outlined.Person, Icons.Filled.Person, ProfileNavKey),
}
