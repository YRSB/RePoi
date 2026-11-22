package com.nobody.repoi.platform

import androidx.compose.runtime.Composable

@Composable
actual fun PoiShelfBackHandler(enabled: Boolean, onBack: () -> Unit) {
}

@Composable
actual fun isLandscapeOrientation(): Boolean = false
