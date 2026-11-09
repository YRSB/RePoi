package com.nobody.repoi.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec

/**
 * 该色彩风格是否实现了 SPEC_2025。
 *
 * material-kolor 6.0.0 只有 TonalSpot / Neutral / Vibrant / Expressive 实现了 2025 规范，
 * 其余 5 种风格在 SPEC_2025 下会**静默回退**到 2021——实测这 5 种的 48 个颜色角色
 * 与 SPEC_2021 逐一相同。若不拦下，用户切「色彩标准」同样会毫无反应，
 * 与本 issue 报告的症状一模一样。判定依据同 KernelSU manager 的 supportsSpec2025。
 */
internal val PaletteStyle.supportsSpec2025: Boolean
    get() = this == PaletteStyle.TonalSpot ||
        this == PaletteStyle.Neutral ||
        this == PaletteStyle.Vibrant ||
        this == PaletteStyle.Expressive

/** 把用户选择的色彩标准收敛到该风格真正支持的版本，避免静默回退。 */
internal fun ColorSpec.SpecVersion.effectiveFor(style: PaletteStyle): ColorSpec.SpecVersion =
    if (this == ColorSpec.SpecVersion.SPEC_2025 && !style.supportsSpec2025) {
        ColorSpec.SpecVersion.SPEC_2021
    } else {
        this
    }

/**
 * 配色来源判定结果。
 *
 * 这是 issue #82 的核心不变式：只要用户开着动态主题且平台给得出壁纸配色，
 * 就必须走 [Dynamic] 分支并**重新推导**，绝不能整份照抄平台配色——照抄会把
 * [PaletteStyle] 与 [ColorSpec.SpecVersion] 变成死设置。
 */
internal sealed interface PoiSchemeSource {
    /** 预置静态种子（含真寻三色）。 */
    data class Seed(val primary: Color, val secondary: Color?, val tertiary: Color?) : PoiSchemeSource

    /** 动态壁纸取色：只取壁纸主色作锚点，其余交给所选风格派生。 */
    data class Dynamic(val anchor: Color) : PoiSchemeSource

    /** 无种子也无壁纸色：落到内置珊瑚红兜底。 */
    data object Fallback : PoiSchemeSource
}

/**
 * 纯判定：决定配色来源。无 Compose 依赖，`commonTest` 可直接锁死（issue #82）。
 *
 * @param wallpaperPrimary 平台壁纸配色的主色；平台不支持或非动态主题时为 `null`
 */
internal fun resolveSchemeSource(
    dynamicColor: Boolean,
    seedPrimary: Color?,
    seedSecondary: Color?,
    seedTertiary: Color?,
    wallpaperPrimary: Color?,
): PoiSchemeSource = when {
    seedPrimary != null -> PoiSchemeSource.Seed(seedPrimary, seedSecondary, seedTertiary)
    dynamicColor && wallpaperPrimary != null -> PoiSchemeSource.Dynamic(wallpaperPrimary)
    else -> PoiSchemeSource.Fallback
}

/**
 * 主题配色的**决策核心**：给定种子与用户配置，产出一份配色。
 *
 * 抽取为 `internal` 函数是为了让 issue #82 这类"设置项失效"缺陷可以被直接测死——
 * [PoiShelfTheme] 依赖平台 `expect/actual` 取色，而这里的决策树不依赖任何平台实现。
 *
 * 三条分支都显式接收 [paletteStyle] 与 [specVersion]：这是修复的核心，
 * 动态主题也不例外（修复前该分支直接照抄平台配色）。
 *
 * @param seedPrimary 静态种子主色；`null` 表示走动态壁纸取色
 * @param seedSecondary 静态种子的第二色（仅真寻三色）
 * @param seedTertiary 静态种子的第三色（仅真寻三色）
 * @param wallpaperPrimary 平台壁纸配色主色；非动态主题或平台不支持时为 `null`
 * @param staticFallback 内置兜底配色（调用方按深浅模式与 AMOLED 预先处理）
 */
@Composable
internal fun resolvePoiColorScheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    seedPrimary: Color?,
    seedSecondary: Color?,
    seedTertiary: Color?,
    wallpaperPrimary: Color?,
    amoled: Boolean,
    paletteStyle: PaletteStyle,
    specVersion: ColorSpec.SpecVersion,
    staticFallback: ColorScheme,
): ColorScheme {
    // 「色彩标准」先按风格收敛：未实现 SPEC_2025 的风格会被 material-kolor 静默回退，
    // 不拦下的话用户切标准会毫无反应（实测 Rainbow/FruitSalad/Monochrome/Fidelity/Content）
    val effectiveSpec = specVersion.effectiveFor(paletteStyle)
    return when (
        val source = resolveSchemeSource(dynamicColor, seedPrimary, seedSecondary, seedTertiary, wallpaperPrimary)
    ) {
        is PoiSchemeSource.Seed -> seedColorScheme(
            seedColor = source.primary,
            darkTheme = darkTheme,
            amoled = amoled,
            secondary = source.secondary,
            tertiary = source.tertiary,
            style = paletteStyle,
            specVersion = effectiveSpec
        )

        is PoiSchemeSource.Dynamic -> seedColorScheme(
            seedColor = source.anchor,
            darkTheme = darkTheme,
            amoled = amoled,
            style = paletteStyle,
            specVersion = effectiveSpec
        )

        PoiSchemeSource.Fallback -> staticFallback
    }
}