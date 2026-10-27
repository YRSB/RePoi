package com.nobody.repoi.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobody.repoi.ui.theme.ExpressiveElevation
import com.nobody.repoi.ui.theme.ExpressiveTokens

/**
 * Material 3 Expressive 悬浮操作栏容器 (Floating Toolbar Capsule)。
 *
 * 遵循 M3 Expressive Floating Toolbar 规范：
 * - 容器形状采用 Full Shape (PillShape)
 * - 表面色阶采用 surfaceContainerHigh，并辅以半透明微描边增强画报背景下的边缘对比
 * - 标高与阴影采用 ExpressiveElevation.Level3 (6.dp)
 * - 高度对齐标准 56dp 胶囊交互尺寸
 * - 布局采用 8dp 基础栅格体系
 */
@Composable
fun ExpressiveFloatingToolbar(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    elevation: Dp = ExpressiveElevation.Level3,
    border: BorderStroke? = BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    ),
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier.heightIn(min = 56.dp),
        shape = ExpressiveTokens.PillShape,
        color = containerColor,
        border = border,
        tonalElevation = elevation,
        shadowElevation = elevation
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/**
 * Material 3 Expressive 悬浮操作栏子项交互药丸按钮。
 *
 * 核心规范对齐：
 * 1. 原生 Surface(onClick) 确保水波纹严格在 PillShape 轮廓内触发；
 * 2. 保证 minHeight >= 40dp 与充足的水平内边距，达到无障碍安全热区；
 * 3. 图标对齐 M3 紧凑级标准 20dp；
 * 4. 图文并存时图标 contentDescription 设为 null，消除 TalkBack 重复朗读；
 * 5. 文字排版接入 MaterialTheme.typography.labelLarge，支持全局无障碍字阶缩放；
 * 6. isProminent 主操作高亮采用 primaryContainer/onPrimaryContainer，呈现符合 MD3 语义的 Filled Tonal 样式。
 */
@Composable
fun ExpressiveToolbarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    isProminent: Boolean = false,
    containerColor: Color = if (isProminent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
    contentColor: Color = if (isProminent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    iconTint: Color = if (isProminent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
) {
    val hasText = !text.isNullOrBlank()
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        shape = ExpressiveTokens.PillShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (hasText) 12.dp else 10.dp,
                vertical = 8.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (hasText) null else contentDescription,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (hasText) {
                Text(
                    text = text!!,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = contentColor
                )
            }
        }
    }
}



