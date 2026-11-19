package com.nobody.repoi.platform

import androidx.compose.runtime.Composable

/**
 * 桌面端全屏弹窗沉浸注入：空实现。
 *
 * 桌面端（Windows / Desktop）由操作系统窗口管理器接管窗口边界，无移动端系统状态栏与手势条，无需注入穿透逻辑。
 */
@Composable
actual fun FullscreenDialogEdgeToEdgeEffect() {
    // 桌面端无系统栏，空实现
}

@Composable
actual fun ReplyDialogEdgeToEdgeEffect() {
    // 桌面端无软键盘与系统栏，空实现
}
