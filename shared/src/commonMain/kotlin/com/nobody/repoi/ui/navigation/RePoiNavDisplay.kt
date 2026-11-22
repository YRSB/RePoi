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

@Composable
fun <T : Any> RePoiNavDisplay(
    entries: List<NavEntry<T>>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
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
) = NavDisplay(
    entries = entries,
    modifier = modifier.background(MaterialTheme.colorScheme.background),
    onBack = onBack,
    sceneStrategies = sceneStrategies,
    transitionSpec = transitionSpec,
    popTransitionSpec = popTransitionSpec,
    predictivePopTransitionSpec = predictivePopTransitionSpec,
)
