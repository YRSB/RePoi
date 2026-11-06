package com.nobody.repoi.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
 * 设置分组的小标题。
 *
 * 遵循 Material 3 Expressive 规范，与 [SettingItemGroup] 的标题排版与边距规范完全对齐：
 * - 字体排版：[MaterialTheme.typography.labelLarge]；
 * - 字体颜色：[MaterialTheme.colorScheme.primary]；
 * - 边距对齐：水平起始对齐 24.dp（外层 16.dp + 内层 8.dp），垂直上下内边距各 8.dp。
 */
@Composable
fun SettingGroupTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
    )
}

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

/**
 * 带 Material 3 Expressive 粗轨细棒刻度滑块的设置条目。
 *
 * 1:1 对标 M3 Expressive 设计规范：
 * 1. 16dp 高度的全胶囊粗轨（带居中对齐刻度圆点）；
 * 2. 4dp 细长垂直圆角手柄（Thumb），比轨道稍微突出，触感清爽；
 * 3. 手柄与已激活/未激活轨道之间具备 6dp 的纯透明物理间隙（Gap），视觉晶莹通透。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingSliderItem(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon?.let {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .padding(end = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Slider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth(),
                thumb = {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(26.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        thumbTrackGapSize = 6.dp,
                        trackInsideCornerSize = 4.dp,
                        modifier = Modifier.height(16.dp),
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            activeTickColor = MaterialTheme.colorScheme.onPrimary,
                            inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            )
        }
    }
}


/**
 * 带 DropdownMenu 下拉菜单的设置条目。
 *
 * 遵循 Material 3 Expressive 规范：
 * 1. 采用 [SegmentedListItem] 排版，右侧展示当前选中项值；
 * 2. 点击整行在条目处呼出带有圆角的 [DropdownMenu]；
 * 3. 选中项采用高亮胶囊背景块 [RoundedCornerShape] 与前置对勾图标。
 */
@Composable
fun <T> SettingDropdownItem(
    title: String,
    selectedValue: T,
    values: List<T>,
    valueLabel: (T) -> String,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    SettingItem(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        onClick = { if (enabled) expanded = true },
        trailingContent = {
            Box {
                Text(
                    text = valueLabel(selectedValue),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.heightIn(max = 360.dp)
                ) {
                    values.forEach { item ->
                        val isSelected = item == selectedValue
                        val itemModifier = if (isSelected) {
                            Modifier
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp)
                                )
                        } else {
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        }

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = valueLabel(item),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            } else null,
                            onClick = {
                                onValueChange(item)
                                expanded = false
                            },
                            modifier = itemModifier,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    )
}
