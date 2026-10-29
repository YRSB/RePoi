package com.nobody.poishelf.ui.character

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.FavoriteBorder
import com.nobody.poishelf.ui.common.ExpressiveFloatingToolbar
import com.nobody.poishelf.ui.common.ExpressiveToolbarButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.nobody.poishelf.ui.home.components.ShimmerHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nobody.poishelf.data.remote.model.AlbumData
import com.nobody.poishelf.data.remote.model.CharacterData
import com.nobody.poishelf.data.remote.model.CharacterPerformerItem
import com.nobody.poishelf.data.remote.model.CharacterRelevantItem
import com.nobody.poishelf.data.remote.model.CharacterWorksItem
import com.nobody.poishelf.data.remote.model.HobbyItemSummary
import com.nobody.poishelf.data.remote.model.ZoneItem
import com.nobody.poishelf.ui.common.AdaptiveChunkedGrid
import com.nobody.poishelf.ui.home.components.shimmerBackground
import com.nobody.poishelf.ui.theme.ExpressiveElevatedCard
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.util.CdnUrlUtils
import com.nobody.poishelf.util.ImageAspectRatioCache

// ─────────────────────────────────────────────────────────────
// 区块 0：角色档案综合头部 (CharacterHeaderSection)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CharacterHeaderSection(
    character: CharacterData?,
    initialName: String?,
    initialCover: String?,
    zones: List<ZoneItem>,
    isZonesLoading: Boolean,
    isIntroExpanded: Boolean,
    onToggleIntro: () -> Unit,
    onCoverClick: () -> Unit,
    onTagClick: (String) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val displayName = character?.displayName?.ifBlank { null } ?: initialName ?: "角色详情"
    val subName = character?.subName
    val cover = character?.cover?.ifBlank { null } ?: initialCover
    val coverUrl = CdnUrlUtils.getCoverUrl(cover)
    val intro = character?.detailPlainText.orEmpty()

    ExpressiveElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 封面与基础资料
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 角色封面（黄金比例微缩卡，点击可查看原图大图）
                Box(
                    modifier = Modifier
                        .width(105.dp)
                        .aspectRatio(9f / 13f)
                        .clip(ExpressiveTokens.MediumCardShape)
                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                        .clickable(onClick = onCoverClick)
                ) {
                    if (coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 角色标题与元信息徽章
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!subName.isNullOrBlank()) {
                        Text(
                            text = subName,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 性别与生日等属性
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        character?.let { c ->
                            if (c.sex != 0) {
                                Surface(
                                    shape = ExpressiveTokens.SmallCardShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "性别：${c.sexText}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                    )
                                }
                            }
                            if (!c.birthday.isNullOrBlank()) {
                                Surface(
                                    shape = ExpressiveTokens.SmallCardShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "生日：${c.birthday}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 统计行（热度与收藏）
                    if (character != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = "收藏 ${character.collect}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "·",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "评论 ${character.commentCount}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // 属性/分区标签列表
            if (zones.isNotEmpty() || isZonesLoading) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    zones.forEach { zone ->
                        AssistChip(
                            onClick = { onTagClick(zone.displayName) },
                            label = { Text(text = zone.displayName, fontSize = 12.sp) },
                            shape = ExpressiveTokens.ButtonShape,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    }
                }
            }

            // 角色背景与详细介绍（带平滑展开折叠）
            if (intro.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                ) {
                    Text(
                        text = intro,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isIntroExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (intro.length > 80 || intro.count { it == '\n' } >= 3) {
                        TextButton(
                            onClick = onToggleIntro,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = if (isIntroExpanded) "收起" else "展开全文",
                                fontSize = 12.sp,
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
// 区块 2：登场作品 (CharacterWorksSection)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun CharacterWorksSection(
    works: List<CharacterWorksItem>,
    isLoading: Boolean,
    onWorkClick: (CharacterWorksItem) -> Unit,
    onPersonClick: (Long, String, String?) -> Unit,
    onViewMore: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLoading && works.isEmpty()) return

    ExpressiveElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "登场作品",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onViewMore) {
                    Text(text = "查看更多", fontSize = 12.sp)
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isLoading && works.isEmpty()) {
                ShimmerHost { Box(modifier = Modifier.fillMaxWidth().height(80.dp).shimmerBackground()) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    works.take(5).forEach { work ->
                        val coverUrl = CdnUrlUtils.getCoverUrl(work.wCover)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveTokens.CardShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            onClick = { onWorkClick(work) }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(ExpressiveTokens.SmallCardShape)
                                            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                    ) {
                                        if (coverUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = coverUrl,
                                                contentDescription = work.displayName,
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
                                            text = work.displayName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        work.characterRoleText?.let { role ->
                                            Text(
                                                text = role,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        work.formattedReleaseDate?.let { date ->
                                            Text(
                                                text = "发售: $date",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // 演职员胶囊数量较少，用 Row 直铺避免懒加载开销
                                if (work.validPerformers.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        work.validPerformers.forEach { performer ->
                                            key(performer.personId) {
                                                PerformerPill(
                                                    performer = performer,
                                                    onClick = {
                                                        onPersonClick(
                                                            performer.personId,
                                                            performer.displayName,
                                                            performer.pCover
                                                        )
                                                    }
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
        }
    }
}

@Composable
private fun PerformerPill(
    performer: CharacterPerformerItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val avatarUrl = CdnUrlUtils.getCoverSmallUrl(performer.pCover)
    Surface(
        onClick = onClick,
        shape = ExpressiveTokens.ButtonShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Text(
                text = "${performer.careerLabel}：${performer.displayName}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 区块 3：周边手办 (CharacterHobbysSection)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun CharacterHobbysSection(
    hobbys: List<HobbyItemSummary>,
    isLoading: Boolean,
    onHobbyClick: (HobbyItemSummary) -> Unit,
    onViewMore: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLoading && hobbys.isEmpty()) return

    ExpressiveElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "周边手办",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onViewMore) {
                    Text(text = "查看更多", fontSize = 12.sp)
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isLoading && hobbys.isEmpty()) {
                ShimmerHost { Box(modifier = Modifier.fillMaxWidth().height(140.dp).shimmerBackground()) }
            } else {
                AdaptiveChunkedGrid(
                    items = hobbys.take(9),
                    maxRows = 3,
                    horizontalSpacing = 8.dp,
                    verticalSpacing = 8.dp
                ) { hobby ->
                    CharacterHobbyCard(
                        hobby = hobby,
                        onClick = { onHobbyClick(hobby) },
                        isDark = isDark
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterHobbyCard(
    hobby: HobbyItemSummary,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val coverUrl = CdnUrlUtils.getCoverUrl(hobby.cover)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveTokens.CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 13f)
                    .clip(ExpressiveTokens.SmallCardShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
            ) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = hobby.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = hobby.displayName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            val priceText = (hobby.price ?: hobby.moneyString)?.ifBlank { null }
            if (!priceText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = priceText,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 区块 4：相关相册 (CharacterAlbumsSection)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun CharacterAlbumsSection(
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
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "相关相册",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp)
            )

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
                        key = { it.actualAlbumId },
                        contentType = { "character_album" }
                    ) { album ->
                        CharacterAlbumCard(
                            album = album,
                            onClick = { onAlbumClick(album) }
                        )
                    }

                    if (hasMore) {
                        item(key = "character_album_more_tile") {
                            AlbumMoreTile(
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
private fun CharacterAlbumCard(
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
                    contentDescription = album.name,
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

            // 右上角半透明浮层点赞徽标（与相关相册页完全一致的 ExtraSmallShape 与 padding）
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
 * 角色相关相册「查看更多」卡片（等高且正方形比例，样式与手办 ExHobby 保持一致）。
 */
@Composable
private fun AlbumMoreTile(
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
// 区块 5：推荐要素 (CharacterRelevantSection)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun CharacterRelevantSection(
    characters: List<CharacterRelevantItem>,
    isLoading: Boolean,
    onCharacterClick: (CharacterRelevantItem) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLoading && characters.isEmpty()) return

    ExpressiveElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "推荐要素",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (isLoading && characters.isEmpty()) {
                ShimmerHost { Box(modifier = Modifier.fillMaxWidth().height(100.dp).shimmerBackground()) }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(
                        characters,
                        key = { it.actualItemId },
                        contentType = { "relevant_character" }
                    ) { item ->
                        CharacterRelevantCard(
                            item = item,
                            onClick = { onCharacterClick(item) },
                            isDark = isDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterRelevantCard(
    item: CharacterRelevantItem,
    onClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val coverUrl = CdnUrlUtils.getCoverSmallUrl(item.cover)
    Surface(
        modifier = modifier.width(108.dp),
        shape = ExpressiveTokens.CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(ExpressiveTokens.SmallCardShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
            ) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.displayName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            item.worksCharacter?.ifBlank { null }?.let { role ->
                Text(
                    text = role,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 骨架屏与错误重试态
// ─────────────────────────────────────────────────────────────

@Composable
internal fun CharacterDetailSkeleton(
    onBack: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        ExpressiveElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(105.dp)
                        .aspectRatio(9f / 13f)
                        .clip(ExpressiveTokens.MediumCardShape)
                        .shimmerBackground()
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth(0.7f).height(20.dp).shimmerBackground())
                    Box(modifier = Modifier.fillMaxWidth(0.5f).height(14.dp).shimmerBackground())
                    Box(modifier = Modifier.fillMaxWidth(0.9f).height(14.dp).shimmerBackground())
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(40.dp).shimmerBackground())
        Box(modifier = Modifier.fillMaxWidth().height(160.dp).shimmerBackground())
        Box(modifier = Modifier.fillMaxWidth().height(180.dp).shimmerBackground())
    }
}

@Composable
internal fun CharacterErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = errorMessage,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.error
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("重试")
                }
                TextButton(onClick = onBack) {
                    Text("返回")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 底部悬浮操作栏 (CharacterFloatingActionBar)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun CharacterFloatingActionBar(
    collectCount: Long,
    isCollected: Boolean,
    commentCount: Int,
    onToggleCollect: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    showCommentButton: Boolean = true
) {
    ExpressiveFloatingToolbar(modifier = modifier) {
        // 1. 收藏角色
        ExpressiveToolbarButton(
            onClick = onToggleCollect,
            text = if (collectCount > 0) "$collectCount" else null,
            icon = if (isCollected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isCollected) "已收藏" else "收藏",
            isProminent = isCollected,
            containerColor = if (isCollected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (isCollected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            iconTint = if (isCollected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 2. 评论主操作（小屏显示，统一显示数字，没有则显示0；大屏隐藏）
        if (showCommentButton) {
            ExpressiveToolbarButton(
                onClick = onCommentClick,
                text = "${commentCount.coerceAtLeast(0)}",
                icon = Icons.AutoMirrored.Filled.Comment,
                contentDescription = "评论",
                isProminent = !isCollected
            )
        }

        // 3. 分享次级操作
        ExpressiveToolbarButton(
            onClick = onShareClick,
            icon = Icons.Filled.Share,
            contentDescription = "分享",
            isProminent = false
        )
    }
}

