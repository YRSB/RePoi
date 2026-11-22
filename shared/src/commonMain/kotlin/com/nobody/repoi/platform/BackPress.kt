package com.nobody.repoi.platform

import androidx.compose.runtime.Composable

@Composable
expect fun PoiShelfBackHandler(enabled: Boolean = true, onBack: () -> Unit)

@Composable
expect fun isLandscapeOrientation(): Boolean
