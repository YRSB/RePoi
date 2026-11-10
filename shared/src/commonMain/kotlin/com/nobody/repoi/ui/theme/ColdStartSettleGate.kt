package com.nobody.repoi.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * 冷启动首态落定门禁（ADR-0012 的 Compose 侧保险）。
 *
 * 只作用于主题动画节点生命周期内的**首次**配色跃迁，不依赖外部标志位与平台能力：
 * 1. 首个组合传入的配色记为「启动基准」，动画节点本就以该配色初始化；
 * 2. 基准之后、就绪窗口内的第一次配色变化即持久化主题配置到达，判定为「首态落定」，
 *    强制瞬间落定；落定后永久锁定，此后每次配色变化都恢复弹性过渡；
 * 3. 就绪窗口内一次配色变化都没有，说明启动配置与兜底默认值一致（用户从未自定义外观），
 *    窗口过期后同样锁定，避免把用户第一次真实切换误吞成落定。
 *
 * 「就绪窗口」是门禁能不看配置读取状态、只看配色变化的必要条件：真实冷启动读盘在数十毫秒内
 * 完成，路由到设置页再改配色至少需要数秒，故 [settlingWindow] 取 2 秒既有充足余量，
 * 又不会把正常交互误判为启动落定。
 *
 * 状态由调用方以 `remember { ColdStartSettleGate() }` 持有，且必须记忆在
 * `key(darkTheme)` 之外：深浅模式切换会重建动画节点，但「本次启动是否已落定」
 * 属于节点生命周期之上的事实，不能随重建复位。深浅模式本身的变化由 [darkTheme] 单独识别，
 * 只重建基准、不消耗落定机会——否则读盘期间的一次系统夜间切换会把真实配置的到来错判成交互切换。
 *
 * 判定挂在组合树节点上而不是全局：Activity 重建（如旋转）会重新武装门禁与新窗口，
 * 此时首个组合拿到的已是持久化配置，仅当用户在一两秒内就改配色时才可能少一次过渡。
 */
internal class ColdStartSettleGate(
    private val settlingWindow: Duration = 2.seconds,
) {
    private val launchedAt = TimeSource.Monotonic.markNow()
    private var launchSignature: List<Color>? = null
    private var launchDarkTheme: Boolean? = null
    private var settled = false

    /** 记录本次组合的配色，返回本次是否仍处于冷启动色彩就绪期（true 表示应瞬间落定）。 */
    fun shouldSnap(scheme: ColorScheme, darkTheme: Boolean): Boolean {
        if (settled) return false

        val signature = scheme.settlingSignature()
        val launch = launchSignature
        if (launch == null || launchDarkTheme != darkTheme) {
            // 首次组合，或深浅模式刚刚翻转（动画节点已按新配色重建）：重记基准，不消耗落定
            launchSignature = signature
            launchDarkTheme = darkTheme
            return true
        }
        if (launch != signature) {
            settled = true
            // 窗口内的第一次配色变化只可能是读盘结果；窗口外的则是用户交互，交给弹簧
            return launchedAt.elapsedNow() <= settlingWindow
        }
        if (launchedAt.elapsedNow() > settlingWindow) {
            settled = true
            return false
        }
        return true
    }
}

/**
 * 用于判定「配色是否真的变了」的锚点角色。
 *
 * [ColorScheme] 是 `@Immutable class` 而没有结构化 `equals`，实例相等即引用相等；
 * 直接比实例会把「参数未变的重组」误判成配置到达，从而提前解除门禁。
 * 这里取各维度设置都会触及的角色：风格 / 标准 / 种子色改主辅色，AMOLED 改表面，
 * 深浅模式改明暗两极，故任一维度的真实变更都能被识别。
 */
private fun ColorScheme.settlingSignature(): List<Color> = listOf(
    primary,
    secondary,
    tertiary,
    background,
    surface,
    surfaceContainer,
    onSurface,
    outline,
    error,
    surfaceVariant,
)
