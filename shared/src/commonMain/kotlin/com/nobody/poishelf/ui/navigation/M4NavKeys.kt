package com.nobody.poishelf.ui.navigation

import kotlinx.serialization.Serializable
import com.nobody.poishelf.ui.person.PersonNavParam
import com.nobody.poishelf.ui.works.WorksNavParam
import com.nobody.poishelf.ui.series.SeriesNavParam
import com.nobody.poishelf.ui.vendor.VendorNavParam

/**
 * M4 batch-A/B 详情页路由键占位（M6 `NavRoutes.kt` 完整定义移植前占位）。
 *
 * 背景：M4 移植的详情屏（F17 角色 / F20 人物 / F21 作品 / F22 系列 /
 * F23 厂商 / F24 厂商动态）经各自 NavKey 入栈；完整 root nav
 *（含 `AppNavKey`/`NavBackStack`）归属 M6。
 * M4 仅保留跳转回调签名编译通过：`param` 即各屏 `navParam:` 实参形态，
 * `java.util.UUID` 唯一 id 按 M3 `NavParams.kt` 惯例在此占位层不设。
 * M6 落地时删除本文件、改由导航模块 import（TODO(M6)）。
 */
@Serializable
data class CharacterNavKey(
    val param: CharacterNavParam,
)

@Serializable
data class PersonDetailNavKey(
    val param: PersonNavParam,
)

@Serializable
data class WorksDetailNavKey(
    val param: WorksNavParam,
)

@Serializable
data class SeriesDetailNavKey(
    val param: SeriesNavParam,
)

@Serializable
data class VendorDetailNavKey(
    val param: VendorNavParam,
)

@Serializable
data class VendorActionsNavKey(
    val param: VendorActionsNavParam,
)
