package com.nobody.repoi.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.nobody.repoi.data.local.ThemeSeed
import com.nobody.repoi.data.local.toColors
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ThemeMotionTest {

    private val spec = ColorSpec.SpecVersion.SPEC_2021

    @Composable
    private fun targetScheme(darkTheme: Boolean, style: PaletteStyle): ColorScheme {
        val (seedPrimary, seedSecondary, seedTertiary) = ThemeSeed.Lotus.toColors()
        return resolvePoiColorScheme(
            darkTheme = darkTheme,
            dynamicColor = false,
            seedPrimary = seedPrimary,
            seedSecondary = seedSecondary,
            seedTertiary = seedTertiary,
            wallpaperPrimary = null,
            amoled = false,
            paletteStyle = style,
            specVersion = spec,
            staticFallback = lightColorScheme(),
        )
    }

    private fun ComposeUiTest.setThemeUnderTest(
        style: State<PaletteStyle>,
        onTarget: (Triple<Color, Color, Color>) -> Unit,
        onAnimated: (Triple<Color, Color, Color>) -> Unit,
        dark: State<Boolean> = mutableStateOf(false),
    ) {
        setContent {
            val expected = targetScheme(darkTheme = dark.value, style = style.value)
            SideEffect { onTarget(expected.rgb()) }
            PoiShelfTheme(
                darkTheme = dark.value,
                dynamicColor = false,
                themeSeed = ThemeSeed.Lotus,
                paletteStyle = style.value,
                specVersion = spec,
                systemSchemeProvider = { error("静态种子分支不得调用平台取色") },
            ) {
                val scheme = MaterialTheme.colorScheme
                SideEffect { onAnimated(scheme.rgb()) }
            }
        }
    }

    private fun ColorScheme.rgb() = Triple(primary, secondary, tertiary)

    private fun Color.channels(): List<Int> {
        val argb = toArgb()
        return listOf(
            (argb ushr 24) and 0xFF,
            (argb ushr 16) and 0xFF,
            (argb ushr 8) and 0xFF,
            argb and 0xFF,
        )
    }

    private fun isClose(
        expected: Triple<Color, Color, Color>?,
        actual: Triple<Color, Color, Color>?,
    ): Boolean {
        val expectedColors = expected ?: return false
        val actualColors = actual ?: return false
        return listOf(expectedColors.first, expectedColors.second, expectedColors.third)
            .zip(listOf(actualColors.first, actualColors.second, actualColors.third))
            .all { (expectedColor, actualColor) ->
                expectedColor.channels()
                    .zip(actualColor.channels())
                    .maxOf { (expectedChannel, actualChannel) -> abs(expectedChannel - actualChannel) } <= 2
            }
    }

    private fun assertClose(
        expected: Triple<Color, Color, Color>?,
        actual: Triple<Color, Color, Color>?,
        message: String,
    ) {
        assertTrue(isClose(expected, actual), "$message（期望 $expected，实际 $actual）")
    }

    @Test
    fun coldStart_themeConfigArrival_snapsToTargetWithoutInterpolation() = runComposeUiTest {
        val style = mutableStateOf<PaletteStyle>(PaletteStyle.TonalSpot)
        var animated: Triple<Color, Color, Color>? = null
        var target: Triple<Color, Color, Color>? = null
        setThemeUnderTest(
            style = style,
            onTarget = { target = it },
            onAnimated = { animated = it },
        )
        waitForIdle()
        val launch = assertNotNull(target, "未捕获启动基准配色")

        mainClock.autoAdvance = false
        style.value = PaletteStyle.Vibrant
        val sampled = mutableListOf<Triple<Color, Color, Color>>()
        repeat(3) {
            mainClock.advanceTimeByFrame()
            animated?.let(sampled::add)
        }

        val arrived = assertNotNull(target, "未捕获持久化配色")
        assertNotEquals(launch, arrived, "持久化配色必须不同于启动基准，否则本用例无效")
        assertTrue(sampled.isNotEmpty(), "未捕获落定过程中的任何帧")
        sampled.forEachIndexed { index, palette ->
            assertTrue(
                isClose(launch, palette) || isClose(arrived, palette),
                "冷启动落定的第 ${index + 1} 帧出现了中间插值（$palette），应为瞬间落定（Snap）",
            )
        }

        val settledAt = sampled.indexOfFirst { isClose(arrived, it) }
        assertTrue(
            settledAt in 0..1,
            "冷启动配色必须在一个步进帧内收敛到持久化配置（实际第 ${settledAt + 1} 帧），采样 $sampled",
        )

        mainClock.autoAdvance = true
    }

    @Test
    fun styleChange_animatesThenSettlesOnTarget() = runComposeUiTest {
        val style = mutableStateOf<PaletteStyle>(PaletteStyle.TonalSpot)
        var animated: Triple<Color, Color, Color>? = null
        var target: Triple<Color, Color, Color>? = null
        setThemeUnderTest(
            style = style,
            onTarget = { target = it },
            onAnimated = { animated = it },
        )
        waitForIdle()

        val initial = assertNotNull(target, "未捕获初始目标配色")
        assertEquals(initial, animated, "首帧应直接落在目标配色上，不做冷启动淡入")

        style.value = PaletteStyle.Neutral
        waitForIdle()

        mainClock.autoAdvance = false
        val settled = assertNotNull(animated, "未捕获落定配色")
        style.value = PaletteStyle.Vibrant
        mainClock.advanceTimeByFrame()

        val changed = assertNotNull(target, "未捕获新目标配色")
        assertNotEquals(settled, changed, "换风格的目标配色必须不同，否则本用例无效")
        assertNotEquals(changed, animated, "落定后的交互切换不应首帧即到目标，否则没有过渡")

        mainClock.advanceTimeBy(5_000)
        assertClose(changed, animated, "动画必须收敛到目标配色，否则界面会永久显示错色")

        mainClock.autoAdvance = true
    }

    @Test
    fun darkModeSwitch_appliesImmediately() = runComposeUiTest {
        val dark = mutableStateOf(false)
        val style = mutableStateOf<PaletteStyle>(PaletteStyle.TonalSpot)
        var animated: Triple<Color, Color, Color>? = null
        var target: Triple<Color, Color, Color>? = null
        setThemeUnderTest(
            style = style,
            dark = dark,
            onTarget = { target = it },
            onAnimated = { animated = it },
        )
        waitForIdle()

        val light = assertNotNull(target, "未捕获浅色目标配色")
        assertEquals(light, animated)

        style.value = PaletteStyle.Vibrant
        waitForIdle()

        dark.value = true
        waitForIdle()

        val darkTarget = assertNotNull(target, "未捕获深色目标配色")
        assertNotEquals(light, darkTarget, "深浅模式的目标配色必须不同，否则本用例无效")
        assertClose(darkTarget, animated, "深浅模式切换不得补间，必须立即生效")
    }
}
