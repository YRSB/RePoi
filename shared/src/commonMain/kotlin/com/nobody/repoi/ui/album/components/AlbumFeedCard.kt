package com.nobody.repoi.ui.album.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.layout.ContentScale
import com.nobody.repoi.ui.common.listImageKey
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nobody.repoi.ui.common.rememberListImageRequest
import com.nobody.repoi.ui.common.UserAvatar
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.platform.DateTimeFormats
import com.nobody.repoi.util.CdnUrlUtils
import com.nobody.repoi.util.ImageAspectRatioCache
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
fun AlbumFeedCard(
    item: AlbumListItem,
    showTime: Boolean = false,
    rank: Int? = null,
    onClick: (AlbumListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = { onClick(item) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 1.dp
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            AlbumCover(
                item = item,
                rank = rank,
                modifier = Modifier.fillMaxWidth()
            )
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                if (item.displayName.isNotBlank()) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                val hasAuthor = item.hasAuthor
                val formattedTime = remember(item.addTime, showTime) {
                    if (showTime) formatAlbumTime(item.addTime) else null
                }

                if (hasAuthor || formattedTime != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (hasAuthor) {
                            UserAvatar(
                                avatarUrl = item.userAvatar,
                                size = 18.dp,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = item.userNickname,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        if (formattedTime != null) {
                            if (hasAuthor) {
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = formattedTime,
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

@Composable
private fun AlbumCover(item: AlbumListItem, rank: Int? = null, modifier: Modifier = Modifier) {
    val isNoCover = !item.hasCover || CdnUrlUtils.isNoCover(item.cover)
    val coverUrl = if (isNoCover) "" else CdnUrlUtils.getCoverSrUrl(item.cover)
    val ratioKey = listImageKey(item.cover, coverUrl, "sr")
    val coverRequest = rememberListImageRequest(item.cover, coverUrl, "sr:waterfall")
    val defaultRatio = if (isNoCover) 17f / 24f else 1.0f
    var aspectRatio by remember(ratioKey, isNoCover) {
        mutableFloatStateOf(if (isNoCover) defaultRatio else (ImageAspectRatioCache[ratioKey] ?: 1.0f))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        if (isNoCover) {
            AlbumTextCover(
                title = item.displayName,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = coverRequest,
                contentDescription = item.displayName.ifBlank { "相册封面" },
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
        }
        if (rank != null) {
            RankBadge(
                rank = rank,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            )
        }
        if (item.praiseCountValue > 0) {
            PraiseBadge(
                count = item.praiseCountValue,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun PraiseBadge(count: Long, modifier: Modifier = Modifier) {
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RankBadge(rank: Int, modifier: Modifier = Modifier) {
    val containerColor = when (rank) {
        1 -> MaterialTheme.colorScheme.primaryContainer
        2 -> MaterialTheme.colorScheme.secondaryContainer
        3 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
    }
    val contentColor = when (rank) {
        1 -> MaterialTheme.colorScheme.onPrimaryContainer
        2 -> MaterialTheme.colorScheme.onSecondaryContainer
        3 -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = modifier.size(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@OptIn(ExperimentalTime::class)
fun formatAlbumTime(addTime: String?, nowMillis: Long = Clock.System.now().toEpochMilliseconds()): String? {
    if (addTime.isNullOrBlank()) return null
    val parsed = DateTimeFormats.parseServerDateTime(addTime.trim()) ?: return addTime
    val minutes = (nowMillis - parsed) / 60_000L
    if (minutes < 0) return addTime
    if (minutes < 1) return "刚刚"
    if (minutes < 60) return "${minutes}分钟前"
    val hours = minutes / 60
    if (hours < 24) return "${hours}小时前"
    val days = hours / 24
    if (days < 30) return "${days}天前"
    val months = days / 30
    if (months < 12) return "${months}个月前"
    val years = days / 365
    return "${years}年前"
}