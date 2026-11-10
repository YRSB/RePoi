package com.nobody.repoi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.nobody.repoi.platform.dynamicPoiShelfColorScheme
import com.nobody.repoi.data.local.ThemeSeed
import com.nobody.repoi.data.local.toColors
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

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
 * 根主题。
 *
 * [systemSchemeProvider] 是平台壁纸取色的注入缝：生产环境走
 * `expect/actual` 的 [dynamicPoiShelfColorScheme]，`jvmTest` 可注入固定的
 * 假系统配色来验证「动态主题下 style/spec 是否生效」（issue #82 回归）。
 */
@Composable
fun PoiShelfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedArgb: Long? = null,
    themeSeed: ThemeSeed? = null,
    amoled: Boolean = false,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    specVersion: ColorSpec.SpecVersion = ColorSpec.SpecVersion.SPEC_2025,
    systemSchemeProvider: @Composable (darkTheme: Boolean) -> ColorScheme? = { dynamicPoiShelfColorScheme(it) },
    content: @Composable () -> Unit
) {
    val activeSeed = themeSeed ?: seedArgb?.let { argb -> ThemeSeed.entries.firstOrNull { it.argb == argb } }
    val (seedPrimary, seedSecondary, seedTertiary) = activeSeed?.toColors()
        ?: Triple(seedArgb?.let { Color(it) }, null, null)

    // 平台取色是 @Composable 调用，必须在组合内直接调用；只取主色作锚点（issue #82）
    val wallpaperPrimary = if (dynamicColor && seedPrimary == null) {
        systemSchemeProvider(darkTheme)?.primary
    } else {
        null
    }

    val colorScheme = resolvePoiColorScheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        seedPrimary = seedPrimary,
        seedSecondary = seedSecondary,
        seedTertiary = seedTertiary,
        wallpaperPrimary = wallpaperPrimary,
        amoled = amoled,
        paletteStyle = paletteStyle,
        specVersion = specVersion,
        // 兜底配色不经过取色算法，需在此补一次 AMOLED 纯黑
        staticFallback = (if (darkTheme) DarkColorScheme else LightColorScheme)
            .let { if (amoled && darkTheme) it.withAmoledBlack() else it }
    )
    // 交互切换色彩风格 / 标准 / 种子色时弹簧过渡；冷启动首态落定与深浅模式切换均不补间（ADR-0012）
    val animatedScheme = colorScheme.animateAsState(darkTheme = darkTheme)
    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialExpressiveTheme(
            colorScheme = animatedScheme,
            typography = Typography,
            shapes = ExpressiveShapes,
            content = content
        )
    }
}
