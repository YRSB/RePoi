package com.nobody.repoi.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.repoi.data.local.CategoryItem
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.data.remote.model.ResaleSearchItem
import com.nobody.repoi.data.remote.model.ZoneEntityItem
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.platform.isLandscapeOrientation
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.board.ZoneBoardHobbySheets
import com.nobody.repoi.ui.board.ZoneBoardItemActions
import com.nobody.repoi.ui.board.ZoneBoardPane
import com.nobody.repoi.ui.board.boardFilterToken
import com.nobody.repoi.ui.board.itemCount
import com.nobody.repoi.ui.board.components.ZoneBoardFilterBar
import com.nobody.repoi.ui.board.components.ZoneBoardStatusCard
import com.nobody.repoi.ui.board.components.ZoneEmptyPlaceholder
import com.nobody.repoi.ui.board.components.ZoneErrorPlaceholder
import com.nobody.repoi.ui.board.rememberResaleStaggeredGridColumns
import com.nobody.repoi.ui.board.session.ZoneBoardIntent
import com.nobody.repoi.ui.board.session.ZoneBoardSession
import com.nobody.repoi.ui.board.session.ZoneBoardSnapshot
import com.nobody.repoi.ui.board.session.itemCount
import com.nobody.repoi.ui.common.EmptyState
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import com.nobody.repoi.ui.common.rememberSynchronousTopAppBarScrollBehavior
import com.nobody.repoi.ui.detail.DetailNavParam
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
    /** 二手条目点击（跳二手详情）。 */
    onNavigateToResaleDetail: ((Long) -> Unit)? = null,
    onNavigateToHobby: ((DetailNavParam) -> Unit)? = null,
    /**
     * 实体卡片点击（厂商/系列/作品/角色/人物）。
     */
    onEntityClick: ((ZoneBoard, ZoneEntityItem) -> Unit)? = null,
    onNavigateToSearch: ((SearchNavParam) -> Unit)? = null,
    onNavigateToZoneBrowse: (() -> Unit)? = null,
    onNavigateToZoneSettings: (() -> Unit)? = null,
    onNavigateToForumHome: (() -> Unit)? = null,
    onNavigateToMessage: (() -> Unit)? = null,
    /** 讨论帖点击（itemId, nodeId, 初始标题）；为空时提示「即将上线」。 */
    onNavigateToPost: ((Long, Long?, String?) -> Unit)? = null,
    /** 讨论帖作者点击。 */
    onNavigateToUser: ((Long) -> Unit)? = null,
    viewModel: HomeFeedViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val session = viewModel.session
    val activeSnapshot by session.activeSnapshot.collectAsStateWithLifecycle()
    val hobbyCategoryTree by viewModel.hobbyCategoryTree.collectAsStateWithLifecycle()
    val imageViewer = LocalImageViewer.current
    val messages = LocalAppMessages.current
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    // ≥ 840dp 用锚定下拉菜单，其余用底部抽屉；两种呈现共享同一份分组与选中态。
    val useZoneSwitcherDropdown =
        windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
    var zoneSwitcherExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(useZoneSwitcherDropdown) {
        zoneSwitcherExpanded = false
    }
    // 消息中心内的已读操作只改服务端计数，首页快照会失效；
    // 返回首页时重拉一次，红点不残留陈旧值（issue #195）。
    LifecycleResumeEffect(Unit) {
        viewModel.refreshUnreadMessages()
        onPauseOrDispose { }
    }
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

    val onCardImageClick = remember(imageViewer) {
        { rawUrls: List<String>, initialIndex: Int ->
            imageViewer.show(rawUrls, initialIndex)
        }
    }
    val handlePostClick = remember(onNavigateToPost) {
        { item: PostItem ->
            val targetItemId = if (item.itemId > 0L) item.itemId else item.id
            val targetNodeId = if (item.id > 0L) item.id else null
            if (onNavigateToPost != null && targetItemId > 0L) {
                onNavigateToPost(targetItemId, targetNodeId, item.title.ifBlank { null })
            } else {
                messages.show("帖子详情功能即将上线")
            }
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
    val handleResaleClick = remember(onNavigateToResaleDetail) {
        { item: ResaleSearchItem ->
            if (item.id > 0L && onNavigateToResaleDetail != null) {
                onNavigateToResaleDetail(item.id)
            }
        }
    }
    val handleHobbyClick = remember(onNavigateToHobby) {
        { item: HobbyItemSummary ->
            val actualId = item.actualItemId
            if (actualId > 0L && onNavigateToHobby != null) {
                onNavigateToHobby(
                    DetailNavParam(
                        itemId = actualId,
                        initialName = item.displayName,
                        initialCover = item.cover
                    )
                )
            }
        }
    }
    val boardItemActions = remember(
        onActionClick,
        onCardImageClick,
        handlePostClick,
        handleAlbumClick,
        handleResaleClick,
        handleHobbyClick,
        onNavigateToUser,
        onEntityClick
    ) {
        ZoneBoardItemActions(
            onActionClick = onActionClick,
            onActionImageClick = onCardImageClick,
            onPostClick = handlePostClick,
            onPostImageClick = onCardImageClick,
            onPostUserClick = onNavigateToUser,
            onAlbumClick = handleAlbumClick,
            onResaleClick = handleResaleClick,
            onResaleUserClick = onNavigateToUser,
            onHobbyClick = handleHobbyClick,
            onEntityClick = onEntityClick
        )
    }

    // 活跃板块条目数与刷新态：仅由 activeSnapshot 驱动
    val boardItemCount = activeSnapshot.content.itemCount
    val isRefreshing = activeSnapshot.itemCount > 0 && activeSnapshot.pageView.isLoading
    val boardHasFilterBar = uiState.boards.isNotEmpty()

    val currentBoardKey = homeBoardMemoryKey(uiState.zoneScope, uiState.selectedBoard)

    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topBarHeight = statusBarTopPadding + 64.dp
    val headerStack = rememberHomeHeaderStack(topBarHeight = topBarHeight)
    val boardFilterHeights = remember { mutableStateMapOf<ZoneBoard, Float>() }
    val scrollBehavior = rememberSynchronousTopAppBarScrollBehavior(
        initialHeightOffsetLimit = -headerStack.collapsibleHeightPx,
        state = rememberTopAppBarState(
            initialHeightOffsetLimit = -headerStack.collapsibleHeightPx,
            initialHeightOffset = -activeSnapshot.scrollPosition.filterCollapseFraction * headerStack.collapsibleHeightPx
        ),
        canScroll = { boardItemCount > 0 }
    )

    LaunchedEffect(uiState.boards.isEmpty()) {
        if (uiState.boards.isEmpty()) headerStack.clearMeasuredRows()
    }

    LaunchedEffect(uiState.selectedBoard) {
        val board = uiState.selectedBoard
        if (board.isEntity) {
            headerStack.resetCollapsibleHeight()
        } else {
            boardFilterHeights[board]?.let { heightPx ->
                if (heightPx > 0f) headerStack.onCollapsibleMeasured(heightPx.roundToInt())
            }
        }
    }

    val pullRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val bottomContentPadding = rememberBottomBarContentPadding(16.dp)
    val fabBottomPadding = rememberBottomBarFabPadding()
    val feedColumns = rememberFeedStaggeredGridColumns()
    val resaleColumns = rememberResaleStaggeredGridColumns()

    val boards = uiState.boards
    val selectedBoardIndex = boards.indexOf(uiState.selectedBoard).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedBoardIndex) {
        boards.size.coerceAtLeast(1)
    }
    val pageGridStates = remember { mutableStateMapOf<ZoneBoard, LazyStaggeredGridState>() }
    val currentFilterHeightPx: Float = if (uiState.selectedBoard.isEntity) {
        0f
    } else {
        boardFilterHeights[uiState.selectedBoard]
            ?: if (headerStack.collapsibleHeightPx > 0f) {
                headerStack.collapsibleHeightPx
            } else {
                with(LocalDensity.current) { 48.dp.toPx() }
            }
    }

    val isConfirmedEmptyOrError =
        (boardItemCount == 0 && !activeSnapshot.pageView.isLoading) || activeSnapshot.pageView.errorMessage != null
    LaunchedEffect(activeSnapshot.board, currentFilterHeightPx, isConfirmedEmptyOrError) {
        val fraction = if (isConfirmedEmptyOrError) {
            0f
        } else {
            activeSnapshot.scrollPosition.filterCollapseFraction
        }
        scrollBehavior.state.heightOffsetLimit = -currentFilterHeightPx
        scrollBehavior.state.contentOffset = 0f
        scrollBehavior.state.heightOffset =
            (-fraction * currentFilterHeightPx).coerceIn(-currentFilterHeightPx, 0f)
    }

    LaunchedEffect(pagerState, boards) {
        if (boards.isNotEmpty()) {
            val target = boards.indexOf(uiState.selectedBoard).coerceAtLeast(0)
            if (pagerState.currentPage != target) pagerState.scrollToPage(target)
        }
        snapshotFlow { pagerState.settledPage }.collect { page ->
            boards.getOrNull(page)?.let { board ->
                if (board != uiState.selectedBoard) viewModel.selectBoard(board)
            }
        }
    }

    LaunchedEffect(selectedBoardIndex, boards.size) {
        if (boards.isNotEmpty() && pagerState.currentPage != selectedBoardIndex) {
            pagerState.scrollToPage(selectedBoardIndex)
        }
    }

    val scrollCurrentBoardToTop: suspend () -> Unit = {
        pageGridStates[uiState.selectedBoard]?.scrollToItem(0)
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch { scrollCurrentBoardToTop() }
                viewModel.refreshFeed()
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
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
                if (boards.isEmpty()) {
                    ZoneEmptyPlaceholder(
                        text = "该专区暂无可用板块",
                        onRefresh = { viewModel.refreshBoards() },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = headerStack.totalHeaderHeight)
                            .padding(24.dp)
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        key = { index -> boards[index] },
                        beyondViewportPageCount = 0,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val board = boards[page]
                        val snapshot = if (board == activeSnapshot.board) activeSnapshot else session.snapshotFor(board)
                        HomeBoardPage(
                            board = board,
                            snapshot = snapshot,
                            session = session,
                            uiState = uiState,
                            viewModel = viewModel,
                            headerStack = headerStack,
                            scrollBehavior = scrollBehavior,
                            columns = if (board == ZoneBoard.Resale) resaleColumns else feedColumns,
                            bottomContentPadding = bottomContentPadding,
                            boardItemActions = boardItemActions,
                            hobbyCategoryTree = hobbyCategoryTree,
                            onFilterHeightMeasured = { heightPx ->
                                boardFilterHeights[board] = heightPx.toFloat()
                            },
                            isCurrentProvider = { uiState.selectedBoard == board },
                            onNavigateToZoneBrowse = onNavigateToZoneBrowse,
                            onGridStateReady = { boardKey, state -> pageGridStates[boardKey] = state }
                        )
                    }
                }
            }
        }

        if (uiState.selectedBoard == ZoneBoard.Hobby) {
            ZoneBoardHobbySheets(
                filters = activeSnapshot.filters,
                hobbyCategoryTree = hobbyCategoryTree,
                onIntent = session::dispatch
            )
        }

        if (boardHasFilterBar) {
            val currentPage = pagerState.currentPage.coerceIn(0, boards.lastIndex)
            PrimaryScrollableTabRow(
                selectedTabIndex = currentPage,
                edgePadding = 16.dp,
                divider = {},
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = headerStack.topBarHeight)
                    .onSizeChanged { size -> headerStack.onTabRowMeasured(size.height) }
            ) {
                boards.forEachIndexed { index, board ->
                    val isSelected = index == currentPage
                    Tab(
                        selected = isSelected,
                        onClick = { viewModel.selectBoard(board) },
                        text = {
                            Text(
                                text = uiState.boardTitle(board),
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
                    ZoneSwitcherAnchor(
                        currentTitle = uiState.selectedCategory.name,
                        expanded = zoneSwitcherExpanded,
                        onClick = { zoneSwitcherExpanded = true }
                    )
                }
            },
            actions = {
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
                    scrollCurrentBoardToTop()
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
 * 单个板块页：四态 + 瀑布流内容 + 该板块自己的滚动状态与触底翻页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeBoardPage(
    board: ZoneBoard,
    snapshot: ZoneBoardSnapshot,
    session: ZoneBoardSession,
    uiState: HomeFeedUiState,
    viewModel: HomeFeedViewModel,
    headerStack: HomeHeaderStackState,
    scrollBehavior: TopAppBarScrollBehavior,
    columns: StaggeredGridCells,
    bottomContentPadding: Dp,
    boardItemActions: ZoneBoardItemActions,
    hobbyCategoryTree: Map<Int, List<CategoryItem>>,
    onFilterHeightMeasured: (Int) -> Unit,
    isCurrentProvider: () -> Boolean,
    onNavigateToZoneBrowse: (() -> Unit)?,
    onGridStateReady: (ZoneBoard, LazyStaggeredGridState) -> Unit
) {
    val gridState = rememberLazyStaggeredGridState()
    LaunchedEffect(gridState, board) { onGridStateReady(board, gridState) }

    val isCurrent = isCurrentProvider()
    val memoryKey = homeBoardMemoryKey(uiState.zoneScope, board)
    val itemCount = snapshot.content.itemCount
    val errorMessage = snapshot.pageView.errorMessage
    val contentEmpty = itemCount == 0 && errorMessage == null && !snapshot.pageView.isLoading
    val errorEmpty = errorMessage != null && itemCount == 0
    var restoredKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(board, uiState.zoneScope) {
        viewModel.ensureBoardLoaded(board)
    }

    var collapseFraction by remember(memoryKey) {
        mutableFloatStateOf(snapshot.scrollPosition.filterCollapseFraction)
    }
    LaunchedEffect(itemCount, isCurrent, snapshot.pageView.isLoading) {
        if (isCurrent && itemCount == 0 && !snapshot.pageView.isLoading) collapseFraction = 0f
    }
    var filterRestored by remember(memoryKey) { mutableStateOf(false) }
    LaunchedEffect(memoryKey, isCurrent) {
        if (!isCurrent) {
            filterRestored = false
            return@LaunchedEffect
        }
        val targetFraction = snapshot.scrollPosition.filterCollapseFraction
        collapseFraction = targetFraction
        filterRestored = true
    }

    var filterBarHeightPx by rememberSaveable { mutableStateOf(0) }
    val density = LocalDensity.current
    val ownFilterHeight = if (board.isEntity) {
        0.dp
    } else if (filterBarHeightPx > 0) {
        with(density) { filterBarHeightPx.toDp() }
    } else {
        headerStack.collapsibleHeight
    }
    val topInset = headerStack.pinnedHeight + ownFilterHeight

    val shouldLoadMore by remember(gridState) {
        derivedStateOf {
            val totalCount = gridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalCount > 0 && lastVisibleIndex >= totalCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, isCurrent, snapshot.pageView.isEndReached, snapshot.pageView.isLoadingMore, snapshot.pageView.loadMoreError) {
        if (isCurrent && shouldLoadMore && !snapshot.pageView.isEndReached &&
            !snapshot.pageView.isLoadingMore && snapshot.pageView.loadMoreError == null
        ) {
            session.dispatch(ZoneBoardIntent.LoadMore(board))
        }
    }

    LaunchedEffect(memoryKey, itemCount > 0) {
        if (itemCount <= 0) return@LaunchedEffect
        if (restoredKey == memoryKey) return@LaunchedEffect
        val memory = snapshot.scrollPosition
        val inRange = memory.index in 0 until itemCount
        if (inRange && (memory.index != 0 || memory.offset != 0)) {
            gridState.scrollToItem(memory.index, memory.offset)
        } else {
            if (!inRange) session.dispatch(ZoneBoardIntent.ResetScrollMemory(board))
            gridState.scrollToItem(0)
        }
        restoredKey = memoryKey
    }

    LaunchedEffect(gridState, scrollBehavior.state, memoryKey) {
        snapshotFlow {
            val driverHeight = if (board.isEntity) {
                0f
            } else if (filterBarHeightPx > 0) {
                filterBarHeightPx.toFloat()
            } else {
                headerStack.collapsibleHeightPx
            }
            Triple(
                gridState.firstVisibleItemIndex,
                gridState.firstVisibleItemScrollOffset,
                if (driverHeight > 0f) {
                    (-scrollBehavior.state.heightOffset / driverHeight).coerceIn(0f, 1f)
                } else {
                    0f
                }
            )
        }.collect { (index, offset, filterCollapseFraction) ->
            if (!isCurrentProvider()) return@collect
            if (filterRestored) {
                collapseFraction = filterCollapseFraction
                session.dispatch(ZoneBoardIntent.SaveFilterCollapseFraction(board, filterCollapseFraction))
            }
            if (restoredKey == memoryKey) {
                session.dispatch(ZoneBoardIntent.SaveScrollPosition(board, index, offset))
            }
        }
    }

    val filterToken = boardFilterToken(snapshot.filters, board)
    var lastFilterToken by remember(memoryKey) { mutableStateOf(filterToken) }
    LaunchedEffect(filterToken, memoryKey) {
        if (filterToken == lastFilterToken) return@LaunchedEffect
        lastFilterToken = filterToken
        session.dispatch(ZoneBoardIntent.ResetScrollMemory(board))
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
        gridState.scrollToItem(0)
        restoredKey = memoryKey
    }

    val isRefreshing = snapshot.content.itemCount > 0 && snapshot.pageView.isLoading
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing && errorMessage == null && isCurrentProvider()) {
            gridState.scrollToItem(0)
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
        wasRefreshing = isRefreshing
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            snapshot.pageView.isLoading && itemCount == 0 -> {
                PoiLoadingView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topInset, bottom = bottomContentPadding)
                )
            }

            errorEmpty -> {
                ZoneErrorPlaceholder(
                    message = errorMessage ?: "数据加载失败",
                    onRetry = { if (isCurrent) session.dispatch(ZoneBoardIntent.RetryInitial(board)) },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = topInset)
                        .padding(24.dp)
                )
            }

            contentEmpty -> {
                if (uiState.zoneScope.isFollowEmpty && board != ZoneBoard.Posts) {
                    ZoneBoardStatusCard(
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = topInset)
                            .padding(24.dp)
                    ) {
                        EmptyState(
                            icon = Icons.Outlined.Category,
                            title = "还没有关注的专区",
                            description = "去专区广场关注感兴趣的专区，这里会汇总它们的动态",
                            actionLabel = "去专区广场",
                            onAction = { onNavigateToZoneBrowse?.invoke() }
                        )
                    }
                } else {
                    ZoneEmptyPlaceholder(
                        text = board.emptyText,
                        onRefresh = { if (isCurrent) session.dispatch(ZoneBoardIntent.Refresh(board)) },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = topInset)
                            .padding(24.dp)
                    )
                }
            }

            else -> {
                ZoneBoardPane(
                    board = board,
                    content = snapshot.content,
                    gridState = gridState,
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = topInset + 6.dp,
                        bottom = bottomContentPadding
                    ),
                    onIntent = session::dispatch,
                    itemActions = boardItemActions,
                    columns = columns,
                    showPinnedPosts = uiState.zoneScope is ZoneQueryScope.Single,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (!board.isEntity) {
            LaunchedEffect(filterBarHeightPx) {
                if (filterBarHeightPx > 0) onFilterHeightMeasured(filterBarHeightPx)
            }
            LaunchedEffect(isCurrent, filterBarHeightPx) {
                if (isCurrent && filterBarHeightPx > 0) {
                    headerStack.onCollapsibleMeasured(filterBarHeightPx)
                }
            }

            val filterRowScroll = remember { ScrollState(0) }
            var filterRowRestored by remember(memoryKey) { mutableStateOf(false) }
            LaunchedEffect(memoryKey, filterRowScroll.maxValue) {
                if (filterRowRestored) return@LaunchedEffect
                val savedX = snapshot.scrollPosition.filterRowScrollX
                if (savedX > 0 && filterRowScroll.maxValue <= 0) return@LaunchedEffect
                if (savedX > 0) filterRowScroll.scrollTo(savedX)
                filterRowRestored = true
            }
            LaunchedEffect(memoryKey, filterRowScroll) {
                snapshotFlow { filterRowScroll.value }.collect { scrollX ->
                    if (filterRowRestored) session.dispatch(ZoneBoardIntent.SaveFilterRowScroll(board, scrollX))
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = headerStack.pinnedHeight)
                    .offset {
                        val heightPx = if (filterBarHeightPx > 0) {
                            filterBarHeightPx
                        } else {
                            headerStack.collapsibleHeightPx.roundToInt()
                        }
                        val offsetY = if (isCurrentProvider() && filterRestored) {
                            scrollBehavior.state.heightOffset
                        } else {
                            -(collapseFraction * heightPx)
                        }
                        IntOffset(0, offsetY.roundToInt())
                    }
                    .onSizeChanged { filterBarHeightPx = it.height }
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                ZoneBoardFilterBar(
                    board = board,
                    filters = snapshot.filters,
                    subTypeOptions = viewModel.subTypes,
                    onIntent = session::dispatch,
                    albumCategoryOptions = uiState.albumCategoryOptions,
                    hobbyCategoryTree = hobbyCategoryTree,
                    hobbyIsGridMode = (snapshot.content as? ZoneBoardContent.Hobby)?.isGridMode ?: true,
                    rowScrollState = filterRowScroll
                )
            }
        }
    }
}

/**
 * 动态瀑布流自适应列数：
 * - 横屏桌面端 / 超宽大屏 (≥ 1400dp)：3 列；
 * - 横屏平板 / 折叠屏展开 (≥ 600dp MEDIUM)：2 列；
 * - 竖屏状态（含手机竖屏、平板竖屏、折叠屏竖屏）及超窄横屏：统一 1 列。
 */
@Composable
fun rememberFeedStaggeredGridColumns(): StaggeredGridCells {
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
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
