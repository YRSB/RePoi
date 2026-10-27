package com.nobody.repoi.ui.search.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobody.repoi.ui.theme.ExpressiveTokens

/**
 * Wave 5 先行副本（源自 `SearchFilterSheet.kt` 的纯 Compose 原语，供 `HomeFilterSheet` 引用）。
 *
 * Wave 6 迁移真实 `SearchFilterSheet` 时删除本文件。
 */

/**
 * 通用 FilterChip 流式区块组件：
 * 消除多处重复的 Column + Title + FlowRow + FilterChip 模板代码
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> FilterChipSection(
    title: String,
    items: List<T>,
    selectedPredicate: (T) -> Boolean,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    showLeadingCheckmark: Boolean = true
) {
    if (items.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items.forEach { item ->
                val isSelected = selectedPredicate(item)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(item) },
                    label = { Text(label(item)) },
                    leadingIcon = if (showLeadingCheckmark && isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Done,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null,
                    shape = ExpressiveTokens.ChipShape
                )
            }
        }
    }
}

/**
 * 筛选抽屉共用底部固定操作栏（重置 + 完成）
 */
@Composable
fun FilterSheetActions(
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(
            onClick = onReset,
            shape = ExpressiveTokens.ButtonShape,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            Text("重置", fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onDismiss,
            shape = ExpressiveTokens.ButtonShape,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            Text("完成", fontWeight = FontWeight.Bold)
        }
    }
}
