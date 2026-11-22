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
import androidx.compose.foundation.shape.CircleShape
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

internal val CollectHobbySubCategories: List<Pair<Int, String>> = listOf(
    10000 to "全部周边",
    100 to "手办",
    200 to "动漫模型",
    500 to "真实模型",
    400 to "毛绒布偶",
    300 to "Doll娃娃"
)

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

    var pendingSubCategoryId by remember(subCategoryId) { mutableStateOf(subCategoryId) }
    var pendingTag by remember(selectedTag) { mutableStateOf(selectedTag) }

    val allSubCategoryId = subCategories.firstOrNull()?.first
    val highlightedSubCategoryId = pendingSubCategoryId ?: allSubCategoryId

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

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
                                            painter = painterResource(PoiIcons.Check),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                shape = MaterialTheme.shapes.small
                            )
                        }
                    }
                }
            }

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
                                            painter = painterResource(PoiIcons.Check),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                shape = MaterialTheme.shapes.small
                            )
                        }
                    }
                }
            }

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
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Text(text = "确定")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
