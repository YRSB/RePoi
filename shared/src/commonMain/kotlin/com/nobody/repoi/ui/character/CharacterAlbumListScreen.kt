package com.nobody.repoi.ui.character

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import com.nobody.repoi.ui.common.PoiPullToRefreshBox
import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.remote.model.AlbumData
import com.nobody.repoi.ui.album.components.AlbumTextCover
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.repoi.ui.common.PoiAsyncImage
import com.nobody.repoi.ui.navigation.CharacterAlbumListNavParam
import androidx.compose.material3.ElevatedCard
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterAlbumListScreen(
    param: CharacterAlbumListNavParam,
    onBack: () -> Unit,
    onNavigateToAlbum: (AlbumNavParam) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CharacterAlbumListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(param) {
        viewModel.loadInitial(param)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = uiState.title.ifBlank { param.title.ifBlank { "相关相册" } },
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
        }
    ) { innerPadding ->
        PoiPullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
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
                    CharacterAlbumListError(
                        errorMessage = uiState.errorMessage ?: "加载失败",
                        onRetry = { viewModel.retry() }
                    )
                }
                uiState.albums.isEmpty() -> {
                    CharacterAlbumListEmpty()
                }
                    else -> {
                        val staggeredGridState = rememberLazyStaggeredGridState()

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
                                key = { it.actualAlbumId },
                                contentType = { "character_album_item" }
                            ) { album ->
                                CharacterAlbumStaggeredCard(
                                    album = album,
                                    onClick = {
                                        onNavigateToAlbum(
                                            AlbumNavParam(
                                                albumId = album.actualAlbumId,
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
private fun CharacterAlbumStaggeredCard(
    album: AlbumData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    val isNoCover = MediaPipeline.Default.isPlaceholder(album.cover)

    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isNoCover) Modifier.aspectRatio(17f / 24f) else Modifier)
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    text = album.displayName.ifBlank { "未命名相册" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
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
            }
        }
    }
}

@Composable
private fun CharacterAlbumListError(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                painter = painterResource(PoiIcons.BrokenImage),
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
            Button(
                onClick = onRetry,
                shape = CircleShape
            ) {
                Icon(
                    painter = painterResource(PoiIcons.Refresh),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(text = "重新加载")
            }
        }
    }
}

@Composable
private fun CharacterAlbumListEmpty(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                painter = painterResource(PoiIcons.PhotoAlbum),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "暂无相关相册",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
