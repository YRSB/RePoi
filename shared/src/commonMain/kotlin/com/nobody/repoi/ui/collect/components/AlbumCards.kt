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
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

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

@Composable
private fun CollectAlbumCover(
    item: UserCollectAlbumItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier
) {

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
                    painter = painterResource(PoiIcons.Filled.Check),
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

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
                painter = painterResource(PoiIcons.Filled.ThumbUp),
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

internal fun formatCollectAlbumTime(
    album: UserCollectAlbumItem,
    currentOrder: String,
    albumType: String = AlbumTypeOption.DEFAULT
): String? = when (currentOrder) {

    MediaOrderOption.ADD_TIME -> formatAlbumTime(
        album.updTime
            ?: album.item?.updTime
            ?: album.addTime
            ?: album.item?.addTime
    )

    MediaOrderOption.ACTION_DATE -> if (albumType == AlbumTypeOption.UPLOAD) {

        formatAlbumTime(album.actualActionDate ?: album.addTime ?: album.item?.addTime)
    } else {
        formatAlbumTime(album.actualActionDate)
    }

    else -> null
}
