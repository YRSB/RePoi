package com.nobody.repoi.ui.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 验证 Issue #209：页面导航（Tab 切换、二级页压栈/出栈）时自动清除当前活动 SnackBar 的契约。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NavigationSnackbarDismissalTest {

    private val testDispatcher = StandardTestDispatcher()

    private fun createTabState(start: AppDestinations = AppDestinations.PROFILE): RePoiNavigationState {
        val stacks = mapOf(
            AppDestinations.HOME to NavBackStack<NavKey>(HomeNavKey),
            AppDestinations.ITEMS to NavBackStack<NavKey>(ItemsNavKey),
            AppDestinations.ALBUM to NavBackStack<NavKey>(AlbumNavKey),
            AppDestinations.FOLLOW to NavBackStack<NavKey>(FollowNavKey),
            AppDestinations.PROFILE to NavBackStack<NavKey>(ProfileNavKey),
        )
        return RePoiNavigationState(
            startDestination = start,
            currentDestinationState = mutableStateOf<AppDestinations>(start),
            backStacks = stacks
        )
    }

    private fun createNavigator(startTab: AppDestinations = AppDestinations.PROFILE): Pair<RePoiNavigator, RePoiNavigationState> {
        val tabState = createTabState(startTab)
        val rootBackStack = NavBackStack<NavKey>(MainNavKey)
        return RePoiNavigator(rootBackStack = rootBackStack, tabState = tabState) to tabState
    }

    private fun CoroutineScope.launchDismissalCollector(
        tabState: RePoiNavigationState,
        rootStack: NavBackStack<NavKey>,
        snackbarHostState: SnackbarHostState,
    ): Job = launch {
        navigationDestinationFlow(tabState, rootStack)
            .drop(1)
            .collect {
                snackbarHostState.currentSnackbarData?.dismiss()
            }
    }

    @Test
    fun testNavigationDestinationFlow_emitsOnTabAndStackChanges() = runTest(testDispatcher) {
        val (navigator, tabState) = createNavigator(AppDestinations.PROFILE)
        val rootStack = navigator.rootBackStack

        navigationDestinationFlow(tabState, rootStack).test {
            // 初始值：(PROFILE, MainNavKey)
            assertEquals(AppDestinations.PROFILE to MainNavKey, awaitItem())

            // 切换 Tab 到首页
            Snapshot.withMutableSnapshot {
                navigator.selectTab(AppDestinations.HOME)
            }
            Snapshot.sendApplyNotifications()
            assertEquals(AppDestinations.HOME to MainNavKey, awaitItem())

            // 跳转二级页面（设置页）
            val settingsKey = SettingsNavKey
            Snapshot.withMutableSnapshot {
                navigator.navigate(settingsKey)
            }
            Snapshot.sendApplyNotifications()
            assertEquals(AppDestinations.HOME to settingsKey, awaitItem())

            // 出栈返回
            Snapshot.withMutableSnapshot {
                navigator.pop()
            }
            Snapshot.sendApplyNotifications()
            assertEquals(AppDestinations.HOME to MainNavKey, awaitItem())

            // 重新选中相同 Tab 不应重复发射
            Snapshot.withMutableSnapshot {
                navigator.selectTab(AppDestinations.HOME)
            }
            Snapshot.sendApplyNotifications()
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun testSwitchTab_dismissesActiveSnackbar() = runTest(testDispatcher) {
        val (navigator, tabState) = createNavigator(AppDestinations.PROFILE)
        val snackbarHostState = SnackbarHostState()
        val dismissalJob = launchDismissalCollector(tabState, navigator.rootBackStack, snackbarHostState)
        advanceUntilIdle()

        // 1. 在「我的」页面触发签到 SnackBar
        val snackbarJob = launch {
            snackbarHostState.showSnackbar("签到成功！已连续签到 1 天")
        }
        advanceUntilIdle()

        // 验证当前 SnackBar 正常处于展示状态
        assertNotNull(snackbarHostState.currentSnackbarData)
        assertEquals("签到成功！已连续签到 1 天", snackbarHostState.currentSnackbarData?.visuals?.message)

        // 2. 用户切换至「首页」Tab
        Snapshot.withMutableSnapshot {
            navigator.selectTab(AppDestinations.HOME)
        }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        // 验证 SnackBar 已立即消失（被自动 dismiss），不再跟随至首页
        assertNull(snackbarHostState.currentSnackbarData)

        snackbarJob.cancel()
        dismissalJob.cancel()
    }

    @Test
    fun testNavigateToSecondaryPage_dismissesActiveSnackbar() = runTest(testDispatcher) {
        val (navigator, tabState) = createNavigator(AppDestinations.PROFILE)
        val snackbarHostState = SnackbarHostState()
        val dismissalJob = launchDismissalCollector(tabState, navigator.rootBackStack, snackbarHostState)
        advanceUntilIdle()

        // 1. 在「我的」页面触发签到 SnackBar
        val snackbarJob = launch {
            snackbarHostState.showSnackbar("签到成功！已连续签到 1 天")
        }
        advanceUntilIdle()
        assertNotNull(snackbarHostState.currentSnackbarData)

        // 2. 用户点击进入「设置页」
        Snapshot.withMutableSnapshot {
            navigator.navigate(SettingsNavKey)
        }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        // 验证 SnackBar 已立即消失，不再跟随至设置页
        assertNull(snackbarHostState.currentSnackbarData)

        snackbarJob.cancel()
        dismissalJob.cancel()
    }

    @Test
    fun testPopSecondaryPage_dismissesActiveSnackbar() = runTest(testDispatcher) {
        val (navigator, tabState) = createNavigator(AppDestinations.PROFILE)
        val snackbarHostState = SnackbarHostState()
        val settingsKey = SettingsNavKey
        navigator.navigate(settingsKey)
        val dismissalJob = launchDismissalCollector(tabState, navigator.rootBackStack, snackbarHostState)
        advanceUntilIdle()

        // 1. 在设置页触发某条 SnackBar
        val snackbarJob = launch {
            snackbarHostState.showSnackbar("设置项更新提示")
        }
        advanceUntilIdle()
        assertNotNull(snackbarHostState.currentSnackbarData)

        // 2. 用户返回上一页
        Snapshot.withMutableSnapshot {
            navigator.pop()
        }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        // 验证 SnackBar 已立即消失
        assertNull(snackbarHostState.currentSnackbarData)

        snackbarJob.cancel()
        dismissalJob.cancel()
    }

    @Test
    fun testReSelectingSameTab_doesNotDismissSnackbar() = runTest(testDispatcher) {
        val (navigator, tabState) = createNavigator(AppDestinations.PROFILE)
        val snackbarHostState = SnackbarHostState()
        val dismissalJob = launchDismissalCollector(tabState, navigator.rootBackStack, snackbarHostState)
        advanceUntilIdle()

        val snackbarJob = launch {
            snackbarHostState.showSnackbar("签到成功！已连续签到 1 天")
        }
        advanceUntilIdle()
        assertNotNull(snackbarHostState.currentSnackbarData)

        // 重新点击同一个「我的」Tab
        Snapshot.withMutableSnapshot {
            navigator.selectTab(AppDestinations.PROFILE)
        }
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()

        // 验证 SnackBar 仍然保持展示状态
        assertNotNull(snackbarHostState.currentSnackbarData)
        assertEquals("签到成功！已连续签到 1 天", snackbarHostState.currentSnackbarData?.visuals?.message)

        snackbarJob.cancel()
        dismissalJob.cancel()
    }
}
