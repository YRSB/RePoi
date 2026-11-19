package com.nobody.repoi.platform

import androidx.compose.runtime.Composable

/**
 * 为具有全屏属性（usePlatformDefaultWidth = false + fillMaxSize）的 Dialog 注入底层窗口 edge-to-edge 穿透效果。
 *
 * Android 端通过回溯 DialogWindowProvider 获取原生 Dialog Window，注入 setDecorFitsSystemWindows(false)
 * 并清除系统栏遮挡；桌面端无系统栏，保留空实现。
 */
@Composable
expect fun FullscreenDialogEdgeToEdgeEffect()
