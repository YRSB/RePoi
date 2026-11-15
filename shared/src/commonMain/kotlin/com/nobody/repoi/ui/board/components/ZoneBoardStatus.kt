package com.nobody.repoi.ui.board.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 板块空态占位。
 *
 * 从 `ZoneDetailScreen` 的私有件**逐字提升**（签名、文案、留白不变），供专区详情与首页共享。
 * 注：现状为空列表居中文字（无「刷新看看」按钮），与 `docs/home-hierarchy.md` §3.6 的卡片式空态
 * 不一致；该差异属既有行为，本阶段不做视觉变更。
 */
@Composable
fun ZoneEmptyPlaceholder(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 板块首屏错误占位（全行渲染，含「重试」按钮）。
 *
 * 从 `ZoneDetailScreen` 的私有件**逐字提升**，外观/文案与现状一致。
 */
@Composable
fun ZoneErrorPlaceholder(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

/**
 * 板块分页页脚（加载更多中 / 加载失败可重试 / 已经到底）。
 *
 * 从 `ZoneDetailScreen` 的私有件**逐字提升**，供两类宿主复用；由宿主以全行 `item(span = FullLine)` 放置。
 */
@Composable
fun ZonePaginationFooter(
    isLoadingMore: Boolean,
    loadMoreError: String?,
    isEndReached: Boolean,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoadingMore -> {
                LoadingIndicator(
                    modifier = Modifier.size(28.dp)
                )
            }
            loadMoreError != null -> {
                OutlinedButton(onClick = onRetry) {
                    Text("加载更多失败，点击重试")
                }
            }
            isEndReached -> {
                Text(
                    text = "已经到底啦",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
