package com.nobody.repoi.ui.series

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Share
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
import com.nobody.repoi.ui.home.components.ShimmerHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.nobody.repoi.platform.PlatformActions
import org.koin.compose.koinInject
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nobody.repoi.data.remote.model.AlbumData
import com.nobody.repoi.data.remote.model.RelatedHobbyItem
import com.nobody.repoi.data.remote.model.SeriesData
import com.nobody.repoi.util.ImageAspectRatioCache
import com.nobody.repoi.data.remote.model.SeriesForumPayload
import com.nobody.repoi.data.remote.model.SeriesGoodsItem
import com.nobody.repoi.data.remote.model.SeriesLinkItem
import com.nobody.repoi.ui.home.components.shimmerBackground
import com.nobody.repoi.ui.theme.ExpressiveElevation
import com.nobody.repoi.ui.theme.ExpressiveElevatedCard
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.util.CdnUrlUtils



// ─────────────────────────────────────────────────────────────
// 主体展台与基础信息卡 (SeriesHeaderSection)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun SeriesHeaderSection(
    series: SeriesData,
    onShowDetailInfo: () -> Unit = {},
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val coverUrl = CdnUrlUtils.getRawCoverUrl(series.cover)
    val displayName = series.displayName.ifBlank { "系列详情" }
    val subName = series.subName
    val introText = series.detailPlainText

    ExpressiveElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 上半部分：Logo 封面 + 中英文标题 + 胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // 系列 Logo/封面 (86.dp, 16.dp圆角)
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                ) {
                    if (!coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 右侧：中文标题 / 主名 + 外文原名 + 属性胶囊
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

                    // 胶囊列表：周边款数、收藏人数
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        if (series.hobbyCount > 0) {
                            Surface(
                                shape = ExpressiveTokens.PillShape,
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
                                shape = ExpressiveTokens.PillShape,
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

            // 中部：系列简介（默认全展开，点击可查看系列详情抽屉）
            if (introText.isNotBlank()) {
                Surface(
                    shape = ExpressiveTokens.MediumCardShape,
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

// ─────────────────────────────────────────────────────────────
// 周边板块 (SeriesHobbysSection) - 3x3 纯图九宫格
// ─────────────────────────────────────────────────────────────

@Composable
internal fun SeriesHobbysSection(
    hobbys: List<RelatedHobbyItem>,
    isLoading: Boolean,
    onHobbyClick: (RelatedHobbyItem) -> Unit,
    onViewMore: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    ExpressiveElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题栏
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
                            .clip(ExpressiveTokens.SmallCardShape)
                            .clickable { onViewMore() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // 内容九宫格
            if (isLoading && hobbys.isEmpty()) {
                ShimmerHost { SeriesGridSkeleton() }
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
                        shape = ExpressiveTokens.MediumCardShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { onHobbyClick(item) }
                    ) {
                        if (!item.cover.isNullOrBlank()) {
                            AsyncImage(
                                model = CdnUrlUtils.getRawCoverUrl(item.cover),
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 子系列板块 (SeriesSubSeriesSection)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun SeriesSubSeriesSection(
    subSeriesList: List<SeriesLinkItem>,
    isLoading: Boolean,
    onSubSeriesClick: (SeriesLinkItem) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLoading && subSeriesList.isEmpty()) return

    ExpressiveElevatedCard(
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
                ShimmerHost {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(ExpressiveTokens.CardShape)
                            .shimmerBackground()
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    subSeriesList.forEach { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveTokens.CardShape,
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
                                        .clip(ExpressiveTokens.SmallCardShape)
                                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                ) {
                                    if (!item.cover.isNullOrBlank()) {
                                        AsyncImage(
                                            model = CdnUrlUtils.getRawCoverUrl(item.cover),
                                            contentDescription = item.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
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

// ─────────────────────────────────────────────────────────────
// 相关系列板块 (SeriesRelateSeriesSection)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun SeriesRelateSeriesSection(
    relateSeriesList: List<SeriesLinkItem>,
    isLoading: Boolean,
    onRelateSeriesClick: (SeriesLinkItem) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLoading && relateSeriesList.isEmpty()) return

    ExpressiveElevatedCard(
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
                ShimmerHost {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(ExpressiveTokens.CardShape)
                            .shimmerBackground()
                    )
                }
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
                            shape = ExpressiveTokens.CardShape,
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
                                        .clip(ExpressiveTokens.SmallCardShape)
                                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                ) {
                                    if (!item.cover.isNullOrBlank()) {
                                        AsyncImage(
                                            model = CdnUrlUtils.getRawCoverUrl(item.cover),
                                            contentDescription = item.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
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

// ─────────────────────────────────────────────────────────────
// 关联相册板块 (SeriesAlbumsSection) - 对标手办详情与角色相册流
// ─────────────────────────────────────────────────────────────

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

    ExpressiveElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 标题栏：左侧标题 + 右侧查看更多
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
                            .clip(ExpressiveTokens.SmallCardShape)
                            .clickable { onViewMore() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            if (isLoading && albums.isEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(4) { index ->
                        val shimmerWidth = when (index % 3) {
                            0 -> 140.dp
                            1 -> 160.dp
                            else -> 120.dp
                        }
                        ShimmerHost {
                            Box(
                                modifier = Modifier
                                    .height(160.dp)
                                    .width(shimmerWidth)
                                    .clip(ExpressiveTokens.MediumCardShape)
                                    .shimmerBackground()
                            )
                        }
                    }
                }
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
    val coverUrl = remember(album.cover) {
        album.cover?.let { CdnUrlUtils.getCoverSrUrl(it) }.orEmpty()
    }
    val ratioKey = remember(coverUrl) { "album_cover:$coverUrl" }
    val initialRatio = ImageAspectRatioCache[ratioKey] ?: 1.0f
    var aspectRatio by remember(ratioKey) { mutableFloatStateOf(initialRatio) }

    Card(
        onClick = onClick,
        modifier = modifier.height(160.dp),
        shape = ExpressiveTokens.MediumCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Box(
            modifier = Modifier
                .height(160.dp)
                .aspectRatio(aspectRatio)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = album.displayName,
                    contentScale = ContentScale.Crop,
                    onSuccess = { state ->
                        val size = state.painter.intrinsicSize
                        if (size.width > 0 && size.height > 0) {
                            val ratio = (size.width / size.height).coerceIn(0.6f, 2.2f)
                            aspectRatio = ratio
                            ImageAspectRatioCache[ratioKey] = ratio
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // 右上角点赞徽标
            val praiseCount = album.praiseCount ?: 0L
            if (praiseCount > 0L) {
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f),
                    shape = ExpressiveTokens.ExtraSmallShape,
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
                            imageVector = Icons.Filled.ThumbUp,
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

/**
 * 系列相关相册「查看更多」卡片（等高且正方形比例，样式与手办/角色保持一致）。
 */
@Composable
private fun SeriesAlbumMoreTile(
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onMoreClick,
        modifier = modifier,
        shape = ExpressiveTokens.MediumCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
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

// ─────────────────────────────────────────────────────────────
// ─────────────────────────────────────────────────────────────
// 在售周边板块 (SeriesGoodsSection)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun SeriesGoodsSection(
    goodsList: List<SeriesGoodsItem>,
    isLoading: Boolean,
    onGoodsClick: (SeriesGoodsItem) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLoading && goodsList.isEmpty()) return

    ExpressiveElevatedCard(
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
                ShimmerHost {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(ExpressiveTokens.CardShape)
                            .shimmerBackground()
                    )
                }
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
                            shape = ExpressiveTokens.CardShape,
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
                                        .clip(ExpressiveTokens.SmallCardShape)
                                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                ) {
                                    if (!goods.cover.isNullOrBlank()) {
                                        AsyncImage(
                                            model = goods.cover,
                                            contentDescription = goods.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

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

// ─────────────────────────────────────────────────────────────
// 底部悬浮操作栏 (SeriesFloatingActionBar)
// ─────────────────────────────────────────────────────────────

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
        // 1. 收藏（心形切换，只显示数字）
        ExpressiveToolbarButton(
            onClick = onCollectClick,
            text = if (collectCount > 0) "$collectCount" else null,
            icon = if (isCollected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isCollected) "已收藏" else "收藏",
            isProminent = isCollected,
            containerColor = if (isCollected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (isCollected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            iconTint = if (isCollected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 2. 评论（小屏显示，统一显示数字，没有则显示0；大屏隐藏）
        if (showCommentButton) {
            ExpressiveToolbarButton(
                onClick = onCommentClick,
                text = "${commentCount.coerceAtLeast(0)}",
                icon = Icons.AutoMirrored.Filled.Comment,
                contentDescription = "评论",
                isProminent = !isCollected
            )
        }

        // 3. 分享（次级操作，纯图标）
        ExpressiveToolbarButton(
            onClick = onShareClick,
            icon = Icons.Filled.Share,
            contentDescription = "分享",
            isProminent = false
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 骨架屏与错误占位 (SeriesDetailSkeleton & SeriesErrorState)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun SeriesDetailSkeleton(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 头部展台骨架
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(ExpressiveTokens.LargeCardShape)
                .shimmerBackground()
        )

        // 周边九宫格骨架
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(ExpressiveTokens.LargeCardShape)
                .shimmerBackground()
        )
    }
}

@Composable
private fun SeriesGridSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(0.8f)
                            .clip(ExpressiveTokens.MediumCardShape)
                            .shimmerBackground()
                    )
                }
            }
        }
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
        ExpressiveElevatedCard(
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
                    shape = ExpressiveTokens.ButtonShape,
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

// ─────────────────────────────────────────────────────────────
// 系列详细信息底部抽屉 (SeriesInfoBottomSheet)
// ─────────────────────────────────────────────────────────────

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
    val platformActions = koinInject<PlatformActions>()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = ExpressiveTokens.BottomSheetShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶栏：标题 + 关闭按钮
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
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 属性列表容器
            Surface(
                shape = ExpressiveTokens.MediumCardShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 中文主名
                    val nameCN = series?.nameCN?.ifBlank { null }
                    if (!nameCN.isNullOrBlank()) {
                        SeriesInfoRow(
                            label = "中文名",
                            value = nameCN
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }

                    // 外文名/日文名
                    val name = series?.name?.ifBlank { null }
                    if (!name.isNullOrBlank()) {
                        SeriesInfoRow(
                            label = "外文名",
                            value = name
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }

                    // 官网
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
                                    platformActions.openUrl(targetUrl)
                                } catch (_: Exception) {
                                    platformActions.showToast("无法打开官网链接")
                                }
                            }
                        } else null
                    )

                    // 扩展属性（如推特以及 otherInfo 中剩余项）
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
                                            platformActions.openUrl(targetUrl)
                                        } catch (_: Exception) {
                                            platformActions.showToast("无法打开链接")
                                        }
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            // 简介全文
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
                        shape = ExpressiveTokens.MediumCardShape,
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

