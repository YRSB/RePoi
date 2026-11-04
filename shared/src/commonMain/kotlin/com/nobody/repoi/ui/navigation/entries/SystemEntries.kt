package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.data.local.ThemeMode
import com.nobody.repoi.ui.login.LoginScreen
import com.nobody.repoi.ui.settings.SettingsScreen

/**
 * 注册登录鉴权与系统设置等系统支撑路由。
 */
fun EntryProviderScope<NavKey>.systemEntries(
    navigator: RePoiNavigator,
    onBack: () -> Unit,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
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
            onOpenShieldList = { navigator.navigateToSettingsShieldList() },
            onOpenBlocklist = { navigator.navigateToMessageBlock() },
            onBack = onBack
        )
    }
}
