package com.nobody.repoi.ui.theme

import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * issue #82 的**纯决策**回归测试（commonTest，CI 的 JVM 与 Android-host 双目标都会跑）。
 *
 * 修复前的 bug 本质就是决策错误：动态主题拿到平台配色后直接整份采用，
 * 完全不看 `paletteStyle` / `specVersion`。这里把该决策钉死，
 * 不依赖 Compose 运行时与平台取色实现。
 */
class ThemeSchemeResolverTest {

    private val wallpaperPrimary = Color(0xFF2E6ADB)

    @Test
    fun dynamicThemeWithWallpaperColor_usesDynamicSourceNotPlatformScheme() {
        val source = resolveSchemeSource(
            dynamicColor = true,
            seedPrimary = null,
            seedSecondary = null,
            seedTertiary = null,
            wallpaperPrimary = wallpaperPrimary,
        )

        assertEquals(PoiSchemeSource.Dynamic(anchor = wallpaperPrimary), source)
    }

    @Test
    fun dynamicThemeDisabled_fallsBackToStaticFallback() {
        val source = resolveSchemeSource(
            dynamicColor = false,
            seedPrimary = null,
            seedSecondary = null,
            seedTertiary = null,
            wallpaperPrimary = wallpaperPrimary,
        )

        assertEquals(PoiSchemeSource.Fallback, source)
    }

    @Test
    fun platformWithoutWallpaperColor_fallsBackToStaticFallback() {
        val source = resolveSchemeSource(
            dynamicColor = true,
            seedPrimary = null,
            seedSecondary = null,
            seedTertiary = null,
            wallpaperPrimary = null,
        )

        assertEquals(PoiSchemeSource.Fallback, source)
    }

    @Test
    fun staticSeedWinsOverWallpaperColor() {
        val source = resolveSchemeSource(
            dynamicColor = true,
            seedPrimary = Color(0xFF006EBE),
            seedSecondary = null,
            seedTertiary = null,
            wallpaperPrimary = wallpaperPrimary,
        )

        assertEquals(PoiSchemeSource.Seed(primary = Color(0xFF006EBE), secondary = null, tertiary = null), source)
    }

    @Test
    fun spec2025_isDowngradedForStylesWithout2025Implementation() {
        // material-kolor 6.0.0 对这 5 种风格会静默回退到 2021（实测 48 个颜色角色全等），
        // 必须显式收敛，否则用户切「色彩标准」毫无反应
        val unsupported = listOf(
            PaletteStyle.Rainbow,
            PaletteStyle.FruitSalad,
            PaletteStyle.Monochrome,
            PaletteStyle.Fidelity,
            PaletteStyle.Content,
        )

        unsupported.forEach { style ->
            assertEquals(
                ColorSpec.SpecVersion.SPEC_2021,
                ColorSpec.SpecVersion.SPEC_2025.effectiveFor(style),
                "{style.name} 未实现 SPEC_2025，应回落到 SPEC_2021",
            )
            assertEquals(
                ColorSpec.SpecVersion.SPEC_2021,
                ColorSpec.SpecVersion.SPEC_2021.effectiveFor(style),
                "用户本就选择 SPEC_2021 时不应被改写",
            )
        }
    }

    @Test
    fun spec2025_isKeptForSupportingStyles() {
        val supported = listOf(
            PaletteStyle.TonalSpot,
            PaletteStyle.Neutral,
            PaletteStyle.Vibrant,
            PaletteStyle.Expressive,
        )

        supported.forEach { style ->
            assertEquals(
                ColorSpec.SpecVersion.SPEC_2025,
                ColorSpec.SpecVersion.SPEC_2025.effectiveFor(style),
                "{style.name} 实现了 SPEC_2025，不应被降级",
            )
        }
    }

    @Test
    fun tricolorSeedKeepsAllThreeRoles() {
        val source = resolveSchemeSource(
            dynamicColor = false,
            seedPrimary = Color(0xFFEAD4CE),
            seedSecondary = Color(0xFF7D859D),
            seedTertiary = Color(0xFFEC9CA8),
            wallpaperPrimary = null,
        )

        assertEquals(
            PoiSchemeSource.Seed(
                primary = Color(0xFFEAD4CE),
                secondary = Color(0xFF7D859D),
                tertiary = Color(0xFFEC9CA8),
            ),
            source,
        )
    }
}
