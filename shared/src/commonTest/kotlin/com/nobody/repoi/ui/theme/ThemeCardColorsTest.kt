package com.nobody.repoi.ui.theme

import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.nobody.repoi.data.local.ThemeSeed
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThemeCardColorsTest {

    @BeforeTest
    fun setUp() {
        clearThemeCardColorsCacheForTesting()
    }

    @Test
    fun computeThemeCardColors_returnsValidColors() {
        for (seed in ThemeSeed.entries) {
            if (seed == ThemeSeed.Dynamic) continue
            val colors = computeThemeCardColors(
                seed = seed,
                paletteStyle = PaletteStyle.TonalSpot,
                specVersion = ColorSpec.SpecVersion.SPEC_2025
            )
            assertNotEquals(Color.Unspecified, colors.primaryTone36)
            assertNotEquals(Color.Unspecified, colors.secondaryTone80)
            assertNotEquals(Color.Unspecified, colors.tertiaryTone65)
            assertNotEquals(Color.Unspecified, colors.primaryTone50)
        }
    }

    @Test
    fun getOrCreateThemeCardColors_cachesInstance() {
        val first = getOrCreateThemeCardColors(
            seed = ThemeSeed.Coral,
            paletteStyle = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2025
        )
        val second = getOrCreateThemeCardColors(
            seed = ThemeSeed.Coral,
            paletteStyle = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2025
        )
        assertSame(first, second, "多次请求相同配置的卡片色应直接命中相同缓存实例")
    }

    @Test
    fun warmUpThemeCardColors_populatesCache() {
        assertEquals(0, getThemeCardCacheSize())
        warmUpThemeCardColors()
        val staticSeedCount = ThemeSeed.entries.count { it != ThemeSeed.Dynamic }
        val expectedMinCount = staticSeedCount * 2 // TonalSpot and Expressive
        assertTrue(
            getThemeCardCacheSize() >= expectedMinCount,
            "预热后缓存项数量应至少包含静态种子数 * 预热样式数"
        )
    }

    @Test
    fun computeThemeCardColors_tricolorHasDistinctTones() {
        val tricolorColors = computeThemeCardColors(
            seed = ThemeSeed.Tricolor,
            paletteStyle = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2025
        )
        assertNotNull(tricolorColors)
        assertNotEquals(tricolorColors.primaryTone36, tricolorColors.secondaryTone80)
    }
}
