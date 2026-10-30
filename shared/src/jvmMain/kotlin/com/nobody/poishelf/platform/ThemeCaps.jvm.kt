package com.nobody.poishelf.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

actual val supportsDynamicColor: Boolean = false

@Composable
actual fun dynamicPoiShelfColorScheme(darkTheme: Boolean): ColorScheme? = null
