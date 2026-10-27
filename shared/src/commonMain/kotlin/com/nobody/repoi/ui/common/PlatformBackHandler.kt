package com.nobody.repoi.ui.common

import androidx.compose.runtime.Composable

/**
 * 跨平台返回键拦截。
 *
 * `androidx.activity.compose.BackHandler` 仅 Android 可用，Desktop 端退化为空实现。
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
