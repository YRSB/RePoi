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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.remote.model.UserCollectAlbumItem
import com.nobody.repoi.ui.album.components.AlbumTextCover
import com.nobody.repoi.ui.album.components.formatAlbumTime
import com.nobody.repoi.ui.collect.AlbumTypeOption
import com.nobody.repoi.ui.collect.MediaOrderOption
import com.nobody.repoi.ui.common.PoiAsyncImage
import com.nobody.repoi.ui.common.card.PoiCaptionGridCard
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults

// ── 收藏相册页卡片 ──────────────────────────────────────────

/**
 * 收藏页（category key=70000）专用相册卡片。
 *
 * 与相册主页面 `ui/album/components/AlbumFeedCard.kt` 的差异（本文件独立实现，**不改 AlbumFeedCard**）：
 * 1. **封面 CDN 变体走 `cover/sr/`**（`MediaCategory.ALBUM` + `ImagePurpose.ADAPTIVE_GRID`）——
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
 * @param albumType 相册语料档位，取值见 [AlbumTypeOption]（决定「收藏时间」档的时间回落）
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
    albumType: String = AlbumTypeOption.DEFAULT,
    modifier: Modifier = Modifier
) {
    PoiCaptionGridCard(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        modifier = modifier,
        cover = {
            CollectAlbumCover(
                item = item,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode
            )
        },
        caption = {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                if (item.actualTitle.isNotBlank()) {
                    Text(
                        text = item.actualTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // 时间字段与排序档联动（交付项 #5）；「我创建的」档无收藏时间，退到发布时间
                val timeText = remember(currentOrder, item.id, albumType) {
                    formatCollectAlbumTime(item, currentOrder, albumType)
                }
                val hasAuthor = !item.actualUserNickName.isNullOrBlank()
                val hasAvatar = !item.actualUserAvatar.isNullOrBlank()

                if (hasAuthor || hasAvatar || timeText != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (hasAvatar) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                PoiAsyncImage(
                                    path = item.actualUserAvatar,
                                    category = MediaCategory.AVATAR,
                                    purpose = ImagePurpose.CARD_STANDARD,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
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
    )
}

/**
 * 收藏页相册封面：`cover/sr/` 变体 + 自适应长宽比（比例缓存与夹取由 [PoiAsyncImage] 统一承担）。
 */
@Composable
private fun CollectAlbumCover(
    item: UserCollectAlbumItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier
) {
    // 无封面（含官方缺省吉祥物）沿用历史 17:24 纯文本卡
    val isNoCover = !item.hasCover || MediaPipeline.Default.isPlaceholder(item.actualCover)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isNoCover) Modifier.aspectRatio(17f / 24f) else Modifier)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (isNoCover) {
            AlbumTextCover(
                title = item.actualTitle,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // 相册无 cover/n/ 目录：瀑布流缩略统一由管道裁决到保留纵横比的 cover/sr/
            PoiAsyncImage(
                path = item.actualCover,
                category = MediaCategory.ALBUM,
                purpose = ImagePurpose.ADAPTIVE_GRID,
                contentDescription = item.actualTitle.ifBlank { "相册封面" },
                modifier = Modifier.fillMaxWidth(),
                adaptiveRatio = true,
                fallbackRatio = 1.0f
            )
        }

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
        shape = CircleShape,
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
 * @param albumType 相册语料档位（`fav` 我收藏的 / `upload` 我创建的）
 */
internal fun formatCollectAlbumTime(
    album: UserCollectAlbumItem,
    currentOrder: String,
    albumType: String = AlbumTypeOption.DEFAULT
): String? = when (currentOrder) {
    // 发布/更新时间：原版以 updTime 为准（纯相对时间），降级内层 updTime 再退到 addTime
    MediaOrderOption.ADD_TIME -> formatAlbumTime(
        album.updTime
            ?: album.item?.updTime
            ?: album.addTime
            ?: album.item?.addTime
    )
    // 收藏时间：actualActionDate 已做「外层 → 内层」的回退
    MediaOrderOption.ACTION_DATE -> if (albumType == AlbumTypeOption.UPLOAD) {
        // 「我创建的」条目本身没有收藏动作，退到发布时间，避免整行空白
        formatAlbumTime(album.actualActionDate ?: album.addTime ?: album.item?.addTime)
    } else {
        formatAlbumTime(album.actualActionDate)
    }
    // 按点赞量排序时发布时间与收藏时间都失去参考意义，整行隐藏
    else -> null
}
