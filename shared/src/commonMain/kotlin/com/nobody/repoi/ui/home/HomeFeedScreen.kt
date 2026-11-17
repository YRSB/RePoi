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
import com.nobody.repoi.ui.board.ZoneBoardActions
import com.nobody.repoi.ui.board.ZoneBoardHobbySheets
import com.nobody.repoi.ui.board.ZoneBoardItemActions
import com.nobody.repoi.ui.board.ZoneBoardPane
import com.nobody.repoi.ui.board.components.ZoneBoardFilterBar
import com.nobody.repoi.ui.board.components.ZoneBoardStatusCard
import com.nobody.repoi.ui.board.components.ZoneEmptyPlaceholder
import com.nobody.repoi.ui.board.components.ZoneErrorPlaceholder
import com.nobody.repoi.ui.board.rememberResaleStaggeredGridColumns
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
     *
     * 首页不感知详情路由类型：由 `TopLevelEntries` 按板块类型分发到对应详情页导航，
     * 与共享面板「导航经 itemActions 注入」的约束一致。
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
    val hobbyCategoryTree by viewModel.hobbyCategoryTree.collectAsStateWithLifecycle()
    val imageViewer = LocalImageViewer.current
    val messages = LocalAppMessages.current
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    // ≥ 840dp 用锚定下拉菜单，其余用底部抽屉；两种呈现共享同一份分组与选中态。
    val useZoneSwitcherDropdown =
        windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
    var zoneSwitcherExpanded by remember { mutableStateOf(false) }
    // 两种呈现共享同一展开标志：跨 840dp 断点时收起，避免另一套呈现首次组合即自动弹出
    LaunchedEffect(useZoneSwitcherDropdown) {
        zoneSwitcherExpanded = false
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

    // 共享板块面板的宿主机适配：动作仍落到本页 ViewModel 现有入口，请求与分页语义不变。
    // 重试 / 翻页按「当前板块」分派（讨论板块有独立请求链路），筛选值仍由 ViewModel 持有。
    val boardActions = remember(viewModel) {
        ZoneBoardActions(
            selectSubType = viewModel::selectSubType,
            selectPostsOrder = viewModel::selectPostsOrder,
            selectAlbumOrder = viewModel::selectAlbumOrder,
            selectAlbumCategory = viewModel::selectAlbumCategory,
            selectResaleType = viewModel::selectResaleType,
            selectResalePurity = viewModel::selectResalePurity,
            retryInitial = viewModel::retrySelectedBoard,
            loadMore = viewModel::loadMoreSelectedBoard,
            hobbyOrder = viewModel::selectHobbyOrder,
            hobbyYear = viewModel::selectHobbyYear,
            hobbyMonth = viewModel::selectHobbyMonth,
            hobbyCategory = viewModel::selectHobbyCategory,
            hobbyAdvancedApply = viewModel::applyHobbyAdvancedFilters,
            hobbyAdvancedReset = viewModel::resetHobbyAdvancedFilters,
            hobbyViewMode = viewModel::toggleHobbyViewMode,
            hobbyFilterSheetVisible = viewModel::setHobbyFilterSheetVisible,
            hobbyCategoryPickerVisible = viewModel::setHobbyCategoryPickerVisible
        )
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

    // 当前板块条目数：折叠可滚动性（顶栏 canScroll）与下拉刷新指示器高度共用的边界值
    val boardItemCount = uiState.currentBoardItemCount
    val boardHasFilterBar = uiState.boards.isNotEmpty()

    // 滚动记忆键：作用域 + 板块，跨「专区 ↔ 板块」切换互不覆盖
    val currentBoardKey = homeBoardMemoryKey(uiState.zoneScope, uiState.selectedBoard)

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
        canScroll = { boardItemCount > 0 }
    )

    // 没有可用板块时二级 Tab 与筛选行都不渲染：把两层实测高度归零，避免内容被空档顶开。
    LaunchedEffect(uiState.boards.isEmpty()) {
        if (uiState.boards.isEmpty()) headerStack.clearMeasuredRows()
    }

    // 实体 5 类没有筛选行：筛选项不再测量，必须显式清零，避免沿用上一板块的折叠高度。
    LaunchedEffect(uiState.selectedBoard.isEntity) {
        if (uiState.selectedBoard.isEntity) headerStack.resetCollapsibleHeight()
    }

    val pullRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()
    val bottomContentPadding = rememberBottomBarContentPadding(16.dp)
    val fabBottomPadding = rememberBottomBarFabPadding()
    val feedColumns = rememberFeedStaggeredGridColumns()
    // 二手列表沿用专区详情的列数策略（竖屏 1 列 / 横屏 2-3 列），与动态瀑布流不同
    val resaleColumns = rememberResaleStaggeredGridColumns()

    // ---- 二级板块横向分页 ----
    // 页数与顺序完全由 UiState.boards 决定（能力矩阵 ∩ 服务端 tabs）；页面以板块为 key，
    // 各页的瀑布流状态因此按板块保存，滑动来回不丢滚动位置。
    val boards = uiState.boards
    val selectedBoardIndex = boards.indexOf(uiState.selectedBoard).coerceAtLeast(0)
    // 页数下限 1：无可用板块时宿主不组合分页器（只渲染卡片式空态），避免 0 页状态被 Pager 拒绝
    val pagerState = rememberPagerState(initialPage = selectedBoardIndex) {
        boards.size.coerceAtLeast(1)
    }
    // 页内自持的瀑布流状态注册表：宿主只在「下拉刷新 / FAB」时借当前页回顶
    val pageGridStates = remember { mutableStateMapOf<ZoneBoard, LazyStaggeredGridState>() }

    // 折叠度按「作用域 + 板块」键恢复：切走前收起多少，切回就收起多少。
    // 列表为空/错误时纵向滚动被禁用（canScroll = boardItemCount > 0），必须强制展开，
    // 否则会恢复出「收起却展不开」的死状态；有内容后再按记忆恢复（effect 随 boardItemCount 重跑）。
    LaunchedEffect(currentBoardKey, headerStack.collapsibleHeightPx, boardItemCount > 0) {
        val filterHeight = headerStack.collapsibleHeightPx
        scrollBehavior.state.heightOffsetLimit = -filterHeight
        val fraction = if (boardItemCount > 0) {
            viewModel.scrollMemoryOrNull(currentBoardKey)?.filterCollapseFraction ?: 0f
        } else {
            0f
        }
        scrollBehavior.state.contentOffset = 0f
        scrollBehavior.state.heightOffset =
            (-fraction * filterHeight).coerceIn(-filterHeight, 0f)
    }

    // 翻页手势落定 → 同步 ViewModel 的当前板块（筛选行 / 刷新 / 翻页分派都以 selectedBoard 为准）。
    // 作用域切换会整体替换 boards：先把分页器对齐到新选中板块再开始监听，
    // 否则旧页码会被映射成新作用域的某个板块，切专区后落在错误的二级页。
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

    // 板块变更（Tab 点击 / 专区切换 / 冷启动恢复）反向驱动分页器位置
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
            isRefreshing = uiState.currentBoardIsRefreshing,
            onRefresh = {
                scope.launch { scrollCurrentBoardToTop() }
                viewModel.refreshFeed()
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = uiState.currentBoardIsRefreshing,
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
                // 该专区没有任何已接入板块：不渲染空 Tab 条与分页器，只给卡片式空态与刷新入口（§3.6）
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
                    // 二级板块左右滑分页：每页自持瀑布流状态与四态，页面以板块为 key。
                    // 相邻页不预取：落定后由 selectBoard 发起该板块首屏请求，未请求过的页先给加载态。
                    HorizontalPager(
                        state = pagerState,
                        key = { index -> boards[index] },
                        beyondViewportPageCount = 0,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val board = boards[page]
                        HomeBoardPage(
                            board = board,
                            uiState = uiState,
                            view = uiState.boardView(board),
                            viewModel = viewModel,
                            headerStack = headerStack,
                            scrollBehavior = scrollBehavior,
                            columns = if (board == ZoneBoard.Resale) resaleColumns else feedColumns,
                            bottomContentPadding = bottomContentPadding,
                            boardActions = boardActions,
                            boardItemActions = boardItemActions,
                            hobbyCategoryTree = hobbyCategoryTree,
                            onNavigateToZoneBrowse = onNavigateToZoneBrowse,
                            onGridStateReady = { boardKey, state -> pageGridStates[boardKey] = state }
                        )
                    }
                }
            }
        }

        // 产品两个弹层挂在四态之外（见 `ZoneBoardPane` 约束 6）：空/错/加载态下筛选入口仍可用，
        // 也不会因面板重新挂载而残留弹层。
        if (uiState.selectedBoard == ZoneBoard.Hobby) {
            ZoneBoardHobbySheets(
                filters = uiState.currentBoardFilters,
                hobbyCategoryTree = hobbyCategoryTree,
                actions = boardActions
            )
        }

        // 三级筛选行已移入各板块页（`HomeBoardPage`）：随分页器横向滑动，
        // 折叠位移仍由共享的 `scrollBehavior` 驱动，高度回填只认当前页。

        // 二级板块 Tab（常驻吸顶，不随滚动折叠）：渲染于筛选行之后，折叠时遮盖筛选行。
        // 点击走 ViewModel（立即切板块、立即发请求），指示器跟随分页器当前页实时移动；
        // 左右滑手势由分页器处理，落定后再回写 ViewModel。
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
                                // 单专区优先服务端 title、空则回退枚举文案；聚合态无服务端 title（A8）
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
 * 单个板块页：四态 + 瀑布流内容 + 该板块自己的滚动状态与触底翻页。
 *
 * 分页器会同时组合相邻页，因此这里只读传入的 [view]（按 [board] 投影），不读宿主选中的板块：
 * - 滚动位置、筛选变化回顶、刷新收尾只作用于本页；
 * - 触底翻页只在 `board == selectedBoard` 时发起（ViewModel 按选中板块派发请求）；
 * - 未请求过首屏的板块由投影给 `isLoading`，滑动到该页不会先闪一帧空态卡片。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeBoardPage(
    board: ZoneBoard,
    uiState: HomeFeedUiState,
    view: HomeBoardView,
    viewModel: HomeFeedViewModel,
    headerStack: HomeHeaderStackState,
    scrollBehavior: TopAppBarScrollBehavior,
    columns: StaggeredGridCells,
    /** 内容底部留白（底部悬浮栏高度，Dp；与面板 contentPadding 的 bottom 同源）。 */
    bottomContentPadding: Dp,
    boardActions: ZoneBoardActions,
    boardItemActions: ZoneBoardItemActions,
    hobbyCategoryTree: Map<Int, List<CategoryItem>>,
    onNavigateToZoneBrowse: (() -> Unit)?,
    onGridStateReady: (ZoneBoard, LazyStaggeredGridState) -> Unit
) {
    val gridState = rememberLazyStaggeredGridState()
    LaunchedEffect(gridState, board) { onGridStateReady(board, gridState) }

    val isCurrent = board == uiState.selectedBoard
    val memoryKey = homeBoardMemoryKey(uiState.zoneScope, board)
    val itemCount = view.itemCount
    val errorMessage = view.errorMessage
    // 卡片式空态：既不是加载也不是错误时才成立（未请求过的板块已由投影计入加载态）
    val contentEmpty = itemCount == 0 && errorMessage == null && !view.isLoading
    val errorEmpty = errorMessage != null && itemCount == 0
    var restoredKey by remember { mutableStateOf<String?>(null) }
    // 本页筛选行实测高度：内容顶部留白按「本页」算，滑动中两页各自对齐，不会互相遮挡。
    // 用 saveable 保存：页被分页器回收后重建时不至于先套用别的板块的留白。
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

    // 上拉触底翻页判断（本页自己的瀑布流状态）
    val shouldLoadMore by remember(gridState) {
        derivedStateOf {
            val totalCount = gridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalCount > 0 && lastVisibleIndex >= totalCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, isCurrent, view.hasMore, view.isLoadingMore, view.loadMoreError) {
        if (isCurrent && shouldLoadMore && view.hasMore &&
            !view.isLoadingMore && view.loadMoreError == null
        ) {
            boardActions.onLoadMore()
        }
    }

    // 按「作用域 + 板块」键恢复滚动位置：首屏、切专区、滑回本页都走这里。
    // 记忆跨专区保留，但切专区后只重新加载第一页：下标可能超出当前已加载条目数，
    // 越界时按「无记忆」处理并清掉脏数据，否则 Lazy 网格会停在已加载页末尾。
    LaunchedEffect(memoryKey, itemCount > 0) {
        if (itemCount <= 0) return@LaunchedEffect
        if (restoredKey == memoryKey) return@LaunchedEffect
        val memory = viewModel.scrollMemoryFor(memoryKey)
        val inRange = memory.index in 0 until itemCount
        if (inRange && (memory.index != 0 || memory.offset != 0)) {
            gridState.scrollToItem(memory.index, memory.offset)
        } else {
            if (!inRange) viewModel.resetBoardScrollMemory(memoryKey)
            gridState.scrollToItem(0)
        }
        restoredKey = memoryKey
    }

    // 滚动位置 / 折叠比例按本板块键保存（仅内存；持久化为 #156）。
    // 只写回「已完成恢复」的键：切换瞬间旧板块的首帧不会把新键的位置写坏（A1）。
    LaunchedEffect(gridState, scrollBehavior.state, memoryKey) {
        snapshotFlow {
            val driverHeight = headerStack.collapsibleHeightPx
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
            // 只写当前页：拖动中相邻页也在组合，它算出的比例属于共享的 heightOffset（另一个板块），
            // 写回会污染本页记忆。滚动位置还需已按要求恢复过（A1）。
            if (!isCurrent) return@collect
            if (restoredKey == memoryKey) {
                viewModel.saveBoardScrollPosition(memoryKey, index, offset)
            }
            viewModel.saveBoardFilterCollapseFraction(memoryKey, filterCollapseFraction)
        }
    }

    // 同一板块内筛选变化：回顶 + 展开筛选层 + 清空本板块记忆（换板块不算筛选变化）
    val filterToken = uiState.boardFilterToken(board)
    var lastFilterToken by remember(memoryKey) { mutableStateOf(filterToken) }
    LaunchedEffect(filterToken, memoryKey) {
        if (filterToken == lastFilterToken) return@LaunchedEffect
        lastFilterToken = filterToken
        viewModel.resetBoardScrollMemory(memoryKey)
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
        gridState.scrollToItem(0)
        restoredKey = memoryKey
    }

    // 刷新收尾：完成且无错误时回顶。只在本页仍是选中板块时动手，
    // 刷新途中滑到别的板块不会把新板块滚回顶部（本轮修复的跨板块副作用）。
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(view.isRefreshing) {
        if (wasRefreshing && !view.isRefreshing && errorMessage == null && isCurrent) {
            gridState.scrollToItem(0)
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
        wasRefreshing = view.isRefreshing
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            view.isLoading -> {
                PoiLoadingView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topInset, bottom = bottomContentPadding)
                )
            }

            // 卡片式错误态：与专区详情共用同一基元；产品板块同样纳入四态（A5 / Spec c5）
            errorEmpty -> {
                ZoneErrorPlaceholder(
                    message = errorMessage ?: "数据加载失败",
                    onRetry = { if (isCurrent) boardActions.onRetryInitial() },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = topInset)
                        .padding(24.dp)
                )
            }

            // 卡片式空态：与专区详情共用同一基元；关注为空时换成引导页（不发起请求）
            contentEmpty -> {
                if (uiState.zoneScope.isFollowEmpty && board != ZoneBoard.Posts) {
                    // 未关注任何专区：「关注」仍可选中，但只引导去专区广场，不发起请求。
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
                        // 文案与共享面板同源（ZoneBoard.emptyText），避免产品板块落到动态措辞
                        text = board.emptyText,
                        onRefresh = { if (isCurrent) viewModel.refreshFeed() },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = topInset)
                            .padding(24.dp)
                    )
                }
            }

            else -> {
                // 有内容：走共享板块面板（与专区详情同一内容渲染入口）；
                // 加载 / 错误 / 空三态由上方卡片式分支接管，pane 只负责列表与页脚。
                ZoneBoardPane(
                    board = board,
                    content = view.content,
                    gridState = gridState,
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = topInset + 6.dp,
                        bottom = bottomContentPadding
                    ),
                    actions = boardActions,
                    itemActions = boardItemActions,
                    columns = columns,
                    // 置顶帖条是单专区语义：聚合态（全部）不渲染
                    showPinnedPosts = uiState.zoneScope is ZoneQueryScope.Single,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // 三级筛选行属于本页：随分页器一起横向滑动（滑动中能同时看到相邻页的筛选条），
        // 折叠位移仍由共享 `scrollBehavior` 驱动。高度只由当前页回填：
        // 拖动中相邻页同时组合，两页都写会把头部高度栈来回覆盖；
        // 且相邻页先测量、落定后才成为当前页，所以回填走 `LaunchedEffect` 而不是直接写在 onSizeChanged 里。
        if (!board.isEntity) {
            LaunchedEffect(isCurrent, filterBarHeightPx) {
                if (isCurrent && filterBarHeightPx > 0) {
                    headerStack.onCollapsibleMeasured(filterBarHeightPx)
                }
            }
            // 筛选条横向滚动位置按「作用域 + 板块」记忆：Chip 条滚到一半，切走再切回仍在原处。
            // 恢复要等内容测量完（maxValue 就绪）再对齐，并先置位 restored 再开始写回，
            // 否则恢复过程中的 0 会把记忆清掉。
            val filterRowScroll = remember { ScrollState(0) }
            var filterRowRestored by remember(memoryKey) { mutableStateOf(false) }
            LaunchedEffect(memoryKey, filterRowScroll.maxValue) {
                if (filterRowRestored) return@LaunchedEffect
                val savedX = viewModel.scrollMemoryFor(memoryKey).filterRowScrollX
                // 没有横向溢出时不需要等测量；有记忆值则等 maxValue 就绪再对齐
                if (savedX > 0 && filterRowScroll.maxValue <= 0) return@LaunchedEffect
                if (savedX > 0) filterRowScroll.scrollTo(savedX)
                filterRowRestored = true
            }
            LaunchedEffect(memoryKey, filterRowScroll) {
                snapshotFlow { filterRowScroll.value }.collect { scrollX ->
                    if (filterRowRestored) viewModel.saveBoardFilterRowScroll(memoryKey, scrollX)
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = headerStack.pinnedHeight)
                    .offset { IntOffset(x = 0, y = scrollBehavior.state.heightOffset.roundToInt()) }
                    .onSizeChanged { filterBarHeightPx = it.height }
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                ZoneBoardFilterBar(
                    board = board,
                    filters = view.filters,
                    subTypeOptions = viewModel.subTypes,
                    actions = boardActions,
                    albumCategoryOptions = uiState.albumCategoryOptions,
                    hobbyCategoryTree = hobbyCategoryTree,
                    hobbyIsGridMode = uiState.hobbyBoard.isGridMode,
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
