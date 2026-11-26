package com.nobody.repoi.ui.detail.album

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
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import com.nobody.repoi.ui.common.PoiPullToRefreshBox
import androidx.compose.runtime.Composable
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.remote.model.HobbyAlbumItem
import com.nobody.repoi.ui.album.components.AlbumTextCover
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.repoi.ui.common.PoiAsyncImage
import com.nobody.repoi.ui.navigation.HobbyAlbumListNavParam
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.material3.ElevatedCard
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

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

    LaunchedEffect(uiState.selectedOrder) {
        staggeredGridState.scrollToItem(0)
    }

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
                                painter = painterResource(PoiIcons.AutoMirrored.Filled.ArrowBack),
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

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
        PoiPullToRefreshBox(
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
            when {
                uiState.isInitialLoading && uiState.albums.isEmpty() -> {
                    PoiLoadingView()
                }
                uiState.errorMessage != null && uiState.albums.isEmpty() -> {
                    HobbyAlbumListError(
                        errorMessage = uiState.errorMessage ?: "加载失败",
                        onRetry = { viewModel.retry() }
                    )
                }
                uiState.albums.isEmpty() -> {
                    HobbyAlbumListEmpty()
                }
                else -> {

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
                                        LoadingIndicator(
                                            modifier = Modifier.size(28.dp)
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
private fun HobbyAlbumStaggeredCard(
    album: HobbyAlbumItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    val isNoCover = MediaPipeline.Default.isPlaceholder(album.cover)

    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()

                    .then(if (isNoCover) Modifier.aspectRatio(17f / 24f) else Modifier)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                if (isNoCover) {
                    AlbumTextCover(
                        title = album.displayName,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {

                    PoiAsyncImage(
                        path = album.cover,
                        category = MediaCategory.ALBUM,
                        purpose = ImagePurpose.ADAPTIVE_GRID,
                        contentDescription = album.displayName,
                        modifier = Modifier.fillMaxWidth(),
                        adaptiveRatio = true,
                        fallbackRatio = 1.0f
                    )
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

                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center
                        ) {

                            PoiAsyncImage(
                                path = album.user?.header,
                                category = MediaCategory.AVATAR,
                                purpose = ImagePurpose.THUMBNAIL,
                                contentDescription = album.userNickname,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text(
                            text = album.userNickname,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val praiseCount = album.praiseCount ?: 0L
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            painter = painterResource(PoiIcons.Filled.ThumbUp),
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
            painter = painterResource(PoiIcons.Filled.BrokenImage),
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
                painter = painterResource(PoiIcons.Filled.Refresh),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text("重试")
        }
    }
}

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
            painter = painterResource(PoiIcons.Filled.PhotoAlbum),
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
