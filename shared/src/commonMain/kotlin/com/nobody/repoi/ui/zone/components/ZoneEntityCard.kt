package com.nobody.repoi.ui.zone.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.remote.model.ZoneEntityItem
import com.nobody.repoi.ui.common.PoiAsyncImage
import com.nobody.repoi.ui.common.card.PoiListCard
import com.nobody.repoi.ui.common.card.PoiListCardSkeleton
import com.nobody.repoi.ui.common.card.PoiThumbnail

/**
 * 专区通用实体卡片（厂商、系列、作品、角色、人物）
 * 遵循搜索结果页与通用卡片规范：基于 PoiListCard 深基元，左侧 68dp 方形缩略图，右侧两行标题与浏览量统计。
 */
@Composable
fun ZoneEntityCard(
    item: ZoneEntityItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PoiListCard(
        title = item.displayName,
        onClick = onClick,
        modifier = modifier,
        thumbnail = {
            PoiThumbnail.Square {
                PoiAsyncImage(
                    path = item.cover,
                    category = MediaCategory.ZONE,
                    purpose = ImagePurpose.THUMBNAIL,
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    ) {
        Text(
            text = item.displayHitsText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * 专区通用实体卡片骨架占位
 */
@Composable
fun ZoneEntityCardSkeleton(
    modifier: Modifier = Modifier
) {
    PoiListCardSkeleton(modifier = modifier)
}

