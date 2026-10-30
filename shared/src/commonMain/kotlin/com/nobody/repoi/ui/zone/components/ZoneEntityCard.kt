package com.nobody.repoi.ui.zone.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.nobody.repoi.data.remote.model.ZoneEntityItem
import com.nobody.repoi.ui.common.rememberListImageRequest
import com.nobody.repoi.ui.home.components.shimmerBackground
import com.nobody.repoi.ui.theme.ExpressiveElevatedCard
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.util.CdnUrlUtils

/**
 * 专区通用实体卡片（厂商、系列、作品、角色、人物）
 * 遵循搜索结果页与通用卡片规范：左侧 68dp 方形缩略图，右侧两行标题与浏览量统计。
 */
@Composable
fun ZoneEntityCard(
    item: ZoneEntityItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = CdnUrlUtils.getCoverSmallUrl(item.cover)
    val thumbRequest = rememberListImageRequest(item.cover, coverUrl, "thumb")
    var loadFailed by remember(coverUrl) { mutableStateOf(false) }

    ExpressiveElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveTokens.MediumCardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧方形微曲角封面（68dp x 68dp）
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(ExpressiveTokens.SmallCardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (!coverUrl.isNullOrBlank() && !loadFailed) {
                    AsyncImage(
                        model = thumbRequest,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onError = { loadFailed = true }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // 右侧信息展示（名称 + 浏览量）
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = item.displayHitsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 专区通用实体卡片骨架占位
 */
@Composable
fun ZoneEntityCardSkeleton(
    modifier: Modifier = Modifier
) {
    ExpressiveElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveTokens.MediumCardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(ExpressiveTokens.SmallCardShape)
                    .shimmerBackground()
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .size(16.dp)
                        .clip(ExpressiveTokens.ExtraSmallShape)
                        .shimmerBackground()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.35f)
                        .size(12.dp)
                        .clip(ExpressiveTokens.ExtraSmallShape)
                        .shimmerBackground()
                )
            }
        }
    }
}
