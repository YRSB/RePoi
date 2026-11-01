package com.nobody.repoi.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import kotlin.math.roundToInt

/**
 * 主界面：自适应导航套件（底栏/导航栏）+ 当前 Tab 首屏内容。
 *
 * Tab 首屏的 decorated entries 由根层 [RePoiNavHost] 计算后传入，保证二级页面压栈期间
 * Tab 的 SaveableState / ViewModel 装饰器始终常驻，返回主界面时滚动位置与数据不丢失。
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainScreen(
    navigator: RePoiNavigator,
    tabEntries: State<List<NavEntry<NavKey>>>,
    snackbarHostState: SnackbarHostState,
    showSnackbarHost: Boolean,
    modifier: Modifier = Modifier,
) {
    val navigationState = navigator.tabState
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    val itemColors = NavigationSuiteDefaults.itemColors()
    val bottomBarState = remember { BottomBarScrollState() }
    val collapseDistancePx = with(LocalDensity.current) { 80.dp.toPx() }
    val collapseConnection = remember(collapseDistancePx, bottomBarState) {
        BottomBarCollapseConnection(
            state = bottomBarState,
            distancePx = collapseDistancePx,
            isEnabled = { navigationState.currentDestination != AppDestinations.HOME }
        )
    }

    val homeCollapseFraction = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(navigationState.currentDestination) {
        if (navigationState.currentDestination == AppDestinations.HOME) {
            bottomBarState.collapseFraction = homeCollapseFraction.value
        } else {
            homeCollapseFraction.value = bottomBarState.collapseFraction
            collapseConnection.reset()
            bottomBarState.collapseFraction = 0f
        }
    }

    CompositionLocalProvider(LocalBottomBarScrollState provides bottomBarState) {
        if (layoutType == NavigationSuiteType.NavigationBar) {
            Scaffold(
                snackbarHost = {
                    if (showSnackbarHost) SnackbarHost(hostState = snackbarHostState)
                },
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier
                            .onSizeChanged { bottomBarState.barHeightPx = it.height.toFloat() }
                            .offset {
                                IntOffset(
                                    x = 0,
                                    y = (bottomBarState.collapseFraction * bottomBarState.barHeightPx).roundToInt()
                                )
                            }
                    ) {
                        AppDestinations.entries.forEach { destination ->
                            val isSelected = destination == navigationState.currentDestination
                            NavigationBarItem(
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
                                }
                            )
                        }
                    }
                },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                modifier = modifier.fillMaxSize()
            ) { _ ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(collapseConnection)
                ) {
                    RePoiNavDisplay(
                        entries = tabEntries.value,
                        transitionSpec = { MaterialSharedAxis.Fade.TransitionSpec },
                        popTransitionSpec = { MaterialSharedAxis.Fade.TransitionSpec },
                        predictivePopTransitionSpec = { MaterialSharedAxis.Fade.TransitionSpec }
                    )
                }
            }
        } else {
            NavigationSuiteScaffold(
                layoutType = layoutType,
                state = rememberNavigationSuiteScaffoldState(),
                navigationSuiteItems = {
                    AppDestinations.entries.forEach { destination ->
                        val isSelected = destination == navigationState.currentDestination
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
                modifier = modifier.fillMaxSize()
            ) {
                Scaffold(
                    snackbarHost = {
                        if (showSnackbarHost) SnackbarHost(hostState = snackbarHostState)
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.fillMaxSize()
                ) { _ ->
                    RePoiNavDisplay(
                        entries = tabEntries.value,
                        transitionSpec = { MaterialSharedAxis.Fade.TransitionSpec },
                        popTransitionSpec = { MaterialSharedAxis.Fade.TransitionSpec },
                        predictivePopTransitionSpec = { MaterialSharedAxis.Fade.TransitionSpec }
                    )
                }
            }
        }
    }
}

private class BottomBarCollapseConnection(
    private val state: BottomBarScrollState,
    private val distancePx: Float,
    private val isEnabled: () -> Boolean,
) : NestedScrollConnection {

    private var travelPx = 0f

    fun reset() {
        travelPx = 0f
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!isEnabled() || distancePx <= 0f) return Offset.Zero
        val next = (travelPx - available.y).coerceIn(0f, distancePx)
        if (next != travelPx) {
            travelPx = next
            state.collapseFraction = next / distancePx
        }
        return Offset.Zero
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
