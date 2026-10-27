package com.nobody.repoi.ui.picture

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nobody.repoi.data.remote.model.PicItem
import com.nobody.repoi.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.listImageKey
import com.nobody.repoi.ui.common.rememberListImageRequest
import com.nobody.repoi.ui.navigation.PictureListNavParam
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.ui.home.components.shimmerBackground
import com.nobody.repoi.util.CdnUrlUtils
import com.nobody.repoi.util.ImageAspectRatioCache
import org.koin.compose.viewmodel.koinViewModel

/**
 * 通用图片列表/全集页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PictureListScreen(
    navParam: PictureListNavParam,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PictureListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(navParam) {
        viewModel.loadInitial(navParam)
    }

    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 提取所有高清原图链接，供全屏画廊查看
    val allRawUrls = remember(uiState.items) {
        uiState.items.mapNotNull { it.imagePath?.let { path -> CdnUrlUtils.getRawPicUrl(path) } }
    }

    val imageViewer = LocalImageViewer.current

    // 当列表静默追加新图片时，若画廊正处于打开状态，同步更新画廊图片列表
    LaunchedEffect(allRawUrls) {
        imageViewer.updateImages(allRawUrls)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // 极简顶栏：返回键 + 居中标题 + 下拉刷新
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
            onRefresh = { viewModel.refresh(navParam) },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(PaddingValues(top = innerPadding.calculateTopPadding()))
        ) {
            when {
                uiState.isLoading && uiState.items.isEmpty() -> {
                    ShimmerHost { PictureListSkeleton(navBarBottomPadding = innerPadding.calculateBottomPadding()) }
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
                        // 瀑布流图片按图片类型复用，避免异构条目互相污染复用池
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
                                    val rawUrl = pic.imagePath?.let { CdnUrlUtils.getRawPicUrl(it) }
                                    val targetIndex = if (rawUrl != null) {
                                        allRawUrls.indexOf(rawUrl).takeIf { it >= 0 } ?: index.coerceIn(0, allRawUrls.size - 1)
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

                        // 底部状态指示
                        if (uiState.isAppending || uiState.loadMoreError != null || !uiState.hasMore) {
                            item(span = StaggeredGridItemSpan.FullLine, key = "footer_state") {
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

/**
 * 极简瀑布流相片卡片项（对齐相册页自适应纵横比与 ImageAspectRatioCache 策略，100% 对齐 Hpoi 原版使用 /pic/n/ 规格展示）。
 */
@Composable
private fun PictureGridCard(
    pic: PicItem,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageUrl = remember(pic.imagePath) {
        pic.imagePath?.let { CdnUrlUtils.getPicNUrl(it) }.orEmpty()
    }
    val ratioKey = listImageKey(pic.imagePath, imageUrl, "n")
    val initialRatio = pic.aspectRatio ?: ImageAspectRatioCache[ratioKey] ?: 1.0f
    var aspectRatio by remember(ratioKey) { mutableFloatStateOf(initialRatio) }
    val imageRequest = rememberListImageRequest(pic.imagePath, imageUrl, "n:waterfall")

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
                    model = imageRequest,
                    contentDescription = "第 ${index + 1} 张图片",
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
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
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
                        shape = ExpressiveTokens.ButtonShape,
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

/**
 * 瀑布流骨架屏闪烁占位（对齐相册页自适应列数策略）。
 */
@Composable
private fun PictureListSkeleton(
    navBarBottomPadding: androidx.compose.ui.unit.Dp,
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
        items(6) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((180 + (index % 3) * 50).dp)
                    .clip(ExpressiveTokens.MediumCardShape)
                    .shimmerBackground()
            )
        }
    }
}

/**
 * 错误重试占位界面。
 */
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
                imageVector = Icons.Default.BrokenImage,
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
                shape = ExpressiveTokens.ButtonShape
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(text = "重新加载")
            }
        }
    }
}

/**
 * 空数据占位界面。
 */
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
                imageVector = Icons.Default.Image,
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
