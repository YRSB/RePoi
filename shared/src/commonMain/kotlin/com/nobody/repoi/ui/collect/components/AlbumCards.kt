package com.nobody.repoi.ui.collect.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nobody.repoi.data.remote.model.UserCollectAlbumItem
import com.nobody.repoi.ui.album.components.formatAlbumTime
import com.nobody.repoi.ui.collect.MediaOrderOption
import com.nobody.repoi.ui.common.listImageKey
import com.nobody.repoi.ui.common.rememberListImageRequest
import com.nobody.repoi.ui.theme.ExpressiveElevatedCard
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.util.CdnUrlUtils
import com.nobody.repoi.util.ImageAspectRatioCache

// ── 收藏相册页卡片 ──────────────────────────────────────────

/**
 * 收藏页（category key=70000）专用相册卡片。
 *
 * 与相册主页面 `ui/album/components/AlbumFeedCard.kt` 的差异（本文件独立实现，**不改 AlbumFeedCard**）：
 * 1. **封面 CDN 变体用 `cover/sr/`**（`CdnUrlUtils.getCoverSrUrl`）——
 *    原版收藏相册页封面取 `pe/c.java:158` 的 `cover/sr/` 自适应长宽比变体，
 *    而主页面卡片用的是 `/n`。瀑布流与相册场景按 AGENTS.md 规范必须走 `/sr`。
 * 2. **时间字段与排序档联动**（交付项 #5）：
 *    - `addTime`（发布时间）档显示相册发布时间；
 *    - `actionDate`（收藏时间）档显示收藏时间 `actualActionDate`；
 *    - `praiseCount`（点赞量）档**隐藏整行时间**（按点赞排序时时间无参考意义）。
 * 3. **多选态**：多选模式下封面浮出选中徽标、卡片底色高亮；
 *    长按卡片（封面/标题/头像任一处命中同一个 `combinedClickable`）进入多选。
 *
 * @param item 外层收藏节点。⚠️ **批量取消收藏用 `item.id`，不是 `actualAlbumId`**。
 * @param currentOrder 当前排序档，取值见 [MediaOrderOption]
 * @param isSelected 多选模式下是否已选中
 * @param isSelectionMode 是否处于多选模式（决定点击是「进详情」还是「切换选中」）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CollectAlbumCard(
    item: UserCollectAlbumItem,
    currentOrder: String,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExpressiveElevatedCard(
        // 卡片本体不带 onClick，点击统一由 combinedClickable 接管，避免双重点击区
        onClick = null,
        shape = ExpressiveTokens.MediumCardShape,
        containerColor = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                // 多选模式下单击切换选中；非多选模式单击进详情（语义都由调用方决定）
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column {
            if (item.hasCover) {
                CollectAlbumCover(
                    item = item,
                    isSelected = isSelected,
                    isSelectionMode = isSelectionMode
                )
            } else {
                // 无封面兜底：原版过滤 none.jpg 后改为直接显标题（UserCollectAlbumAdapter）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.actualTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    if (isSelectionMode) {
                        SelectionMark(
                            selected = isSelected,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                if (item.hasCover && item.actualTitle.isNotBlank()) {
                    Text(
                        text = item.actualTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // 时间字段与排序档联动（交付项 #5）
                val timeText = remember(currentOrder, item.id) {
                    formatCollectAlbumTime(item, currentOrder)
                }
                val avatarUrl = remember(item.id) {
                    CdnUrlUtils.getHeadUrl(item.actualUserAvatar)
                }
                val hasAuthor = !item.actualUserNickName.isNullOrBlank()
                val hasAvatar = avatarUrl.isNotBlank()

                if (hasAuthor || hasAvatar || timeText != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (hasAvatar) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (hasAuthor) {
                            Text(
                                text = item.actualUserNickName.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        if (timeText != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 收藏页相册封面：`cover/sr/` 变体 + 自适应长宽比（跨项缓存 [ImageAspectRatioCache]，比例夹取 [0.4, 2.0]）。
 */
@Composable
private fun CollectAlbumCover(
    item: UserCollectAlbumItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier
) {
    val coverUrl = remember(item.id, item.actualCover) {
        CdnUrlUtils.getCoverSrUrl(item.actualCover)
    }
    val ratioKey = listImageKey(item.actualCover, coverUrl, "sr")
    val coverRequest = rememberListImageRequest(item.actualCover, coverUrl, "sr:collect")
    var aspectRatio by remember(ratioKey) {
        mutableFloatStateOf(ImageAspectRatioCache[ratioKey] ?: 1.0f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        AsyncImage(
            model = coverRequest,
            contentDescription = item.actualTitle.ifBlank { "相册封面" },
            contentScale = ContentScale.Crop,
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                if (size.width > 0 && size.height > 0) {
                    val ratio = (size.width / size.height).coerceIn(0.4f, 2.0f)
                    aspectRatio = ratio
                    ImageAspectRatioCache[ratioKey] = ratio
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (item.actualPraiseCount > 0) {
            CollectPraiseBadge(
                count = item.actualPraiseCount,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )
        }

        if (isSelectionMode) {
            SelectionMark(
                selected = isSelected,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            )
        }
    }
}

/**
 * 多选态选中徽标：未选中为空心圈，选中为实心勾。
 */
@Composable
private fun SelectionMark(
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f),
        border = if (selected) null else BorderStroke(
            width = 2.dp,
            color = Color.White.copy(alpha = 0.9f)
        ),
        modifier = modifier.size(22.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * 点赞数角标（对齐原版收藏相册卡右上角的点赞胶囊）。
 */
@Composable
private fun CollectPraiseBadge(
    count: Long,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = ExpressiveTokens.PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ThumbUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 按排序档解析收藏相册卡片的时间文案（交付项 #5）。
 *
 * - [MediaOrderOption.ADD_TIME]「发布时间」→ 显示相册发布时间 `addTime`；
 * - [MediaOrderOption.ACTION_DATE]「收藏时间」→ 显示收藏时间 `actualActionDate`；
 * - [MediaOrderOption.PRAISE_COUNT]「点赞量」→ 返回 null，由调用方隐藏整行时间。
 *
 * @param album 外层收藏节点
 * @param currentOrder 当前排序档
 */
internal fun formatCollectAlbumTime(
    album: UserCollectAlbumItem,
    currentOrder: String
): String? = when (currentOrder) {
    // 发布/更新时间：原版以 updTime 为准（纯相对时间），降级内层 updTime 再退到 addTime
    MediaOrderOption.ADD_TIME -> formatAlbumTime(
        album.updTime
            ?: album.item?.updTime
            ?: album.addTime
            ?: album.item?.addTime
    )
    // 收藏时间：actualActionDate 已做「外层 → 内层」的回退
    MediaOrderOption.ACTION_DATE -> formatAlbumTime(album.actualActionDate)
    // 按点赞量排序时发布时间与收藏时间都失去参考意义，整行隐藏
    else -> null
}