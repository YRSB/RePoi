package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * RePoi 全局导航控制器（PodAura 单根栈范式）：
 *
 * 应用只有一条全局根栈 `[MainNavKey, 二级页...]`：
 * 1. [rootBackStack] 栈底常驻 [MainNavKey]（主界面 = 底栏 + 5 个 Tab 首屏），二级页面平铺压栈并整屏覆盖；
 * 2. 返回完全交由 Nav3 原生出栈与系统 Activity 退出，控制器不持有任何返回状态机；
 * 3. Tab 切换仅变更 [RePoiNavigationState.currentDestination]，不影响根栈。
 */
class RePoiNavigator(
    val rootBackStack: NavBackStack<NavKey>,
    val tabState: RePoiNavigationState,
) {
    val currentBackStack: NavBackStack<NavKey>
        get() = rootBackStack

    fun navigate(key: NavKey) {
        rootBackStack.add(key)
    }

    fun pop(): Boolean {
        if (rootBackStack.size <= 1) return false
        rootBackStack.removeLastOrNull()
        return true
    }

    fun selectTab(destination: AppDestinations) {
        tabState.currentDestination = destination
    }
}
