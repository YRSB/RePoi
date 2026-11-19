package com.nobody.repoi.platform

import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Android 端全屏弹窗沉浸注入：
 *
 * 1. 遍历当前 View 及父级链（包括 [LocalView.current] 本身及祖先节点），查找 [DialogWindowProvider]；
 * 2. 备选方案：从 Context 回溯 [android.app.Dialog] 原生实例获取底层 [Window]；
 * 3. 注入 [WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS] 突破系统窗口物理限制，全屏穿透；
 * 4. 注入 [WindowCompat.setDecorFitsSystemWindows(window, false)]、系统栏全透明并关闭 API 29+ 导航栏对比度蒙层；
 * 5. API 30+ 注入 fitInsetsTypes = 0，彻底防止系统状态栏与导航栏强制向内裁切 Dialog；
 * 6. 尺寸设为 [ViewGroup.LayoutParams.MATCH_PARENT]；
 * 7. API 28+ 开启 [WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES]，支持大图穿透刘海/挖孔屏区域；
 * 8. 强制状态栏与导航栏图标为浅色（白色图标），防止在纯黑全屏背景上因深色图标产生灰色背景保底。
 */
@Composable
actual fun FullscreenDialogEdgeToEdgeEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val apply = {
            findDialogWindow(view)?.let { window ->
                applyEdgeToEdgeToDialogWindow(window)
            }
        }
        apply()

        // 监听 View 挂载，确保即使首次组合时 View 尚未 attach 也能在挂载瞬间完成穿透配置
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                apply()
            }
            override fun onViewDetachedFromWindow(v: View) {}
        }
        view.addOnAttachStateChangeListener(attachListener)

        onDispose {
            view.removeOnAttachStateChangeListener(attachListener)
        }
    }
}

private fun findDialogWindow(view: View): Window? {
    var current: Any? = view
    while (current != null) {
        if (current is DialogWindowProvider) {
            return current.window
        }
        current = (current as? View)?.parent
    }
    return null
}

private fun applyEdgeToEdgeToDialogWindow(window: Window) {
    WindowCompat.setDecorFitsSystemWindows(window, false)

    window.setFlags(
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    )
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT

    val params = window.attributes
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        params.fitInsetsTypes = 0
        params.fitInsetsSides = 0
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }

    window.setLayout(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    window.attributes = params

    (window.decorView as? ViewGroup)?.fitsSystemWindows = false

    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
    insetsController.isAppearanceLightStatusBars = false
    insetsController.isAppearanceLightNavigationBars = false
}

/**
 * 评论回复等含软键盘输入的抽屉式 Dialog 专属 Edge-to-Edge 配置：
 * 绝不注入 FLAG_LAYOUT_NO_LIMITS 与 fitInsetsTypes = 0，保证系统软键盘 Insets 动态分发给 Compose。
 */
@Composable
actual fun ReplyDialogEdgeToEdgeEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val apply = {
            findDialogWindow(view)?.let { window ->
                applyEdgeToEdgeToReplyDialogWindow(window)
            }
        }
        apply()

        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                apply()
            }
            override fun onViewDetachedFromWindow(v: View) {}
        }
        view.addOnAttachStateChangeListener(attachListener)

        onDispose {
            view.removeOnAttachStateChangeListener(attachListener)
        }
    }
}

private fun applyEdgeToEdgeToReplyDialogWindow(window: Window) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }

    window.setLayout(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )

    (window.decorView as? ViewGroup)?.fitsSystemWindows = false
}

