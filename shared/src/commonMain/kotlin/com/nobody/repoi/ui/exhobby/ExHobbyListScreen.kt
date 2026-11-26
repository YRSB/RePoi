package com.nobody.repoi.ui.exhobby

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import com.nobody.repoi.ui.common.PoiPullToRefreshBox
import androidx.compose.runtime.Composable
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.remote.model.ExHobbyImageItem
import com.nobody.repoi.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.PoiAsyncImage
import com.nobody.repoi.ui.navigation.ExHobbyListNavKey
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExHobbyListScreen(
    navParam: ExHobbyListNavKey,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExHobbyListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(navParam.itemId) {
        viewModel.loadInitial(navParam.itemId)
    }

    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val allFullUrls = remember(uiState.items) {
        uiState.items.map {
            MediaPipeline.Default.resolveUrl(it.path, MediaCategory.EX_HOBBY, ImagePurpose.CARD_STANDARD)
        }
    }

    val imageViewer = LocalImageViewer.current

    LaunchedEffect(allFullUrls) {
        imageViewer.updateImages(allFullUrls)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {

            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = navParam.title.ifBlank { "ExHobby 扩展图库" },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState.items.isNotEmpty()) {
                            Text(
                                text = "已载入 ${uiState.items.size} 张",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
            onRefresh = { viewModel.refresh(navParam.itemId) },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(PaddingValues(top = innerPadding.calculateTopPadding()))
        ) {
            when {
                uiState.isLoading && uiState.items.isEmpty() -> {
                    PoiLoadingView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    )
                }
                uiState.errorMessage != null && uiState.items.isEmpty() -> {
                    ExHobbyListError(
                        errorMessage = uiState.errorMessage ?: "加载失败，请重试",
                        onRetry = { viewModel.retry(navParam.itemId) }
                    )
                }
                uiState.items.isEmpty() -> {
                    ExHobbyListEmpty()
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
                        if (shouldLoadMore && uiState.hasMore && !uiState.isAppending) {
                            viewModel.loadMore(navParam.itemId)
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
                        itemsIndexed(
                            items = uiState.items,
                            key = { index, item -> "ex_${item.path}_$index" },
                            contentType = { _, _ -> "exhobby_grid" }
                        ) { index, item ->
                            ExHobbyGridCard(
                                item = item,
                                index = index,
                                onClick = {
                                    imageViewer.show(
                                        images = allFullUrls,
                                        initialIndex = index.coerceIn(0, allFullUrls.size - 1),
                                        onLoadMore = { viewModel.loadMoreSilently(navParam.itemId) }
                                    )
                                }
                            )
                        }

                        if (uiState.isAppending || uiState.loadMoreError != null || !uiState.hasMore) {
                            item(
                                span = StaggeredGridItemSpan.FullLine,
                                key = "footer_state",
                                contentType = "exhobby_footer"
                            ) {
                                ExHobbyListFooter(
                                    isAppending = uiState.isAppending,
                                    loadMoreError = uiState.loadMoreError,
                                    hasMore = uiState.hasMore,
                                    onRetry = { viewModel.loadMore(navParam.itemId) }
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
private fun ExHobbyGridCard(
    item: ExHobbyImageItem,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {

            PoiAsyncImage(
                path = item.path,
                category = MediaCategory.EX_HOBBY,
                purpose = ImagePurpose.CARD_STANDARD,
                contentDescription = "ExHobby 图片 ${index + 1}",
                modifier = Modifier.fillMaxWidth(),
                adaptiveRatio = true,
                fallbackRatio = 1.0f
            )
        }
    }
}

@Composable
private fun ExHobbyListFooter(
    isAppending: Boolean,
    loadMoreError: String?,
    hasMore: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isAppending -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LoadingIndicator(
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "正在加载更多图片...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            loadMoreError != null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = loadMoreError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(onClick = onRetry) {
                        Icon(
                            painter = painterResource(PoiIcons.Refresh),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "重试", fontSize = 12.sp)
                    }
                }
            }
            !hasMore -> {
                Text(
                    text = "已经到底啦，没有更多扩展图片了",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun ExHobbyListEmpty(
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
                painter = painterResource(PoiIcons.Image),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无 ExHobby 扩展图库记录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "该条目尚未收录第三方扩展插画与相片",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ExHobbyListError(
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
                painter = painterResource(PoiIcons.Warning),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "加载失败",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onRetry) {
                Icon(painter = painterResource(PoiIcons.Refresh), contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "点击重试")
            }
        }
    }
}
