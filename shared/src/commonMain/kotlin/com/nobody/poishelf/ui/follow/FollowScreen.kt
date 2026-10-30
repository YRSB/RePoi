package com.nobody.poishelf.ui.follow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import com.nobody.poishelf.ui.home.components.ShimmerHost
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.nobody.poishelf.ui.home.rememberFeedStaggeredGridColumns
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import kotlin.math.roundToInt
import com.nobody.poishelf.ui.common.rememberSynchronousTopAppBarScrollBehavior
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nobody.poishelf.platform.LocalAppMessages
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.poishelf.data.remote.model.ActionItem
import com.nobody.poishelf.ui.album.AlbumNavParam
import com.nobody.poishelf.ui.common.LocalImageViewer
import com.nobody.poishelf.ui.detail.DetailNavParam
import com.nobody.poishelf.ui.home.components.ActionCard
import com.nobody.poishelf.ui.home.components.ActionCardSkeleton
import com.nobody.poishelf.ui.navigation.CharacterNavParam
import com.nobody.poishelf.ui.person.PersonNavParam
import com.nobody.poishelf.ui.series.SeriesNavParam
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.ui.vendor.VendorNavParam
import com.nobody.poishelf.ui.works.WorksNavParam
import com.nobody.poishelf.util.FeedActionDispatcher
import kotlinx.coroutines.launch

