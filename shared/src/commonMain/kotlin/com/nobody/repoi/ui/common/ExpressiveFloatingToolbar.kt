package com.nobody.repoi.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarColors
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobody.repoi.ui.theme.ExpressiveTokens

/**
 * Material 3 Expressive 悬浮操作栏（Floating Toolbar）适配层。
 *
 * 本组件不再自绘容器，直接委托官方 `HorizontalFloatingToolbar`：
 * - 容器形状、容器色、内容内边距、展开/收起阴影与动效全部取自官方默认值；
 * - 容器配色默认使用官方 `standardFloatingToolbarColors()`（surfaceContainer 色阶），
 *   需要高对比画报背景时可由调用方传入 `vibrantFloatingToolbarColors()`；
 * - 展开态固定为 `expanded = true`，滚动收起行为交给调用方按需传入官方 scrollBehavior。
 *
 * 说明：官方 content 槽本身是 `RowScope`，本层额外包一层 8dp 等距 Row，
 * 用于保留改造前悬浮栏内各操作项之间的固定间距。
 */
@Composable
fun ExpressiveFloatingToolbar(
    modifier: Modifier = Modifier,
    colors: FloatingToolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    content: @Composable RowScope.() -> Unit
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = colors
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

/**
 * 悬浮操作栏内的操作项。
 *
 * 基于官方按钮原语实现，不再自绘 Surface 药丸：
 * 1. 仅图标且非主操作 → 官方 `IconButton`；
 * 2. 仅图标且主操作 → 官方 `FilledTonalIconButton`；
 * 3. 图标 + 文字且非主操作 → 官方 `TextButton`；
 * 4. 图标 + 文字且主操作 → 官方 `FilledTonalButton`。
 *
 * 规范对齐：
 * - 形状沿用设计系统 `ExpressiveTokens.PillShape`（官方按钮默认外形同为全圆角）；
 * - 图文并存时图标 `contentDescription` 设为 null，避免 TalkBack 重复朗读；
 * - 文字接入 `MaterialTheme.typography.labelLarge`，支持全局无障碍字阶缩放；
 * - 图标尺寸按官方按钮规范：图文按钮 18dp，纯图标按钮 24dp。
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

    if (hasText) {
        val label: @Composable () -> Unit = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = text!!,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
            }
        }
        if (isProminent) {
            FilledTonalButton(
                onClick = onClick,
                modifier = modifier,
                shape = ExpressiveTokens.PillShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                label()
            }
        } else {
            TextButton(
                onClick = onClick,
                modifier = modifier,
                shape = ExpressiveTokens.PillShape,
                colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                label()
            }
        }
    } else {
        val iconContent: @Composable () -> Unit = {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        if (isProminent) {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = modifier,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                )
            ) {
                iconContent()
            }
        } else {
            IconButton(
                onClick = onClick,
                modifier = modifier,
                colors = IconButtonDefaults.iconButtonColors(contentColor = contentColor)
            ) {
                iconContent()
            }
        }
    }
}
