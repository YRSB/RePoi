package com.nobody.repoi.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * 冷启动首态落定（ADR-0012）专用的瞬时规格：把 48 个颜色角色的跃迁压进单帧。
 */
private val ColdStartSnapSpec: AnimationSpec<Color> = snap()

/**
 * 主题切换时的颜色弹簧过渡（对齐 KernelSU manager 的 ColorScheme.animateAsState()）。
 *
 * 用户在外观设置里改「色彩风格 / 色彩标准 / 种子色 / AMOLED」时，[ColorScheme] 会整份换新，
 * 48 个颜色角色瞬间跳变，观感上像闪一下。这里为每个角色套一条 animateColorAsState，
 * 让新旧配色之间弹簧过渡。
 *
 * 三条边界（缺一不可）：
 * 1. **冷启动色彩就绪不补间**（ADR-0012）：[ColdStartSettleGate] 把节点首个组合拿到的配色
 *    记为「启动基准」，基准之后就绪窗口内的第一次配色变化判定为「持久化主题配置到达」，
 *    强制 [ColdStartSnapSpec] 瞬间落定；落定后永久锁定，此后所有交互切换恢复弹簧过渡。
 *    桌面端没有原生启动遮罩，这一层是它唯一的冷启动保护。
 * 2. **深浅模式切换不补间**：以 [darkTheme] 作为 [key]，模式一变就重建全部动画节点，
 *    新节点以目标色初始化，于是整屏明暗反转立即生效；否则逐角色补间会经过一串灰，既脏又慢。
 * 3. **首帧不淡入**：animateColorAsState 首次组合即以目标值初始化。
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
 * @param darkTheme 当前深浅模式；变化时重建动画节点，不补间
 * @param animationSpec 交互切换时使用的动画规格
 */
@Composable
internal fun ColorScheme.animateAsState(
    darkTheme: Boolean,
    animationSpec: AnimationSpec<Color> = spring(),
): ColorScheme {
    // 门禁必须记忆在 key(darkTheme) 之外：深浅模式切换会重建动画节点，
    // 但「本次启动是否已落定」属于节点生命周期之上的事实，不能随重建复位。
    val settleGate = remember { ColdStartSettleGate() }
    val effectiveSpec = if (settleGate.shouldSnap(this, darkTheme)) ColdStartSnapSpec else animationSpec

    return key(darkTheme) {
        @Composable
        fun animateColor(target: Color): Color = animateColorAsState(
            targetValue = target,
            animationSpec = effectiveSpec,
            label = "poi_theme_color"
        ).value

        copy(
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
}
