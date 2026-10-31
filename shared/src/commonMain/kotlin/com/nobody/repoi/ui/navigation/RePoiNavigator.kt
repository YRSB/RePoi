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
    val state: RePoiNavigationState,
    val onExitApp: () -> Unit = {},
    val onShowExitMessage: (String) -> Unit = {}
) {
    /**
     * 兼容快捷构造器，用于单栈测试或简单场景
     */
    constructor(
        backStack: NavBackStack<NavKey>,
        onExitApp: () -> Unit = {},
        onShowExitMessage: (String) -> Unit = {}
    ) : this(
        state = RePoiNavigationState.forSingleStack(backStack),
        onExitApp = onExitApp,
        onShowExitMessage = onShowExitMessage
    )

    val currentBackStack: NavBackStack<NavKey>
        get() = state.currentBackStack

    // 维护顶层 Tab 访问历史序列
    private val tabHistory = mutableListOf<AppDestinations>()

    init {
        tabHistory.add(state.startDestination)
    }

    /**
     * 切换顶层 Tab：
     * 若点击当前已激活的 Tab，则退回当前 Tab 的根页面；
     * 若切换到新 Tab，记录访问历史并切换当前目标。
     */
    fun selectTab(destination: AppDestinations) {
        if (state.isSingleStackMode) {
            // 单栈兼容模式（用于单测）
            val currentTop = state.currentBackStack.lastOrNull()
            if (destinationOf(currentTop) == destination) {
                state.currentBackStack.popToTopLevel()
            } else {
                state.currentBackStack.switchTopLevelTab(destination)
                if (tabHistory.lastOrNull() != destination) {
                    tabHistory.remove(destination)
                    tabHistory.add(destination)
                }
                state.currentDestination = destination
            }
            return
        }

        // 完整多栈模式（运行期）：5 个 Tab 独立持有回退栈与状态
        if (state.currentDestination == destination) {
            // 再次点击当前已选中的 Tab：清空当前栈内的二级详情页，回退到根视图
            state.currentBackStack.popToTopLevel()
        } else {
            // 切换到目标 Tab 并记录 Tab 浏览历史（避免相邻重复）
            if (tabHistory.lastOrNull() != destination) {
                tabHistory.remove(destination)
                tabHistory.add(destination)
            }
            state.currentDestination = destination
        }
    }

    private var lastBackPressTimeMs = 0L

    /**
     * 统一处理返回动作（无论是来自 UI 返回键、NavDisplay 内置手势还是系统返回键）。
     * 返回是否消费了该返回事件。
     */
    fun goBack(): Boolean {
        val currentStack = state.currentBackStack

        if (state.isSingleStackMode) {
            // 单栈兼容模式（用于单测）
            if (currentStack.isEmpty()) return false
            val currentTop = currentStack.last()
            val isTopLevel = isTopLevelNavKey(currentTop)

            // 1. 如果当前处于二级/详情页面：退栈回到上一级页面
            if (!isTopLevel) {
                currentStack.removeLastOrNull()
                return true
            }

            // 2. 如果当前处于非首页的一级 Tab 根页面：
            // 按照浏览历史回退到前序 Tab；若历史已空则回到 Home
            if (currentTop != HomeNavKey) {
                tabHistory.removeLastOrNull()
                val previousTab = tabHistory.lastOrNull() ?: state.startDestination
                currentStack.switchTopLevelTab(previousTab)
                state.currentDestination = previousTab
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

        // 完整多栈模式（运行期）：
        // 1. 如果当前处于二级/详情页面：当前 Tab 栈退回上一级
        if (currentStack.size > 1 && !isTopLevelNavKey(currentStack.last())) {
            currentStack.removeLastOrNull()
            return true
        }

        // 2. 如果当前处于非首页的一级 Tab 根页面：
        // 按照浏览历史回退到前序 Tab；若历史已空则回到 Home
        if (state.currentDestination != state.startDestination) {
            tabHistory.removeLastOrNull()
            val previousTab = tabHistory.lastOrNull() ?: state.startDestination
            state.currentDestination = previousTab
            return true
        }

        // 3. 当前已在首页根页面：触发双击防误触退出
        val now = nowMillis()
        if (now - lastBackPressTimeMs < 2000L) {
            onExitApp()
        } else {
            lastBackPressTimeMs = now
            onShowExitMessage("再按一次退出应用")
        }
        return true
    }

    /**
     * 便捷跳转到指定路由（向当前激活的 Tab 栈压入二级新页面）。
     */
    fun navigate(key: NavKey) {
        state.currentBackStack.add(key)
    }
}
