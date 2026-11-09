package com.nobody.repoi.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * 主题切换时的颜色过渡动画（对齐 KernelSU manager 的 ColorScheme.animateAsState()）。
 *
 * 用户在外观设置里改「色彩风格 / 色彩标准 / 种子色 / AMOLED」时，[ColorScheme] 会整份换新，
 * 48 个颜色角色瞬间跳变，观感上像闪一下。这里为每个角色套一条 animateColorAsState，
 * 让新旧配色之间弹簧过渡。
 *
 * 两条边界：
 * 1. 首帧不过渡：animateColorAsState 首次组合即取目标值，冷启动不会从默认色淡入；
 * 2. 深浅模式切换不过渡：那是整屏明暗反转，逐角色补间会经过一串中间灰，反而更脏更慢。
 *
 * 正确性不受影响：动画只作用于最终呈现的配色对象，取色决策仍在
 * [resolvePoiColorScheme] 内完成，回归测试读到的目标值不变。
 *
 * 代价：过渡期间每一帧都会产出新的 [ColorScheme]，读取 MaterialTheme.colorScheme
 * 的组件会逐帧重组，且同时挂着 48 个动画节点。若后续低端机反馈掉帧，
 * 应先引入「减弱动画」开关（把 spring 换成 snap），而不是砍掉颜色角色。
 *
 * 注：ColorScheme 的 48 参主构造在 material3 1.12.0-alpha03 里是 private，
 * 公开的整份替换入口只有 [ColorScheme.copy]，故此处统一走 copy。
 *
 * @param darkTheme 当前深浅模式，用于抑制模式切换时的补间
 */
@Composable
internal fun ColorScheme.animateAsState(
    darkTheme: Boolean,
    animationSpec: AnimationSpec<Color> = spring(),
): ColorScheme {
    // 必须先于提前返回调用，保证组合结构稳定
    val previousDarkTheme = rememberPrevious(darkTheme)
    // 深浅模式变化是整屏明暗反转，逐角色补间会经过中间灰，观感反而更差
    if (previousDarkTheme != darkTheme) return this

    @Composable
    fun animateColor(target: Color): Color = animateColorAsState(
        targetValue = target,
        animationSpec = animationSpec,
        label = "poi_theme_color"
    ).value

    return copy(
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

/**
 * 返回上一帧的 [value]，首次组合返回空。
 *
 * 用 remember 只记一个值、在组合体内就地更新，而不是 LaunchedEffect：
 * 后者要多跑一轮重组才能拿到上一次的值，深浅模式切换那一帧会误开动画。
 */
@Composable
private fun <T : Any> rememberPrevious(value: T): T? {
    val holder = remember { PreviousValue<T>() }
    val previous = holder.value
    holder.value = value
    return previous
}

private class PreviousValue<T : Any> {
    var value: T? = null
}
