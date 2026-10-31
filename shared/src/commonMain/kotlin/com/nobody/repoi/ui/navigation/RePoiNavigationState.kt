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
 * RePoi 主界面 Tab 状态持有者（全局单根栈拓扑）：
 *
 * 5 个顶层目的地各自持有一条仅含根条目的 [NavBackStack]，经独立的
 * [rememberSaveableStateHolderNavEntryDecorator] 与 [rememberViewModelStoreNavEntryDecorator] 修饰，
 * 保证 Tab 切换时页面 ViewModel、Lazy 列表滚动位置与表单状态全量保留。
 *
 * 二级页面不再压入 Tab 栈，而是压入 [RePoiNavigator.rootBackStack] 并整屏覆盖主界面。
 */
class RePoiNavigationState(
    val startDestination: AppDestinations = AppDestinations.HOME,
    private val currentDestinationState: MutableState<AppDestinations>,
    val backStacks: Map<AppDestinations, NavBackStack<NavKey>>
) {
    var currentDestination: AppDestinations by currentDestinationState

    /**
     * 将每个 Tab 栈分别用独立的作用域装饰器进行装饰，并返回当前活跃呈现的 Entry 序列。
     */
    @Composable
    fun toDecoratedEntries(
        entryProvider: (NavKey) -> NavEntry<NavKey>
    ): List<NavEntry<NavKey>> {
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

        return decoratedEntries[currentDestination] ?: emptyList()
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
            backStacks = backStacks
        )
    }
}
