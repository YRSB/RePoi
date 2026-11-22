package com.nobody.repoi.ui.settings.navigation

import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.common.PoiIcons
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.compose.resources.DrawableResource

@Serializable
sealed interface SettingsSubNavKey : NavKey

@Serializable
data object SettingsListNavKey : SettingsSubNavKey

@Serializable
data object SettingsAppearanceNavKey : SettingsSubNavKey

@Serializable
data object SettingsContentNavKey : SettingsSubNavKey

@Serializable
data object SettingsAccountNavKey : SettingsSubNavKey

val SettingsSubNavSerializersModule: SerializersModule = SerializersModule {
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

enum class SettingsGroup(
    val title: String,
    val description: String,
    val icon: DrawableResource,
    val navKey: SettingsSubNavKey
) {
    Appearance("外观与主题", "主题颜色、深色模式与壁纸取色", PoiIcons.Outlined.Palette, SettingsAppearanceNavKey),
    Content("浏览与内容", "屏蔽过滤、R18 限制与隐私可见性", PoiIcons.Outlined.Tune, SettingsContentNavKey),
    Account("账号与安全", "当前账号状态、个人主页与手账同步", PoiIcons.Outlined.ManageAccounts, SettingsAccountNavKey);

    companion object {
        fun fromNavKey(key: NavKey?): SettingsGroup? = when (key) {
            is SettingsAppearanceNavKey -> Appearance
            is SettingsContentNavKey -> Content
            is SettingsAccountNavKey -> Account
            else -> null
        }
    }
}
