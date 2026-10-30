package com.nobody.repoi.ui.user.creation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.repoi.data.remote.model.UserCollectAlbumItem
import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.repoi.ui.collect.components.CollectAlbumCard
import com.nobody.repoi.ui.collect.components.EmptyCollectView
import com.nobody.repoi.ui.collect.components.ErrorCollectView
import com.nobody.repoi.ui.collect.components.LoadingCollectView
import com.nobody.repoi.ui.collect.subpage.UserCollectPicCard
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.navigation.UserCreationNavParam
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.util.CdnUrlUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserCreationScreen(
    param: UserCreationNavParam,
    onNavigateBack: () -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    viewModel: UserCreationViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(param) {
        viewModel.initParam(param)
    }

    val initialIndex = remember(param.initialCategory) {
        UserCreationCategory.entries.indexOfFirst { it.key == param.initialCategory }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialIndex) {
        UserCreationCategory.entries.size
    }

    // 单向驱动 Pager 滚动：ViewModel 选中项变更且完成初始化后，联动 Pager
    LaunchedEffect(uiState.selectedCategory, uiState.isInitialized) {
        if (uiState.isInitialized) {
            val targetIndex = uiState.selectedCategory.ordinal
            if (pagerState.currentPage != targetIndex) {
                pagerState.animateScrollToPage(targetIndex)
            }
        }
    }

    // 用户滑动手势完成后同步给 ViewModel
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress, uiState.isInitialized) {
        if (uiState.isInitialized && !pagerState.isScrollInProgress && pagerState.currentPage != uiState.selectedCategory.ordinal) {
            viewModel.switchCategory(UserCreationCategory.entries[pagerState.currentPage])
        }
    }

    val imageViewer = LocalImageViewer.current

    // 获取当前图片列表所有原始 CDN 路径
    val rawImageUrls = remember(uiState.picState.items) {
        uiState.picState.items.mapNotNull { item ->
            item.actualUrl.takeIf { it.isNotBlank() }?.let { CdnUrlUtils.getRawPicUrl(it) }
        }
    }

    // 当列表追加新图片时，若画廊正处于打开状态，同步更新画廊图片列表
    LaunchedEffect(rawImageUrls) {
        imageViewer.updateImages(rawImageUrls)
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                TopAppBar(
                    title = {
                        Text(
                            text = if (uiState.isSelf) "我的发布" else "Ta的发布",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // 一级分类 TabRow
                PrimaryTabRow(
                    selectedTabIndex = uiState.selectedCategory.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    UserCreationCategory.entries.forEachIndexed { index, category ->
                        Tab(
                            selected = uiState.selectedCategory.ordinal == index,
                            onClick = { viewModel.switchCategory(category) },
                            text = {
                                Text(
                                    text = category.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (uiState.selectedCategory.ordinal == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(PaddingValues(top = innerPadding.calculateTopPadding()))
        ) { pageIndex ->
            when (UserCreationCategory.entries[pageIndex]) {
                UserCreationCategory.ALBUM -> {
                    CreationSubPageContent(
                        tabState = uiState.albumState,
                        emptyMessage = "暂无发布的相册",
                        onRefresh = { viewModel.refresh(UserCreationCategory.ALBUM) },
                        onLoadMore = { viewModel.loadNextPage(UserCreationCategory.ALBUM) },
                        onSwitchOrder = { order -> viewModel.switchOrder(UserCreationCategory.ALBUM, order) },
                        onRetry = { viewModel.retry(UserCreationCategory.ALBUM) },
                        itemKey = { _, item -> "album_${item.actualAlbumId}_${item.id}" },
                        itemContentType = { _, _ -> "album_item" },
                        footerKey = "album_footer"
                    ) { _, album ->
                        CollectAlbumCard(
                            item = album,
                            currentOrder = uiState.albumState.order,
                            isSelected = false,
                            isSelectionMode = false,
                            onClick = { onNavigateToAlbum(album.actualAlbumId) },
                            onLongClick = {}
                        )
                    }
                }
                UserCreationCategory.PICTURE -> {
                    CreationSubPageContent(
                        tabState = uiState.picState,
                        emptyMessage = "暂无发布的图片",
                        onRefresh = { viewModel.refresh(UserCreationCategory.PICTURE) },
                        onLoadMore = { viewModel.loadNextPage(UserCreationCategory.PICTURE) },
                        onSwitchOrder = { order -> viewModel.switchOrder(UserCreationCategory.PICTURE, order) },
                        onRetry = { viewModel.retry(UserCreationCategory.PICTURE) },
                        itemKey = { _, item -> "pic_${item.actualId}_${item.actualUrl}" },
                        itemContentType = { _, _ -> "pic_item" },
                        footerKey = "pic_footer"
                    ) { index, item ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            UserCollectPicCard(
                                item = item,
                                onClick = {
                                    imageViewer.show(
                                        images = rawImageUrls,
                                        initialIndex = index,
                                        onLoadMore = {
                                            viewModel.loadNextPage(UserCreationCategory.PICTURE)
                                        }
                                    )
                                }
                            )
                            item.relateItemTitle?.let { title ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                                    shape = ExpressiveTokens.ChipShape,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

/**
 * 用户发布二级分类（相册/图片）通用瀑布流内容容器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> CreationSubPageContent(
    tabState: CreationTabState<T>,
    emptyMessage: String,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSwitchOrder: (String) -> Unit,
    onRetry: () -> Unit,
    itemKey: (index: Int, item: T) -> Any,
    itemContentType: (index: Int, item: T) -> Any? = { _, _ -> null },
    footerKey: String,
    modifier: Modifier = Modifier,
    itemContent: @Composable (index: Int, item: T) -> Unit
) {
    val gridState = rememberLazyStaggeredGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && tabState.hasMore && !tabState.isPaging && !tabState.isLoading && !tabState.isRefreshing) {
            onLoadMore()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        CreationOrderBar(
            currentOrder = tabState.order,
            onSelectOrder = onSwitchOrder
        )

        PullToRefreshBox(
            isRefreshing = tabState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                tabState.isLoading && tabState.items.isEmpty() -> {
                    LoadingCollectView()
                }
                tabState.errorMessage != null && tabState.items.isEmpty() -> {
                    ErrorCollectView(errorMessage = tabState.errorMessage, onRetry = onRetry)
                }
                tabState.items.isEmpty() -> {
                    EmptyCollectView(title = emptyMessage)
                }
                else -> {
                    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyVerticalStaggeredGrid(
                        columns = rememberAlbumStaggeredGridColumns(),
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            top = 8.dp,
                            end = 8.dp,
                            bottom = 16.dp + navBarBottom
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 8.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(WindowInsets.navigationBars)
                    ) {
                        itemsIndexed(
                            items = tabState.items,
                            key = itemKey,
                            contentType = itemContentType
                        ) { index, item ->
                            itemContent(index, item)
                        }

                        item(
                            span = StaggeredGridItemSpan.FullLine,
                            key = footerKey,
                            contentType = "${footerKey}_type"
                        ) {
                            FooterIndicator(
                                isPaging = tabState.isPaging,
                                hasMore = tabState.hasMore,
                                errorMessage = tabState.errorMessage,
                                onRetry = onLoadMore
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 二级排序栏（最新发布 / 最多喜欢）
 */
@Composable
private fun CreationOrderBar(
    currentOrder: String,
    onSelectOrder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserCreationOrder.entries.forEach { order ->
            val selected = currentOrder == order.key
            FilterChip(
                selected = selected,
                onClick = { onSelectOrder(order.key) },
                label = {
                    Text(
                        text = order.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                shape = ExpressiveTokens.PillShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

/**
 * 列表触底加载更多指示器
 */
@Composable
private fun FooterIndicator(
    isPaging: Boolean,
    hasMore: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit
) {
    when {
        isPaging -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        errorMessage != null -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${errorMessage}，点击重试",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onRetry)
                )
            }
        }
        !hasMore -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "没有更多了",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
