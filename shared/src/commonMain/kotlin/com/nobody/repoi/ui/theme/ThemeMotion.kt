package com.nobody.repoi.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

private val ColdStartSnapSpec: AnimationSpec<Color> = snap()

@Composable
internal fun ColorScheme.animateAsState(
    darkTheme: Boolean,
    animationSpec: AnimationSpec<Color> = spring(),
): ColorScheme {

    val settleGate = remember { ColdStartSettleGate() }
    val effectiveSpec = if (settleGate.shouldSnap(this, darkTheme)) ColdStartSnapSpec else animationSpec

    return key(darkTheme) {
        @Composable
        fun animateColor(target: Color): Color = animateColorAsState(
            targetValue = target,
            animationSpec = effectiveSpec,
            label = "poi_theme_color"
        ).value

        copy(
            primary = animateColor(primary),
            onPrimary = animateColor(onPrimary),
            primaryContainer = animateColor(primaryContainer),
            onPrimaryContainer = animateColor(onPrimaryContainer),
            inversePrimary = animateColor(inversePrimary),
            secondary = animateColor(secondary),
            onSecondary = animateColor(onSecondary),
            secondaryContainer = animateColor(secondaryContainer),
            onSecondaryContainer = animateColor(onSecondaryContainer),
            tertiary = animateColor(tertiary),
            onTertiary = animateColor(onTertiary),
            tertiaryContainer = animateColor(tertiaryContainer),
            onTertiaryContainer = animateColor(onTertiaryContainer),
            background = animateColor(background),
            onBackground = animateColor(onBackground),
            surface = animateColor(surface),
            onSurface = animateColor(onSurface),
            surfaceVariant = animateColor(surfaceVariant),
            onSurfaceVariant = animateColor(onSurfaceVariant),
            surfaceTint = animateColor(surfaceTint),
            inverseSurface = animateColor(inverseSurface),
            inverseOnSurface = animateColor(inverseOnSurface),
            error = animateColor(error),
            onError = animateColor(onError),
            errorContainer = animateColor(errorContainer),
            onErrorContainer = animateColor(onErrorContainer),
            outline = animateColor(outline),
            outlineVariant = animateColor(outlineVariant),
            scrim = animateColor(scrim),
            surfaceBright = animateColor(surfaceBright),
            surfaceDim = animateColor(surfaceDim),
            surfaceContainer = animateColor(surfaceContainer),
            surfaceContainerHigh = animateColor(surfaceContainerHigh),
            surfaceContainerHighest = animateColor(surfaceContainerHighest),
            surfaceContainerLow = animateColor(surfaceContainerLow),
            surfaceContainerLowest = animateColor(surfaceContainerLowest),
            primaryFixed = animateColor(primaryFixed),
            primaryFixedDim = animateColor(primaryFixedDim),
            onPrimaryFixed = animateColor(onPrimaryFixed),
            onPrimaryFixedVariant = animateColor(onPrimaryFixedVariant),
            secondaryFixed = animateColor(secondaryFixed),
            secondaryFixedDim = animateColor(secondaryFixedDim),
            onSecondaryFixed = animateColor(onSecondaryFixed),
            onSecondaryFixedVariant = animateColor(onSecondaryFixedVariant),
            tertiaryFixed = animateColor(tertiaryFixed),
            tertiaryFixedDim = animateColor(tertiaryFixedDim),
            onTertiaryFixed = animateColor(onTertiaryFixed),
            onTertiaryFixedVariant = animateColor(onTertiaryFixedVariant)
        )
    }
}
