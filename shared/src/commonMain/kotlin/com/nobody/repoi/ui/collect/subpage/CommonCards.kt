package com.nobody.repoi.ui.collect.subpage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.remote.model.UserCollectCommonItem
import com.nobody.repoi.ui.common.PoiAsyncImage
import com.nobody.repoi.ui.common.card.PoiCaptionGridCard
import com.nobody.repoi.ui.common.card.PoiListCard
import com.nobody.repoi.ui.common.card.PoiThumbnail
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline

internal fun UserCollectCommonItem.isSeriesDetailRoute(): Boolean =
    itemType?.trim()?.equals(SERIES_ITEM_TYPE, ignoreCase = true) == true && pageState == 1

internal const val SERIES_ITEM_TYPE = "series"

internal fun commonCollectCoverUrl(item: UserCollectCommonItem, isListView: Boolean): String =
    if (isListView) {
        MediaPipeline.Default.resolveUrl(item.actualCover, MediaCategory.HOBBY, ImagePurpose.THUMBNAIL)
    } else {
        MediaPipeline.Default.resolveUrl(item.actualCover, MediaCategory.HOBBY, ImagePurpose.ADAPTIVE_GRID)
    }

@Composable
internal fun CommonCollectListCard(
    item: UserCollectCommonItem,
    category: UserCollectCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = remember(item.actualCover) { commonCollectCoverUrl(item, isListView = true) }

    PoiListCard(
        title = item.displayName,
        onClick = onClick,
        modifier = modifier,
        thumbnail = {
            PoiThumbnail.Square {
                PoiAsyncImage(
                    model = coverUrl,
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}

@Composable
internal fun CommonCollectGridCard(
    item: UserCollectCommonItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = remember(item.actualCover) { commonCollectCoverUrl(item, isListView = false) }

    PoiCaptionGridCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
        cover = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                PoiAsyncImage(
                    model = coverUrl,
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        caption = {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    )
}
