package com.nobody.repoi.ui.zone

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import com.nobody.repoi.platform.isLandscapeOrientation
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.repoi.data.local.CategoryItem
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.data.remote.model.ResaleSearchItem
import com.nobody.repoi.data.remote.model.ZoneEntityItem
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardActions
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.board.ZoneBoardFilters
import com.nobody.repoi.ui.board.ZoneBoardItemActions
import com.nobody.repoi.ui.board.ZoneBoardPageView
import com.nobody.repoi.ui.board.ZoneBoardPane
import com.nobody.repoi.ui.board.components.ZoneBoardFilterBar
import com.nobody.repoi.ui.board.rememberResaleStaggeredGridColumns
import com.nobody.repoi.ui.board.components.ZoneErrorPlaceholder
import com.nobody.repoi.ui.board.components.ZonePaginationFooter
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.rememberSynchronousTopAppBarScrollBehavior
import com.nobody.repoi.ui.detail.DetailNavParam
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.nobody.repoi.ui.home.rememberFeedStaggeredGridColumns
import com.nobody.repoi.ui.item.list.components.HobbyAdvancedFilterParam
import com.nobody.repoi.ui.navigation.CharacterNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam
import com.nobody.repoi.ui.resale.components.ResaleMoreBottomSheet
import com.nobody.repoi.ui.resale.components.ResaleReportDialog
import com.nobody.repoi.ui.zone.components.ZonePublishSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneDetailScreen(
    nodeId: Long,
    initialName: String? = null,
    initialIcon: String? = null,
    initialCover: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: ((DetailNavParam) -> Unit)? = null,
    onNavigateToAlbum: ((AlbumNavParam) -> Unit)? = null,
    onNavigateToPerson: ((PersonNavParam) -> Unit)? = null,
    onNavigateToWorks: ((WorksNavParam) -> Unit)? = null,
    onNavigateToVendor: ((VendorNavParam) -> Unit)? = null,
    onNavigateToCharacter: ((CharacterNavParam) -> Unit)? = null,
    onNavigateToSeries: ((SeriesNavParam) -> Unit)? = null,
    onNavigateToUser: ((Long) -> Unit)? = null,
    onNavigateToPost: ((Long, Long?, String?) -> Unit)? = null,
    onNavigateToResaleDetail: ((Long) -> Unit)? = null,
    onNavigateToResaleMarket: ((Long, String?) -> Unit)? = null,
    onActionClick: ((ActionItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ZoneDetailViewModel = koinViewModel()
) {
    LaunchedEffect(nodeId) {
        viewModel.setNodeId(nodeId, initialName, initialIcon, initialCover)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 产品分类树：筛选条子分类回显 + 共享面板分类级联弹层（与首页同一数据源 CategoryManager）
    val categoryTree by viewModel.categoryTree.collectAsStateWithLifecycle()
    val staggeredGridState = rememberLazyStaggeredGridState()
    val pullRefreshState = rememberPullToRefreshState()
    val messages = LocalAppMessages.current
    val focusManager = LocalFocusManager.current
    val imageViewer = LocalImageViewer.current

    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val windowSizeClass = adaptiveInfo.windowSizeClass
    val isLandscape = isLandscapeOrientation()
    val isWideScreen = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomContentPadding = if (!isWideScreen) 16.dp else (16.dp + navBarBottomPadding)

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // 常驻吸顶层与折叠层高度：实测仅用于位移锚点与折叠上限，内容边距使用固定估高
    // 提供合理的初始默认值，防止首帧 measurement 前的 layout shift
    val defaultPinnedPx = with(density) { (if (uiState.isHobbyTab || uiState.isResaleTab) 96.dp else 48.dp).toPx() }
    val defaultCollapsiblePx = with(density) { (if (uiState.isEntityTab) 0.dp else 48.dp).toPx() }
    var pinnedHeaderHeightPx by remember { mutableFloatStateOf(defaultPinnedPx) }
    var collapsibleHeightPx by remember { mutableFloatStateOf(defaultCollapsiblePx) }
    // 实测常驻栏高度仅用于中层折叠栏位移锚点
    val pinnedHeaderHeightDp = with(density) { pinnedHeaderHeightPx.toDp() }
    // 内容边距固定估高，不回灌实测值
    val pinnedEstimatedDp = if (uiState.isHobbyTab || uiState.isResaleTab) 96.dp else 48.dp
    val collapsibleEstimatedDp = if (uiState.isEntityTab) 0.dp else 48.dp
    val collapsibleMeasuredDp = with(density) { collapsibleHeightPx.toDp() }
    val totalHeaderHeightDp = (if (pinnedHeaderHeightDp > pinnedEstimatedDp) pinnedHeaderHeightDp else pinnedEstimatedDp) +
        if (collapsibleMeasuredDp > collapsibleEstimatedDp) collapsibleMeasuredDp else collapsibleEstimatedDp

    val currentTabTotalItemsCount = when {
        uiState.isAlbumTab -> uiState.albumItems.size
        uiState.isPostsTab -> uiState.postsItems.size
        uiState.isHobbyTab -> uiState.hobbyState.items.size
        uiState.isResaleTab -> uiState.resaleState.items.size
        uiState.isEntityTab -> uiState.currentEntityState.items.size
        else -> uiState.items.size
    }

    val scrollBehavior = rememberSynchronousTopAppBarScrollBehavior(
        initialHeightOffsetLimit = -collapsibleHeightPx,
        canScroll = { currentTabTotalItemsCount > 0 }
    )

    // 折叠行程自动贴合实际高度
    LaunchedEffect(collapsibleHeightPx) {
        scrollBehavior.state.heightOffsetLimit = -collapsibleHeightPx
        if (collapsibleHeightPx == 0f) {
            scrollBehavior.state.heightOffset = 0f
        }
    }

    // 列数自适应（相册列数由共享面板内部按相册规则计算，此处只处理其余板块）
    val feedColumns = rememberFeedStaggeredGridColumns()

    val entityColumns = remember(windowSizeClass, isLandscape) {
        when {
            windowSizeClass.isWidthAtLeastBreakpoint(1200) || (isLandscape && windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)) -> StaggeredGridCells.Fixed(3)
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) -> StaggeredGridCells.Fixed(2)
            else -> StaggeredGridCells.Fixed(1)
        }
    }

    // 二手沿用共享列数策略（与首页同一实现，避免两屏视觉漂移）
    val resaleColumns = rememberResaleStaggeredGridColumns()

    // 列数自适应（实体 5 类走 pane，列数由 entityColumns 直接注入；相册与产品列数由 pane 内部按各自规则计算）
    val gridColumns = when {
        uiState.isEntityTab -> entityColumns
        uiState.isResaleTab -> resaleColumns
        uiState.isPostsTab -> feedColumns
        else -> feedColumns
    }

    // 触底翻页
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalCount = staggeredGridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = staggeredGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalCount > 0 && lastVisibleIndex >= totalCount - 3
        }
    }

    LaunchedEffect(
        shouldLoadMore,
        uiState.items.size,
        uiState.albumItems.size,
        uiState.postsItems.size,
        uiState.hobbyState.items.size,
        uiState.resaleState.items.size,
        uiState.currentEntityState.items.size,
        uiState.selectedTab
    ) {
        if (shouldLoadMore) {
            when {
                uiState.isAlbumTab -> {
                    if (uiState.hasMoreAlbum && !uiState.isAlbumLoadingMore && uiState.albumLoadMoreError == null) {
                        viewModel.loadNextAlbumPage()
                    }
                }
                uiState.isPostsTab -> {
                    if (uiState.hasMorePosts && !uiState.isPostsLoadingMore && uiState.postsLoadMoreError == null) {
                        viewModel.loadNextPostsPage()
                    }
                }
                uiState.isHobbyTab -> {
                    val hobby = uiState.hobbyState
                    if (!hobby.isEndReached && !hobby.isLoadingMore && hobby.loadMoreError == null) {
                        viewModel.loadNextHobbyPage()
                    }
                }
                uiState.isResaleTab -> {
                    val resale = uiState.resaleState
                    if (!resale.isEndReached && !resale.isLoadingMore && resale.loadMoreError == null) {
                        viewModel.loadNextResalePage()
                    }
                }
                uiState.isEntityTab -> {
                    val cur = uiState.currentEntityState
                    if (!cur.isEndReached && !cur.isLoadingMore && cur.loadMoreError == null) {
                        viewModel.loadNextEntityPage(uiState.selectedTab)
                    }
                }
                uiState.selectedTab == "action" -> {
                    if (uiState.hasMore && !uiState.isLoadingMore && uiState.loadMoreError == null) {
                        viewModel.loadNextPage()
                    }
                }
            }
        }
    }

    // 实时记忆当前分类的滚动位置到 ViewModel
    LaunchedEffect(staggeredGridState, uiState.selectedTab) {
        snapshotFlow {
            Pair(staggeredGridState.firstVisibleItemIndex, staggeredGridState.firstVisibleItemScrollOffset)
        }.collect { (index, offset) ->
            if (staggeredGridState.layoutInfo.totalItemsCount > 0) {
                viewModel.saveTabScrollPosition(uiState.selectedTab, index, offset)
            }
        }
    }

    // 记录上一轮的筛选条件，确保从二级页面返回时【绝不】误触发回顶
    var lastSelectedTab by rememberSaveable { mutableStateOf<String?>(null) }
    var lastSelectedSubType by rememberSaveable { mutableStateOf<String?>(null) }
    var lastAlbumOrder by rememberSaveable { mutableStateOf<String?>(null) }
    var lastAlbumCategory by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastPostsOrder by rememberSaveable { mutableStateOf<String?>(null) }
    var lastHobbyOrder by rememberSaveable { mutableStateOf<String?>(null) }
    var lastHobbyCategoryId by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastHobbySubCategoryId by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastResaleType by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastResalePurity by rememberSaveable { mutableStateOf<String?>(null) }

    // 主动切换 Tab 时平滑恢复该 Tab 的记忆位置；主动切换过滤条件时滚回顶部并展开
    LaunchedEffect(
        uiState.selectedTab,
        uiState.selectedSubType,
        uiState.albumSelectedOrder,
        uiState.albumSelectedCategory,
        uiState.postsSelectedOrder,
        uiState.hobbyState.order,
        uiState.hobbyState.categoryId,
        uiState.hobbyState.subCategoryId,
        uiState.resaleState.type,
        uiState.resaleState.purity
    ) {
        val isInitial = lastSelectedTab == null
        val isTabChanged = lastSelectedTab != null && lastSelectedTab != uiState.selectedTab
        val isFilterChanged = !isInitial && !isTabChanged && (
            lastSelectedSubType != uiState.selectedSubType ||
            lastAlbumOrder != uiState.albumSelectedOrder ||
            lastAlbumCategory != uiState.albumSelectedCategory ||
            lastPostsOrder != uiState.postsSelectedOrder ||
            lastHobbyOrder != uiState.hobbyState.order ||
            lastHobbyCategoryId != uiState.hobbyState.categoryId ||
            lastHobbySubCategoryId != uiState.hobbyState.subCategoryId ||
            lastResaleType != uiState.resaleState.type ||
            lastResalePurity != uiState.resaleState.purity
        )

        if (isTabChanged) {
            val (savedIndex, savedOffset) = viewModel.getTabScrollPosition(uiState.selectedTab)
            staggeredGridState.scrollToItem(savedIndex, savedOffset)
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        } else if (isFilterChanged) {
            viewModel.saveTabScrollPosition(uiState.selectedTab, 0, 0)
            staggeredGridState.scrollToItem(0)
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }

        lastSelectedTab = uiState.selectedTab
        lastSelectedSubType = uiState.selectedSubType
        lastAlbumOrder = uiState.albumSelectedOrder
        lastAlbumCategory = uiState.albumSelectedCategory
        lastPostsOrder = uiState.postsSelectedOrder
        lastHobbyOrder = uiState.hobbyState.order
        lastHobbyCategoryId = uiState.hobbyState.categoryId
        lastHobbySubCategoryId = uiState.hobbyState.subCategoryId
        lastResaleType = uiState.resaleState.type
        lastResalePurity = uiState.resaleState.purity
    }

    // 保险兜底：当从二级页面返回恢复或数据就绪时，若处于第 0 项但 ViewModel 中有记忆位置，安全恢复
    var hasRestoredCurrentTabPosition by remember(uiState.selectedTab) { mutableStateOf(false) }

    LaunchedEffect(uiState.selectedTab, currentTabTotalItemsCount > 0) {
        if (!hasRestoredCurrentTabPosition && currentTabTotalItemsCount > 0) {
            hasRestoredCurrentTabPosition = true
            val (savedIndex, savedOffset) = viewModel.getTabScrollPosition(uiState.selectedTab)
            if (savedIndex > 0 || savedOffset > 0) {
                if (staggeredGridState.firstVisibleItemIndex == 0) {
                    staggeredGridState.scrollToItem(savedIndex, savedOffset)
                }
            }
        }
    }

    // 下拉刷新完成且无错误时滚回顶部并展开折叠层
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isAnyRefreshing) {
        if (wasRefreshing && !uiState.isAnyRefreshing && uiState.errorMessage == null && uiState.albumErrorMessage == null && uiState.postsErrorMessage == null) {
            staggeredGridState.scrollToItem(0)
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
        wasRefreshing = uiState.isAnyRefreshing
    }

    val onCardImageClick = remember(imageViewer) {
        { rawUrls: List<String>, initialIndex: Int ->
            imageViewer.show(rawUrls, initialIndex)
        }
    }

    val handleAlbumClick = remember(onNavigateToAlbum) {
        { item: AlbumListItem ->
            if (item.targetAlbumId > 0) {
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

    val handleHobbyClick = remember(onNavigateToDetail) {
        { item: HobbyItemSummary ->
            val actualId = item.actualItemId
            if (actualId > 0L && onNavigateToDetail != null) {
                onNavigateToDetail(
                    DetailNavParam(
                        itemId = actualId,
                        initialName = item.displayName,
                        initialCover = item.cover
                    )
                )
            }
        }
    }

    val handleEntityClick = remember(
        uiState.selectedTab,
        onNavigateToVendor,
        onNavigateToSeries,
        onNavigateToWorks,
        onNavigateToCharacter,
        onNavigateToPerson
    ) {
        { item: ZoneEntityItem ->
            val targetId = item.targetId
            when (uiState.selectedTab) {
                "company" -> {
                    onNavigateToVendor?.invoke(
                        VendorNavParam(
                            companyId = targetId,
                            companyNodeId = if (item.id > 0L) item.id else 0L,
                            initialName = item.displayName,
                            initialCover = item.cover
                        )
                    )
                }
                "series" -> {
                    onNavigateToSeries?.invoke(
                        SeriesNavParam(
                            seriesId = targetId,
                            initialName = item.displayName,
                            initialCover = item.cover
                        )
                    )
                }
                "works" -> {
                    onNavigateToWorks?.invoke(
                        WorksNavParam(
                            worksId = targetId,
                            initialName = item.displayName,
                            initialCover = item.cover
                        )
                    )
                }
                "charactar" -> {
                    onNavigateToCharacter?.invoke(
                        CharacterNavParam(
                            characterId = targetId,
                            initialName = item.displayName,
                            initialCover = item.cover
                        )
                    )
                }
                "person" -> {
                    onNavigateToPerson?.invoke(
                        PersonNavParam(
                            personId = targetId,
                            initialName = item.displayName,
                            initialCover = item.cover
                        )
                    )
                }
            }
        }
    }

    var activeMoreResaleItem by remember { mutableStateOf<ResaleSearchItem?>(null) }
    var reportResaleTargetItem by remember { mutableStateOf<ResaleSearchItem?>(null) }

    val handleResaleClick = remember(onNavigateToDetail, onNavigateToResaleDetail) {
        { item: ResaleSearchItem ->
            if (item.isBuy) {
                val targetHobbyId = item.targetItemId
                if (targetHobbyId > 0L && onNavigateToDetail != null) {
                    onNavigateToDetail(DetailNavParam(itemId = targetHobbyId))
                } else {
                    messages.show("未关联条目详情")
                }
            } else {
                if (onNavigateToResaleDetail != null) {
                    onNavigateToResaleDetail(item.id)
                } else {
                    messages.show("二手出售详情即将上线")
                }
            }
        }
    }

    val handleResaleContactClick = remember {
        { item: ResaleSearchItem ->
            messages.show("私信功能即将上线")
        }
    }

    // 共享板块面板的宿主机适配：动作仍落到本页 ViewModel 的现有入口，不改变请求与分页语义。
    // 触底翻页按当前 Tab 分派（讨论板块有独立分页入口）；重试走 `refresh()`（内部按 Tab 分派）。
    val boardActions = remember(viewModel) {
        object : ZoneBoardActions {
            override fun onSelectSubType(subType: String) {
                viewModel.selectSubType(subType)
            }

            override fun onSelectPostsOrder(order: String) {
                viewModel.selectPostsOrder(order)
            }

            override fun onSelectAlbumOrder(order: String) {
                viewModel.selectAlbumOrder(order)
            }

            override fun onSelectAlbumCategory(categoryId: Int?) {
                viewModel.selectAlbumCategory(categoryId)
            }

            override fun onSelectResaleType(type: Int) {
                viewModel.setResaleType(type)
            }

            override fun onSelectResalePurity(purity: String?) {
                viewModel.setResalePurity(purity)
            }

            override fun onResaleKeywordChange(keyword: String) {
                viewModel.updateResaleKeyword(keyword)
            }

            override fun onResaleSearch() {
                viewModel.submitResaleSearch()
            }

            override fun onRetryInitial() {
                // 实体 5 类走 pane 后仍复用原错误重试入口（loadInitialEntity），保持行为不变
                val tab = viewModel.uiState.value.selectedTab
                if (tab in ZoneDetailUiState.ENTITY_TAB_TYPES) {
                    viewModel.loadInitialEntity(tab)
                } else {
                    viewModel.refresh()
                }
            }

            override fun onLoadMore() {
                val tab = viewModel.uiState.value.selectedTab
                when {
                    // 页脚「加载更多失败，点击重试」按当前 Tab 分派（面板不感知板块专属 loader）
                    viewModel.uiState.value.isResaleTab -> viewModel.loadNextResalePage()
                    // 实体 5 类仍在原实体 loader 上翻页
                    tab in ZoneDetailUiState.ENTITY_TAB_TYPES -> viewModel.loadNextEntityPage(tab)
                    viewModel.uiState.value.isPostsTab -> viewModel.loadNextPostsPage()
                    viewModel.uiState.value.isAlbumTab -> viewModel.loadNextAlbumPage()
                    viewModel.uiState.value.isHobbyTab -> viewModel.loadNextHobbyPage()
                    else -> viewModel.loadNextPage()
                }
            }

            override fun onHobbyOrder(order: String) {
                viewModel.setHobbyOrder(order)
            }

            override fun onHobbyYear(year: Int?) {
                viewModel.setHobbyYear(year)
            }

            override fun onHobbyMonth(month: Int?) {
                viewModel.setHobbyMonth(month)
            }

            override fun onHobbyCategory(categoryId: Int, subCategoryId: Int?) {
                viewModel.selectHobbyCategory(categoryId, subCategoryId)
            }

            override fun onHobbyKeywordChange(keyword: String) {
                viewModel.updateHobbyKeyword(keyword)
            }

            override fun onHobbySearch() {
                viewModel.submitHobbySearch()
            }

            override fun onHobbyAdvancedApply(filterParam: HobbyAdvancedFilterParam) {
                viewModel.applyHobbyAdvancedFilters(
                    sex = filterParam.sex,
                    specs = filterParam.specs,
                    scale = filterParam.scale,
                    r18 = filterParam.r18
                )
            }

            override fun onHobbyAdvancedReset() {
                viewModel.resetHobbyAdvancedFilters()
            }

            override fun onHobbyViewMode() {
                viewModel.toggleHobbyViewMode()
            }

            override fun onHobbyFilterSheetVisible(show: Boolean) {
                viewModel.openHobbyFilterSheet(show)
            }

            override fun onHobbyCategoryPickerVisible(show: Boolean) {
                viewModel.openHobbyCategoryPicker(show)
            }
        }
    }
    val boardItemActions = remember(
        onActionClick,
        onCardImageClick,
        handlePostClick,
        handleAlbumClick,
        handleResaleClick,
        onNavigateToUser,
        handleEntityClick,
        handleHobbyClick
    ) {
        ZoneBoardItemActions(
            onActionClick = onActionClick,
            onActionImageClick = onCardImageClick,
            onPostClick = handlePostClick,
            onPostImageClick = onCardImageClick,
            onPostUserClick = onNavigateToUser,
            onAlbumClick = handleAlbumClick,
            onResaleClick = handleResaleClick,
            onResaleContactClick = handleResaleContactClick,
            onResaleUserClick = onNavigateToUser,
            onResaleMoreClick = { item -> activeMoreResaleItem = item },
            onHobbyClick = handleHobbyClick,
            // pane 以 (board, item) 回调；本页跳转逻辑仍按 selectedTab 区分（行为不变）
            onEntityClick = { _, item -> handleEntityClick(item) }
        )
    }

    // 各板块筛选快照只构造一次：内容区（ZoneBoardPane）与筛选条（ZoneBoardFilterBar）读同一份，
    // 避免同一板块两处各构造一次导致漂移（#150 审查 B5）。
    val actionBoardFilters = remember(uiState.selectedSubType) {
        ZoneBoardFilters(subType = uiState.selectedSubType)
    }
    val albumBoardFilters = remember(uiState.albumSelectedOrder, uiState.albumSelectedCategory) {
        ZoneBoardFilters(
            albumOrder = uiState.albumSelectedOrder,
            albumCategory = uiState.albumSelectedCategory
        )
    }
    val resaleBoardFilters = remember(
        uiState.resaleState.type,
        uiState.resaleState.purity,
        uiState.resaleState.keyword
    ) {
        ZoneBoardFilters(
            resaleType = uiState.resaleState.type,
            resalePurity = uiState.resaleState.purity,
            resaleKeyword = uiState.resaleState.keyword
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.zoneInfo?.displayName ?: initialName ?: "专区详情",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${uiState.followCount} 人已关注",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    val isFocusToggling = uiState.isFocusToggling
                    val focusState = uiState.focusState

                    if (focusState) {
                        OutlinedButton(
                            onClick = { viewModel.toggleFocus() },
                            enabled = !isFocusToggling,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .padding(end = 8.dp)
                        ) {
                            if (isFocusToggling) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "已关注",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = { viewModel.toggleFocus() },
                            enabled = !isFocusToggling,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .padding(end = 8.dp)
                        ) {
                            if (isFocusToggling) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "+ 关注",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showPublishSheet(true) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "发布内容到专区"
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. 底层数据列表与下拉刷新主体（挂载嵌套协同滚动连接）
            PullToRefreshBox(
                isRefreshing = uiState.isAnyRefreshing,
                onRefresh = {
                    scope.launch {
                        staggeredGridState.scrollToItem(0)
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
                        isRefreshing = uiState.isAnyRefreshing,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = totalHeaderHeightDp)
                    )
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                ) {
                    val isCurrentTabLoading = when {
                        uiState.isAlbumTab -> uiState.isAlbumLoading && uiState.albumItems.isEmpty()
                        uiState.isPostsTab -> uiState.isPostsLoading && uiState.postsItems.isEmpty() && uiState.pinnedPosts.isEmpty()
                        uiState.isHobbyTab -> uiState.hobbyState.isLoading && uiState.hobbyState.items.isEmpty()
                        uiState.isResaleTab -> uiState.resaleState.isLoading && uiState.resaleState.items.isEmpty()
                        uiState.isEntityTab -> uiState.currentEntityState.isLoading && uiState.currentEntityState.items.isEmpty()
                        uiState.selectedTab == "action" -> uiState.isFeedLoading && uiState.items.isEmpty()
                        else -> false
                    }

                    if (isCurrentTabLoading) {
                        PoiLoadingView(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = totalHeaderHeightDp + 8.dp,
                                    bottom = bottomContentPadding
                                )
                        )
                    } else if (uiState.selectedTab == "action") {
                        // 动态板块：走共享面板（唯一内容渲染入口），其余板块本阶段仍用下方内联分支
                        ZoneBoardPane(
                            board = ZoneBoard.Action,
                            scope = viewModel.zoneScope,
                            content = ZoneBoardContent.Action(
                                items = uiState.items,
                                page = ZoneBoardPageView(
                                    isLoading = uiState.isFeedLoading,
                                    isLoadingMore = uiState.isLoadingMore,
                                    errorMessage = uiState.errorMessage,
                                    loadMoreError = uiState.loadMoreError,
                                    isEndReached = !uiState.hasMore
                                )
                            ),
                            filters = actionBoardFilters,
                            gridState = staggeredGridState,
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = totalHeaderHeightDp + 8.dp,
                                bottom = bottomContentPadding
                            ),
                            actions = boardActions,
                            itemActions = boardItemActions,
                            columns = feedColumns,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (uiState.isAlbumTab) {
                        // 分享板块：走共享面板（#157），与首页同一内容渲染入口；
                        // 列数由面板内部按相册策略计算，筛选值来自 ViewModel 投影。
                        ZoneBoardPane(
                            board = ZoneBoard.Album,
                            scope = viewModel.zoneScope,
                            content = ZoneBoardContent.Album(
                                items = uiState.albumItems,
                                page = ZoneBoardPageView(
                                    isLoading = uiState.isAlbumLoading,
                                    isLoadingMore = uiState.isAlbumLoadingMore,
                                    errorMessage = uiState.albumErrorMessage,
                                    loadMoreError = uiState.albumLoadMoreError,
                                    isEndReached = !uiState.hasMoreAlbum
                                )
                            ),
                            filters = albumBoardFilters,
                            gridState = staggeredGridState,
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = totalHeaderHeightDp + 8.dp,
                                bottom = bottomContentPadding
                            ),
                            actions = boardActions,
                            itemActions = boardItemActions,
                            columns = gridColumns,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (uiState.isPostsTab) {
                        // 讨论板块：走共享面板（与首页同一渲染入口），单专区恒展示置顶帖条
                        ZoneBoardPane(
                            board = ZoneBoard.Posts,
                            scope = viewModel.zoneScope,
                            content = uiState.postsBoardContent,
                            filters = uiState.postsBoardFilters,
                            gridState = staggeredGridState,
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = totalHeaderHeightDp + 8.dp,
                                bottom = bottomContentPadding
                            ),
                            actions = boardActions,
                            itemActions = boardItemActions,
                            columns = feedColumns,
                            showPinnedPosts = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (uiState.isResaleTab) {
                        // 二手板块：走共享面板（#160），与首页同一内容渲染入口；
                        // 搜索栏 / 更多操作 / 举报弹窗仍属专区详情 chrome，留在宿主渲染。
                        ZoneBoardPane(
                            board = ZoneBoard.Resale,
                            scope = viewModel.zoneScope,
                            content = ZoneBoardContent.Resale(
                                items = uiState.resaleState.items,
                                page = ZoneBoardPageView(
                                    isLoading = uiState.resaleState.isLoading,
                                    isLoadingMore = uiState.resaleState.isLoadingMore,
                                    errorMessage = uiState.resaleState.errorMessage,
                                    loadMoreError = uiState.resaleState.loadMoreError,
                                    isEndReached = uiState.resaleState.isEndReached
                                )
                            ),
                            filters = resaleBoardFilters,
                            gridState = staggeredGridState,
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = totalHeaderHeightDp + 8.dp,
                                bottom = bottomContentPadding
                            ),
                            actions = boardActions,
                            itemActions = boardItemActions,
                            columns = resaleColumns,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (uiState.isHobbyTab) {
                        // 产品板块：走共享面板（#159），与首页同一内容渲染入口；
                        // 列数由面板按网格 / 列表模式计算，筛选值与两个弹层显隐来自 ViewModel 投影。
                        ZoneBoardPane(
                            board = ZoneBoard.Hobby,
                            scope = viewModel.zoneScope,
                            content = uiState.hobbyBoardContent,
                            filters = uiState.hobbyBoardFilters,
                            gridState = staggeredGridState,
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = totalHeaderHeightDp + 8.dp,
                                bottom = bottomContentPadding
                            ),
                            actions = boardActions,
                            itemActions = boardItemActions,
                            columns = gridColumns,
                            hobbyCategoryTree = categoryTree,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (uiState.isEntityTab) {
                        // 实体 5 类（#161）：走共享面板，与首页同一内容渲染入口；
                        // 点击回调仍由本页 handleEntityClick 按 selectedTab 分发（行为不变）。
                        val entityState = uiState.currentEntityState
                        val entityBoard = ZoneBoard.fromType(uiState.selectedTab) ?: ZoneBoard.Company
                        ZoneBoardPane(
                            board = entityBoard,
                            scope = viewModel.zoneScope,
                            content = ZoneBoardContent.Entity(
                                items = entityState.items,
                                page = ZoneBoardPageView(
                                    isLoading = entityState.isLoading,
                                    isLoadingMore = entityState.isLoadingMore,
                                    errorMessage = entityState.errorMessage,
                                    loadMoreError = entityState.loadMoreError,
                                    isEndReached = entityState.isEndReached
                                )
                            ),
                            filters = ZoneBoardFilters(),
                            gridState = staggeredGridState,
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = totalHeaderHeightDp + 8.dp,
                                bottom = bottomContentPadding
                            ),
                            actions = boardActions,
                            itemActions = boardItemActions,
                            columns = entityColumns,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // 2. 中层随动折叠筛选栏（吸附于常驻栏正下方，随列表滚动平滑折叠）
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = pinnedHeaderHeightDp)
                    .offset { IntOffset(x = 0, y = scrollBehavior.state.heightOffset.roundToInt()) }
                    .onSizeChanged { size ->
                        collapsibleHeightPx = size.height.toFloat()
                    }
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                when {
                    uiState.isAlbumTab -> {
                        // 分享/相册：6 档排序 Chip + 分类入口，统一走共享筛选条（与首页同一实现）
                        ZoneBoardFilterBar(
                            board = ZoneBoard.Album,
                            filters = albumBoardFilters,
                            subTypeOptions = viewModel.subTypes,
                            actions = boardActions,
                            albumCategoryOptions = uiState.albumCategoryOptions
                        )
                    }
                    uiState.isPostsTab -> {
                        // 讨论排序胶囊：共享筛选条（回复/新贴/推荐；讨论无分类入口，见 PostsBoardOptions KDoc）
                        ZoneBoardFilterBar(
                            board = ZoneBoard.Posts,
                            filters = uiState.postsBoardFilters,
                            subTypeOptions = viewModel.subTypes,
                            actions = boardActions
                        )
                    }
                    uiState.isHobbyTab -> {
                        // 产品：排序 / 分类级联 / 年份 / 月份，统一走共享筛选条（与首页同一实现）；
                        // 高级筛选与网格切换入口保留在顶栏常驻控制栏（见下方「常驻搜索与控制栏」）。
                        ZoneBoardFilterBar(
                            board = ZoneBoard.Hobby,
                            filters = uiState.hobbyBoardFilters,
                            subTypeOptions = viewModel.subTypes,
                            actions = boardActions,
                            hobbyCategoryTree = categoryTree,
                            hobbyIsGridMode = uiState.hobbyState.isGridMode,
                            showHobbyControlActions = false
                        )
                    }
                    uiState.isResaleTab -> {
                        // 二手：出售/求购分段 + 成色 Chip，统一走共享筛选条（与首页同一实现）
                        ZoneBoardFilterBar(
                            board = ZoneBoard.Resale,
                            filters = resaleBoardFilters,
                            subTypeOptions = viewModel.subTypes,
                            actions = boardActions
                        )
                    }
                    uiState.selectedTab == "action" -> {
                        // 动态二级子分类胶囊：共享筛选条（选项仍由本页 ViewModel 提供，#153 合入后切共享定义）
                        ZoneBoardFilterBar(
                            board = ZoneBoard.Action,
                            filters = actionBoardFilters,
                            subTypeOptions = viewModel.subTypes,
                            actions = boardActions
                        )
                    }
                }
            }

            // 3. 顶层常驻吸顶层（一级 Tab 栏 + 搜索控制栏），Z 轴最高，背景遮盖折叠层
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        pinnedHeaderHeightPx = size.height.toFloat()
                    }
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // 一级 Tab 栏（由服务端返回或默认映射驱动）
                val selectedTabIndex = uiState.availableTabs.indexOfFirst { it.first == uiState.selectedTab }
                    .coerceAtLeast(0)
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    uiState.availableTabs.forEachIndexed { index, (tabType, tabTitle) ->
                        val isSelected = index == selectedTabIndex
                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.selectTab(tabType) },
                            text = {
                                Text(
                                    text = tabTitle,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                // 常驻搜索与控制栏（仅在有搜索栏的 Tab 中展示）
                when {
                    uiState.isHobbyTab -> {
                        val hobbyState = uiState.hobbyState
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    BasicTextField(
                                        value = hobbyState.keyword,
                                        onValueChange = { viewModel.updateHobbyKeyword(it) },
                                        singleLine = true,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                focusManager.clearFocus()
                                                viewModel.submitHobbySearch()
                                            }
                                        ),
                                        decorationBox = { innerTextField ->
                                            if (hobbyState.keyword.isEmpty()) {
                                                Text(
                                                    text = "在专区产品中搜索",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                            innerTextField()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (hobbyState.keyword.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                viewModel.updateHobbyKeyword("")
                                                viewModel.submitHobbySearch()
                                            },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "清空搜索",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            IconButton(
                                onClick = { viewModel.toggleHobbyViewMode() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (hobbyState.isGridMode) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView,
                                    contentDescription = if (hobbyState.isGridMode) "切换列表模式" else "切换网格模式",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { viewModel.openHobbyFilterSheet(true) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (hobbyState.hasAdvancedFilters) {
                                            Badge()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FilterList,
                                        contentDescription = "高级筛选",
                                        tint = if (hobbyState.hasAdvancedFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    uiState.isResaleTab -> {
                        val resaleState = uiState.resaleState
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    BasicTextField(
                                        value = resaleState.keyword,
                                        onValueChange = { viewModel.updateResaleKeyword(it) },
                                        singleLine = true,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                focusManager.clearFocus()
                                                viewModel.submitResaleSearch()
                                            }
                                        ),
                                        decorationBox = { innerTextField ->
                                            if (resaleState.keyword.isEmpty()) {
                                                Text(
                                                    text = "在二手交易中搜索",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                            innerTextField()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (resaleState.keyword.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                viewModel.updateResaleKeyword("")
                                                viewModel.submitResaleSearch()
                                            },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "清空搜索",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
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
    }

    // 4. 产品高级筛选 / 分类级联两个底板由共享面板 `ZoneBoardPane` 按筛选快照自渲染（与首页同一实现），
    //    本页不再重复内联（#159）。

    // 6. 发布弹窗
    if (uiState.showPublishSheet) {
        ZonePublishSheet(
            onDismiss = { viewModel.showPublishSheet(false) },
            onPublishPostClick = {
                messages.show("发布讨论贴功能即将上线")
            },
            onPublishAlbumClick = {
                messages.show("发布相册功能即将上线")
            },
            onPublishResaleClick = {
                messages.show("发布二手条目功能即将上线")
            }
        )
    }

    // 7. 二手卡片三点更多弹窗 (复用公共组件)
    val moreResaleItem = activeMoreResaleItem
    if (moreResaleItem != null) {
        ResaleMoreBottomSheet(
            item = moreResaleItem,
            onDismiss = { activeMoreResaleItem = null },
            onSelectSameResale = { selectedItem ->
                val targetHobbyId = selectedItem.targetItemId
                val figureName = selectedItem.item?.firstOrNull()?.nameCN ?: selectedItem.item?.firstOrNull()?.name
                if (targetHobbyId > 0L) {
                    onNavigateToResaleMarket?.invoke(targetHobbyId, figureName)
                } else {
                    messages.show("未关联手办详情")
                }
            },
            onSelectReport = { reportResaleTargetItem = it }
        )
    }

    // 8. 二手举报原因弹窗 (复用公共组件)
    val reportItem = reportResaleTargetItem
    if (reportItem != null) {
        var isReporting by remember { mutableStateOf(false) }
        ResaleReportDialog(
            isSubmitting = isReporting,
            onDismiss = { if (!isReporting) reportResaleTargetItem = null },
            onSubmit = { reason ->
                isReporting = true
                viewModel.submitResaleReport(reportItem.id, reason) { success, msg ->
                    isReporting = false
                    messages.show(msg)
                    if (success) {
                        reportResaleTargetItem = null
                    }
                }
            }
        )
    }
}
