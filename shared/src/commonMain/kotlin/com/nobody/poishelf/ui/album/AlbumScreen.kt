package com.nobody.poishelf.ui.album

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import com.nobody.poishelf.ui.home.components.ShimmerHost
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nobody.poishelf.ui.common.rememberSynchronousTopAppBarScrollBehavior
import kotlin.math.roundToInt
import com.nobody.poishelf.platform.LocalAppMessages
import org.koin.compose.viewmodel.koinViewModel
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.poishelf.data.remote.model.AlbumOrder
import com.nobody.poishelf.ui.album.components.AlbumFeedCard
import com.nobody.poishelf.ui.home.components.shimmerBackground
import com.nobody.poishelf.ui.theme.ExpressiveTokens

import kotlinx.coroutines.launch
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    onNavigateToAlbum: (AlbumNavParam) -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onNavigateToRankList: (AlbumRankListNavParam) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    val gridState = rememberLazyStaggeredGridState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // 实测高度仅用于折叠行程与位移，内容边距取估高与实测的最大值：实测更大时补足避免遮盖，更小或相等时零额外重组
    var tabsHeightPx by remember { mutableFloatStateOf(with(density) { 48.dp.toPx() }) }
    var pillsHeightPx by remember { mutableFloatStateOf(with(density) { 48.dp.toPx() }) }
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val tabsEstimatedDp = 48.dp
    val pillsEstimatedDp = 48.dp
    val estimatedHeaderDp = tabsEstimatedDp + pillsEstimatedDp
    val measuredHeaderDp = with(density) { (tabsHeightPx + pillsHeightPx).toDp() }
    val totalHeaderHeight = statusBarTopPadding + if (measuredHeaderDp > estimatedHeaderDp) measuredHeaderDp else estimatedHeaderDp

    val scrollBehavior = rememberSynchronousTopAppBarScrollBehavior(
        initialHeightOffsetLimit = -pillsHeightPx,
        canScroll = { if (uiState.showRanking) uiState.sections.isNotEmpty() else uiState.items.isNotEmpty() }
    )

    val reachEnd by remember {
        derivedStateOf {
            val totalCount = gridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalCount > 0 && lastVisibleIndex >= totalCount - 3
        }
    }
    var restored by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadInitial()
    }
    LaunchedEffect(reachEnd, uiState.items.size) {
        if (reachEnd && uiState.items.isNotEmpty()) viewModel.loadMore()
    }
    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.collect { (currentIndex, currentOffset) ->
            if (restored) {
                viewModel.saveScrollPosition(currentIndex, currentOffset)
            }
        }
    }
    LaunchedEffect(uiState.selectedTab, uiState.order, uiState.showRanking) {
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
        gridState.scrollToItem(0)
    }
    LaunchedEffect(uiState.items.isNotEmpty()) {
        if (!restored && uiState.items.isNotEmpty()) {
            restored = true
            if (viewModel.savedScrollIndex != 0 || viewModel.savedScrollOffset != 0) {
                gridState.scrollToItem(viewModel.savedScrollIndex, viewModel.savedScrollOffset)
            }
        }
    }

    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val isCompact = !adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomContentPadding = if (isCompact) 16.dp else (16.dp + navBarBottomPadding)
    val pullRefreshState = rememberPullToRefreshState()
    val isAnyRefreshing = uiState.isRefreshing || (uiState.showRanking && uiState.isSectionsLoading && uiState.sections.isEmpty())

    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isAnyRefreshing) {
        if (wasRefreshing && !isAnyRefreshing && uiState.errorMessage == null) {
            gridState.scrollToItem(0)
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
        wasRefreshing = isAnyRefreshing
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PullToRefreshBox(
            isRefreshing = isAnyRefreshing,
            onRefresh = {
                scope.launch {
                    gridState.scrollToItem(0)
                    scrollBehavior.state.heightOffset = 0f
                    scrollBehavior.state.contentOffset = 0f
                }
                viewModel.refresh()
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = isAnyRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = totalHeaderHeight)
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            ) {
                val handleAlbumClick = remember(onNavigateToAlbum) {
                { item: com.nobody.poishelf.data.remote.model.AlbumListItem ->
                    if (item.targetAlbumId > 0) {
                        onNavigateToAlbum(
                            AlbumNavParam(
                                albumId = item.targetAlbumId,
                                initialTitle = item.displayName.ifBlank { null },
                                initialCover = item.cover
                            )
                        )
                    }
                }
            }

            when {
                uiState.showRanking -> RankingSections(
                    uiState = uiState,
                    onAlbumClick = handleAlbumClick,
                    onMoreClick = onNavigateToRankList,
                    onRetry = viewModel::retry,
                    topContentPadding = totalHeaderHeight,
                    bottomContentPadding = bottomContentPadding
                )
                uiState.isLoading && uiState.items.isEmpty() -> ShimmerHost {
                    AlbumFeedSkeleton(
                    topContentPadding = totalHeaderHeight,
                    bottomContentPadding = bottomContentPadding
                    )
                }
                uiState.loginRequired -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = totalHeaderHeight),
                    contentAlignment = Alignment.Center
                ) {
                    AlbumLoginGuide(onLogin = onNavigateToLogin)
                }
                uiState.errorMessage != null && uiState.items.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = totalHeaderHeight),
                    contentAlignment = Alignment.Center
                ) {
                    AlbumErrorState(
                        errorMessage = uiState.errorMessage ?: "加载失败",
                        onRetry = viewModel::retry
                    )
                }
                uiState.isFollowEmpty -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = totalHeaderHeight),
                    contentAlignment = Alignment.Center
                ) {
                    AlbumEmptyState(
                        title = "还没有关注",
                        hint = "去看看感兴趣的分区吧"
                    )
                }
                else -> AlbumFeedGrid(
                    uiState = uiState,
                    gridState = gridState,
                    onAlbumClick = handleAlbumClick,
                    topContentPadding = totalHeaderHeight,
                    bottomContentPadding = bottomContentPadding
                )
            }
        }
    }

        // 顶部操作区结构：
        // 1. 底层：AlbumOrderPills，位于 ZoneTabs 下方，通过 Y 轴平移收进 ZoneTabs 底下
        // 实测高度仅用于位移锚点与折叠上限，不回灌内容边距
        val tabsHeightDp = statusBarTopPadding + with(density) { tabsHeightPx.toDp() }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = tabsHeightDp)
                .offset { IntOffset(x = 0, y = scrollBehavior.state.heightOffset.roundToInt()) }
                .onSizeChanged { size ->
                    pillsHeightPx = size.height.toFloat()
                    scrollBehavior.state.heightOffsetLimit = -pillsHeightPx
                }
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AlbumOrderPills(
                uiState = uiState,
                onSelectOrder = viewModel::selectOrder,
                onEnterRanking = viewModel::enterRanking
            )
        }

        // 2. 顶层：AlbumZoneTabs 常驻顶端，Surface 遮盖上浮的 OrderPills
        AlbumZoneTabs(
            uiState = uiState,
            onSelectZone = viewModel::selectZone,
            onBrowseZones = {
                messages.show("分区管理敬请期待")
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    val rowHeight = size.height - with(density) { statusBarTopPadding.roundToPx() }
                    tabsHeightPx = rowHeight.coerceAtLeast(0).toFloat()
                }
        )

        AnimatedVisibility(
            visible = !uiState.showRanking,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (isCompact) 16.dp else (24.dp + navBarBottomPadding))
        ) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        gridState.scrollToItem(0)
                        scrollBehavior.state.heightOffset = 0f
                        viewModel.refresh()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "回到顶部并刷新"
                )
            }
        }
    }
}

