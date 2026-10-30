package com.nobody.repoi.ui.navigation.entries

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.data.local.ThemeMode
import com.nobody.repoi.ui.login.LoginScreen
import com.nobody.repoi.ui.navigation.LoginNavKey
import com.nobody.repoi.ui.navigation.SettingsNavKey
import com.nobody.repoi.ui.settings.SettingsScreen

/**
 * 注册登录鉴权与系统设置等系统支撑路由。
 */
fun EntryProviderScope<NavKey>.systemEntries(
    onBack: () -> Unit,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onOpenDebugScreen: () -> Unit,
    onLoginSuccess: () -> Unit,
) {
    entry<LoginNavKey> {
        LoginScreen(
            onBack = onBack,
            onLoginSuccess = {
                onLoginSuccess()
            }
        )
    }

    entry<SettingsNavKey> {
        SettingsScreen(
            themeMode = themeMode,
            dynamicColor = dynamicColor,
            onThemeModeChange = onThemeModeChange,
            onDynamicColorChange = onDynamicColorChange,
            onOpenDebug = onOpenDebugScreen,
            onBack = onBack
        )
    }
}
