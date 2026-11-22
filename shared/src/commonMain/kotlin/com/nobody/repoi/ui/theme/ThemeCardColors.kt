package com.nobody.repoi.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.ktx.from
import com.materialkolor.ktx.toneColor
import com.materialkolor.material3.dynamicColorScheme
import com.materialkolor.palettes.TonalPalette
import com.nobody.repoi.data.local.ThemeSeed
import com.nobody.repoi.data.local.toColors
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@Immutable
data class ThemeCardColors(
    val primaryTone36: Color,
    val secondaryTone80: Color,
    val tertiaryTone65: Color,
    val primaryTone50: Color
)

fun computeThemeCardColors(
    seed: ThemeSeed,
    paletteStyle: PaletteStyle,
    specVersion: ColorSpec.SpecVersion
): ThemeCardColors {
    val (primary, secondary, tertiary) = seed.toColors()
    val scheme = dynamicColorScheme(
        seedColor = primary ?: Color(0xFF006EBE),
        isDark = false,
        isAmoled = false,
        secondary = secondary,
        tertiary = tertiary,
        style = paletteStyle,
        specVersion = specVersion
    )
    return themeCardColorsFromScheme(scheme)
}

fun themeCardColorsFromScheme(scheme: ColorScheme): ThemeCardColors {
    val pPalette = TonalPalette.from(scheme.primary)
    val sPalette = TonalPalette.from(scheme.secondary)
    val tPalette = TonalPalette.from(scheme.tertiary)
    return ThemeCardColors(
        primaryTone36 = pPalette.toneColor(36),
        secondaryTone80 = sPalette.toneColor(80),
        tertiaryTone65 = tPalette.toneColor(65),
        primaryTone50 = pPalette.toneColor(50)
    )
}

private data class ThemeCardColorsCacheKey(
    val seed: ThemeSeed,
    val style: PaletteStyle,
    val specVersion: ColorSpec.SpecVersion
)

@OptIn(ExperimentalAtomicApi::class)
private val cardColorsCache = AtomicReference<Map<ThemeCardColorsCacheKey, ThemeCardColors>>(emptyMap())

@OptIn(ExperimentalAtomicApi::class)
fun getOrCreateThemeCardColors(
    seed: ThemeSeed,
    paletteStyle: PaletteStyle,
    specVersion: ColorSpec.SpecVersion
): ThemeCardColors {
    val key = ThemeCardColorsCacheKey(seed, paletteStyle, specVersion)
    val current = cardColorsCache.load()
    current[key]?.let { return it }

    val computed = computeThemeCardColors(seed, paletteStyle, specVersion)
    var success = false
    while (!success) {
        val old = cardColorsCache.load()
        if (old.containsKey(key)) {
            return old.getValue(key)
        }
        val newMap = old + (key to computed)
        success = cardColorsCache.compareAndSet(old, newMap)
    }
    return computed
}

fun warmUpThemeCardColors() {
    val styles = listOf(PaletteStyle.TonalSpot, PaletteStyle.Expressive)
    val specVersions = listOf(ColorSpec.SpecVersion.SPEC_2025)
    for (style in styles) {
        for (spec in specVersions) {
            for (seed in ThemeSeed.entries) {
                if (seed != ThemeSeed.Dynamic) {
                    getOrCreateThemeCardColors(seed, style, spec)
                }
            }
        }
    }
}

@OptIn(ExperimentalAtomicApi::class)
internal fun getThemeCardCacheSize(): Int = cardColorsCache.load().size

@OptIn(ExperimentalAtomicApi::class)
internal fun clearThemeCardColorsCacheForTesting() {
    cardColorsCache.store(emptyMap())
}
