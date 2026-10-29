package com.nobody.poishelf.ui.navigation

import kotlinx.serialization.Serializable

/**
 * M4 batch-A/B 详情页路由键占位（M6 `NavRoutes.kt` 完整定义移植前占位）。
 *
 * 背景：M4 移植的详情屏（F17 角色 / F20 人物 / F21 作品 / F22 系列 /
 * F23 厂商 / F24 厂商动态）经各自 NavKey 入栈；完整 root nav
 *（含 `AppNavKey`/`NavBackStack`）归属 M6。
 * M4 仅保留跳转回调签名编译通过：`param` 即各屏 `navParam:` 实参形态，
 * `java.util.UUID` 唯一 id 按 M3 `NavParams.kt` 惯例在此占位层不设。
 * M6 落地时删除本文件、改由导航模块 import（TODO(M6)）。
 *
 * 注意：人物/作品/系列/厂商四类 param 经由同包 `NavParams.kt`
 * 的 `PersonDetailNavParam/WorksDetailNavParam/SeriesDetailNavParam/VendorDetailNavParam`
 * 类型别名引用，不直接 import 各 VM 包，避免跨包同名遮蔽。
 */
@Serializable
data class CharacterNavKey(
    val param: CharacterNavParam,
)

@Serializable
data class PersonDetailNavKey(
    val param: PersonDetailNavParam,
)

@Serializable
data class WorksDetailNavKey(
    val param: WorksDetailNavParam,
)

@Serializable
data class SeriesDetailNavKey(
    val param: SeriesDetailNavParam,
)

@Serializable
data class VendorDetailNavKey(
    val param: VendorDetailNavParam,
)

@Serializable
data class VendorActionsNavKey(
    val param: VendorActionsNavParam,
)