@Composable
private fun AlbumZoneTabs(
    uiState: AlbumUiState,
    onSelectZone: (com.nobody.poishelf.data.remote.model.AlbumZoneTab) -> Unit,
    onBrowseZones: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            val selectedIndex = uiState.tabs.indexOf(uiState.selectedTab).coerceAtLeast(0)
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedIndex,
                edgePadding = 8.dp,
                modifier = Modifier.weight(1f)
            ) {
                uiState.tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { onSelectZone(tab) },
                        text = {
                            Text(
                                text = tab.name,
                                fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }
            IconButton(onClick = onBrowseZones) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "分区管理"
                )
            }
        }
    }
}

@Composable
private fun AlbumOrderPills(
    uiState: AlbumUiState,
    onSelectOrder: (AlbumOrder) -> Unit,
    onEnterRanking: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        AlbumOrder.entries.forEach { order ->
            FilterChip(
                selected = !uiState.showRanking && uiState.order == order,
                onClick = { onSelectOrder(order) },
                label = { Text(text = order.label) }
            )
        }
        FilterChip(
            selected = uiState.showRanking,
            onClick = onEnterRanking,
            label = { Text(text = "排行榜") }
        )
    }
}

/** 相册信息流网格。 */
@Composable
private fun AlbumFeedGrid(
    uiState: AlbumUiState,
    gridState: androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState,
    onAlbumClick: (com.nobody.poishelf.data.remote.model.AlbumListItem) -> Unit,
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 16.dp
) {
    val showTime = !uiState.showRanking && uiState.order == AlbumOrder.Latest
    LazyVerticalStaggeredGrid(
        columns = rememberAlbumStaggeredGridColumns(),
        state = gridState,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = topContentPadding + 8.dp, bottom = bottomContentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = uiState.items,
            key = { it.targetAlbumId },
            contentType = { "album_feed" }
        ) { item ->
            AlbumFeedCard(
                item = item,
                showTime = showTime,
                onClick = onAlbumClick
            )
        }
        if (uiState.isAppending) {
            item(span = StaggeredGridItemSpan.FullLine, contentType = "album_feed_footer") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun RankingSections(
    uiState: AlbumUiState,
    onAlbumClick: (com.nobody.poishelf.data.remote.model.AlbumListItem) -> Unit,
    onMoreClick: (AlbumRankListNavParam) -> Unit,
    onRetry: () -> Unit,
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 16.dp
) {
    if (uiState.isSectionsLoading && uiState.sections.isEmpty()) {
        ShimmerHost { AlbumFeedSkeleton(topContentPadding = topContentPadding, bottomContentPadding = bottomContentPadding) }
        return
    }
    if (uiState.sections.isEmpty()) {
        if (uiState.errorMessage != null) {
            AlbumErrorState(
                errorMessage = uiState.errorMessage ?: "加载失败",
                onRetry = onRetry
            )
        } else {
            AlbumEmptyState()
        }
        return
    }
    val rankingWindowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val rowSize = when {
        rankingWindowSizeClass.isWidthAtLeastBreakpoint(1400) -> 5
        rankingWindowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) -> 4
        rankingWindowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) -> 3
        else -> 2
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = topContentPadding + 8.dp, bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        uiState.sections.forEach { section ->
            item(key = "ranking_${section.title}") {
                Column {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.Center)
                        )
                        TextButton(
                            onClick = { onMoreClick(section.moreParam) },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Text(text = "查看更多")
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    val visibleAlbums = section.albums.take(10)
                    visibleAlbums.chunked(rowSize).forEachIndexed { rowIndex, rowItems ->
                        if (rowIndex > 0) Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowItems.forEachIndexed { colIndex, item ->
                                AlbumFeedCard(
                                    item = item,
                                    rank = rowIndex * rowSize + colIndex + 1,
                                    onClick = onAlbumClick,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(rowSize - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        if (uiState.isSectionsLoading) {
            item(key = "ranking_loading") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun AlbumFeedSkeleton(
    topContentPadding: Dp = 0.dp,
    bottomContentPadding: Dp = 16.dp
) {
    // 相册骨架按固定类型复用，避免与正式卡片混用复用池
    LazyVerticalStaggeredGrid(
        columns = rememberAlbumStaggeredGridColumns(),
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = topContentPadding + 8.dp, bottom = bottomContentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            6,
            key = { index -> "album_skeleton_$index" },
            contentType = { "album_skeleton" }
        ) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((160 + (index % 3) * 40).dp)
                    .shimmerBackground()
            )
        }
    }
}

@Composable
private fun AlbumEmptyState(
    title: String = "还没有相册",
    hint: String = "换个分区或排序看看"
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlbumErrorState(errorMessage: String, onRetry: () -> Unit) {
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
            Button(onClick = onRetry) {
                Text(text = "重试")
            }
        }
    }
}

@Composable
private fun AlbumLoginGuide(onLogin: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "登录后查看关注分区",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onLogin) {
                Text(text = "去登录")
            }
        }
    }
}
