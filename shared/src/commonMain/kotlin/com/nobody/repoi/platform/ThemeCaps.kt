package com.nobody.repoi.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

expect val supportsDynamicColor: Boolean

@Composable
expect fun dynamicPoiShelfColorScheme(darkTheme: Boolean): ColorScheme?
