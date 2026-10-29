package com.nobody.poishelf.ui.search.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nobody.poishelf.data.remote.model.HotwordItem

/**
 * 实时联想建议列表
 * 匹配输入的文字高亮为主题色，点击直接搜索
 */
@Composable
fun SearchSuggestionsList(
    suggestions: List<String>,
    query: String,
    onSelectWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        suggestions.take(10).forEach { word ->
            val annotated = buildAnnotatedString {
                val trimmedQuery = query.trim()
                val matchIndex = if (trimmedQuery.isNotEmpty()) word.indexOf(trimmedQuery, ignoreCase = true) else -1
                if (matchIndex >= 0) {
                    if (matchIndex > 0) {
                        append(word.substring(0, matchIndex))
                    }
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(word.substring(matchIndex, matchIndex + trimmedQuery.length))
                    }
                    if (matchIndex + trimmedQuery.length < word.length) {
                        append(word.substring(matchIndex + trimmedQuery.length))
                    }
                } else {
                    append(word)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSelectWord(word) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 搜索历史模块
 * 包含“搜索历史”标题与右侧清空按钮、流式标签。
 * 支持单击搜索、长按删除（无弹窗确认）、一键清空全部（无弹窗确认），chip 内部无多余删除按钮。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SearchHistorySection(
    historyList: List<String>,
    onSelectWord: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (historyList.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题行：搜索历史 + 右侧清空 (去掉原先多余的时钟与排序图标)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "搜索历史",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 清空按钮 (带列表图标 + 清空文本，点击直接清空，无弹窗)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onClearAll() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.List,
                    contentDescription = "清空历史",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "清空",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 搜索历史标签流 (无内部删除图标，单击搜索，长按直接删除)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            historyList.forEach { historyWord ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .combinedClickable(
                            onClick = { onSelectWord(historyWord) },
                            onLongClick = { onDeleteItem(historyWord) }
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = historyWord,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * 大家在找模块 (原搜索发现/热词推荐)
 * 包含刷新按钮、流式 chip 展示大家在找列表，样式和搜索历史同款。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EveryoneSearchingSection(
    hotwords: List<HotwordItem>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSelectWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题行：大家在找 + 右侧刷新
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "大家在找",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onRefresh() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "刷新大家在找",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "刷新",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isLoading && hotwords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
        } else if (hotwords.isNotEmpty()) {
            // 流式 chip 排布 (和搜索历史同款)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                hotwords.forEach { item ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .clickable { onSelectWord(item.word) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.word,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * 兼容旧命名的别名函数，委托至 EveryoneSearchingSection
 */
@Composable
fun SearchDiscoverySection(
    hotwords: List<HotwordItem>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSelectWord: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    EveryoneSearchingSection(
        hotwords = hotwords,
        isLoading = isLoading,
        onRefresh = onRefresh,
        onSelectWord = onSelectWord,
        modifier = modifier
    )
}
