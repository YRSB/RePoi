package com.nobody.repoi.platform

import android.graphics.Color
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Android 端全屏弹窗沉浸注入：
 *
 * 通过回溯 [LocalView] 父级链获取 [DialogWindowProvider]，向底层原生 [android.view.Window] 注入：
 * 1. [WindowCompat.setDecorFitsSystemWindows(window, false)]：突破弹窗默认的内嵌限制，允许视图绘制穿透到系统状态栏与导航栏后方；
 * 2. 状态栏与导航栏颜色全透明，并禁用 API 29+ 导航栏强制对比度蒙层；
 * 3. 尺寸重置为 [ViewGroup.LayoutParams.MATCH_PARENT]，保证物理屏幕全铺满；
 * 4. API 28+ 开启 [WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES]，支持大图穿透刘海/挖孔屏区域。
 */
@Composable
actual fun FullscreenDialogEdgeToEdgeEffect() {
    val view = LocalView.current
    SideEffect {
        var parent = view.parent
        while (parent != null && parent !is DialogWindowProvider) {
            parent = parent.parent
        }
        val window = (parent as? DialogWindowProvider)?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
    }
}
