package com.nobody.repoi.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import com.nobody.repoi.ui.home.components.ShimmerHost
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import coil3.compose.AsyncImage
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.ui.album.components.formatAlbumTime
import com.nobody.repoi.ui.home.components.shimmerBackground
import com.nobody.repoi.ui.theme.ExpressiveCard
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.util.CdnUrlUtils
import org.koin.compose.viewmodel.koinViewModel

internal fun levelTier(level: Int): Int = when {
    level <= 0 -> 0
    level <= 5 -> 1
    else -> 2
}

internal fun userLevelOf(hp: Int, atk: Int, def: Int): Int =
    kotlin.math.floor(kotlin.math.cbrt(((hp * 3) + (atk * 2) + (def * 3)) / 100.0)).toInt()

internal fun displayLevelOf(level: Int, hp: Int, atk: Int, def: Int): Int =
    if (level > 0) level else userLevelOf(hp, atk, def)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumRankListScreen(
    param: AlbumRankListNavParam,
    onBack: () -> Unit,
    onNavigateToAlbum: (AlbumNavParam) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumRankListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reachEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
        }
    }

    LaunchedEffect(param) {
        viewModel.loadInitial(param)
    }
    LaunchedEffect(reachEnd, uiState.items.size) {
        if (reachEnd && uiState.items.isNotEmpty()) viewModel.loadMore(param)
    }

    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isRefreshing) {
        if (wasRefreshing && !uiState.isRefreshing && uiState.errorMessage == null) {
            listState.scrollToItem(0)
        }
        wasRefreshing = uiState.isRefreshing
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = uiState.title.ifBlank { param.title.ifBlank { "相册周榜" } },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                scope.launch {
                    listState.scrollToItem(0)
                }
                viewModel.refresh(param)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(PaddingValues(top = innerPadding.calculateTopPadding()))
        ) {
            when {
                uiState.isLoading && uiState.items.isEmpty() -> ShimmerHost { RankListSkeleton(navBarBottomPadding = innerPadding.calculateBottomPadding()) }
                uiState.errorMessage != null && uiState.items.isEmpty() -> RankListError(
                    errorMessage = uiState.errorMessage ?: "加载失败",
                    onRetry = { viewModel.retry(param) }
                )
                uiState.items.isEmpty() -> RankListEmpty()
                else -> {
                    val isWideList = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
                    val rowSize = if (isWideList) 2 else 1
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 16.dp + innerPadding.calculateBottomPadding()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(PaddingValues(bottom = innerPadding.calculateBottomPadding()))
                    ) {
                        if (uiState.period != null) {
                            item(key = "rank_period") {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Surface(
                                        shape = ExpressiveTokens.PillShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { viewModel.shiftPeriod(param, -1) },
                                                enabled = !uiState.isLoading
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                                    contentDescription = "上一期"
                                                )
                                            }
                                            Text(
                                                text = uiState.periodLabel ?: "",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .widthIn(min = 72.dp)
                                                    .semantics { heading() }
                                            )
                                            IconButton(
                                                onClick = { viewModel.shiftPeriod(param, 1) },
                                                enabled = !uiState.isLoading && uiState.diff < 0
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = "下一期"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        uiState.items.chunked(rowSize).forEach { rowItems ->
                            item(key = "rank_row_${rowItems.joinToString(separator = "_") { it.targetAlbumId.toString() }}") {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowItems.forEach { item ->
                                        RankRow(
                                            item = item,
                                            rank = uiState.ranks[item.targetAlbumId] ?: 0,
                                            onClick = {
                                                if (item.targetAlbumId > 0) {
                                                    onNavigateToAlbum(
                                                        AlbumNavParam(
                                                            albumId = item.targetAlbumId,
                                                            initialTitle = item.displayName.ifBlank { null },
                                                            initialCover = item.cover
                                                        )
                                                    )
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(rowSize - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                        if (uiState.isAppending || uiState.loadMoreError != null || !uiState.hasMore) {
                            item(key = "rank_appending") {
                                RankListFooter(
                                    isLoadingMore = uiState.isAppending,
                                    loadMoreError = uiState.loadMoreError,
                                    isEndReached = !uiState.hasMore && uiState.loadMoreError == null,
                                    onRetry = { viewModel.loadMore(param) }
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
private fun RankRow(item: AlbumListItem, rank: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExpressiveCard(
        onClick = onClick,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
        RankMedal(rank = rank)
        Spacer(modifier = Modifier.width(10.dp))
        val coverUrl = CdnUrlUtils.getCoverUrl(item.cover)
        if (item.hasCover && coverUrl.isNotBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = item.displayName.ifBlank { "相册封面" },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 72.dp, height = 96.dp)
                    .clip(ExpressiveTokens.SmallCardShape)
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(width = 72.dp, height = 96.dp)
                    .clip(ExpressiveTokens.SmallCardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(4.dp)
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName.ifBlank { "相册" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${item.praiseCountValue}点赞",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val avatarUrl = CdnUrlUtils.getHeadUrl(item.userAvatar)
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
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
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = item.userNickname,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(4.dp))
                LevelBadge(level = item.user?.let { displayLevelOf(it.level, it.hp, it.atk, it.def) } ?: 0)
                formatAlbumTime(item.addTime)?.let { timeText ->
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

@Composable
private fun RankMedal(rank: Int) {
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
        modifier = Modifier
            .size(28.dp)
            .semantics(mergeDescendants = true) { contentDescription = "第${rank}名" }
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

@Composable
private fun LevelBadge(level: Int) {
    val containerColor = when (levelTier(level)) {
        2 -> MaterialTheme.colorScheme.primaryContainer
        1 -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when (levelTier(level)) {
        2 -> MaterialTheme.colorScheme.onPrimaryContainer
        1 -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = ExpressiveTokens.ExtraSmallShape,
        color = containerColor
    ) {
        Text(
            text = "Lv.$level",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun RankListSkeleton(
    navBarBottomPadding: Dp = 0.dp
) {
    val isWideSkeleton = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val skeletonRowSize = if (isWideSkeleton) 2 else 1
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 16.dp + navBarBottomPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize()
    ) {
        (0 until 8).chunked(skeletonRowSize).forEachIndexed { rowIndex, rowItems ->
            item(key = "rank_skeleton_$rowIndex") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowItems.forEach { _ ->
                        ExpressiveCard(
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .shimmerBackground()
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier
                                        .size(width = 72.dp, height = 96.dp)
                                        .clip(ExpressiveTokens.SmallCardShape)
                                        .shimmerBackground()
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.7f)
                                            .height(16.dp)
                                            .clip(ExpressiveTokens.ExtraSmallShape)
                                            .shimmerBackground()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.4f)
                                            .height(12.dp)
                                            .clip(ExpressiveTokens.ExtraSmallShape)
                                            .shimmerBackground()
                                    )
                                }
                            }
                        }
                    }
                    repeat(skeletonRowSize - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RankListEmpty() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "还没有上榜相册",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "换个周期再来看看",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RankListError(errorMessage: String, onRetry: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "加载失败",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRetry,
                shape = ExpressiveTokens.ButtonShape
            ) {
                Text(text = "重试")
            }
        }
    }
}

@Composable
private fun RankListFooter(
    isLoadingMore: Boolean,
    loadMoreError: String?,
    isEndReached: Boolean,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoadingMore -> {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier
                        .size(24.dp)
                        .semantics { contentDescription = "正在加载更多" }
                )
            }
            loadMoreError != null -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = loadMoreError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onRetry) {
                        Text(
                            text = "点击重试",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            isEndReached -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        text = "已显示全部",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}