/**
 * 关注屏（顶层关注目的地，承载关注条目/用户模块）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowScreen(
    onNavigateToDetail: ((DetailNavParam) -> Unit)? = null,
    onNavigateToAlbum: ((AlbumNavParam) -> Unit)? = null,
    onNavigateToPerson: ((PersonNavParam) -> Unit)? = null,
    onNavigateToWorks: ((WorksNavParam) -> Unit)? = null,
    onNavigateToVendor: ((VendorNavParam) -> Unit)? = null,
    onNavigateToCharacter: ((CharacterNavParam) -> Unit)? = null,
    onNavigateToSeries: ((SeriesNavParam) -> Unit)? = null,
    onNavigateToUser: ((Long) -> Unit)? = null,
    onNavigateToLogin: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: FollowViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val gridState = rememberLazyStaggeredGridState()
    val messages = LocalAppMessages.current
    val scope = rememberCoroutineScope()
    val imageViewer = LocalImageViewer.current

    LaunchedEffect(Unit) { viewModel.loadInitial() }

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalCount = gridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalCount > 0 && lastVisibleIndex >= totalCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, uiState.items.size) {
        if (shouldLoadMore) viewModel.loadMore()
    }
    val density = LocalDensity.current
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 实测高度用于折叠行程与位移，内容边距取估高与实测的最大值避免遮盖
    var tabRowHeightPx by remember { mutableFloatStateOf(with(density) { 48.dp.toPx() }) }
    var filterRowHeightPx by remember { mutableFloatStateOf(with(density) { 48.dp.toPx() }) }

    val isItemsTab = uiState.selectedTab == FollowTab.ITEMS

    // 无缝同步滚动控制器：仅在条目动态且有数据时允许折叠筛选栏
    val scrollBehavior = rememberSynchronousTopAppBarScrollBehavior(
        initialHeightOffsetLimit = -filterRowHeightPx,
        canScroll = { uiState.items.isNotEmpty() && isItemsTab }
    )

    LaunchedEffect(filterRowHeightPx, isItemsTab) {
        if (isItemsTab && filterRowHeightPx > 0f) {
            scrollBehavior.state.heightOffsetLimit = -filterRowHeightPx
        } else {
            scrollBehavior.state.heightOffsetLimit = 0f
            scrollBehavior.state.heightOffset = 0f
        }
    }

    // 首批数据到达前不记录，避免初始位置覆盖已保存位置
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(gridState) {
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            if (restored) viewModel.saveScrollPosition(index, offset)
        }
    }
    LaunchedEffect(uiState.selectedTab, uiState.selectedFilter) {
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

    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isRefreshing) {
        if (wasRefreshing && !uiState.isRefreshing && uiState.errorMessage == null) {
            gridState.scrollToItem(0)
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
        wasRefreshing = uiState.isRefreshing
    }

    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val isCompact = !adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomContentPadding = if (isCompact) 16.dp else (16.dp + navBarBottomPadding)
    val pullRefreshState = rememberPullToRefreshState()
    val showRefreshFab by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 3 && uiState.items.isNotEmpty() }
    }

    val measuredTabDp = with(density) { tabRowHeightPx.toDp() }
    val measuredFilterDp = with(density) { filterRowHeightPx.toDp() }
    val topPermanentHeightDp = statusBarTopPadding + if (measuredTabDp > 48.dp) measuredTabDp else 48.dp
    val totalHeaderHeight = if (isItemsTab) {
        topPermanentHeightDp + if (measuredFilterDp > 48.dp) measuredFilterDp else 48.dp
    } else {
        topPermanentHeightDp
    }
    val topContentPadding = totalHeaderHeight
    // 实测 tabRowHeightPx 仅用于下方折叠栏位移锚点，不回灌内容边距
    val feedColumns = rememberFeedStaggeredGridColumns()

    // 条目卡片点击分发（与首页信息流一致）
    val onActionClick: (ActionItem) -> Unit = remember(
        messages,
        onNavigateToDetail,
        onNavigateToAlbum,
        onNavigateToVendor,
        onNavigateToCharacter,
        onNavigateToPerson,
        onNavigateToWorks,
        onNavigateToSeries,
        onNavigateToUser
    ) {
        { item ->
            FeedActionDispatcher.dispatch(
                actionItem = item,
                onMessage = { messages.show(it) },
                onNavigateToDetail = onNavigateToDetail,
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToVendor = onNavigateToVendor,
                onNavigateToCharacter = onNavigateToCharacter,
                onNavigateToPerson = onNavigateToPerson,
                onNavigateToWorks = onNavigateToWorks,
                onNavigateToSeries = onNavigateToSeries,
                onNavigateToUser = onNavigateToUser
            )
        }
    }
    val onCardImageClick = remember(imageViewer) {
        { rawUrls: List<String>, initialIndex: Int ->
            imageViewer.show(rawUrls, initialIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. 底层：内容数据流（铺满全屏，受 topContentPadding 约束）
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
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
                    isRefreshing = uiState.isRefreshing,
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
                when {
                uiState.loginRequired -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topContentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    FollowLoginGuide(onLogin = { onNavigateToLogin?.invoke() })
                }
                uiState.isInitialLoading && uiState.items.isEmpty() -> ShimmerHost {
                    FollowSkeleton(
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = topContentPadding + 6.dp,
                        bottom = bottomContentPadding
                    )
                    )
                }
                uiState.errorMessage != null && uiState.items.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topContentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    FollowErrorState(
                        errorMessage = uiState.errorMessage ?: "加载失败",
                        onRetry = viewModel::retry
                    )
                }
                uiState.items.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topContentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    FollowEmptyState(selectedTab = uiState.selectedTab)
                }
                else -> LazyVerticalStaggeredGrid(
                    columns = feedColumns,
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = topContentPadding + 6.dp,
                        bottom = bottomContentPadding
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp
                ) {
                    items(
                        items = uiState.items,
                        key = { item -> "${item.id}_${item.actionDate}" }
                    ) { actionItem ->
                        ActionCard(
                            actionItem = actionItem,
                            onItemClick = onActionClick,
                            onUserClick = onNavigateToUser,
                            onImageClick = onCardImageClick
                        )
                    }
                    item(span = StaggeredGridItemSpan.FullLine) {
                        when {
                            uiState.isLoadingMore -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            uiState.loadMoreError != null -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.loadMore() }
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${uiState.loadMoreError}，点击重试 ↻",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            !uiState.hasMore && uiState.items.isNotEmpty() -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "—— 已经到底啦 (´･ω･`) ——",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

        // 2. 次级折叠筛选栏（仅 ITEMS 标签页存在，位于常驻顶栏下方，跟随手势平滑 tuck 进 TabRow 底下）
        // 实测高度仅用于位移锚点与折叠上限，不回灌内容边距
        if (isItemsTab) {
            val tabsHeightDp = statusBarTopPadding + with(density) { tabRowHeightPx.toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = tabsHeightDp)
                    .offset { IntOffset(0, scrollBehavior.state.heightOffset.roundToInt()) }
                    .onSizeChanged { size ->
                        if (size.height > 0) {
                            filterRowHeightPx = size.height.toFloat()
                            scrollBehavior.state.heightOffsetLimit = -filterRowHeightPx
                        }
                    }
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(viewModel.filters, key = { it.first }) { (key, label) ->
                        FilterChip(
                            selected = uiState.selectedFilter == key,
                            onClick = { viewModel.selectFilter(key) },
                            label = { Text(text = label) },
                            shape = ExpressiveTokens.ChipShape
                        )
                    }
                }
            }
        }

        // 3. 常驻顶栏（状态栏占位 + TabRow），位于 Z 轴最上层，Surface 遮盖上浮的筛选栏
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { size ->
                    val rowHeight = size.height - with(density) { statusBarTopPadding.roundToPx() }
                    tabRowHeightPx = rowHeight.coerceAtLeast(0).toFloat()
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                val tabIndex = if (isItemsTab) 0 else 1
                PrimaryTabRow(selectedTabIndex = tabIndex) {
                    FollowTab.entries.forEachIndexed { index, tab ->
                        Tab(
                            selected = tabIndex == index,
                            onClick = { viewModel.switchTab(tab) },
                            text = { Text(text = tab.label) }
                        )
                    }
                }
            }
        }

        // 4. 回到顶部 FAB
        AnimatedVisibility(
            visible = showRefreshFab,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (isCompact) 16.dp else (24.dp + navBarBottomPadding))
        ) {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        scrollBehavior.state.heightOffset = 0f
                        gridState.scrollToItem(0)
                        viewModel.refresh()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = "回到顶部并刷新")
            }
        }
    }
}

/**
 * 关注屏骨架屏（按固定类型复用，避免与正式卡片混用复用池）。
 */
@Composable
private fun FollowSkeleton(
    columns: StaggeredGridCells = rememberFeedStaggeredGridColumns(),
    contentPadding: PaddingValues = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)
) {
    LazyVerticalStaggeredGrid(
        columns = columns,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp
    ) {
        items(
            6,
            key = { index -> "follow_skeleton_$index" },
            contentType = { "follow_skeleton" }
        ) { ActionCardSkeleton() }
    }
}

@Composable
private fun FollowEmptyState(selectedTab: FollowTab) {
    // 按页签区分空状态，避免用户动态页误显示条目文案
    val title = if (selectedTab == FollowTab.USERS) "暂无用户动态" else "暂无条目动态"
    val hint = if (selectedTab == FollowTab.USERS) "去关注感兴趣的用户吧" else "去关注感兴趣的条目吧"
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FollowErrorState(errorMessage: String, onRetry: () -> Unit) {
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
private fun FollowLoginGuide(onLogin: () -> Unit) {
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
                text = "登录后查看关注动态",
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
