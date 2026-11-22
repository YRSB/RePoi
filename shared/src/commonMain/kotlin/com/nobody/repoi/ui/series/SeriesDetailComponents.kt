package com.nobody.repoi.ui.series

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalUriHandler
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.ui.common.AdaptiveChunkedGrid
import com.nobody.repoi.ui.common.ExpressiveFloatingToolbar
import com.nobody.repoi.ui.common.ExpressiveToolbarButton
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.remote.model.AlbumData
import com.nobody.repoi.data.remote.model.RelatedHobbyItem
import com.nobody.repoi.data.remote.model.SeriesData
import com.nobody.repoi.data.remote.model.SeriesForumPayload
import com.nobody.repoi.data.remote.model.SeriesGoodsItem
import com.nobody.repoi.data.remote.model.SeriesLinkItem
import com.nobody.repoi.ui.common.PoiAsyncImage
import com.nobody.repoi.ui.theme.largeCard
import com.nobody.repoi.ui.theme.bottomSheet
import androidx.compose.material3.ElevatedCard
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SeriesHeaderSection(
    series: SeriesData,
    onShowDetailInfo: () -> Unit = {},
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val displayName = series.displayName.ifBlank { "系列详情" }
    val subName = series.subName
    val introText = series.detailPlainText

    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {

                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                ) {

                    PoiAsyncImage(
                        path = series.cover,
                        category = MediaCategory.HOBBY,
                        purpose = ImagePurpose.CARD_STANDARD,
                        contentDescription = displayName,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!subName.isNullOrBlank()) {
                        Text(
                            text = subName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        if (series.hobbyCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${series.hobbyCount} 款周边",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (series.collect > 0) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = "${series.collect} 人收藏",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (introText.isNotBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onShowDetailInfo)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = introText,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "详细信息 ›",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SeriesHobbysSection(
    hobbys: List<RelatedHobbyItem>,
    isLoading: Boolean,
    onHobbyClick: (RelatedHobbyItem) -> Unit,
    onViewMore: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "周边",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (hobbys.isNotEmpty()) {
                    Text(
                        text = "查看更多 ›",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onViewMore() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            if (isLoading && hobbys.isEmpty()) {
                PoiLoadingView(modifier = Modifier.fillMaxWidth().padding(32.dp))
            } else if (hobbys.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无相关周边手办",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                AdaptiveChunkedGrid(
                    items = hobbys,
                    maxRows = 3,
                    modifier = Modifier.fillMaxWidth()
                ) { item ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.8f),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { onHobbyClick(item) }
                    ) {

                        PoiAsyncImage(
                            path = item.cover,
                            category = MediaCategory.HOBBY,
                            purpose = ImagePurpose.CARD_STANDARD,
                            contentDescription = item.displayName,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SeriesSubSeriesSection(
    subSeriesList: List<SeriesLinkItem>,
    isLoading: Boolean,
    onSubSeriesClick: (SeriesLinkItem) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLoading && subSeriesList.isEmpty()) return

    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "子系列",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (isLoading && subSeriesList.isEmpty()) {
                PoiLoadingView(modifier = Modifier.fillMaxWidth().padding(32.dp))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    subSeriesList.forEach { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            onClick = { onSubSeriesClick(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                ) {

                                    PoiAsyncImage(
                                        path = item.cover,
                                        category = MediaCategory.HOBBY,
                                        purpose = ImagePurpose.CARD_STANDARD,
                                        contentDescription = item.displayName,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = item.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (item.hobbyCount > 0) {
                                        Text(
                                            text = "${item.hobbyCount} 款系列周边",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = "›",
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SeriesRelateSeriesSection(
    relateSeriesList: List<SeriesLinkItem>,
    isLoading: Boolean,
    onRelateSeriesClick: (SeriesLinkItem) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLoading && relateSeriesList.isEmpty()) return

    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "相关系列",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (isLoading && relateSeriesList.isEmpty()) {
                PoiLoadingView(modifier = Modifier.fillMaxWidth().padding(32.dp))
            } else {
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    relateSeriesList.forEach { item ->
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            onClick = { onRelateSeriesClick(item) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(110.dp)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                ) {

                                    PoiAsyncImage(
                                        path = item.cover,
                                        category = MediaCategory.HOBBY,
                                        purpose = ImagePurpose.CARD_STANDARD,
                                        contentDescription = item.displayName,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Text(
                                    text = item.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )

                                if (item.hobbyCount > 0) {
                                    Text(
                                        text = "${item.hobbyCount} 款",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SeriesAlbumsSection(
    albums: List<AlbumData>,
    isLoading: Boolean,
    onAlbumClick: (AlbumData) -> Unit,
    onViewMore: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLoading && albums.isEmpty()) return

    val displayAlbums = remember(albums) { albums.take(8) }
    val hasMore = albums.size > 8

    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "相关相册",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (albums.isNotEmpty()) {
                    Text(
                        text = "查看更多 ›",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onViewMore() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            if (isLoading && albums.isEmpty()) {
                PoiLoadingView(modifier = Modifier.fillMaxWidth().padding(32.dp))
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = displayAlbums,
                        key = { it.actualAlbumId }
                    ) { album ->
                        SeriesAlbumCard(
                            album = album,
                            onClick = { onAlbumClick(album) }
                        )
                    }

                    if (hasMore) {
                        item(key = "series_album_more_tile") {
                            SeriesAlbumMoreTile(
                                onMoreClick = onViewMore,
                                modifier = Modifier.size(160.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesAlbumCard(
    album: AlbumData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    val isNoCover = MediaPipeline.Default.isPlaceholder(album.cover)

    Card(
        onClick = onClick,
        modifier = modifier.height(160.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Box(
            modifier = Modifier
                .height(160.dp)
                .then(if (isNoCover) Modifier.aspectRatio(17f / 24f) else Modifier)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (!isNoCover) {

                PoiAsyncImage(
                    path = album.cover,
                    category = MediaCategory.ALBUM,
                    purpose = ImagePurpose.CARD_STANDARD,
                    contentDescription = album.displayName,
                    modifier = Modifier.fillMaxSize(),
                    adaptiveRatio = true,
                    fallbackRatio = 1.0f
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(PoiIcons.Filled.PhotoLibrary),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            val praiseCount = album.praiseCount ?: 0L
            if (praiseCount > 0L) {
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                    shape = MaterialTheme.shapes.extraSmall,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            painter = painterResource(PoiIcons.Filled.ThumbUp),
                            contentDescription = "点赞",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = praiseCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesAlbumMoreTile(
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onMoreClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(PoiIcons.Filled.PhotoLibrary),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "查看更多",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun SeriesGoodsSection(
    goodsList: List<SeriesGoodsItem>,
    isLoading: Boolean,
    onGoodsClick: (SeriesGoodsItem) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLoading && goodsList.isEmpty()) return

    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "在售周边",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (isLoading && goodsList.isEmpty()) {
                PoiLoadingView(modifier = Modifier.fillMaxWidth().padding(32.dp))
            } else {
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    goodsList.forEach { goods ->
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            onClick = { onGoodsClick(goods) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(130.dp)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                ) {

                                    PoiAsyncImage(
                                        path = goods.cover,
                                        category = MediaCategory.HOBBY,
                                        purpose = ImagePurpose.CARD_STANDARD,
                                        contentDescription = goods.name,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    if (!goods.saleTypeStr.isNullOrBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(4.dp)
                                        ) {
                                            Text(
                                                text = goods.saleTypeStr,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onError,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = goods.name ?: "",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 14.sp
                                )

                                if (!goods.price.isNullOrBlank()) {
                                    Text(
                                        text = goods.price,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SeriesFloatingActionBar(
    collectCount: Int,
    isCollected: Boolean,
    onCollectClick: () -> Unit,
    commentCount: Int,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    showCommentButton: Boolean = true
) {
    ExpressiveFloatingToolbar(modifier = modifier) {

        ExpressiveToolbarButton(
            onClick = onCollectClick,
            text = if (collectCount > 0) "$collectCount" else null,
            icon = if (isCollected) PoiIcons.Filled.Favorite else PoiIcons.Outlined.FavoriteBorder,
            contentDescription = if (isCollected) "已收藏" else "收藏",
            isProminent = isCollected,
            containerColor = if (isCollected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (isCollected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            iconTint = if (isCollected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (showCommentButton) {
            ExpressiveToolbarButton(
                onClick = onCommentClick,
                text = "${commentCount.coerceAtLeast(0)}",
                icon = PoiIcons.AutoMirrored.Filled.Comment,
                contentDescription = "评论",

                isProminent = false
            )
        }

        ExpressiveToolbarButton(
            onClick = onShareClick,
            icon = PoiIcons.Filled.Share,
            contentDescription = "分享",
            isProminent = false
        )
    }
}

@Composable
internal fun SeriesErrorState(
    message: String,
    onRetry: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "⚠️",
                    fontSize = 32.sp
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRetry,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("重试")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeriesInfoBottomSheet(
    series: SeriesData?,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val uriHandler = LocalUriHandler.current
    val messages = LocalAppMessages.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.bottomSheet
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "系列详细信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        painter = painterResource(PoiIcons.Filled.Close),
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    val nameCN = series?.nameCN?.ifBlank { null }
                    if (!nameCN.isNullOrBlank()) {
                        SeriesInfoRow(
                            label = "中文名",
                            value = nameCN
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }

                    val name = series?.name?.ifBlank { null }
                    if (!name.isNullOrBlank()) {
                        SeriesInfoRow(
                            label = "外文名",
                            value = name
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }

                    val website = series?.website?.trim().orEmpty()
                    val isNoneWebsite = website.isBlank() ||
                        website.equals("暂无", ignoreCase = true) ||
                        website.equals("无", ignoreCase = true) ||
                        website.equals("null", ignoreCase = true)
                    SeriesInfoRow(
                        label = "官网",
                        value = if (isNoneWebsite) "暂无" else website,
                        isLink = !isNoneWebsite,
                        onClick = if (!isNoneWebsite) {
                            {
                                val targetUrl = if (website.startsWith("http://", ignoreCase = true) || website.startsWith("https://", ignoreCase = true)) {
                                    website
                                } else {
                                    "https://$website"
                                }
                                try {
                                    uriHandler.openUri(targetUrl)
                                } catch (_: Exception) {
                                    messages.show("无法打开官网链接")
                                }
                            }
                        } else null
                    )

                    val otherItems = series?.parsedOtherInfo.orEmpty().filter { !it.value.isNullOrBlank() }
                    if (otherItems.isNotEmpty()) {
                        otherItems.forEach { item ->
                            val key = item.key.orEmpty()
                            val value = item.value.orEmpty().trim()
                            val isNoneValue = value.isBlank() ||
                                value.equals("暂无", ignoreCase = true) ||
                                value.equals("无", ignoreCase = true) ||
                                value.equals("null", ignoreCase = true) ||
                                value.equals("none", ignoreCase = true)
                            val isTwitter = key.contains("推特", ignoreCase = true) || key.contains("twitter", ignoreCase = true)
                            val isUrl = !isNoneValue && (
                                value.startsWith("http://", ignoreCase = true) ||
                                value.startsWith("https://", ignoreCase = true) ||
                                isTwitter
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SeriesInfoRow(
                                label = key,
                                value = when {
                                    isNoneValue -> "暂无"
                                    isTwitter && !value.startsWith("http", ignoreCase = true) -> "点击进入"
                                    else -> value
                                },
                                isLink = isUrl,
                                onClick = if (isUrl) {
                                    {
                                        val targetUrl = when {
                                            value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true) -> value
                                            isTwitter -> {
                                                val handle = value.removePrefix("@")
                                                "https://x.com/$handle"
                                            }
                                            else -> "https://$value"
                                        }
                                        try {
                                            uriHandler.openUri(targetUrl)
                                        } catch (_: Exception) {
                                            messages.show("无法打开链接")
                                        }
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            val detailText = series?.detailPlainText.orEmpty()
            if (detailText.isNotBlank()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "详细介绍",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = detailText,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesInfoRow(
    label: String,
    value: String,
    isLink: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(68.dp)
        )

        val isNone = value == "暂无"
        if (isLink && !isNone && onClick != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
            ) {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "↗",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Text(
                text = value,
                fontSize = 13.sp,
                color = if (isNone) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
