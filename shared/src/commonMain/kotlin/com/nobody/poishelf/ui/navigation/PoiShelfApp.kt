package com.nobody.poishelf.ui.navigation

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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.poishelf.data.local.ThemeManager
import com.nobody.poishelf.data.local.ThemeMode
import com.nobody.poishelf.platform.LocalAppMessages
import com.nobody.poishelf.platform.PoiShelfBackHandler
import com.nobody.poishelf.platform.moveAppToBackground
import com.nobody.poishelf.platform.nowMillis
import com.nobody.poishelf.ui.common.ImageViewerHost
import com.nobody.poishelf.ui.common.LocalImageViewer
import com.nobody.poishelf.ui.common.rememberImageViewerState
import com.nobody.poishelf.ui.theme.LocalIsDarkTheme
import com.nobody.poishelf.ui.theme.PoiShelfTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 共用根组合（M6）：双端唯一入口。
 *
 * 原生 `ui/PoiShelfApp.kt`（257L）移植：
 * - `SystemClock.uptimeMillis()` → [nowMillis]（M2 datetime 垫片）；
 * - `LocalContext as Activity` + `Toast` + `moveTaskToBack/finish`
 *   → [moveAppToBackground] + 根 Snackbar 提示；
 * - 3× `BackHandler` → [PoiShelfBackHandler]（Android 真拦截，
 *   桌面端为记录在案的 no-op 降级：二级页经 UI 返回键照常退栈）；
 * - `showDebugScreen` + `DebugApiScreen` 调试覆盖层已删
 *   （PROJECT.md：调试页 EXCLUDED；设置页调试行经 Snackbar 说明）。
 * Tab 栈模型（Home 为绝对根基底、重选回根、切换清二级）与
 * 300ms 防抖退栈逐字保留。
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PoiShelfApp() {
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

    PoiShelfTheme(darkTheme = darkTheme) {
        PoiShelfNavHost(
            themeMode = themeMode,
            dynamicColor = dynamicColor,
            onThemeModeChange = onThemeModeChange,
            onDynamicColorChange = onDynamicColorChange
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun PoiShelfNavHost(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit
) {
    val imageViewerState = rememberImageViewerState()
    val appMessages = remember { RootMessageBus() }
    val rootSnackbarHostState = remember { SnackbarHostState() }
    val rootScope = rememberCoroutineScope()
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    val backStack = rememberNavBackStack(HomeNavKey)

    // 根 Snackbar 消费链路：各屏 messages.show() 在此落地为可见提示。
    LaunchedEffect(appMessages) {
        while (true) {
            val message = appMessages.take()
            rootSnackbarHostState.showSnackbar(message)
        }
    }

    var lastPopTimeMs by remember { mutableLongStateOf(0L) }
    val safePopBackStack: () -> Unit = {
        val now = nowMillis()
        if (now - lastPopTimeMs >= 300L) {
            lastPopTimeMs = now
            backStack.safePop()
        }
    }

    val onTabSelect: (AppDestinations) -> Unit = { destination ->
        val targetKey: AppNavKey = when (destination) {
            AppDestinations.HOME -> HomeNavKey
            AppDestinations.ITEMS -> ItemsNavKey
            AppDestinations.ALBUM -> AlbumNavKey
            AppDestinations.FOLLOW -> FollowNavKey
            AppDestinations.PROFILE -> ProfileNavKey
        }
        if (currentDestination == destination) {
            // 点击当前已选中的 Tab：若当前正在二级页面，则退栈回到该 Tab 根视图
            while (backStack.size > 1 && !isTopLevelNavKey(backStack.last())) {
                backStack.removeLastOrNull()
            }
        } else {
            // 切换到目标 Tab：
            // 1. 先退栈清理当前 Tab 遗留的临时二级页面，避免跨 Tab 堆叠二级详情占用过多资源
            while (backStack.size > 1 && !isTopLevelNavKey(backStack.last())) {
                backStack.removeLastOrNull()
            }
            // 2. 规范化顶层 Tab 栈模型：以 HomeNavKey 为绝对根基底，避免多 Tab 堆叠导致返回键死锁
            if (destination == AppDestinations.HOME) {
                // 切换回首页动态：清空所有非首页顶层 Tab，栈仅保留唯一的 HomeNavKey
                backStack.clear()
                backStack.add(HomeNavKey)
            } else {
                // 切换到其他非首页 Tab：基底保持 HomeNavKey，栈顶设为目标 Tab
                backStack.clear()
                backStack.add(HomeNavKey)
                backStack.add(targetKey)
            }
            currentDestination = destination
        }
    }

    val isDark = LocalIsDarkTheme.current

    val currentTopKey = backStack.lastOrNull()
    val isTopLevelDestination = isTopLevelNavKey(currentTopKey)

    // 同步导航导轨/底栏的选中态与当前活跃顶层路由
    LaunchedEffect(backStack.lastOrNull { isTopLevelNavKey(it) }) {
        when (backStack.lastOrNull { isTopLevelNavKey(it) }) {
            is HomeNavKey -> currentDestination = AppDestinations.HOME
            is ItemsNavKey -> currentDestination = AppDestinations.ITEMS
            is AlbumNavKey -> currentDestination = AppDestinations.ALBUM
            is FollowNavKey -> currentDestination = AppDestinations.FOLLOW
            is ProfileNavKey -> currentDestination = AppDestinations.PROFILE
            else -> {}
        }
    }

    var lastBackPressTimeMs by remember { mutableLongStateOf(0L) }

    // 处理系统返回手势：二级/详情栈返回 -> Tab 返回首页 -> 退出到桌面
    // （调试覆盖层分支已随 DebugApiScreen 删除；桌面端 PoiShelfBackHandler 为 no-op 降级。）
    // 1. 当前处于二级/详情页面时：安全防抖退栈返回上一级页面
    PoiShelfBackHandler(enabled = !isTopLevelDestination) {
        safePopBackStack()
    }
    // 2. 当前处于非首页的一级 Tab 根页面时：按返回键返回首页（遵循 Android "Exit through Home" 规范）
    PoiShelfBackHandler(enabled = isTopLevelDestination && currentDestination != AppDestinations.HOME) {
        onTabSelect(AppDestinations.HOME)
    }
    // 3. 当前处于首页动态根页面时：双击返回键退出应用回到系统桌面（防误触，2 秒内再次点击退出）
    PoiShelfBackHandler(enabled = isTopLevelDestination && currentDestination == AppDestinations.HOME) {
        val now = nowMillis()
        if (now - lastBackPressTimeMs < 2000L) {
            moveAppToBackground()
        } else {
            lastBackPressTimeMs = now
            rootScope.launch { appMessages.show("再按一次退出应用") }
        }
    }

    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val defaultLayoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    val isTabletOrFoldableExpanded = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) &&
        adaptiveInfo.windowSizeClass.isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND)

    // 底栏显隐经 NavigationSuiteScaffoldState 维护，避免 layoutType 与退栈动画抢跑
    // 去程立即隐藏，返程延迟到内容退场结束后再显示，二级全屏无底栏行为不变
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
                            onClick = { onTabSelect(destination) },
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
                    onBack = safePopBackStack,
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    onThemeModeChange = onThemeModeChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onOpenDebugScreen = {
                        rootScope.launch { appMessages.show("API 调试控制台在此版本中不可用") }
                    },
                    onLoginSuccess = {
                        backStack.removeAll { it is LoginNavKey }
                        onTabSelect(AppDestinations.PROFILE)
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
