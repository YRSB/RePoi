package com.nobody.repoi.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.nobody.repoi.data.local.ThemeSeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * issue #82 回归测试：**动态主题下「色彩风格 / 色彩标准」必须生效**。
 *
 * 背景：修复前 [PoiShelfTheme] 的动态分支直接采用平台给出的
 * `dynamicLightColorScheme` / `dynamicDarkColorScheme`，`paletteStyle` 与
 * `specVersion` 只在静态种子分支被读取，导致用户在动态主题下切换这两项毫无反应。
 *
 * 为什么放在 `jvmTest`：平台取色由 `expect/actual` 承担，JVM 恒为 `null`，
 * 无法在 commonTest 覆盖。修复同时引入了 `systemSchemeProvider` 注入缝，
 * 使这条真实决策链路第一次变得可测——这正是诊断中标记的"可测性缺口"。
 *
 * 断言：
 * 1. **灵敏度**：8 种 (风格 × 标准) 组合必须产出不止一种配色；
 * 2. **无回归**：默认组合（TonalSpot + SPEC_2021）的主色必须等于平台壁纸主色，
 *    也就是说没动过设置的用户，主色不会跳变。
 *
 * 修复前的等价实测见 `shared/build/diagnose-82/Probe82.java` 步骤 [1]：
 * 8 种组合的 distinct outputs = 1（完全忽略设置）。
 */
@OptIn(ExperimentalTestApi::class)
class ThemeDynamicRegressionTest {

    private val wallpaperArgb = 0xFF2E6ADB

    private val styles = listOf(
        PaletteStyle.TonalSpot,
        PaletteStyle.Vibrant,
        PaletteStyle.Neutral,
        PaletteStyle.Monochrome,
    )

    private val specs = listOf(
        ColorSpec.SpecVersion.SPEC_2021,
        ColorSpec.SpecVersion.SPEC_2025,
    )

    /**
     * 平台侧"Monet 取色"的替身：material-kolor 以壁纸主色 + TonalSpot + SPEC_2021 推导，
     * 与 Android 12+ 的 `dynamicLightColorScheme(context)` 同族。
     */
    @Composable
    private fun fakePlatformScheme(darkTheme: Boolean = false): ColorScheme = seedColorScheme(
        seedColor = Color(wallpaperArgb),
        darkTheme = darkTheme,
        amoled = false,
        style = PaletteStyle.TonalSpot,
        specVersion = ColorSpec.SpecVersion.SPEC_2021,
    )

    @Test
    fun dynamicTheme_honoursPaletteStyleAndSpecVersion() = runComposeUiTest {
        val combos = styles.flatMap { style -> specs.map { spec -> style to spec } }
        val observed = linkedMapOf<Int, Triple<Color, Color, Color>>()
        val index = mutableIntStateOf(0)
        var platformPrimary: Color? = null

        setContent {
            val (style, spec) = combos[index.intValue]
            val platformScheme = fakePlatformScheme(darkTheme = false)
            SideEffect { platformPrimary = platformScheme.primary }
            PoiShelfTheme(
                darkTheme = false,
                dynamicColor = true,
                themeSeed = ThemeSeed.Dynamic,
                paletteStyle = style,
                specVersion = spec,
                systemSchemeProvider = { platformScheme },
            ) {
                val scheme = MaterialTheme.colorScheme
                SideEffect { observed[index.intValue] = Triple(scheme.primary, scheme.secondary, scheme.tertiary) }
            }
        }

        combos.indices.forEach { i ->
            index.intValue = i
            waitForIdle()
        }

        assertEquals(combos.size, observed.size, "每个组合都应被实际组合一次")

        val distinct = observed.values.distinct()
        assertTrue(
            distinct.size > 1,
            "动态主题下切换色彩风格/标准必须改变配色，实际 ${combos.size} 种组合只有 ${distinct.size} 种输出：$observed",
        )

        // 无回归：默认组合的主色必须等于平台壁纸主色（tone 40 由种子直接决定）
        val defaultIndex = combos.indexOf(PaletteStyle.TonalSpot to ColorSpec.SpecVersion.SPEC_2021)
        val expectedPrimary = assertNotNull(platformPrimary, "未捕获平台配色")
        assertEquals(
            expectedPrimary,
            observed.getValue(defaultIndex).first,
            "默认组合必须保留平台壁纸主色，否则修复会改变未自定义用户的外观",
        )
    }

    @Test
    fun staticSeedTheme_stillHonoursPaletteStyle() = runComposeUiTest {
        val observed = linkedMapOf<PaletteStyle, Triple<Color, Color, Color>>()
        val index = mutableIntStateOf(0)
        val candidates = listOf(PaletteStyle.TonalSpot, PaletteStyle.Vibrant, PaletteStyle.Monochrome)

        setContent {
            PoiShelfTheme(
                darkTheme = false,
                dynamicColor = false,
                themeSeed = ThemeSeed.Lotus,
                paletteStyle = candidates[index.intValue],
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
                // 静态种子分支不应触碰平台取色
                systemSchemeProvider = { error("静态种子分支不得调用平台取色") },
            ) {
                val scheme = MaterialTheme.colorScheme
                SideEffect {
                    observed[candidates[index.intValue]] = Triple(scheme.primary, scheme.secondary, scheme.tertiary)
                }
            }
        }

        candidates.indices.forEach { i ->
            index.intValue = i
            waitForIdle()
        }

        assertEquals(candidates.size, observed.size)
        assertEquals(
            candidates.size,
            observed.values.distinct().size,
            "静态种子下切换色彩风格必须逐一切换配色",
        )
    }
}
