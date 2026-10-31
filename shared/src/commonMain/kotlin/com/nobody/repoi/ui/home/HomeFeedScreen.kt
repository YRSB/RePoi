package com.nobody.repoi.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import com.nobody.repoi.ui.home.components.ShimmerHost
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.Color
import com.nobody.repoi.platform.isLandscapeOrientation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.common.FakeSearchField
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.UserAvatar
import com.nobody.repoi.ui.common.rememberSynchronousTopAppBarScrollBehavior
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.navigation.CharacterNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.ui.home.components.ActionCard
import com.nobody.repoi.ui.home.components.ActionCardSkeleton
import com.nobody.repoi.ui.search.SearchNavParam
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.util.FeedActionDispatcher
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    modifier: Modifier = Modifier,
    onNavigateToDetail: ((DetailNavParam) -> Unit)? = null,
    onNavigateToAlbum: ((AlbumNavParam) -> Unit)? = null,
    onNavigateToSearch: ((SearchNavParam) -> Unit)? = null,
    onNavigateToPerson: ((PersonNavParam) -> Unit)? = null,
    onNavigateToWorks: ((WorksNavParam) -> Unit)? = null,
    onNavigateToVendor: ((VendorNavParam) -> Unit)? = null,
    onNavigateToCharacter: ((CharacterNavParam) -> Unit)? = null,
    onNavigateToSeries: ((SeriesNavParam) -> Unit)? = null,
    onNavigateToUser: ((Long) -> Unit)? = null,
    onNavigateToZoneBrowse: (() -> Unit)? = null,
    onNavigateToForumHome: (() -> Unit)? = null,
    onNavigateToMessage: (() -> Unit)? = null,
    onNavigateToProfile: (() -> Unit)? = null,
    viewModel: HomeFeedViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val staggeredGridState = rememberLazyStaggeredGridState()
    val imageViewer = LocalImageViewer.current

    // 上拉触底翻页判断 (瀑布流模式)
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalCount = staggeredGridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = staggeredGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalCount > 0 && lastVisibleIndex >= totalCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore, uiState.items.size) {
        if (shouldLoadMore && uiState.hasMore && !uiState.isLoadingMore && uiState.loadMoreError == null) {
            viewModel.loadNextPage()
        }
    }

    var restored by remember { mutableStateOf(false) }
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarHeight = statusBarTopPadding + 64.dp
    val density = LocalDensity.current
    var tabRowHeightPx by remember { mutableFloatStateOf(with(density) { 48.dp.toPx() }) }
    var filterRowHeightPx by remember { mutableFloatStateOf(with(density) { 48.dp.toPx() }) }
    val scrollBehavior = rememberSynchronousTopAppBarScrollBehavior(
        initialHeightOffsetLimit = -tabRowHeightPx,
        canScroll = { uiState.items.isNotEmpty() }
    )
    LaunchedEffect(tabRowHeightPx) {
        scrollBehavior.state.heightOffsetLimit = -tabRowHeightPx
        scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffset.coerceIn(-tabRowHeightPx, 0f)
    }
    val tabRowHeightDp = with(density) { tabRowHeightPx.toDp() }
    val filterRowHeightDp = with(density) { filterRowHeightPx.toDp() }
    val totalHeaderHeight = topBarHeight + tabRowHeightDp + filterRowHeightDp
    val pullRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val isWideScreen = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val isCompact = !isWideScreen
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomContentPadding = if (isCompact) 16.dp else (16.dp + navBarBottomPadding)
    val feedColumns = rememberFeedStaggeredGridColumns()

    LaunchedEffect(staggeredGridState) {
        snapshotFlow {
            staggeredGridState.firstVisibleItemIndex to staggeredGridState.firstVisibleItemScrollOffset
        }.collect { (currentIndex, currentOffset) ->
            if (restored) {
                viewModel.saveScrollPosition(currentIndex, currentOffset)
            }
        }
    }

    LaunchedEffect(uiState.selectedCategory, uiState.selectedSubType) {
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
        staggeredGridState.scrollToItem(0)
    }

    LaunchedEffect(uiState.items.isNotEmpty()) {
        if (!restored && uiState.items.isNotEmpty()) {
            restored = true
            if (viewModel.savedScrollIndex != 0 || viewModel.savedScrollOffset != 0) {
                staggeredGridState.scrollToItem(viewModel.savedScrollIndex, viewModel.savedScrollOffset)
            }
        }
    }

    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isRefreshing) {
        if (wasRefreshing && !uiState.isRefreshing && uiState.errorMessage == null) {
            staggeredGridState.scrollToItem(0)
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
        wasRefreshing = uiState.isRefreshing
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                scope.launch {
                    staggeredGridState.scrollToItem(0)
                    scrollBehavior.state.heightOffset = 0f
                    scrollBehavior.state.contentOffset = 0f
                }
                viewModel.refreshFeed()
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
                uiState.isInitialLoading -> {
                    // 信息流骨架按固定类型复用，避免与正式卡片混用复用池
                    ShimmerHost {
                    LazyVerticalStaggeredGrid(
                        columns = feedColumns,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = totalHeaderHeight + 8.dp, bottom = bottomContentPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 8.dp
                    ) {
                        items(
                            6,
                            key = { index -> "home_skeleton_$index" },
                            contentType = { "home_skeleton" }
                        ) {
                            ActionCardSkeleton()
                        }
                    }
                    }
                }

                uiState.errorMessage != null && uiState.items.isEmpty() -> {
                    ElevatedCard(
                        shape = ExpressiveTokens.CardShape,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = ExpressiveTokens.ElevationDefault),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = totalHeaderHeight)
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = uiState.errorMessage ?: "数据加载失败",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { viewModel.loadInitialFeed() }) {
                                Text("重新加载")
                            }
                        }
                    }
                }

                uiState.items.isEmpty() -> {
                    ElevatedCard(
                        shape = ExpressiveTokens.CardShape,
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = ExpressiveTokens.ElevationDefault),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = totalHeaderHeight)
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "暂无该分类下的动态情报",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(onClick = { viewModel.refreshFeed() }) {
                                Text("刷新看看")
                            }
                        }
                    }
                }

                else -> {
                    val messages = LocalAppMessages.current
                    val onActionItemClick = remember(
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
                        { item: ActionItem ->
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

                    LazyVerticalStaggeredGrid(
                        columns = feedColumns,
                        state = staggeredGridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = totalHeaderHeight + 6.dp, bottom = bottomContentPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 8.dp
                    ) {
                        items(
                            items = uiState.items,
                            key = { item -> "${item.id}_${item.actionDate}" },
                            contentType = { "feed_action" }
                        ) { actionItem ->
                            ActionCard(
                                actionItem = actionItem,
                                onItemClick = onActionItemClick,
                                onUserClick = onNavigateToUser,
                                onImageClick = onCardImageClick
                            )
                        }

                        // 底部状态区域独占整行
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
                                            .clickable { viewModel.loadNextPage() }
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
    }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = topBarHeight)
                .offset { IntOffset(x = 0, y = scrollBehavior.state.heightOffset.roundToInt()) }
                .onSizeChanged { size -> if (size.height > 0) tabRowHeightPx = size.height.toFloat() }
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val selectedTabIndex = uiState.categories
                .indexOfFirst { it.tabKey == uiState.selectedCategory.tabKey }
                .coerceAtLeast(0)
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                uiState.categories.forEachIndexed { index, category ->
                    val isSelected = index == selectedTabIndex
                    Tab(
                        selected = isSelected,
                        onClick = { viewModel.selectCategory(category) },
                        text = {
                            Text(
                                text = category.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
                Tab(
                    selected = false,
                    onClick = { onNavigateToForumHome?.invoke() },
                    text = { Text("讨论板") }
                )
                Tab(
                    selected = false,
                    onClick = { onNavigateToZoneBrowse?.invoke() },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "专区广场",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = topBarHeight + tabRowHeightDp)
                .offset { IntOffset(x = 0, y = scrollBehavior.state.heightOffset.roundToInt()) }
                .onSizeChanged { size -> if (size.height > 0) filterRowHeightPx = size.height.toFloat() }
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModel.subTypes.forEach { (key, label) ->
                    FilterChip(
                        selected = uiState.selectedSubType == key,
                        onClick = { viewModel.selectSubType(key) },
                        label = { Text(label) },
                        shape = ExpressiveTokens.ChipShape
                    )
                }
            }
        }

        TopAppBar(
            title = {
                FakeSearchField(
                    onSearchClick = {
                        val categoryId = uiState.selectedCategory.id
                        onNavigateToSearch?.invoke(
                            SearchNavParam(initialCategory = if (categoryId == 0) 10000 else categoryId)
                        )
                    },
                    placeholder = "搜索",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 12.dp)
                )
            },
            actions = {
                IconButton(onClick = { onNavigateToMessage?.invoke() }) {
                    BadgedBox(
                        badge = {
                            if (uiState.unreadMessageCount > 0) {
                                Badge(containerColor = MaterialTheme.colorScheme.error)
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Outlined.Email, contentDescription = "消息中心")
                    }
                }
                IconButton(onClick = { onNavigateToProfile?.invoke() }) {
                    UserAvatar(
                        avatarUrl = uiState.userAvatar,
                        size = 34.dp,
                        contentDescription = "我的"
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface
            ),
            windowInsets = WindowInsets.statusBars
        )

        FloatingActionButton(
            onClick = {
                scope.launch {
                    staggeredGridState.scrollToItem(0)
                    scrollBehavior.state.heightOffset = 0f
                    viewModel.refreshFeed()
                }
            },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (isCompact) 16.dp else (24.dp + navBarBottomPadding))
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "回到顶部并刷新"
            )
        }
    }
}

/**
 * 动态瀑布流自适应列数：
 * - 横屏桌面端 / 超宽大屏 (≥ 1400dp)：3 列；
 * - 横屏平板 / 折叠屏展开 (≥ 600dp MEDIUM)：2 列；
 * - 竖屏状态（含手机竖屏、平板竖屏、折叠屏竖屏）及超窄横屏：统一 1 列，避免图文混排卡片内容被挤压。
 */
@Composable
fun rememberFeedStaggeredGridColumns(): StaggeredGridCells {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    // KMP：横屏判定经 expect/actual 垫片（Android 读 Configuration，桌面恒竖屏 + 宽度断点兜底）。
    val isLandscape = isLandscapeOrientation()
    val windowSizeClass = adaptiveInfo.windowSizeClass
    return remember(windowSizeClass, isLandscape) {
        when {
            isLandscape && windowSizeClass.isWidthAtLeastBreakpoint(1400) -> StaggeredGridCells.Fixed(3)
            isLandscape && windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) -> StaggeredGridCells.Fixed(2)
            else -> StaggeredGridCells.Fixed(1)
        }
    }
}
