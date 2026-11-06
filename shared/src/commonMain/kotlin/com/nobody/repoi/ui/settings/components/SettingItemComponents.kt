package com.nobody.repoi.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 设置页的分组容器。
 *
 * 遵循 Material 3 Expressive 规范：
 * 1. 外部裁剪为 [MaterialTheme.shapes.large]（16.dp 圆角）；
 * 2. 组内子项之间使用 [ListItemDefaults.SegmentedGap] 分段微间隙；
 * 3. 动态测量子项高度，在展开/收起动画时平滑收缩间距，避免产生突兀白边。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingItemGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
    ) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }

        header?.invoke()

        androidx.compose.ui.layout.Layout(
            content = content,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints) }
            val spacing = ListItemDefaults.SegmentedGap.roundToPx()
            val baseItemHeight = 48.dp.toPx()

            var yPosition = 0
            val positions = mutableListOf<Int>()
            var hasVisibleBefore = false

            for (i in placeables.indices) {
                val placeable = placeables[i]
                val scale = (placeable.height / baseItemHeight).coerceIn(0f, 1f)

                if (hasVisibleBefore && scale > 0f) {
                    yPosition += (spacing * scale).toInt()
                }

                positions.add(yPosition)
                yPosition += placeable.height

                if (scale > 0f) {
                    hasVisibleBefore = true
                }
            }

            layout(constraints.maxWidth, yPosition) {
                for (i in placeables.indices) {
                    placeables[i].placeRelative(0, positions[i])
                }
            }
        }

        footer?.let {
            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    it()
                }
            }
        }
    }
}

/**
 * 标准设置条目。
 *
 * 采用 Material 3 Expressive [SegmentedListItem]，默认容器底色为纯白高光 [MaterialTheme.colorScheme.surface]，
 * 在背景画布为 surfaceContainer 时呈现清晰通透的卡片悬浮质感。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    titleColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val leading: (@Composable () -> Unit)? = leadingContent ?: icon?.let {
        {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (enabled) iconTint else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }

    val label: @Composable () -> Unit = {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (enabled) titleColor else titleColor.copy(alpha = 0.38f)
        )
    }

    val supporting: (@Composable () -> Unit)? = subtitle?.let {
        {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }

    val trailing: (@Composable () -> Unit)? = trailingContent?.let {
        {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                content = it
            )
        }
    }

    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shapes = ListItemDefaults.shapes(),
            colors = ListItemDefaults.segmentedColors(containerColor = containerColor),
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            content = label
        )
    } else {
        // 无点击动作时排版为同等尺寸规范的一行，不触发多余的水波纹
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(containerColor)
                .heightIn(min = 56.dp)
                .padding(ListItemDefaults.ContentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            leading?.let {
                Box(modifier = Modifier.padding(end = 16.dp)) {
                    it()
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                label()
                supporting?.invoke()
            }
            trailing?.let {
                Box(modifier = Modifier.padding(start = 16.dp)) {
                    it()
                }
            }
        }
    }
}

/**
 * 带 Switch 开关的设置条目。
 *
 * 开关带有开启（对勾）和关闭（叉号）图标状态，整行点击与点击 Switch 等价。
 */
@Composable
fun SettingSwitchItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    SettingItem(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = modifier,
        enabled = enabled,
        containerColor = containerColor,
        iconTint = iconTint,
        onClick = { onCheckedChange(!checked) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                thumbContent = {
                    if (checked) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    )
}

/**
 * [SettingButtonGroup] 的选项定义。
 */
@Immutable
data class ButtonGroupOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null
)

/**
 * Material 3 Expressive 连结式单选按钮组（ButtonGroup）。
 *
 * 采用连结型 [ToggleButton] 与 [ButtonGroupDefaults]，替换传统的 [androidx.compose.material3.SingleChoiceSegmentedButtonRow]：
 * 1. 选项平分宽度并形成平滑连结圆角，所有选项一目了然；
 * 2. 选中项填充主题色 Container 并更圆润饱满，支持可选 Leading Icon 图标指引；
 * 3. 完美兼容 Material 3 Expressive 视觉与触控规范。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SettingButtonGroup(
    options: List<ButtonGroupOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, option ->
            val checked = option.value == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (!checked) onSelect(option.value) },
                enabled = enabled,
                colors = if (checked) {
                    ToggleButtonDefaults.toggleButtonColors(
                        disabledContainerColor = colorScheme.onSurface.copy(alpha = 0.32f),
                        disabledContentColor = colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                } else {
                    ToggleButtonDefaults.toggleButtonColors()
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = checked
                    },
                shapes = when {
                    options.size == 1 -> ToggleButtonDefaults.shapesFor(40.dp)
                    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
            ) {
                option.icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
