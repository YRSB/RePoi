package com.nobody.repoi.ui.collect.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobody.repoi.ui.theme.ExpressiveTokens

/**
 * 周边页细分子分类候选集合。
 *
 * 对标原版 `ui/common/b0.java:231-240`：
 * `{10000 全部周边, 100 手办, 200 动漫模型, 500 真实模型, 400 毛绒布偶, 300 Doll娃娃}`。
 * 「全部」以 `10000` 表达（而非 `null`），且不存在原版没有的 `900 动漫周边`。
 */
internal val CollectHobbySubCategories: List<Pair<Int, String>> = listOf(
    10000 to "全部周边",
    100 to "手办",
    200 to "动漫模型",
    500 to "真实模型",
    400 to "毛绒布偶",
    300 to "Doll娃娃"
)

// ── 筛选抽屉 BottomSheet ────────────────────────────────────

/**
 * 收藏高级筛选抽屉，采用**「暂存-确定」**语义（对齐原版 `PopupCollectHobby`：
 * 弹层内点 chip 只改本地暂存值，底部「确定」才回调提交，「取消」直接丢弃）。
 *
 * 周边页（分类 + 标签）与作品页（仅标签）共用本组件：
 * - `title` 控制抽屉主标题；
 * - `subCategories` 传入空列表时自动隐藏「细分类型」区块，仅保留标签筛选；
 * - `emptyTagHint` 允许各页给出贴合自身编辑入口的标签空态文案。
 *
 * @param subCategoryId 当前**已生效**的细分类（`null` 表示未筛选，等价于候选集首项「全部」）。
 * @param selectedTag 当前**已生效**的标签。
 * @param onConfirm 点「确定」时回传暂存结果，子分类传 `null` 表示「全部」。
 * @param onReset 点「重置」时清空全部条件并立即生效（本身即显式提交动作）。
 * @param onDismiss 点「确定」或「取消」后关闭抽屉。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun CollectFilterBottomSheet(
    subCategoryId: Int?,
    selectedTag: String?,
    availableTags: List<String>,
    onConfirm: (subCategoryId: Int?, tag: String?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "周边筛选",
    subCategories: List<Pair<Int, String>> = CollectHobbySubCategories,
    subCategorySectionTitle: String = "分类",
    tagSectionTitle: String = "标签",
    emptyTagHint: String = "暂无标签（可在条目详情页添加自定义标签）"
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    // 暂存值：chip 点击只改这里，点「确定」才提交
    var pendingSubCategoryId by remember(subCategoryId) { mutableStateOf(subCategoryId) }
    var pendingTag by remember(selectedTag) { mutableStateOf(selectedTag) }

    // 「全部」既可能以 null 也可能以候选集首项（10000）表达，高亮时统一归位
    val allSubCategoryId = subCategories.firstOrNull()?.first
    val highlightedSubCategoryId = pendingSubCategoryId ?: allSubCategoryId

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = ExpressiveTokens.DialogShape,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶栏：标题与重置
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = {
                        pendingSubCategoryId = null
                        pendingTag = null
                        onReset()
                    }
                ) {
                    Text(text = "重置", color = MaterialTheme.colorScheme.error)
                }
            }

            // 细分子分类（仅标签页传空列表即整体隐藏）
            if (subCategories.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = subCategorySectionTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        subCategories.forEach { (catId, label) ->
                            val isSelected = highlightedSubCategoryId == catId
                            FilterChip(
                                selected = isSelected,
                                onClick = { pendingSubCategoryId = catId },
                                label = { Text(text = label) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                shape = ExpressiveTokens.ChipShape
                            )
                        }
                    }
                }
            }

            // 收藏标签池（单选，再次点击同一标签即取消暂存）
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tagSectionTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (availableTags.isEmpty()) {
                    Text(
                        text = emptyTagHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableTags.forEach { tag ->
                            val isSelected = pendingTag == tag
                            FilterChip(
                                selected = isSelected,
                                onClick = { pendingTag = if (isSelected) null else tag },
                                label = { Text(text = tag) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                shape = ExpressiveTokens.ChipShape
                            )
                        }
                    }
                }
            }

            // 底部「取消 / 确定」：确定才提交暂存值，取消直接丢弃
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = "取消")
                }
                Spacer(modifier = Modifier.size(8.dp))
                Button(
                    onClick = {
                        onConfirm(pendingSubCategoryId, pendingTag)
                        onDismiss()
                    },
                    shape = ExpressiveTokens.ButtonShape,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Text(text = "确定")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}