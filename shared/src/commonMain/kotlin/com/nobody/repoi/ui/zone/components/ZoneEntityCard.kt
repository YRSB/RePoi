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
import com.nobody.repoi.ui.common.card.PoiThumbnail

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
