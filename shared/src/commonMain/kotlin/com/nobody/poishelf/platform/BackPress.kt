package com.nobody.poishelf.platform

import androidx.compose.runtime.Composable

/**
 * 跨平台返回键拦截（M3 新增 UI 域桥接，M2 `plainTextClipEntry` 范式）。
 *
 * 背景：`androidx.activity.compose.BackHandler` 仅 `androidMain` 可见
 * （`activity-compose` 在 shared 构建中只接 `androidMain.dependencies`），
 * commonMain 三处调用点（`ModalSideSheet` / `HobbyListScreen` / `CommentSection`
 * 楼中楼覆盖层）无法直接 import。按 M2 expect/actual 范式收敛为本三文件。
 *
 * 语义：`enabled=false` 时不拦截；桌面端以后退导航/窗口关闭键惯例兜底。
 */
@Composable
expect fun PoiShelfBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/**
 * 横屏判定（M3 新增 UI 域桥接，M2 `plainTextClipEntry` 范式）。
 *
 * 背景：`androidx.compose.ui.platform.LocalConfiguration` 在 CMP common
 * 源集中不可用；`HomeFeedScreen` / `ProfileScreen` 仅需“是否横屏”布尔值
 * 做双列布局切换。按 M2 expect/actual 范式收敛为本三文件。
 */
@Composable
expect fun isLandscapeOrientation(): Boolean
