package com.nobody.repoi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.savedstate.serialization.SavedStateConfiguration

/**
 * RePoi 多栈导航状态持有者（Multiple Back Stacks，对齐 Navigation 3 官方规范与 PodAura 范式）：
 *
 * 为 5 个顶层目的地分别维系独立的 [NavBackStack]，并经独立的 [rememberSaveableStateHolderNavEntryDecorator]
 * 与 [rememberViewModelStoreNavEntryDecorator] 进行生命周期与状态修饰。
 *
 * 核心保证：
 * 1. 在 5 个主导航栏目的地之间切换时，各页面内部的 ViewModel、Lazy 列表滚动位置、表单状态分毫不丢；
 * 2. 遵循 "Exit through Home" 模式，栈底始终保留主页基底；
 * 3. 支持各 Tab 内部独立的二级页面深度浏览与回退。
 */
class RePoiNavigationState(
    val startDestination: AppDestinations = AppDestinations.HOME,
    private val currentDestinationState: MutableState<AppDestinations>,
    val backStacks: Map<AppDestinations, NavBackStack<NavKey>>,
    val isSingleStackMode: Boolean = false
) {
    var currentDestination: AppDestinations by currentDestinationState

    val currentBackStack: NavBackStack<NavKey>
        get() = backStacks[currentDestination] ?: error("未找到 $currentDestination 对应的回退栈")

    /**
     * 将每个 Tab 栈分别用独立的作用域装饰器进行装饰，并返回当前活跃呈现的 Entry 序列。
     */
    @Composable
    fun toDecoratedEntries(
        entryProvider: (NavKey) -> NavEntry<NavKey>
    ): List<NavEntry<NavKey>> {
        // 为每一个 Tab 栈挂载独立的 SaveableStateHolder 与 ViewModelStore 装饰器
        val decoratedEntries = backStacks.mapValues { (_, stack) ->
            val decorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>()
            )
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators = decorators,
                entryProvider = entryProvider
            )
        }

        // 当前呈现在屏幕上的，严格为当前激活 Tab 的 entry 序列（保证各 Tab 栈的纯粹与相互隔离）
        return decoratedEntries[currentDestination] ?: emptyList()
    }

    companion object {
        /**
         * 供单测或轻量单栈场景构建简单的 [RePoiNavigationState]
         */
        fun forSingleStack(
            backStack: NavBackStack<NavKey>,
            startDestination: AppDestinations = AppDestinations.HOME
        ): RePoiNavigationState {
            val state = mutableStateOf(startDestination)
            val stacks = AppDestinations.entries.associateWith { _ -> backStack }
            return RePoiNavigationState(
                startDestination = startDestination,
                currentDestinationState = state,
                backStacks = stacks,
                isSingleStackMode = true
            )
        }
    }
}

/**
 * 创建并记住持久化 [RePoiNavigationState]。
 */
@Composable
fun rememberRePoiNavigationState(
    startDestination: AppDestinations = AppDestinations.HOME,
    configuration: SavedStateConfiguration = SavedStateConfiguration {
        serializersModule = RePoiSerializersModule
    }
): RePoiNavigationState {
    val currentDestinationState = rememberSaveable { mutableStateOf(startDestination) }

    val homeStack = rememberNavBackStack(configuration, HomeNavKey)
    val itemsStack = rememberNavBackStack(configuration, ItemsNavKey)
    val albumStack = rememberNavBackStack(configuration, AlbumNavKey)
    val followStack = rememberNavBackStack(configuration, FollowNavKey)
    val profileStack = rememberNavBackStack(configuration, ProfileNavKey)

    val backStacks = remember(homeStack, itemsStack, albumStack, followStack, profileStack) {
        mapOf(
            AppDestinations.HOME to homeStack,
            AppDestinations.ITEMS to itemsStack,
            AppDestinations.ALBUM to albumStack,
            AppDestinations.FOLLOW to followStack,
            AppDestinations.PROFILE to profileStack,
        )
    }

    return remember(startDestination, currentDestinationState, backStacks) {
        RePoiNavigationState(
            startDestination = startDestination,
            currentDestinationState = currentDestinationState,
            backStacks = backStacks,
            isSingleStackMode = false
        )
    }
}
