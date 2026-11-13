package com.nobody.repoi.ui.item.list.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.ui.common.PoiAsyncImage
import com.nobody.repoi.ui.common.card.PoiListCard
import com.nobody.repoi.ui.common.card.PoiPosterCard
import com.nobody.repoi.ui.common.card.PoiThumbnail

/**
 * 周边网格封面卡片（自适应多列网格）
 * 基于 Material 3 与 PoiPosterCard 深基元规范
 */
@Composable
fun HobbyGridCard(
    item: HobbyItemSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PoiPosterCard(
        onClick = onClick,
        modifier = modifier,
        aspectRatio = 9f / 13f,
        badgeText = item.displayHitsText.takeIf { it.isNotBlank() },
        footerText = item.displayReleaseDateText.takeIf { it.isNotBlank() },
        cover = {
            PoiAsyncImage(
                path = item.cover,
                category = MediaCategory.HOBBY,
                purpose = ImagePurpose.CARD_STANDARD,
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}

/**
 * 周边单列图文详情卡片（适配 Hpoi 官方列表项布局 item_hobby_list.xml）
 * 基于 Material 3 与 PoiListCard 深基元规范
 */
@Composable
fun HobbyListCard(
    item: HobbyItemSummary,
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
                    category = MediaCategory.HOBBY,
                    purpose = ImagePurpose.THUMBNAIL,
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    ) {
        // 第 1 行：厂商 与 发售（等宽 2 列）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HpoiInfoBadgeItem(
                label = "厂商",
                value = item.companyName.orEmpty(),
                modifier = Modifier.weight(1f)
            )

            HpoiInfoBadgeItem(
                label = "发售",
                value = item.displayReleaseDateText,
                modifier = Modifier.weight(1f)
            )
        }

        // 第 2 行：定价 与 评分（等宽 2 列）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HpoiInfoBadgeItem(
                label = "定价",
                value = item.displayPriceText,
                modifier = Modifier.weight(1f)
            )

            HpoiInfoBadgeItem(
                label = "评分",
                value = if (item.rating > 0) item.displayRatingText else "0.00",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 官方条目列表属性胶囊徽章项（对应 item_hobby_list 中的 txt_em_* 与 txt_val_*）
 * 遵循 M3 颜色系统与排版规范，杜绝写死色值
 */
@Composable
fun HpoiInfoBadgeItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 标签小徽章：固定 36.dp 宽，ExtraSmallShape 圆角，surfaceContainerHigh 浅阶容器
        Surface(
            modifier = Modifier.width(36.dp),
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }

        // 数值内容
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
    }
}
