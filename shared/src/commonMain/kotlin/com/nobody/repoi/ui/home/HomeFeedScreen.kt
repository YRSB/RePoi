package com.nobody.repoi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.platform.isLandscapeOrientation
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardActions
import com.nobody.repoi.ui.board.ZoneBoardItemActions
import com.nobody.repoi.ui.board.ZoneBoardPane
import com.nobody.repoi.ui.board.components.ZoneBoardFilterBar
import com.nobody.repoi.ui.common.EmptyState
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import com.nobody.repoi.ui.common.rememberSynchronousTopAppBarScrollBehavior
import com.nobody.repoi.ui.home.components.ZoneSwitcherAnchor
import com.nobody.repoi.ui.home.components.ZoneSwitcherDropdown
import com.nobody.repoi.ui.home.components.ZoneSwitcherSheet
import com.nobody.repoi.ui.navigation.rememberBottomBarContentPadding
import com.nobody.repoi.ui.navigation.rememberBottomBarFabPadding
import com.nobody.repoi.ui.search.SearchNavParam
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    modifier: Modifier = Modifier,
    onActionClick: ((ActionItem) -> Unit)? = null,
    onNavigateToAlbum: ((AlbumNavParam) -> Unit)? = null,
    onNavigateToSearch: ((SearchNavParam) -> Unit)? = null,
    onNavigateToZoneBrowse: (() -> Unit)? = null,
    onNavigateToZoneSettings: (() -> Unit)? = null,
    onNavigateToForumHome: (() -> Unit)? = null,
    onNavigateToMessage: (() -> Unit)? = null,
    viewModel: HomeFeedViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val staggeredGridState = rememberLazyStaggeredGridState()
    val imageViewer = LocalImageViewer.current
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    // ≥ 840dp 用锚定下拉菜单，其余用底部抽屉；两种呈现共享同一份分组与选中态。
    val useZoneSwitcherDropdown =
        windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
    var zoneSwitcherExpanded by remember { mutableStateOf(false) }
    val onCloseZoneSwitcher = { zoneSwitcherExpanded = false }
    val onSelectZoneTab: (String) -> Unit = { tabKey ->
        zoneSwitcherExpanded = false
        viewModel.selectZoneTab(tabKey)
    }
    val onBrowseAllZonesFromSwitcher: () -> Unit = {
        zoneSwitcherExpanded = false
        onNavigateToZoneBrowse?.invoke()
    }
    val onOpenHomeSettingsFromSwitcher: () -> Unit = {
        zoneSwitcherExpanded = false
        onNavigateToZoneSettings?.invoke()
    }

    // 共享板块面板的宿主机适配：动作仍落到本页 ViewModel 现有入口，请求与分页语义不变
    val boardActions = remember(viewModel) {
        object : ZoneBoardActions {
            override fun onSelectSubType(subType: String) {
                viewModel.selectSubType(subType)
            }

            override fun onSelectAlbumOrder(order: String) {
                viewModel.selectAlbumOrder(order)
            }

            override fun onSelectAlbumCategory(categoryId: Int?) {
                viewModel.selectAlbumCategory(categoryId)
            }

            override fun onRetryInitial() {
                viewModel.retryCurrentBoard()
            }

            override fun onLoadMore() {
                viewModel.loadNextPage()
            }
        }
    }
    val onCardImageClick = remember(imageViewer) {
        { rawUrls: List<String>, initialIndex: Int ->
            imageViewer.show(rawUrls, initialIndex)
        }
    }
    val handleAlbumClick = remember(onNavigateToAlbum) {
        { item: AlbumListItem ->
            if (item.targetAlbumId > 0L) {
                onNavigateToAlbum?.invoke(
                    AlbumNavParam(
                        albumId = item.targetAlbumId,
                        initialTitle = item.displayName.ifBlank { null },
                        initialCover = item.cover
                    )
                )
            }
        }
    }
    val boardItemActions = remember(onActionClick, onCardImageClick, handleAlbumClick) {
        ZoneBoardItemActions(
            onActionClick = onActionClick,
            onActionImageClick = onCardImageClick,
            onAlbumClick = handleAlbumClick
        )
    }

    // 上拉触底翻页判断 (瀑布流模式)
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalCount = staggeredGridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = staggeredGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalCount > 0 && lastVisibleIndex >= totalCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore, uiState.selectedBoard, uiState.currentBoardItemCount) {
        if (shouldLoadMore &&
            uiState.currentBoardHasMore &&
            !uiState.currentBoardIsLoadingMore &&
            uiState.currentBoardLoadMoreError == null
        ) {
            viewModel.loadNextPage()
        }
    }

    // 滚动记忆键：作用域 + 板块，跨「专区 ↔ 板块」切换互不覆盖
    val currentBoardKey = homeBoardMemoryKey(uiState.zoneScope, uiState.selectedBoard)
    val memoryKey by rememberUpdatedState(currentBoardKey)
    var restored by remember { mutableStateOf(false) }
    // 切换选择期间暂停快照保存，避免旧板块首帧把新键的滚动位置写坏
    var savingEnabled by remember { mutableStateOf(true) }

    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarHeight = statusBarTopPadding + 64.dp
    // 可配置头部高度栈：顶栏 + 二级板块 Tab（常驻吸顶）+ 可折叠筛选行。
    // 所有依赖头部高度的地方（内容 padding / 下拉指示器 / 首屏各态卡片 / 折叠分母）只读该状态。
    val headerStack = rememberHomeHeaderStack(topBarHeight = topBarHeight)
    val scrollBehavior = rememberSynchronousTopAppBarScrollBehavior(
        initialHeightOffsetLimit = -headerStack.collapsibleHeightPx,
        state = rememberTopAppBarState(
            initialHeightOffsetLimit = -headerStack.collapsibleHeightPx,
            initialHeightOffset = -viewModel.scrollMemoryFor(currentBoardKey)
                .filterCollapseFraction * headerStack.collapsibleHeightPx
        ),
        canScroll = { uiState.currentBoardItemCount > 0 }
    )
    LaunchedEffect(headerStack.collapsibleHeightPx) {
        scrollBehavior.state.heightOffsetLimit = -headerStack.collapsibleHeightPx
        scrollBehavior.state.heightOffset =
            scrollBehavior.state.heightOffset.coerceIn(-headerStack.collapsibleHeightPx, 0f)
    }

    // 没有可用板块时二级 Tab 与筛选行都不渲染：把两层实测高度归零，避免内容被空档顶开。
    LaunchedEffect(uiState.boards.isEmpty()) {
        if (uiState.boards.isEmpty()) headerStack.clearMeasuredRows()
    }

    val pullRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val bottomContentPadding = rememberBottomBarContentPadding(16.dp)
    val fabBottomPadding = rememberBottomBarFabPadding()
    val feedColumns = rememberFeedStaggeredGridColumns()

    // 滚动 / 折叠比例按「作用域 + 板块」键保存（仅内存；持久化为 #156）
    LaunchedEffect(staggeredGridState, scrollBehavior.state) {
        snapshotFlow {
            val driverHeight = headerStack.collapsibleHeightPx
            Triple(
                staggeredGridState.firstVisibleItemIndex,
                staggeredGridState.firstVisibleItemScrollOffset,
                if (driverHeight > 0f) {
                    (-scrollBehavior.state.heightOffset / driverHeight).coerceIn(0f, 1f)
                } else {
                    0f
                }
            )
        }.collect { (currentIndex, currentOffset, filterCollapseFraction) ->
            if (restored && savingEnabled) {
                viewModel.saveBoardScrollPosition(memoryKey, currentIndex, currentOffset)
                viewModel.saveBoardFilterCollapseFraction(memoryKey, filterCollapseFraction)
            }
        }
    }

    // 切换专区 / 板块 / 筛选：复位到顶 + 筛选条展开，并清空新键的滚动记忆。
    // 筛选 token 按板块取当前生效值（动态 = subType；分享 = 排序 + 分类）。
    // 首次组合不触发（保留首屏恢复的滚动位置）。
    val boardFilterToken = when (uiState.selectedBoard) {
        ZoneBoard.Album -> "${uiState.albumSelectedOrder}|${uiState.albumSelectedCategory ?: "all"}"
        else -> uiState.selectedSubType
    }
    var lastSelection by remember {
        mutableStateOf(
            Triple(uiState.selectedCategory.tabKey, uiState.selectedBoard, boardFilterToken)
        )
    }
    LaunchedEffect(uiState.selectedCategory, uiState.selectedBoard, boardFilterToken) {
        val selection = Triple(
            uiState.selectedCategory.tabKey,
            uiState.selectedBoard,
            boardFilterToken
        )
        if (selection == lastSelection) return@LaunchedEffect
        lastSelection = selection
        savingEnabled = false
        viewModel.resetBoardScrollMemory(currentBoardKey)
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
        staggeredGridState.scrollToItem(0)
        savingEnabled = true
    }

    // 首屏（含从二级页返回）按当前键恢复滚动位置与折叠比例
    LaunchedEffect(uiState.currentBoardItemCount > 0, currentBoardKey) {
        if (restored || uiState.currentBoardItemCount == 0) return@LaunchedEffect
        restored = true
        val memory = viewModel.scrollMemoryFor(currentBoardKey)
        if (memory.index != 0 || memory.offset != 0) {
            staggeredGridState.scrollToItem(memory.index, memory.offset)
        }
        if (memory.filterCollapseFraction > 0f) {
            scrollBehavior.state.heightOffset =
                -memory.filterCollapseFraction * headerStack.collapsibleHeightPx
        }
    }

    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isRefreshing) {
        if (wasRefreshing && !uiState.isRefreshing && uiState.currentBoardErrorMessage == null) {
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
                        .padding(top = headerStack.totalHeaderHeight)
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            ) {
                when {
                    // 该专区没有任何已接入板块：不渲染空 Tab 条，只给板块空态与刷新入口
                    uiState.boards.isEmpty() -> {
                        ElevatedCard(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(top = headerStack.totalHeaderHeight)
                                .padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "该专区暂无可用板块",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(onClick = { viewModel.refreshBoards() }) {
                                    Text("刷新看看")
                                }
                            }
                        }
                    }

                    uiState.isCurrentBoardLoading -> {
                        PoiLoadingView(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = headerStack.totalHeaderHeight,
                                    bottom = bottomContentPadding
                                )
                        )
                    }

                    uiState.currentBoardErrorMessage != null && uiState.currentBoardItemCount == 0 -> {
                        ElevatedCard(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(top = headerStack.totalHeaderHeight)
                                .padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = uiState.currentBoardErrorMessage ?: "数据加载失败",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = { viewModel.retryCurrentBoard() }) {
                                    Text("重新加载")
                                }
                            }
                        }
                    }

                    uiState.currentBoardItemCount == 0 -> {
                        val isEmptyFollow = uiState.selectedCategory.tabKey == ZONE_TAB_KEY_FOLLOW &&
                            uiState.selectedCategory.zoneIds == "-1"
                        ElevatedCard(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(top = headerStack.totalHeaderHeight)
                                .padding(24.dp)
                        ) {
                            if (isEmptyFollow) {
                                // 未关注任何专区：「关注」仍可选中，但只引导去专区广场，不发起请求。
                                EmptyState(
                                    icon = Icons.Outlined.Category,
                                    title = "还没有关注的专区",
                                    description = "去专区广场关注感兴趣的专区，这里会汇总它们的动态",
                                    actionLabel = "去专区广场",
                                    onAction = { onNavigateToZoneBrowse?.invoke() }
                                )
                            } else {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (uiState.selectedBoard == ZoneBoard.Album) {
                                            "暂无相关分享"
                                        } else {
                                            "暂无该分类下的动态情报"
                                        },
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
                    }

                    else -> {
                        // 有内容：走共享板块面板（与专区详情同一内容渲染入口）；
                        // 加载 / 错误 / 空三态仍由上方首页卡片式分支接管，pane 只负责列表与页脚，
                        // 因此不会重复展示首屏错误。内容与筛选快照由 UiState 按板块投影（动态 / 分享）。
                        ZoneBoardPane(
                            board = uiState.selectedBoard,
                            scope = uiState.zoneScope,
                            content = uiState.currentBoardContent,
                            filters = uiState.currentBoardFilters,
                            gridState = staggeredGridState,
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = headerStack.totalHeaderHeight + 6.dp,
                                bottom = bottomContentPadding
                            ),
                            actions = boardActions,
                            itemActions = boardItemActions,
                            columns = feedColumns,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // 三级筛选行（可折叠）：位移由 scrollBehavior 驱动，高度实测回填头部高度栈
        if (uiState.boards.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = headerStack.pinnedHeight)
                    .offset { IntOffset(x = 0, y = scrollBehavior.state.heightOffset.roundToInt()) }
                    .onSizeChanged { size -> headerStack.onCollapsibleMeasured(size.height) }
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                ZoneBoardFilterBar(
                    board = uiState.selectedBoard,
                    filters = uiState.currentBoardFilters,
                    subTypeOptions = viewModel.subTypes,
                    actions = boardActions,
                    albumCategoryOptions = uiState.albumCategoryOptions
                )
            }
        }

        // 二级板块 Tab（常驻吸顶，不随滚动折叠）：渲染于筛选行之后，折叠时遮盖筛选行
        if (uiState.boards.isNotEmpty()) {
            val selectedBoardIndex = uiState.boards.indexOf(uiState.selectedBoard).coerceAtLeast(0)
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedBoardIndex,
                edgePadding = 16.dp,
                divider = {},
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = headerStack.topBarHeight)
                    .onSizeChanged { size -> headerStack.onTabRowMeasured(size.height) }
            ) {
                uiState.boards.forEachIndexed { index, board ->
                    val isSelected = index == selectedBoardIndex
                    Tab(
                        selected = isSelected,
                        onClick = { viewModel.selectBoard(board) },
                        text = {
                            Text(
                                text = board.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }

        TopAppBar(
            title = {
                if (useZoneSwitcherDropdown) {
                    ZoneSwitcherDropdown(
                        currentTitle = uiState.selectedCategory.name,
                        groups = uiState.switcherGroups,
                        selectedTabKey = uiState.selectedCategory.tabKey,
                        expanded = zoneSwitcherExpanded,
                        onExpandedChange = { zoneSwitcherExpanded = it },
                        onSelect = onSelectZoneTab,
                        onBrowseAllZones = onBrowseAllZonesFromSwitcher,
                        onOpenHomeSettings = onOpenHomeSettingsFromSwitcher
                    )
                } else {
                    // 紧凑屏只放锚点；底部抽屉挂在页面根部（见下方 ZoneSwitcherSheet）。
                    ZoneSwitcherAnchor(
                        currentTitle = uiState.selectedCategory.name,
                        expanded = zoneSwitcherExpanded,
                        onClick = { zoneSwitcherExpanded = true }
                    )
                }
            },
            actions = {
                // 搜索仍携带当前专区的初始分类（nodeId 0 → 10000 全部）。
                IconButton(
                    onClick = {
                        val categoryId = uiState.selectedCategory.id
                        onNavigateToSearch?.invoke(
                            SearchNavParam(initialCategory = if (categoryId == 0) 10000 else categoryId)
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "搜索"
                    )
                }
                IconButton(onClick = { onNavigateToForumHome?.invoke() }) {
                    Icon(
                        imageVector = Icons.Outlined.Forum,
                        contentDescription = "讨论板"
                    )
                }
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
                .padding(end = 16.dp, bottom = fabBottomPadding)
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "回到顶部并刷新"
            )
        }

        // 紧凑屏专区切换器：底部抽屉挂在页面根部，与顶栏锚点共享同一份分组/选中态。
        if (!useZoneSwitcherDropdown && zoneSwitcherExpanded) {
            ZoneSwitcherSheet(
                groups = uiState.switcherGroups,
                selectedTabKey = uiState.selectedCategory.tabKey,
                onSelect = onSelectZoneTab,
                onBrowseAllZones = onBrowseAllZonesFromSwitcher,
                onOpenHomeSettings = onOpenHomeSettingsFromSwitcher,
                onDismiss = onCloseZoneSwitcher
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
