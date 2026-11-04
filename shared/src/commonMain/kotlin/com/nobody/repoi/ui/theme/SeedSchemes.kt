package com.nobody.repoi.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.material3.dynamicColorScheme

@Composable
fun seedColorScheme(seedArgb: Long, darkTheme: Boolean, amoled: Boolean): ColorScheme {
    val scheme = dynamicColorScheme(
        seedColor = Color(seedArgb),
        isDark = darkTheme,
        isAmoled = amoled
    )
    return if (darkTheme && amoled) scheme.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black,
        surfaceContainer = Color(0xFF0A0A0A),
        surfaceContainerHigh = Color(0xFF111111),
        surfaceContainerHighest = Color(0xFF1A1A1A)
    ) else scheme
}

fun fallbackSeedScheme(seedArgb: Long, darkTheme: Boolean): ColorScheme =
    if (darkTheme) darkColorScheme(
        primary = Color(seedArgb),
        onPrimary = Color.White,
        primaryContainer = Color(seedArgb).copy(alpha = 0.35f),
        secondary = Color(seedArgb),
        tertiary = Color(seedArgb)
    ) else lightColorScheme(
        primary = Color(seedArgb),
        onPrimary = Color.White,
        primaryContainer = Color(seedArgb).copy(alpha = 0.15f),
        secondary = Color(seedArgb),
        tertiary = Color(seedArgb)
    )
