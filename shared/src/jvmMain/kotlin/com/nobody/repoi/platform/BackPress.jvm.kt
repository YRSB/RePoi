package com.nobody.repoi.platform

import androidx.compose.runtime.Composable

/**
 * JVM 桌面返回键桥接：桌面无系统返回键概念，此处为空实现
 * （调用点仅在弹窗/覆盖层展示时启用；桌面端经窗口关闭键关闭）。
 */
@Composable
actual fun PoiShelfBackHandler(enabled: Boolean, onBack: () -> Unit) {
}

/**
 * JVM 桌面横屏判定兜底：桌面双列布局改由 `currentWindowAdaptiveInfoV2`
 * 宽度断点独立判定，本函数恒返回 false（竖屏语义）。
 */
@Composable
actual fun isLandscapeOrientation(): Boolean = false
