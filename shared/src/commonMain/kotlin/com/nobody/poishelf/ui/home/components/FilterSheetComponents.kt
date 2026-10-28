package com.nobody.poishelf.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobody.poishelf.ui.theme.ExpressiveTokens

/**
 * M3 本地筛选共用组件（M5 `ui/search/components/SearchFilterSheet.kt` 移植前占位）。
 *
 * 背景：`HomeFilterSheetContent` 引用的 `FilterChipSection` / `FilterSheetActions`
 * 定义在 M5 范围（`SearchFilterSheet.kt:119/:523`），M3 分支尚无 `ui/search/components`。
 * 为保持 M3 编译自包含，此处提供与 M5 同签名的最小实现；M5 落地时删除本文件、
 * 改由 `ui.search.components` import（TODO(M5)）。
 */
@Composable
fun <T> FilterChipSection(
    title: String,
    items: List<T>,
    selectedPredicate: (T) -> Boolean,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                val selected = selectedPredicate(item)
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(item) },
                    label = { Text(label(item)) },
                    shape = ExpressiveTokens.ChipShape
                )
            }
        }
    }
}

/**
 * M3 本地筛选底部操作栏（M5 `ui/search/components/SearchFilterSheet.kt:523`
 * `FilterSheetActions` 移植前占位，TODO(M5) 同上）。
 */
@Composable
fun FilterSheetActions(
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.weight(1f),
            shape = ExpressiveTokens.PillShape
        ) {
            Text(text = "重置", color = MaterialTheme.colorScheme.onSurface)
        }
        Button(
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
            shape = ExpressiveTokens.PillShape
        ) {
            Text(text = "确定", color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
