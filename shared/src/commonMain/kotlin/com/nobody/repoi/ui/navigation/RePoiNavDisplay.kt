package com.nobody.repoi.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent

/**
 * RePoi 统一通用导航容器（参考 PodAura 架构范式）：
 *
 * 1. 泛型抽象：解耦具体业务 NavKey，提供开箱即用的 Navigation 3 规范底座；
 * 2. 状态与作用域保持：默认装配 [rememberSaveableStateHolderNavEntryDecorator] 与 [rememberViewModelStoreNavEntryDecorator]；
 * 3. 场景策略扩展：默认挂载 [SinglePaneSceneStrategy]，为未来大屏双窗格多 Pane 演进预留标准化接口；
 * 4. Material 3 Expressive 动效基底：默认配置 [MaterialSharedAxis.Z] 进出场与预测性手势返回动效；
 * 5. 背景色防穿透：通过 [MaterialTheme.colorScheme.background] 彻底规避页面转场过程中的透明重叠与底色穿透闪烁。
 */
@Composable
fun <T : Any> RePoiNavDisplay(
    backStack: List<T>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {
        if (backStack is MutableList<T>) {
            backStack.removeLastOrNull()
        }
    },
    entryDecorators: List<NavEntryDecorator<T>> = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator()
    ),
    sceneStrategies: List<SceneStrategy<T>> = listOf(SinglePaneSceneStrategy()),
    transitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
        MaterialSharedAxis.Z.TransitionSpec
    },
    popTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
        MaterialSharedAxis.Z.PopTransitionSpec
    },
    predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<T>>.(
        @NavigationEvent.SwipeEdge Int
    ) -> ContentTransform = {
        MaterialSharedAxis.Z.PopTransitionSpec
    },
    entryProvider: (key: T) -> NavEntry<T>,
) = NavDisplay(
    backStack = backStack,
    modifier = modifier.background(MaterialTheme.colorScheme.background),
    onBack = onBack,
    entryDecorators = entryDecorators,
    sceneStrategies = sceneStrategies,
    transitionSpec = transitionSpec,
    popTransitionSpec = popTransitionSpec,
    predictivePopTransitionSpec = predictivePopTransitionSpec,
    entryProvider = entryProvider,
)
