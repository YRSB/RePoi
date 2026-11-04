package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.data.local.ThemeMode
import com.nobody.repoi.data.local.ThemeSeed
import com.nobody.repoi.ui.login.LoginScreen
import com.nobody.repoi.ui.settings.SettingsScreen
import com.nobody.repoi.ui.settings.shield.SettingsShieldListContent

/**
 * 注册登录鉴权与系统设置等系统支撑路由。
 */
fun EntryProviderScope<NavKey>.systemEntries(
    navigator: RePoiNavigator,
    onBack: () -> Unit,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    themeSeed: ThemeSeed,
    amoled: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onThemeSeedChange: (ThemeSeed) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
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
            themeSeed = themeSeed,
            amoled = amoled,
            onThemeModeChange = onThemeModeChange,
            onDynamicColorChange = onDynamicColorChange,
            onThemeSeedChange = onThemeSeedChange,
            onAmoledChange = onAmoledChange,
            onBack = onBack,
            onOpenUserInfo = { userId -> navigator.navigateToUser(userId) },
            onOpenShieldList = { navigator.navigateToSettingsShieldList() },
            onOpenBlocklist = { navigator.navigateToMessageBlock() }
        )
    }
    entry<SettingsShieldListNavKey> {
        SettingsShieldListContent(onBack = onBack)
    }
}
