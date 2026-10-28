package com.nobody.poishelf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = CoralRedPrimaryLight,
    onPrimary = Color(0xFF420005),
    primaryContainer = CoralRedPrimaryDark,
    onPrimaryContainer = Color(0xFFFFDCDA),
    secondary = AmberSecondaryLight,
    onSecondary = Color(0xFF452200),
    secondaryContainer = Color(0xFF6B3700),
    onSecondaryContainer = Color(0xFFFFDBC8),
    tertiary = InfoBlueDark,
    onTertiary = Color(0xFF00344F),
    tertiaryContainer = Color(0xFF004A77),
    onTertiaryContainer = Color(0xFFD6EBFF),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = ErrorRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = CoralRedPrimary,
    onPrimary = Color.White,
    primaryContainer = CoralRedContainer,
    onPrimaryContainer = OnCoralRedContainer,
    secondary = AmberSecondary,
    onSecondary = Color.White,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiary = InfoBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6F4FF),
    onTertiaryContainer = Color(0xFF001D33),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = ErrorRed,
    onError = Color.White
)

/**
 * PoiShelf 跨平台主题（KMP commonMain）。
 *
 * 与原生版差异（`ui/theme/Theme.kt`）：
 * - 删除动态取色（`dynamicDarkColorScheme/dynamicLightColorScheme`，`Build.VERSION.SDK` 分支）；
 *   是否启用动态取色由 M6 root 经 [com.nobody.poishelf.platform.supportsDynamicColor]
 *   （ThemeCaps expect）决定，主题本体只保留静态双色。
 * - 删除 Android 专属窗口行为（`LocalView`/`SideEffect`/`WindowCompat` 状态栏字色控制）；
 *   各 shell 按平台惯例自行处理（M6）。
 */
@Composable
fun PoiShelfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
