package com.nobody.repoi.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobody.repoi.ui.home.FeedCategory
import com.nobody.repoi.ui.search.components.FilterChipSection
import com.nobody.repoi.ui.search.components.FilterSheetActions
import com.nobody.repoi.ui.theme.ExpressiveTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeFilterSheetContent(
    categories: List<FeedCategory>,
    selectedCategory: FeedCategory,
    onCategorySelect: (FeedCategory) -> Unit,
    subTypes: List<Pair<String, String>>,
    selectedSubType: String,
    onSubTypeSelect: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onNavigateToZoneBrowse: (() -> Unit)? = null,
    onNavigateToForumHome: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "筛选与专区",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 专区分类区块
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "专区分区",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    categories.forEach { category ->
                        val isSelected = selectedCategory.tabKey == category.tabKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { onCategorySelect(category) },
                            label = { Text(category.name) },
                            leadingIcon = if (isSelected) {
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

                    // 末尾「+ 专区」快捷操作胶囊，一键进入专区列表页
                    AssistChip(
                        onClick = {
                            onDismiss()
                            onNavigateToZoneBrowse?.invoke()
                        },
                        label = { Text("专区", fontWeight = FontWeight.Medium) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "专区列表",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        shape = ExpressiveTokens.ChipShape,
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )

                    // 「讨论板」快捷操作胶囊，一键进入讨论板主页
                    AssistChip(
                        onClick = {
                            onDismiss()
                            onNavigateToForumHome?.invoke()
                        },
                        label = { Text("讨论板", fontWeight = FontWeight.Medium) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "讨论板",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        shape = ExpressiveTokens.ChipShape,
                        colors = AssistChipDefaults.assistChipColors(
                            labelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            // 动态类型区块
            FilterChipSection(
                title = "动态子分类",
                items = subTypes,
                selectedPredicate = { selectedSubType == it.first },
                onSelect = { onSubTypeSelect(it.first) },
                label = { it.second }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        FilterSheetActions(
            onReset = onReset,
            onDismiss = onDismiss
        )
    }
}
