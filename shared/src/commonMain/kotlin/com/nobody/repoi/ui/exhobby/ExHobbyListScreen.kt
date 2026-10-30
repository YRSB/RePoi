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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import com.nobody.repoi.ui.home.components.ShimmerHost
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nobody.repoi.data.remote.model.ExHobbyImageItem
import com.nobody.repoi.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.home.components.shimmerBackground
import com.nobody.repoi.ui.navigation.ExHobbyListNavKey
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.util.CdnUrlUtils
import com.nobody.repoi.util.ImageAspectRatioCache
import org.koin.compose.viewmodel.koinViewModel

/**
 * ExHobby 扩展图库二级全量列表页面。
 */
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

    // 提取所有高清大图链接，供全屏画廊查看
    val allFullUrls = remember(uiState.items) {
        uiState.items.map { CdnUrlUtils.getExHobbyFullUrl(it.path) }
    }

    val imageViewer = LocalImageViewer.current

    // 当列表静默追加新图片时，若画廊正处于打开状态，同步更新画廊图片列表
    LaunchedEffect(allFullUrls) {
        imageViewer.updateImages(allFullUrls)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // 极简顶栏：返回键 + 居中标题与数量
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
            onRefresh = { viewModel.refresh(navParam.itemId) },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(PaddingValues(top = innerPadding.calculateTopPadding()))
        ) {
            when {
                uiState.isLoading && uiState.items.isEmpty() -> {
                    ShimmerHost { ExHobbyListSkeleton(navBarBottomPadding = innerPadding.calculateBottomPadding()) }
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
                            key = { index, item -> "ex_${item.path}_$index" }
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

                        // 底部加载状态指示
                        if (uiState.isAppending || uiState.loadMoreError != null || !uiState.hasMore) {
                            item(span = StaggeredGridItemSpan.FullLine, key = "footer_state") {
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

/**
 * 瀑布流扩展相片卡片项（自适应宽高比并缓存长宽比）。
 */
@Composable
private fun ExHobbyGridCard(
    item: ExHobbyImageItem,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 采用与全屏大图画廊一致的 /n/ 高清变体，使列表与查看器共享 Coil 缓存，实现点击卡片 0 延迟秒开
    val imageUrl = remember(item.path) {
        CdnUrlUtils.getExHobbyFullUrl(item.path)
    }
    val ratioKey = remember(item.path) { "exhobby:${item.path}" }
    val initialRatio = ImageAspectRatioCache[ratioKey] ?: 1.0f
    var aspectRatio by remember(ratioKey) { mutableFloatStateOf(initialRatio) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = ExpressiveTokens.MediumCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "ExHobby 图片 ${index + 1}",
                    contentScale = ContentScale.Crop,
                    onSuccess = { state ->
                        val size = state.painter.intrinsicSize
                        if (size.width > 0 && size.height > 0) {
                            val ratio = (size.width / size.height).coerceIn(0.4f, 2.5f)
                            aspectRatio = ratio
                            ImageAspectRatioCache[ratioKey] = ratio
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = "图片损坏",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * 瀑布流底部加载/提示区。
 */
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
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
                            imageVector = Icons.Default.Refresh,
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

/**
 * 骨架屏占位。
 */
@Composable
private fun ExHobbyListSkeleton(
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
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        val dummyHeights = listOf(180.dp, 240.dp, 160.dp, 220.dp, 200.dp, 190.dp)
        items(10) { index ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dummyHeights[index % dummyHeights.size]),
                shape = ExpressiveTokens.MediumCardShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .shimmerBackground()
                )
            }
        }
    }
}

/**
 * 空数据占位。
 */
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
                imageVector = Icons.Default.Image,
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

/**
 * 错误占位。
 */
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
                imageVector = Icons.Default.Warning,
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
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "点击重试")
            }
        }
    }
}
