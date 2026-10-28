package com.nobody.poishelf.ui.navigation

import kotlinx.serialization.Serializable

/**
 * M3 本地图包列表路由键占位（M6 `NavRoutes.kt:370` 完整定义移植前占位）。
 *
 * 背景：`AlbumDetailScreen` / `HobbyDetailScreen` 的图包预览区经此键跳转
 * M5 `ExHobbyList` 屏；完整 root nav（含 NavKey）归属 M6。
 * M3 仅保留跳转回调签名编译通过，M6 落地时删除本文件、改由导航模块 import（TODO(M6)）。
 */
@Serializable
data class ExHobbyListNavKey(
    val itemId: Long,
    val title: String = "ExHobby"
)
