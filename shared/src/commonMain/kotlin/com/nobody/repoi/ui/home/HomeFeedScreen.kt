package com.nobody.repoi.ui.home

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
    val staggeredGridState = rememberLazyStaggeredGridState()
    val imageViewer = LocalImageViewer.current
    val messages = LocalAppMessages.current
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
            resaleKeywordChange = viewModel::updateResaleKeyword,
            resaleSearch = viewModel::submitResaleSearch,
            retryInitial = viewModel::retrySelectedBoard,
            loadMore = viewModel::loadMoreSelectedBoard,
            hobbyOrder = viewModel::selectHobbyOrder,
            hobbyYear = viewModel::selectHobbyYear,
            hobbyMonth = viewModel::selectHobbyMonth,
            hobbyCategory = viewModel::selectHobbyCategory,
            hobbyKeywordChange = viewModel::updateHobbyKeyword,
            hobbySearch = viewModel::submitHobbySearch,
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
            onHobbyClick = handleHobbyClick,
            onEntityClick = onEntityClick
        )
    }

    // 当前板块内容/分页派生值：三类板块统一经 UiState 的 currentBoard* 投影，宿主不再按板块散写 when
    val boardItemCount = uiState.currentBoardItemCount
    val boardHasMore = uiState.currentBoardHasMore
    val boardIsLoadingMore = uiState.currentBoardIsLoadingMore
    val boardLoadMoreError = uiState.currentBoardLoadMoreError
    val boardErrorMessage = uiState.currentBoardErrorMessage
    val boardLoading = uiState.isCurrentBoardLoading
    val boardErrorEmpty = boardErrorMessage != null && boardItemCount == 0
    // 懒加载哨兵：尚未请求过首屏（且无错误）时已计入加载态，此处只兜「既不是加载也不是错误」的真空
    val boardContentEmpty = boardItemCount == 0 && boardErrorMessage == null && !boardLoading
    val boardHasFilterBar = uiState.boards.isNotEmpty()

    // 上拉触底翻页判断 (瀑布流模式)
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalCount = staggeredGridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = staggeredGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalCount > 0 && lastVisibleIndex >= totalCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore, uiState.selectedBoard, boardItemCount) {
        if (shouldLoadMore && boardHasMore && !boardIsLoadingMore && boardLoadMoreError == null) {
            viewModel.loadMoreSelectedBoard()
        }
    }

    // 滚动记忆键：作用域 + 板块，跨「专区 ↔ 板块」切换互不覆盖
    val currentBoardKey = homeBoardMemoryKey(uiState.zoneScope, uiState.selectedBoard)
    val memoryKey by rememberUpdatedState(currentBoardKey)
    // 已完成恢复的键（按 key 记录而不是一次性布尔）：切换回有记忆的板块时仍会恢复，首次进入才回顶
    var restoredKey by remember { mutableStateOf<String?>(null) }

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
    // 二手列表沿用专区详情的列数策略（竖屏 1 列 / 横屏 2-3 列），与动态瀑布流不同
    val resaleColumns = rememberResaleStaggeredGridColumns()

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
            // 只写回「已完成恢复」的键：切换瞬间旧板块的首帧不会把新键的位置写坏（A1）
            if (restoredKey == memoryKey) {
                viewModel.saveBoardScrollPosition(memoryKey, currentIndex, currentOffset)
                viewModel.saveBoardFilterCollapseFraction(memoryKey, filterCollapseFraction)
            }
        }
    }

    // 切换筛选：复位到顶 + 展开筛选条，并清空当前键的滚动记忆。
    // 切换专区 / 板块不在这里复位——交给下方按 key 的恢复逻辑（有记忆则恢复，首次进入才回顶）。
    // 筛选 token 按板块取当前生效值（动态 = subType；分享 = 排序 + 分类；讨论 = order；
    // 二手 = 出售求购 + 成色；产品 = 全部筛选值），因此切换筛选与切换板块用同一套 token 比较。
    val boardFilterToken = when (uiState.selectedBoard) {
        ZoneBoard.Album -> "${uiState.albumSelectedOrder}|${uiState.albumSelectedCategory ?: "all"}"
        ZoneBoard.Posts -> uiState.posts.order
        ZoneBoard.Resale -> "${uiState.resale.type}|${uiState.resale.purity ?: "all"}"
        ZoneBoard.Hobby -> with(uiState.hobbyBoard.filter) {
            listOf(
                order,
                categoryId,
                subCategoryId ?: "all",
                releaseYear ?: "all",
                releaseMonth ?: "all",
                keyword,
                sex ?: "all",
                specs ?: "all",
                scale ?: "all",
                r18 ?: "all"
            ).joinToString("|")
        }
        else -> uiState.selectedSubType
    }
    var lastSelection by remember {
        mutableStateOf(
            BoardSelectionKey(uiState.selectedCategory.tabKey, uiState.selectedBoard, boardFilterToken)
        )
    }
    LaunchedEffect(uiState.selectedCategory, uiState.selectedBoard, boardFilterToken) {
        val selection = BoardSelectionKey(
            uiState.selectedCategory.tabKey,
            uiState.selectedBoard,
            boardFilterToken
        )
        if (selection == lastSelection) return@LaunchedEffect
        val previous = lastSelection
        lastSelection = selection
        // 仅「同一专区同一板块内的筛选变化」清空记忆并回顶；专区/板块切换由恢复逻辑处理（A1）
        if (previous.tabKey == selection.tabKey && previous.board == selection.board) {
            viewModel.resetBoardScrollMemory(currentBoardKey)
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
            staggeredGridState.scrollToItem(0)
            restoredKey = currentBoardKey
        }
    }

    // 按「作用域 + 板块」键恢复滚动位置与折叠比例：切到有记忆的板块则恢复，首次进入才回顶；
    // 首屏（含从二级页返回）同样走这里。restoredKey 在恢复动作完成后才置位，避免恢复过程中的
    // 中间滚动位置被快照写回覆盖记忆（A1 / US18 / US19）。
    LaunchedEffect(currentBoardKey, boardItemCount > 0) {
        if (boardItemCount <= 0) return@LaunchedEffect
        if (restoredKey == currentBoardKey) return@LaunchedEffect
        val memory = viewModel.scrollMemoryFor(currentBoardKey)
        if (memory.index != 0 || memory.offset != 0) {
            staggeredGridState.scrollToItem(memory.index, memory.offset)
        } else {
            // 该键尚无记忆：首次进入该板块，回顶并展开筛选层
            staggeredGridState.scrollToItem(0)
        }
        scrollBehavior.state.contentOffset = 0f
        scrollBehavior.state.heightOffset =
            -memory.filterCollapseFraction * headerStack.collapsibleHeightPx
        restoredKey = currentBoardKey
    }

    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.currentBoardIsRefreshing) {
        if (wasRefreshing && !uiState.currentBoardIsRefreshing && boardErrorMessage == null) {
            staggeredGridState.scrollToItem(0)
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
        wasRefreshing = uiState.currentBoardIsRefreshing
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PullToRefreshBox(
            isRefreshing = uiState.currentBoardIsRefreshing,
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
                when {
                    // 该专区没有任何已接入板块：不渲染空 Tab 条，只给卡片式空态与刷新入口（§3.6）
                    uiState.boards.isEmpty() -> {
                        ZoneEmptyPlaceholder(
                            text = "该专区暂无可用板块",
                            onRefresh = { viewModel.refreshBoards() },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(top = headerStack.totalHeaderHeight)
                                .padding(24.dp)
                        )
                    }

                    boardLoading -> {
                        PoiLoadingView(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = headerStack.totalHeaderHeight,
                                    bottom = bottomContentPadding
                                )
                        )
                    }

                    // 卡片式错误态：与专区详情共用同一基元；产品板块同样纳入四态（A5 / Spec c5）
                    boardErrorEmpty -> {
                        ZoneErrorPlaceholder(
                            message = boardErrorMessage ?: "数据加载失败",
                            onRetry = { viewModel.retrySelectedBoard() },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(top = headerStack.totalHeaderHeight)
                                .padding(24.dp)
                        )
                    }

                    // 卡片式空态：与专区详情共用同一基元；关注为空时换成引导页（不发起请求）
                    boardContentEmpty -> {
                        if (uiState.zoneScope.isFollowEmpty && uiState.selectedBoard != ZoneBoard.Posts) {
                            // 未关注任何专区：「关注」仍可选中，但只引导去专区广场，不发起请求。
                            ZoneBoardStatusCard(
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(top = headerStack.totalHeaderHeight)
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
                                text = when {
                                    uiState.selectedBoard == ZoneBoard.Posts -> "暂无相关讨论"
                                    uiState.selectedBoard == ZoneBoard.Album -> "暂无相关分享"
                                    uiState.selectedBoard == ZoneBoard.Resale -> "暂无相关二手条目"
                                    uiState.selectedBoard.isEntity -> "暂无相关条目"
                                    else -> "暂无该分类下的动态情报"
                                },
                                onRefresh = { viewModel.refreshFeed() },
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(top = headerStack.totalHeaderHeight)
                                    .padding(24.dp)
                            )
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
                            columns = if (uiState.selectedBoard == ZoneBoard.Resale) resaleColumns else feedColumns,
                            hobbyCategoryTree = hobbyCategoryTree,
                            // 置顶帖条是单专区语义：聚合态（全部）不渲染
                            showPinnedPosts = uiState.zoneScope is ZoneQueryScope.Single,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // 三级筛选行（可折叠）：位移由 scrollBehavior 驱动，高度实测回填头部高度栈
        if (boardHasFilterBar) {
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
                    albumCategoryOptions = uiState.albumCategoryOptions,
                    hobbyCategoryTree = hobbyCategoryTree,
                    hobbyIsGridMode = uiState.hobbyBoard.isGridMode
                )
            }
        }

        // 二级板块 Tab（常驻吸顶，不随滚动折叠）：渲染于筛选行之后，折叠时遮盖筛选行
        if (boardHasFilterBar) {
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
 * 「专区 + 板块 + 该板块筛选值」的选择快照。
 *
 * 用于判断「专区/板块/筛选」是否真的变了——变了才复位到顶并清空新键滚动记忆。
 * 筛选维度按板块取（动态 `subType` / 讨论 `order`），后续板块 ticket 在这里加自己的字段。
 */
private data class BoardSelectionKey(
    val tabKey: String,
    val board: ZoneBoard,
    val filterKey: String
)

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
