package com.nobody.repoi.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MainScreenBottomBarVisibilityTest {

    private fun createTabState(initial: AppDestinations): RePoiNavigationState {
        val stacks = mapOf(
            AppDestinations.HOME to NavBackStack<NavKey>(HomeNavKey),
            AppDestinations.ITEMS to NavBackStack<NavKey>(ItemsNavKey),
            AppDestinations.ALBUM to NavBackStack<NavKey>(AlbumNavKey),
            AppDestinations.FOLLOW to NavBackStack<NavKey>(FollowNavKey),
            AppDestinations.PROFILE to NavBackStack<NavKey>(ProfileNavKey),
        )
        return RePoiNavigationState(
            startDestination = initial,
            currentDestinationState = mutableStateOf<AppDestinations>(initial),
            backStacks = stacks,
            profileSubRouteActiveState = mutableStateOf(false)
        )
    }

    @Composable
    private fun rememberTestEntryProvider(
        tabState: RePoiNavigationState,
        onProfileBottomPadding: (Float) -> Unit = {}
    ): (NavKey) -> NavEntry<NavKey> = remember(tabState) {
        entryProvider {
            entry<HomeNavKey> {
                Text("HOME_TAB_CONTENT")
            }
            entry<ItemsNavKey> {
                Text("ITEMS_TAB_CONTENT")
            }
            entry<AlbumNavKey> {
                Text("ALBUM_TAB_CONTENT")
            }
            entry<FollowNavKey> {
                Text("FOLLOW_TAB_CONTENT")
            }
            entry<ProfileNavKey> {
                onProfileBottomPadding(rememberBottomBarContentPadding(0.dp).value)
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(if (tabState.isProfileSubRouteActive) "PROFILE_SUB_PAGE" else "PROFILE_ROOT_PAGE")
                }
            }
        }
    }

    @Test
    fun compactPhone_hidesBottomBarOnlyWhenProfileSubRouteActive() = runComposeUiTest {
        val tabState = createTabState(AppDestinations.PROFILE)
        val navigator = RePoiNavigator(
            rootBackStack = NavBackStack<NavKey>(MainNavKey),
            tabState = tabState
        )
        var measuredBottomPadding = -1f

        setContent {
            val entryProvider = rememberTestEntryProvider(
                tabState = tabState,
                onProfileBottomPadding = { measuredBottomPadding = it }
            )
            val tabEntries = rememberUpdatedState(tabState.toDecoratedEntries(entryProvider))
            val snackbarHostState = remember { SnackbarHostState() }

            Box(modifier = Modifier.size(412.dp, 800.dp)) {
                MainScreen(
                    navigator = navigator,
                    tabEntries = tabEntries,
                    snackbarHostState = snackbarHostState,
                    showSnackbarHost = true,
                    layoutType = NavigationSuiteType.NavigationBar
                )
            }
        }

        waitForIdle()
        onNodeWithText("PROFILE_ROOT_PAGE").assertIsDisplayed()
        onNodeWithText("动态").assertIsDisplayed()
        onNodeWithText("我的").assertIsDisplayed()
        assertTrue(measuredBottomPadding > 0f)

        tabState.isProfileSubRouteActive = true
        waitForIdle()
        onNodeWithText("PROFILE_SUB_PAGE").assertIsDisplayed()
        onNodeWithText("动态").assertDoesNotExist()
        onNodeWithText("我的").assertDoesNotExist()
        assertTrue(measuredBottomPadding == 0f)

        tabState.currentDestination = AppDestinations.HOME
        waitForIdle()
        onNodeWithText("HOME_TAB_CONTENT").assertIsDisplayed()
        onNodeWithText("动态").assertIsDisplayed()

        tabState.currentDestination = AppDestinations.PROFILE
        waitForIdle()
        onNodeWithText("动态").assertDoesNotExist()

        tabState.isProfileSubRouteActive = false
        waitForIdle()
        onNodeWithText("PROFILE_ROOT_PAGE").assertIsDisplayed()
        onNodeWithText("动态").assertIsDisplayed()
        assertTrue(measuredBottomPadding > 0f)
    }

    @Test
    fun expandedTablet_keepsNavigationRailWhenProfileSubRouteActive() = runComposeUiTest {
        val tabState = createTabState(AppDestinations.PROFILE)
        val navigator = RePoiNavigator(
            rootBackStack = NavBackStack<NavKey>(MainNavKey),
            tabState = tabState
        )

        setContent {
            val entryProvider = rememberTestEntryProvider(tabState = tabState)
            val tabEntries = rememberUpdatedState(tabState.toDecoratedEntries(entryProvider))
            val snackbarHostState = remember { SnackbarHostState() }

            Box(modifier = Modifier.size(1000.dp, 800.dp)) {
                MainScreen(
                    navigator = navigator,
                    tabEntries = tabEntries,
                    snackbarHostState = snackbarHostState,
                    showSnackbarHost = true,
                    layoutType = NavigationSuiteType.NavigationRail
                )
            }
        }

        waitForIdle()
        onNodeWithText("动态").assertIsDisplayed()
        onNodeWithText("我的").assertIsDisplayed()

        tabState.isProfileSubRouteActive = true
        waitForIdle()
        onNodeWithText("PROFILE_SUB_PAGE").assertIsDisplayed()
        onNodeWithText("动态").assertIsDisplayed()
        onNodeWithText("我的").assertIsDisplayed()
    }
}
