package com.nobody.repoi.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

internal val PaletteStyle.supportsSpec2025: Boolean
    get() = this == PaletteStyle.TonalSpot ||
        this == PaletteStyle.Neutral ||
        this == PaletteStyle.Vibrant ||
        this == PaletteStyle.Expressive

internal fun ColorSpec.SpecVersion.effectiveFor(style: PaletteStyle): ColorSpec.SpecVersion =
    if (this == ColorSpec.SpecVersion.SPEC_2025 && !style.supportsSpec2025) {
        ColorSpec.SpecVersion.SPEC_2021
    } else {
        this
    }

internal sealed interface PoiSchemeSource {

    data class Seed(val primary: Color, val secondary: Color?, val tertiary: Color?) : PoiSchemeSource

    data class Dynamic(val anchor: Color) : PoiSchemeSource

    data object Fallback : PoiSchemeSource
}

internal fun resolveSchemeSource(
    dynamicColor: Boolean,
    seedPrimary: Color?,
    seedSecondary: Color?,
    seedTertiary: Color?,
    wallpaperPrimary: Color?,
): PoiSchemeSource = when {
    seedPrimary != null -> PoiSchemeSource.Seed(seedPrimary, seedSecondary, seedTertiary)
    dynamicColor && wallpaperPrimary != null -> PoiSchemeSource.Dynamic(wallpaperPrimary)
    else -> PoiSchemeSource.Fallback
}

@Composable
internal fun resolvePoiColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    seedPrimary: Color?,
    seedSecondary: Color?,
    seedTertiary: Color?,
    wallpaperPrimary: Color?,
    amoled: Boolean,
    paletteStyle: PaletteStyle,
    specVersion: ColorSpec.SpecVersion,
    staticFallback: ColorScheme,
): ColorScheme {

    val effectiveSpec = specVersion.effectiveFor(paletteStyle)
    return when (
        val source = resolveSchemeSource(dynamicColor, seedPrimary, seedSecondary, seedTertiary, wallpaperPrimary)
    ) {
        is PoiSchemeSource.Seed -> seedColorScheme(
            seedColor = source.primary,
            darkTheme = darkTheme,
            amoled = amoled,
            secondary = source.secondary,
            tertiary = source.tertiary,
            style = paletteStyle,
            specVersion = effectiveSpec
        )

        is PoiSchemeSource.Dynamic -> seedColorScheme(
            seedColor = source.anchor,
            darkTheme = darkTheme,
            amoled = amoled,
            style = paletteStyle,
            specVersion = effectiveSpec
        )

        PoiSchemeSource.Fallback -> staticFallback
    }
}
