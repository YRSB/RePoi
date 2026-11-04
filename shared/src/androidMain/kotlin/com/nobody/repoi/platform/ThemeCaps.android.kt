package com.nobody.repoi.platform

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val dynamicColorAllowlist = setOf(
    "xiaomi", "redmi", "poco", "oppo", "realme", "oneplus", "vivo", "iqoo", "honor", "huawei", "samsung", "google", "nothing", "motorola", "sony", "asus", "lenovo"
)

actual val supportsDynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

actual val isDynamicSeedAvailable: Boolean
    get() = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> false
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> true
        else -> dynamicColorAllowlist.contains(Build.MANUFACTURER.orEmpty().lowercase())
    }


@Composable
actual fun dynamicPoiShelfColorScheme(darkTheme: Boolean): ColorScheme? {
    if (!supportsDynamicColor) return null
    val context = LocalContext.current
    return if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}
