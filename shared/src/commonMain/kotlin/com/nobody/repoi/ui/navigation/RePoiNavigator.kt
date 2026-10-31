package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.platform.nowMillis

/**
 * RePoi 统一导航控制器（参考 PodAura 范式与 Navigation 3 规范）：
 *
 * 统一接管全局顶层 Tab 切换、二级页面深入、返回栈治理及系统返回手势派发。
 * 彻底消除内层 NavDisplay 拦截与外层 BackHandler 之间的抢跑死锁：
 * 1. 二级/详情页面：优先逐级出栈退回；
 * 2. 顶层 Tab 根视图：按浏览历史（Tab History）逐级倒退回前序访问的主目的地；
 * 3. 根视图绝对基底（Home）：交由应用退出双击防误触处理。
 */
class RePoiNavigator(
    val backStack: NavBackStack<NavKey>,
    val onExitApp: () -> Unit = {},
    val onShowExitMessage: (String) -> Unit = {}
) {
    // 维护顶层 Tab 访问历史序列
    private val tabHistory = mutableListOf<AppDestinations>()

    init {
        tabHistory.add(AppDestinations.HOME)
    }

    /**
     * 切换顶层 Tab：
     * 若点击当前已激活的 Tab，则退栈清理临时二级页面（回到该 Tab 根视图）；
     * 若切换到新 Tab，记录访问历史并切换栈顶目标。
     */
    fun selectTab(destination: AppDestinations) {
        val currentTop = backStack.lastOrNull()
        val currentDest = destinationOf(currentTop)

        if (currentDest == destination) {
            // 再次点击当前已激活的 Tab：若在二级页面则退回根视图
            backStack.popToTopLevel()
        } else {
            // 切换到目标 Tab：清理临时二级页面
            backStack.popToTopLevel()
            val targetKey = keyOf(destination)

            // 维护 Tab 浏览历史序列（避免相邻重复）
            if (tabHistory.lastOrNull() != destination) {
                tabHistory.remove(destination)
                tabHistory.add(destination)
            }

            // 规范化顶层栈模型：栈底保持 HomeNavKey，栈顶为目标 Tab
            if (destination == AppDestinations.HOME) {
                backStack.clear()
                backStack.add(HomeNavKey)
            } else {
                backStack.clear()
                backStack.add(HomeNavKey)
                backStack.add(targetKey)
            }
        }
    }

    private var lastBackPressTimeMs = 0L

    /**
     * 统一处理返回动作（无论是来自 UI 返回键、NavDisplay 内置手势还是系统返回键）。
     * 返回是否消费了该返回事件。
     */
    fun goBack(): Boolean {
        if (backStack.isEmpty()) return false
        val currentTop = backStack.last()
        val isTopLevel = isTopLevelNavKey(currentTop)

        // 1. 如果当前处于二级/详情页面：退栈回到上一级页面
        if (!isTopLevel) {
            backStack.removeLastOrNull()
            return true
        }

        // 2. 如果当前处于非首页的一级 Tab 根页面：
        // 按照浏览历史回退到前序 Tab；若历史已空则回到 Home
        if (currentTop != HomeNavKey) {
            tabHistory.removeLastOrNull()
            val previousTab = tabHistory.lastOrNull() ?: AppDestinations.HOME
            val targetKey = keyOf(previousTab)

            if (previousTab == AppDestinations.HOME) {
                backStack.clear()
                backStack.add(HomeNavKey)
            } else {
                backStack.clear()
                backStack.add(HomeNavKey)
                backStack.add(targetKey)
            }
            return true
        }

        // 3. 当前已在首页 HomeNavKey 根页面：触发双击防误触退出
        val now = nowMillis()
        if (now - lastBackPressTimeMs < 2000L) {
            onExitApp()
        } else {
            lastBackPressTimeMs = now
            onShowExitMessage("再按一次退出应用")
        }
        return true
    }

    companion object {
        fun destinationOf(key: Any?): AppDestinations = when (key) {
            is ItemsNavKey -> AppDestinations.ITEMS
            is AlbumNavKey -> AppDestinations.ALBUM
            is FollowNavKey -> AppDestinations.FOLLOW
            is ProfileNavKey -> AppDestinations.PROFILE
            else -> AppDestinations.HOME
        }

        fun keyOf(destination: AppDestinations): AppNavKey = when (destination) {
            AppDestinations.HOME -> HomeNavKey
            AppDestinations.ITEMS -> ItemsNavKey
            AppDestinations.ALBUM -> AlbumNavKey
            AppDestinations.FOLLOW -> FollowNavKey
            AppDestinations.PROFILE -> ProfileNavKey
        }
    }
}
