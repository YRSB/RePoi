package com.nobody.repoi.ui.common

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop 无系统返回键：no-op。
}
