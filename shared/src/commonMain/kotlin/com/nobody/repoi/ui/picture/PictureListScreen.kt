package com.nobody.repoi.ui.picture

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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.remote.model.PicItem
import com.nobody.repoi.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.PoiAsyncImage
import com.nobody.repoi.ui.navigation.PictureListNavParam
import androidx.compose.foundation.shape.CircleShape
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PictureListScreen(
    navParam: PictureListNavParam,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PictureListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(navParam) {
        viewModel.loadInitial(navParam)
    }

    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val allRawUrls = remember(uiState.items) {
        uiState.items.mapNotNull { pic ->
            pic.imagePath
                ?.let { MediaPipeline.Default.resolvePictureUrl(it, ImagePurpose.FULLSCREEN_RAW) }
                ?.takeIf { it.isNotBlank() }
        }
    }

    val imageViewer = LocalImageViewer.current

    LaunchedEffect(allRawUrls) {
        imageViewer.updateImages(allRawUrls)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {

            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = navParam.title.ifBlank { "图片列表" },
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
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh(navParam) },
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
                    PictureListError(
                        errorMessage = uiState.errorMessage ?: "加载失败",
                        onRetry = { viewModel.retry(navParam) }
                    )
                }
                uiState.items.isEmpty() -> {
                    PictureListEmpty()
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
                            viewModel.loadMore(navParam)
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
                            key = { index, pic ->
                                if (pic.id != 0L) "pic_${pic.id}_$index" else "pic_${pic.imagePath}_$index"
                            },
                            contentType = { _, _ -> "picture_grid" }
                        ) { index, pic ->
                            PictureGridCard(
                                pic = pic,
                                index = index,
                                onClick = {

                                    val rawUrl = pic.imagePath
                                        ?.let {
                                            MediaPipeline.Default.resolvePictureUrl(it, ImagePurpose.FULLSCREEN_RAW)
                                        }
                                        ?.takeIf { it.isNotBlank() }
                                    val targetIndex = if (rawUrl != null) {
                                        allRawUrls.indexOf(rawUrl).takeIf { it >= 0 }
                                            ?: index.coerceIn(0, allRawUrls.size - 1)
                                    } else {
                                        index.coerceIn(0, allRawUrls.size - 1)
                                    }
                                    if (allRawUrls.isNotEmpty()) {
                                        imageViewer.show(
                                            images = allRawUrls,
                                            initialIndex = targetIndex,
                                            onLoadMore = { viewModel.loadMoreSilently(navParam) }
                                        )
                                    }
                                }
                            )
                        }

                        if (uiState.isAppending || uiState.loadMoreError != null || !uiState.hasMore) {
                            item(
                                span = StaggeredGridItemSpan.FullLine,
                                key = "footer_state",
                                contentType = "picture_footer"
                            ) {
                                PictureListFooter(
                                    isAppending = uiState.isAppending,
                                    loadMoreError = uiState.loadMoreError,
                                    hasMore = uiState.hasMore,
                                    onRetry = { viewModel.loadMore(navParam) }
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
private fun PictureGridCard(
    pic: PicItem,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    val thumbnailUrl = remember(pic.imagePath) {
        MediaPipeline.Default.resolvePictureUrl(pic.imagePath, ImagePurpose.CARD_STANDARD)
    }

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
                model = thumbnailUrl,
                contentDescription = "第 ${index + 1} 张图片",
                modifier = Modifier.fillMaxWidth(),
                adaptiveRatio = true,
                ratioKey = pic.imagePath,
                fallbackRatio = pic.aspectRatio ?: 1.0f
            )
        }
    }
}

@Composable
private fun PictureListFooter(
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
                    Text(
                        text = "正在加载更多图片...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            loadMoreError != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = loadMoreError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Button(
                        onClick = onRetry,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(text = "重试", fontSize = 12.sp)
                    }
                }
            }
            !hasMore -> {
                Text(
                    text = "已加载全部图片 (´▽`ʃ♡ƪ)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun PictureListError(
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
private fun PictureListEmpty(
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
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "暂无相关图片",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
