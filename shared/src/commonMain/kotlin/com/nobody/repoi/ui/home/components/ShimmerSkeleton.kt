package com.nobody.repoi.ui.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import com.nobody.repoi.ui.theme.LocalIsDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.util.AppLogger

/**
 * 屏级共享的微光时钟：由骨架容器顶层提供，容器内所有占位共用同一个无限过渡，
 * 避免每个占位 Box 各起一个过渡导致的过渡实例爆炸。
 * 下发的 [State] 对象身份稳定，组合期只读身份不订阅，逐帧取值推迟到绘制期，
 * 因此过渡跳动不会触发任何重组。
 * 未被 [ShimmerHost] 包裹的调用点回退为静态占位并记一条日志便于补包。
 */
private val LocalShimmerShift = compositionLocalOf<State<Float>?> { null }

/**
 * 骨架容器顶层：在容器内只启动一个无限过渡并下发共享时钟。
 * 颜色与扫动参数与旧实现保持一致（视觉一致）。
 */
@Composable
fun ShimmerHost(
    content: @Composable () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shiftState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerShift"
    )

    CompositionLocalProvider(LocalShimmerShift provides shiftState) {
        content()
    }
}

/**
 * 微光占位修饰符：有共享时钟时在绘制期取值扫动，不再使用已废弃的
 * Modifier.composed，也不再为每个 Box 创建无限过渡或触发重组。
 */
@Composable
fun Modifier.shimmerBackground(): Modifier {
    val isDark = LocalIsDarkTheme.current
    val baseColor = if (isDark) Color(0xFF232634).copy(alpha = 0.45f) else Color(0xFFDFE3EB).copy(alpha = 0.50f)
    val highlightColor = if (isDark) Color(0xFF383C52).copy(alpha = 0.65f) else Color.White.copy(alpha = 0.85f)
    val shimmerColors = remember(baseColor, highlightColor) {
        listOf(baseColor, highlightColor, baseColor)
    }
    val shiftState = LocalShimmerShift.current
    if (shiftState == null) {
        // 兜底静态占位：正常情况下所有骨架容器均已包裹，此 Host，到此说明有遗漏
        AppLogger.w("骨架屏", "存在未被 ShimmerHost 包裹的微光占位已降级为静态")
        return this.drawWithCache {
            val brush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
            onDrawBehind { drawRect(brush) }
        }
    }
    // 共享时钟逐帧取值发生在绘制期，不触发重组
    return this.drawWithCache {
        onDrawBehind {
            val shift = shiftState.value
            val brush = Brush.linearGradient(
                colors = shimmerColors,
                start = Offset(shift - 300f, shift - 300f),
                end = Offset(shift, shift)
            )
            drawRect(brush)
        }
    }
}

@Composable
fun ActionCardSkeleton(modifier: Modifier = Modifier) {
    // 本卡片为纯占位无自身过渡，由上层骨架容器统一提供共享微光时钟
    ElevatedCard(
        shape = ExpressiveTokens.CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = ExpressiveTokens.ElevationDefault
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // ── 【左侧栏骨架】：海报封面 (1:1 默认占位) ──
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .aspectRatio(1.0f)
                    .clip(ExpressiveTokens.SmallCardShape)
                    .shimmerBackground()
            )

            Spacer(modifier = Modifier.width(12.dp))

            // ── 【右侧栏骨架】：用户行 + 分隔线 + 附图 + 评论 + 条目标题 ──
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 用户行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .shimmerBackground()
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(75.dp)
                                        .height(13.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .shimmerBackground()
                                )
                                Box(
                                    modifier = Modifier
                                        .width(26.dp)
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .shimmerBackground()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(110.dp)
                                    .height(9.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .shimmerBackground()
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .shimmerBackground()
                    )
                }

                // 细分隔线
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.8.dp)
                        .clip(RoundedCornerShape(0.4.dp))
                        .shimmerBackground()
                )

                // 附图骨架（2张小缩略图）
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .shimmerBackground()
                    )
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .shimmerBackground()
                    )
                }

                // 评论文本骨架
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerBackground()
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerBackground()
                )

                // 关联条目与统计数据骨架
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(85.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .shimmerBackground()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(11.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .shimmerBackground()
                        )
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .height(11.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .shimmerBackground()
                        )
                    }
                }
            }
        }
    }
}
}


