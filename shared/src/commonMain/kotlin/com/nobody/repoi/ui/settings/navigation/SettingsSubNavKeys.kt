package com.nobody.repoi.ui.settings.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * 设置页内部子路由契约（模块私有子 Key，不污染全局导航根栈）。
 */
@Serializable
sealed interface SettingsSubNavKey : NavKey

/**
 * 一级分组列表页（主列表）。
 */
@Serializable
data object SettingsListNavKey : SettingsSubNavKey

/**
 * 外观与主题详情页。
 */
@Serializable
data object SettingsAppearanceNavKey : SettingsSubNavKey

/**
 * 浏览与内容详情页。
 */
@Serializable
data object SettingsContentNavKey : SettingsSubNavKey

/**
 * 账号与安全详情页。
 */
@Serializable
data object SettingsAccountNavKey : SettingsSubNavKey

/**
 * 设置页内部子栈多态序列化模块（供 SavedStateConfiguration 持久化与单测使用）。
 */
val SettingsSubNavSerializersModule: kotlinx.serialization.modules.SerializersModule = kotlinx.serialization.modules.SerializersModule {
    polymorphic(NavKey::class) {
        subclass(SettingsListNavKey::class)
        subclass(SettingsAppearanceNavKey::class)
        subclass(SettingsContentNavKey::class)
        subclass(SettingsAccountNavKey::class)
    }
    polymorphic(SettingsSubNavKey::class) {
        subclass(SettingsListNavKey::class)
        subclass(SettingsAppearanceNavKey::class)
        subclass(SettingsContentNavKey::class)
        subclass(SettingsAccountNavKey::class)
    }
}

/**
 * 设置项一级业务分组枚举。
 */
enum class SettingsGroup(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val navKey: SettingsSubNavKey
) {
    Appearance("外观与主题", "主题颜色、深色模式与壁纸取色", Icons.Outlined.Palette, SettingsAppearanceNavKey),
    Content("浏览与内容", "屏蔽过滤、R18 限制与隐私可见性", Icons.Outlined.Tune, SettingsContentNavKey),
    Account("账号与安全", "当前账号状态、个人主页与手账同步", Icons.Outlined.ManageAccounts, SettingsAccountNavKey);

    companion object {
        fun fromNavKey(key: NavKey?): SettingsGroup? = when (key) {
            is SettingsAppearanceNavKey -> Appearance
            is SettingsContentNavKey -> Content
            is SettingsAccountNavKey -> Account
            else -> null
        }
    }
}
