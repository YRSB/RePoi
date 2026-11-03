package com.nobody.repoi.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarColors
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
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
import com.nobody.repoi.ui.theme.ExpressiveElevation
import com.nobody.repoi.ui.theme.ExpressiveTokens

/**
 * Material 3 Expressive 悬浮操作栏（Floating Toolbar）适配层。
 *
 * 本组件不再自绘容器，直接委托官方 `HorizontalFloatingToolbar`：
 * - 容器形状与展开态阴影使用设计系统令牌，容器色固定为 `surfaceContainerHigh`，
 *   与官方 `standardFloatingToolbarColors()` 的高对比画报语义一致；
 * - 内容内边距收敛为 8dp / 6dp，避免多层内边距叠加导致窄屏裁切；
 * - 内容超出可用宽度时可横向滚动，官方容器 `clip = true` 不再直接裁掉末尾操作；
 * - 展开态固定为 `expanded = true`；需要随滚动隐藏时由调用方传入官方 `scrollBehavior`。
 *
 * 说明：官方 content 槽本身是 `RowScope`，本层额外包一层 8dp 等距 Row，
 * 用于保留改造前悬浮栏内各操作项之间的固定间距。
 */
@Composable
fun ExpressiveFloatingToolbar(
    modifier: Modifier = Modifier,
    colors: FloatingToolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors(
        toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ),
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    content: @Composable RowScope.() -> Unit
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
        colors = colors,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        scrollBehavior = scrollBehavior,
        shape = ExpressiveTokens.PillShape,
        expandedShadowElevation = ExpressiveElevation.Level3,
        collapsedShadowElevation = ExpressiveElevation.Level3
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
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
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                label()
            }
        } else {
            TextButton(
                onClick = onClick,
                modifier = modifier,
                shape = ExpressiveTokens.PillShape,
                colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
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
