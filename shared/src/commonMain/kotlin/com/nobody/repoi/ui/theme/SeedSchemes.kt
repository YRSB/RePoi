package com.nobody.repoi.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.material3.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec

@Composable
fun seedColorScheme(
    seedColor: Color,
    darkTheme: Boolean,
    amoled: Boolean,
    secondary: Color? = null,
    tertiary: Color? = null,
    style: PaletteStyle = PaletteStyle.TonalSpot,
    specVersion: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025
): ColorScheme = remember(seedColor, darkTheme, amoled, secondary, tertiary, style, specVersion) {
    val scheme = dynamicColorScheme(
        seedColor = seedColor,
        isDark = darkTheme,
        isAmoled = amoled,
        secondary = secondary,
        tertiary = tertiary,
        style = style,
        specVersion = specVersion
    )
    if (darkTheme && amoled) scheme.withAmoledBlack() else scheme
}

@Composable
fun seedColorScheme(
    seedColor: Color,
    darkTheme: Boolean,
    amoled: Boolean,
    secondary: Color? = null,
    tertiary: Color? = null,
    expressiveColor: Boolean = false
): ColorScheme = seedColorScheme(
    seedColor = seedColor,
    darkTheme = darkTheme,
    amoled = amoled,
    secondary = secondary,
    tertiary = tertiary,
    style = if (expressiveColor) PaletteStyle.Expressive else PaletteStyle.TonalSpot,
    specVersion = if (expressiveColor) ColorSpec.SpecVersion.SPEC_2025 else ColorSpec.SpecVersion.SPEC_2021
)

@Composable
fun seedColorScheme(
    seedArgb: Long,
    darkTheme: Boolean,
    amoled: Boolean,
    secondary: Color? = null,
    tertiary: Color? = null,
    style: PaletteStyle = PaletteStyle.TonalSpot,
    specVersion: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025
): ColorScheme = seedColorScheme(
    seedColor = Color(seedArgb),
    darkTheme = darkTheme,
    amoled = amoled,
    secondary = secondary,
    tertiary = tertiary,
    style = style,
    specVersion = specVersion
)

@Composable
fun seedColorScheme(
    seedArgb: Long,
    darkTheme: Boolean,
    amoled: Boolean,
    secondary: Color? = null,
    tertiary: Color? = null,
    expressiveColor: Boolean = false
): ColorScheme = seedColorScheme(
    seedArgb = seedArgb,
    darkTheme = darkTheme,
    amoled = amoled,
    secondary = secondary,
    tertiary = tertiary,
    style = if (expressiveColor) PaletteStyle.Expressive else PaletteStyle.TonalSpot,
    specVersion = if (expressiveColor) ColorSpec.SpecVersion.SPEC_2025 else ColorSpec.SpecVersion.SPEC_2021
)

fun ColorScheme.withAmoledBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF111111),
    surfaceContainerHighest = Color(0xFF1A1A1A)
)
