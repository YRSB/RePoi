package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.login.LoginScreen
import com.nobody.repoi.ui.settings.SettingsScreen
import com.nobody.repoi.ui.settings.shield.SettingsShieldListContent

fun EntryProviderScope<NavKey>.systemEntries(
    navigator: RePoiNavigator,
    onBack: () -> Unit,
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
            onBack = onBack,
            onOpenUserInfo = { userId -> navigator.navigateToUser(userId) },
            onOpenShieldList = { navigator.navigateToSettingsShieldList() },
            onOpenBlocklist = { navigator.navigateToMessageBlock() },
            onNavigateToLogin = { navigator.navigateToLogin() }
        )
    }
    entry<SettingsShieldListNavKey> {
        SettingsShieldListContent(onBack = onBack)
    }
}
