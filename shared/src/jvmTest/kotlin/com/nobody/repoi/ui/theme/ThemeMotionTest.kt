package com.nobody.repoi.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

/**
 * 颜色过渡动画的回归测试（对应 [animateAsState]）。
 *
 * 断言的是正确性而不是观感：
 * 1. 首帧不做冷启动淡入，直接落在目标配色上；
 * 2. 换风格后动画确实在跑（首帧还没到目标）；
 * 3. 动画最终必须收敛到目标配色，若停在中间值整个应用会永久显示错色；
 * 4. 深浅模式切换不补间（整屏明暗反转不需要逐角色插值）。
 *
 * 前半段放开时钟自动推进，等初始组合稳定；后半段切到手动推进，
 * 避免 `waitForIdle` 直接把动画跳完，从而能在同一段测试里同时观察
 * 「过渡中」与「已收敛」两个状态。
 *
 * 注：收敛断言用逐通道容差比较而非 [Color] 精确相等——`animateColorAsState`
 * 内部把颜色转成浮点向量再转回，末位可能差 1~2 个 8 位步进。
 */
@OptIn(ExperimentalTestApi::class)
class ThemeMotionTest {

    private val spec = ColorSpec.SpecVersion.SPEC_2021

    /** 不带动画的目标配色，与根主题走同一条决策链，避免测试里复述取色算法。 */
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

    private fun ColorScheme.rgb() = Triple(primary, secondary, tertiary)

    /** 拆成 A/R/G/B 四通道，便于做容差比较。 */
    private fun Color.channels(): List<Int> {
        val argb = toArgb()
        return listOf(
            (argb ushr 24) and 0xFF,
            (argb ushr 16) and 0xFF,
            (argb ushr 8) and 0xFF,
            argb and 0xFF,
        )
    }

    private fun assertClose(
        expected: Triple<Color, Color, Color>?,
        actual: Triple<Color, Color, Color>?,
        message: String,
    ) {
        val expectedColors = assertNotNull(expected, "$message：期望值为空")
        val actualColors = assertNotNull(actual, "$message：实际值为空")
        listOf(expectedColors.first, expectedColors.second, expectedColors.third)
            .zip(listOf(actualColors.first, actualColors.second, actualColors.third))
            .forEach { (expectedColor, actualColor) ->
                val diff = expectedColor.channels()
                    .zip(actualColor.channels())
                    .maxOf { (expectedChannel, actualChannel) -> abs(expectedChannel - actualChannel) }
                assertTrue(diff <= 2, "$message（期望 $expectedColor，实际 $actualColor）")
            }
    }

    @Test
    fun styleChange_animatesThenSettlesOnTarget() = runComposeUiTest {
        val style = mutableStateOf<PaletteStyle>(PaletteStyle.TonalSpot)
        var animated: Triple<Color, Color, Color>? = null
        var target: Triple<Color, Color, Color>? = null

        setContent {
            val expected = targetScheme(darkTheme = false, style = style.value)
            SideEffect { target = expected.rgb() }
            PoiShelfTheme(
                darkTheme = false,
                dynamicColor = false,
                themeSeed = ThemeSeed.Lotus,
                paletteStyle = style.value,
                specVersion = spec,
                systemSchemeProvider = { error("静态种子分支不得调用平台取色") },
            ) {
                val scheme = MaterialTheme.colorScheme
                SideEffect { animated = scheme.rgb() }
            }
        }
        waitForIdle()

        val initial = assertNotNull(target, "未捕获初始目标配色")
        assertEquals(initial, animated, "首帧应直接落在目标配色上，不做冷启动淡入")

        mainClock.autoAdvance = false
        style.value = PaletteStyle.Vibrant
        mainClock.advanceTimeByFrame()

        val changed = assertNotNull(target, "未捕获新目标配色")
        assertNotEquals(initial, changed, "换风格的目标配色必须不同，否则本用例无效")
        assertNotEquals(changed, animated, "换风格后的首帧不应已到目标，否则没有过渡")

        mainClock.advanceTimeBy(5_000)
        assertClose(changed, animated, "动画必须收敛到目标配色，否则界面会永久显示错色")

        mainClock.autoAdvance = true
    }

    @Test
    fun darkModeSwitch_appliesImmediately() = runComposeUiTest {
        val dark = mutableStateOf(false)
        var animated: Triple<Color, Color, Color>? = null
        var target: Triple<Color, Color, Color>? = null

        setContent {
            val expected = targetScheme(darkTheme = dark.value, style = PaletteStyle.TonalSpot)
            SideEffect { target = expected.rgb() }
            PoiShelfTheme(
                darkTheme = dark.value,
                dynamicColor = false,
                themeSeed = ThemeSeed.Lotus,
                paletteStyle = PaletteStyle.TonalSpot,
                specVersion = spec,
                systemSchemeProvider = { error("静态种子分支不得调用平台取色") },
            ) {
                val scheme = MaterialTheme.colorScheme
                SideEffect { animated = scheme.rgb() }
            }
        }
        waitForIdle()

        val light = assertNotNull(target, "未捕获浅色目标配色")
        assertEquals(light, animated)

        dark.value = true
        waitForIdle()

        val darkTarget = assertNotNull(target, "未捕获深色目标配色")
        assertNotEquals(light, darkTarget, "深浅模式的目标配色必须不同，否则本用例无效")
        assertClose(darkTarget, animated, "深浅模式切换不得补间，必须立即生效")
    }
}
