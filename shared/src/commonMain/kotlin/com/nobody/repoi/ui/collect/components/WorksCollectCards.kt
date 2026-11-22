package com.nobody.repoi.ui.collect.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.nobody.repoi.data.remote.model.UserCollectCommonItem
import com.nobody.repoi.ui.common.PoiAsyncImage
import com.nobody.repoi.ui.common.card.PoiListCard
import com.nobody.repoi.ui.common.card.PoiThumbnail
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline

internal const val WorksPosterWidth = 90

internal const val WorksPosterHeight = 130

internal fun worksDetailItemId(item: UserCollectCommonItem): Long {
    item.item?.actualItemId?.takeIf { it > 0L }?.let { return it }
    return item.itemId.takeIf { it > 0L } ?: 0L
}

internal fun worksReleaseDatePrefix(categoryId: Int): String = when (categoryId) {
    10100 -> "上映时间："
    10200 -> "首发时间："
    10300, 10400, 10500, 10600 -> "发售时间："
    else -> "发布时间："
}

internal fun worksStateProgressText(state: Int?, current: Int): String = when (state) {
    1 -> "想看"
    2 -> "观看至${current}集"
    3 -> "看过"
    else -> ""
}

@Composable
internal fun WorksCollectCard(
    item: UserCollectCommonItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = remember(item.actualCover) { MediaPipeline.Default.resolveUrl(item.actualCover, MediaCategory.HOBBY, ImagePurpose.THUMBNAIL) }
    val detailId = remember(item.item, item.itemId) { worksDetailItemId(item) }
    val isClickable = detailId > 0L

    val entityCategoryId = item.item?.categoryId?.takeIf { it > 0 } ?: item.categoryId
    val releaseDateString = remember(item.releaseDateString) {
        item.releaseDateString?.trim()?.ifBlank { null }
    }
    val stateText = worksStateProgressText(item.state, item.current)

    PoiListCard(
        title = item.displayName,
        titleMaxLines = 2,
        onClick = onClick,
        enabled = isClickable,
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        thumbnail = {
            PoiThumbnail.Poster {
                PoiAsyncImage(
                    model = coverUrl,
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    ) {
        if (stateText.isNotBlank()) {
            Text(
                text = stateText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!releaseDateString.isNullOrBlank()) {
            Text(
                text = worksReleaseDatePrefix(entityCategoryId) + releaseDateString,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
