package com.nobody.repoi.ui.navigation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import com.nobody.repoi.data.local.ThemeManager
import com.nobody.repoi.data.local.ThemeMode
import com.nobody.repoi.data.local.ThemeSeed
import com.nobody.repoi.data.local.defaultThemeSettings
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.data.local.orAvailable
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.LocalMediaPipeline
import com.nobody.repoi.ui.common.rememberImageViewerState
import com.nobody.repoi.ui.theme.PoiShelfTheme
import org.koin.compose.koinInject

@Composable
fun RePoiApp() {
    val imageLoader: ImageLoader = koinInject()
    setSingletonImageLoaderFactory { imageLoader }
    val themeManager: ThemeManager = koinInject()

    val settings by themeManager.settings.collectAsStateWithLifecycle()
    val current = settings ?: defaultThemeSettings()

    val darkTheme = when (current.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val effectiveSeed = when {
        current.themeSeed != ThemeSeed.Dynamic -> current.themeSeed.orAvailable()
        !current.dynamicColor -> ThemeSeed.Lotus
        else -> null
    }

    PoiShelfTheme(
        darkTheme = darkTheme,
        dynamicColor = current.dynamicColor,
        themeSeed = effectiveSeed,
        amoled = current.amoled,
        paletteStyle = current.paletteStyle,
        specVersion = current.specVersion
    ) {
        RePoiNavHost()
    }
}

@Composable
private fun RePoiNavHost() {
    val appMessages = remember { RootMessageBus() }
    val rootSnackbarHostState = remember { SnackbarHostState() }

    val mediaPipeline: MediaPipeline = koinInject()

    val savedStateConfiguration = remember {
        SavedStateConfiguration { serializersModule = RePoiSerializersModule }
    }

    val rootBackStack = rememberNavBackStack(savedStateConfiguration, MainNavKey)
    val tabState = rememberRePoiNavigationState(
        startDestination = AppDestinations.HOME,
        configuration = savedStateConfiguration
    )
    val navigator = remember(rootBackStack, tabState) {
        RePoiNavigator(rootBackStack = rootBackStack, tabState = tabState)
    }

    val imageViewerState = rememberImageViewerState(navigator)
    val tabEntryProvider: (NavKey) -> NavEntry<NavKey> = remember(navigator) {
        entryProvider<NavKey> { topLevelEntries(navigator) }
    }

    val tabEntries = rememberUpdatedState(tabState.toDecoratedEntries(tabEntryProvider))

    NavigationSnackbarDismissalEffect(
        navigator = navigator,
        snackbarHostState = rootSnackbarHostState
    )

    LaunchedEffect(appMessages) {
        while (true) {
            val message = appMessages.take()
            rootSnackbarHostState.showSnackbar(message)
        }
    }

    val systemUriHandler = LocalUriHandler.current
    val appUriHandler = remember(navigator, systemUriHandler, appMessages) {
        object : UriHandler {
            override fun openUri(uri: String) {
                navigator.navigateUrl(uri, systemUriHandler, appMessages::show)
            }
        }
    }

    CompositionLocalProvider(
        LocalUriHandler provides appUriHandler,
        LocalImageViewer provides imageViewerState,
        LocalAppMessages provides appMessages,
        LocalMediaPipeline provides mediaPipeline
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val atMainRoot = rootBackStack.size <= 1
            AppNavDisplay(
                rootBackStack = rootBackStack,
                navigator = navigator,
                tabEntries = tabEntries,
                snackbarHostState = rootSnackbarHostState,
                showSnackbarHost = atMainRoot,
                onLoginSuccess = {
                    while (rootBackStack.size > 1) rootBackStack.removeLastOrNull()
                    navigator.selectTab(AppDestinations.PROFILE)
                }
            )

            if (!atMainRoot) {
                SnackbarHost(
                    hostState = rootSnackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                )
            }
        }
    }
}
