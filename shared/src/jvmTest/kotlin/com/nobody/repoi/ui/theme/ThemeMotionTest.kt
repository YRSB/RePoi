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

/**
 * 颜色过渡动画的回归测试（对应 [animateAsState]，ADR-0009 + ADR-0012）。
 *
 * 断言的是正确性而不是观感：
 * 1. 首帧不做冷启动淡入，直接落在目标配色上；
 * 2. 冷启动色彩就绪（持久化主题配置到达）必须瞬间落定，不经过任何中间插值；
 * 3. 首态落定之后，用户在外观设置里切换风格仍触发弹簧过渡：首帧还没到目标；
 * 4. 动画最终必须收敛到目标配色，若停在中间值整个应用会永久显示错色；
 * 5. 深浅模式切换不补间，且落定之后依旧单帧瞬切。
 *
 * 需要观测「过渡中」的用例必须先走一次跃迁让 [ColdStartSettleGate] 落定，
 * 否则本次跃迁会被当作启动期修复而瞬间落定（这正是 ADR-0012 的设计）。
 *
 * 收敛断言用逐通道容差比较而非 [Color] 精确相等——`animateColorAsState`
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

    /**
     * 组合一棵只观察配色的最小主题树：分别抓取「目标配色」与 MaterialTheme 实际呈现的配色。
     *
     * 三个用例共用同一条装载路径，避免重复八参 [PoiShelfTheme] 调用；
     * 平台取色在静态种子分支不应被触碰，故注入直接报错的 provider。
     */
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

    /** 只取主 / 辅 / 第三色三个锚点，足以区分风格与种子色变化，也便于失败信息阅读。 */
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

    /** 逐通道容差比较，供「某一帧究竟是哪一版配色」的判定复用。 */
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

    /**
     * 冷启动色彩就绪：默认配色（读盘未完成时的兜底）到持久化配置的跃迁必须瞬间落定。
     *
     * 判据是「落定过程从未渲染中间插值」而不是「恰好第几帧到位」：动画值总在目标变更的
     * 下一帧才被重组读到，故逐帧采样，要求每一帧要么是启动色、要么是目标色，
     * 且至多一个步进帧就到达目标色。
     * 修复前这里会触发 48 角色弹簧补间，采样到的中段颜色会明显偏离两端。
     */
    @Test
    fun coldStart_themeConfigArrival_snapsToTargetWithoutInterpolation() = runComposeUiTest {
        val style = mutableStateOf(PaletteStyle.TonalSpot)
        var animated: Triple<Color, Color, Color>? = null
        var target: Triple<Color, Color, Color>? = null
        setThemeUnderTest(
            style = style,
            onTarget = { target = it },
            onAnimated = { animated = it },
        )
        waitForIdle()
        val launch = assertNotNull(target, "未捕获启动基准配色")

        // 持久化配置到达：停表逐帧采样，收集落定过程中的每一帧
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
        // 动画目标变更后的首个步进帧才被重新读到，故「瞬间落定」= 至多一步跳到目标色
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

        // 先完成一次冷启动首态落定（ADR-0012），否则下面的跃迁会被门禁瞬间落定
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
        val style = mutableStateOf(PaletteStyle.TonalSpot)
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

        // 先完成一次冷启动首态落定（ADR-0012），确保下面的瞬切由深浅模式重建保证，
        // 而不是被冷启动门禁顺带掩盖
        style.value = PaletteStyle.Vibrant
        waitForIdle()

        dark.value = true
        waitForIdle()

        val darkTarget = assertNotNull(target, "未捕获深色目标配色")
        assertNotEquals(light, darkTarget, "深浅模式的目标配色必须不同，否则本用例无效")
        assertClose(darkTarget, animated, "深浅模式切换不得补间，必须立即生效")
    }
}
