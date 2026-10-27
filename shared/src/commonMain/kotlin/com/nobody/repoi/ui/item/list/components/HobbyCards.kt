package com.nobody.repoi.ui.item.list.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nobody.repoi.ui.common.rememberListImageRequest
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.ui.home.components.shimmerBackground
import com.nobody.repoi.ui.theme.ExpressiveElevatedCard
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.util.CdnUrlUtils

/**
 * 周边网格封面卡片（自适应多列网格）
 * 基于 Material 3 ExpressiveElevatedCard 规范
 */
@Composable
fun HobbyGridCard(
    item: HobbyItemSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = CdnUrlUtils.getCoverUrl(item.cover)
    val gridImageRequest = rememberListImageRequest(item.cover, coverUrl, "grid")
    val bottomScrim = remember { Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))) }

    ExpressiveElevatedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 13f),
        shape = ExpressiveTokens.SmallCardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = gridImageRequest,
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // 顶部热度角标
            val hitsText = item.displayHitsText
            if (hitsText.isNotBlank()) {
                Surface(
                    shape = ExpressiveTokens.ExtraSmallShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                ) {
                    Text(
                        text = hitsText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            // 底部发售时间（半透明渐变遮罩，保证文字可读性）
            val dateText = item.displayReleaseDateText
            if (dateText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(bottomScrim)
                        .padding(top = 16.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.95f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * 周边单列图文详情卡片（适配 Hpoi 官方列表项布局 item_hobby_list.xml）
 * 基于 Material 3 ExpressiveElevatedCard 规范
 */
@Composable
fun HobbyListCard(
    item: HobbyItemSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = CdnUrlUtils.getCoverSmallUrl(item.cover)
    val thumbImageRequest = rememberListImageRequest(item.cover, coverUrl, "thumb")

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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧封面缩略图（68dp 正方形，圆角 SmallCardShape）
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(ExpressiveTokens.SmallCardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                AsyncImage(
                    model = thumbImageRequest,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 右侧信息列
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // 标题（单行截断，MD3 titleSmall）
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

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
            shape = ExpressiveTokens.ExtraSmallShape,
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

/**
 * 局部通用的骨架占位块，消除模板代码重复并统一使用 ExtraSmallShape
 */
@Composable
private fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = ExpressiveTokens.ExtraSmallShape
) {
    Box(
        modifier = modifier
            .clip(shape)
            .shimmerBackground()
    )
}

/**
 * 网格模式 1:1 动态骨架卡片
 */
@Composable
fun HobbyGridCardSkeleton(modifier: Modifier = Modifier) {
    ExpressiveElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 13f),
        shape = ExpressiveTokens.SmallCardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        SkeletonBox(
            modifier = Modifier.fillMaxSize(),
            shape = ExpressiveTokens.SmallCardShape
        )
    }
}

/**
 * 单列列表模式 1:1 动态骨架卡片
 */
@Composable
fun HobbyListCardSkeleton(modifier: Modifier = Modifier) {
    ExpressiveElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveTokens.MediumCardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面占位
            SkeletonBox(
                modifier = Modifier.size(68.dp),
                shape = ExpressiveTokens.SmallCardShape
            )

            // 右侧信息占位
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 标题占位
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp)
                )

                // 第 1 行占位
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SkeletonBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                    )
                }

                // 第 2 行占位
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SkeletonBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                    )
                }
            }
        }
    }
}
