package com.nobody.repoi.ui.board.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 板块四态（空 / 错误）的**唯一卡片基元**，由首页与专区详情共享。
 *
 * 依据 `docs/home-hierarchy.md` §3.6 与 ADR-0022 决策 4：首页与详情必须同一视觉口径——
 * 卡片式空态 + 「刷新看看」、卡片式错误 + 「重新加载」；加载态仍由宿主用 `PoiLoadingView`
 * 展示（禁骨架屏，ADR-0018）。
 *
 * 卡片容器统一在 [ZoneBoardStatusCard]，空/错两种语义只差文案与按钮，避免两屏各写一份卡片样式。
 */
@Composable
fun ZoneBoardStatusCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(24.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content
            )
        }
    }
}

/**
 * 板块空态卡片：文案 + 可选「刷新看看」。
 *
 * @param text 空态文案（各板块文案不同，由调用方给出）
 * @param onRefresh 刷新动作；为 null 时只展示文案（如宿主已有其它引导入口）
 */
@Composable
fun ZoneEmptyPlaceholder(
    text: String,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null
) {
    ZoneBoardStatusCard(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onRefresh != null) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onRefresh) {
                Text("刷新看看")
            }
        }
    }
}

/**
 * 板块首屏错误卡片：错误文案（错误色）+「重新加载」按钮。
 */
@Composable
fun ZoneErrorPlaceholder(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    ZoneBoardStatusCard(modifier = modifier) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text("重新加载")
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
