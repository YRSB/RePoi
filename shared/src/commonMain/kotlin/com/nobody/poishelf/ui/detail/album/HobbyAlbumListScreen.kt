package com.nobody.poishelf.ui.detail.album

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import com.nobody.poishelf.ui.home.components.ShimmerHost
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nobody.poishelf.data.remote.model.HobbyAlbumItem
import com.nobody.poishelf.ui.album.AlbumNavParam
import com.nobody.poishelf.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.poishelf.ui.common.listImageKey
import com.nobody.poishelf.ui.common.rememberListImageRequest
import com.nobody.poishelf.ui.home.components.shimmerBackground
import com.nobody.poishelf.ui.navigation.HobbyAlbumListNavParam
import com.nobody.poishelf.ui.theme.ExpressiveElevatedCard
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.util.CdnUrlUtils
import com.nobody.poishelf.util.ImageAspectRatioCache
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * 手办专属相关相册列表页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HobbyAlbumListScreen(
    param: HobbyAlbumListNavParam,
    onBack: () -> Unit,
    onNavigateToAlbum: (AlbumNavParam) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HobbyAlbumListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(param) {
        viewModel.loadInitial(param)
    }

    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val coroutineScope = rememberCoroutineScope()
    val staggeredGridState = rememberLazyStaggeredGridState()

    // 切换排序 Tab 时重置滚动位置至顶部
    LaunchedEffect(uiState.selectedOrder) {
        staggeredGridState.scrollToItem(0)
    }

    // 首次载入或排序切换后首屏数据到达时，确保停留在顶部
    LaunchedEffect(uiState.selectedOrder, uiState.albums.firstOrNull()?.id) {
        if (uiState.page == 1 && uiState.albums.isNotEmpty()) {
            staggeredGridState.scrollToItem(0)
        }
    }

    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isRefreshing) {
        if (wasRefreshing && !uiState.isRefreshing && uiState.errorMessage == null) {
            staggeredGridState.scrollToItem(0)
        }
        wasRefreshing = uiState.isRefreshing
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = uiState.title.ifBlank { "关联相册" },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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

                // 排序 Tab 栏（最热、最新、最多图）
                PrimaryTabRow(
                    selectedTabIndex = uiState.selectedOrder.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HobbyAlbumSortOrder.entries.forEach { sortOrder ->
                        val selected = uiState.selectedOrder == sortOrder
                        Tab(
                            selected = selected,
                            onClick = {
                                if (selected) {
                                    coroutineScope.launch {
                                        staggeredGridState.animateScrollToItem(0)
                                    }
                                } else {
                                    viewModel.selectOrder(sortOrder)
                                    coroutineScope.launch {
                                        staggeredGridState.scrollToItem(0)
                                    }
                                }
                            },
                            text = {
                                Text(
                                    text = sortOrder.title,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    staggeredGridState.scrollToItem(0)
                }
                viewModel.refresh()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(PaddingValues(top = innerPadding.calculateTopPadding()))
        ) {
            Crossfade(
                targetState = Triple(
                    uiState.isInitialLoading && uiState.albums.isEmpty(),
                    uiState.errorMessage != null && uiState.albums.isEmpty(),
                    uiState.albums.isEmpty()
                ),
                label = "HobbyAlbumListStateCrossfade"
            ) { (isLoading, isError, isEmpty) ->
                when {
                    isLoading -> {
                        ShimmerHost { HobbyAlbumListSkeleton(navBarBottomPadding = innerPadding.calculateBottomPadding()) }
                    }
                    isError -> {
                        HobbyAlbumListError(
                            errorMessage = uiState.errorMessage ?: "加载失败",
                            onRetry = { viewModel.retry() }
                        )
                    }
                    isEmpty -> {
                        HobbyAlbumListEmpty()
                    }
                    else -> {
                        // 触底加载更多监听
                        val shouldLoadMore by remember {
                            derivedStateOf {
                                val layoutInfo = staggeredGridState.layoutInfo
                                val total = layoutInfo.totalItemsCount
                                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                total > 4 && lastVisible >= total - 4
                            }
                        }

                        LaunchedEffect(shouldLoadMore) {
                            if (shouldLoadMore && uiState.hasMore && !uiState.isLoadingMore) {
                                viewModel.loadMore()
                            }
                        }

                        LazyVerticalStaggeredGrid(
                            columns = rememberAlbumStaggeredGridColumns(),
                            state = staggeredGridState,
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                top = 8.dp,
                                end = 8.dp,
                                bottom = 24.dp + innerPadding.calculateBottomPadding()
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalItemSpacing = 8.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .consumeWindowInsets(PaddingValues(bottom = innerPadding.calculateBottomPadding()))
                        ) {
                            items(
                                items = uiState.albums,
                                key = { it.id },
                                contentType = { "hobby_album_item" }
                            ) { album ->
                                HobbyAlbumStaggeredCard(
                                    album = album,
                                    onClick = {
                                        onNavigateToAlbum(
                                            AlbumNavParam(
                                                albumId = album.id,
                                                initialTitle = album.displayName,
                                                initialCover = album.cover
                                            )
                                        )
                                    }
                                )
                            }

                            if (uiState.isLoadingMore) {
                                item(
                                    span = StaggeredGridItemSpan.FullLine,
                                    contentType = "album_load_more_footer"
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 2.5.dp
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

/**
 * 手办相册卡片（自适应长宽比瀑布流）。
 */
@Composable
private fun HobbyAlbumStaggeredCard(
    album: HobbyAlbumItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = CdnUrlUtils.getCoverSrUrl(album.cover)
    val ratioKey = listImageKey(album.cover, coverUrl, "sr")
    val coverRequest = rememberListImageRequest(album.cover, coverUrl, "sr:waterfall")
    var aspectRatio by remember(ratioKey) {
        mutableFloatStateOf(ImageAspectRatioCache[ratioKey] ?: 1.0f)
    }

    ExpressiveElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveTokens.SmallCardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .clip(ExpressiveTokens.SmallCardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverRequest,
                        contentDescription = album.displayName,
                        contentScale = ContentScale.Crop,
                        onSuccess = { state ->
                            val size = state.painter.intrinsicSize
                            if (size.width > 0 && size.height > 0) {
                                val ratio = (size.width / size.height).coerceIn(0.6f, 2.0f)
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
                            imageVector = Icons.Filled.BrokenImage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = album.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 左侧：头像 + 昵称
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val avatarUrl = CdnUrlUtils.getHeadSmallUrl(album.user?.header)
                        val avatarRequest = rememberListImageRequest(album.user?.header, avatarUrl, "avatar")
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {
                            if (avatarUrl.isNotBlank()) {
                                AsyncImage(
                                    model = avatarRequest,
                                    contentDescription = album.userNickname,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Text(
                            text = album.userNickname,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 右侧：点赞图标 + 点赞数
                    val praiseCount = album.praiseCount ?: 0L
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ThumbUp,
                            contentDescription = "点赞",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = praiseCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 手办相册列表骨架屏。
 */
@Composable
private fun HobbyAlbumListSkeleton(
    navBarBottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    LazyVerticalStaggeredGrid(
        columns = rememberAlbumStaggeredGridColumns(),
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 24.dp + navBarBottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        userScrollEnabled = false,
        modifier = modifier.fillMaxSize()
    ) {
        items(6) { index ->
            val ratio = when (index % 3) {
                0 -> 0.75f
                1 -> 1.2f
                else -> 0.9f
            }
            ExpressiveElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ExpressiveTokens.SmallCardShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio)
                            .shimmerBackground()
                    )
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(14.dp)
                                .clip(ExpressiveTokens.ExtraSmallShape)
                                .shimmerBackground()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .shimmerBackground()
                                )
                                Box(
                                    modifier = Modifier
                                        .size(width = 40.dp, height = 10.dp)
                                        .clip(ExpressiveTokens.ExtraSmallShape)
                                        .shimmerBackground()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(width = 24.dp, height = 10.dp)
                                    .clip(ExpressiveTokens.ExtraSmallShape)
                                    .shimmerBackground()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 手办相册列表加载失败页面。
 */
@Composable
private fun HobbyAlbumListError(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.BrokenImage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text("重试")
        }
    }
}

/**
 * 手办相册列表空状态。
 */
@Composable
private fun HobbyAlbumListEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.PhotoAlbum,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "暂无相关相册",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
