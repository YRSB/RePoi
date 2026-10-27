package com.nobody.repoi.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import com.nobody.repoi.data.remote.model.SearchResultItem
import com.nobody.repoi.data.remote.model.SearchUserItem
import com.nobody.repoi.platform.PlatformActions
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.repoi.ui.common.PlatformBackHandler
import com.nobody.repoi.ui.common.rememberExitUntilCollapsedScrollBehavior
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.navigation.CharacterNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.search.components.AlbumFilterState
import com.nobody.repoi.ui.search.components.AlbumStaggeredCard
import com.nobody.repoi.ui.search.components.ElementsFilterState
import com.nobody.repoi.ui.search.components.EmptyResultPlaceholder
import com.nobody.repoi.ui.search.components.EveryoneSearchingSection
import com.nobody.repoi.ui.search.components.GeneralSearchCard
import com.nobody.repoi.ui.search.components.HobbyFilterState
import com.nobody.repoi.ui.search.components.HobbySearchCard
import com.nobody.repoi.ui.search.components.LoadingMoreFooter
import com.nobody.repoi.ui.search.components.SearchDiscoverySection
import com.nobody.repoi.ui.search.components.SearchFilterAdaptiveSheet
import com.nobody.repoi.ui.search.components.SearchHistorySection
import com.nobody.repoi.ui.search.components.SearchSuggestionsList
import com.nobody.repoi.ui.search.components.SearchTopBar
import com.nobody.repoi.ui.search.components.SearchUserCard
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/**
 * PoiShelf 全局搜索页
 * 放弃原生的搜索控件，采用通栏轻量搜索栏与自适应双栏设计。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navParam: SearchNavParam = SearchNavParam(),
    onBack: () -> Unit,
    onNavigateToDetail: (DetailNavParam) -> Unit,
    onNavigateToAlbum: (AlbumNavParam) -> Unit,
    onNavigateToVendor: ((VendorNavParam) -> Unit)? = null,
    onNavigateToSeries: ((SeriesNavParam) -> Unit)? = null,
    onNavigateToPerson: ((PersonNavParam) -> Unit)? = null,
    onNavigateToWorks: ((WorksNavParam) -> Unit)? = null,
    onNavigateToCharacter: ((CharacterNavParam) -> Unit)? = null,
    viewModel: SearchViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val platformActions = koinInject<PlatformActions>()
    val keyboardController = LocalSoftwareKeyboardController.current

    // 判断大屏环境以适配居中与双栏排版（达到 Expanded 宽度时为大屏双栏，其余为小屏单流）
    val isWideScreen = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

    // 是否处于搜索输入与联想落地页状态
    var isSearchInputActive by rememberSaveable {
        mutableStateOf(navParam.initialKeyword.isBlank() && navParam.initialCompanyNodeId == null)
    }

    // 筛选面板展开状态
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    // 滚动状态
    val userGridState = rememberLazyGridState()
    val resultGridState = rememberLazyGridState()
    val albumStaggeredGridState = rememberLazyStaggeredGridState()

    LaunchedEffect(navParam) {
        viewModel.initParams(
            navParam.initialKeyword,
            navParam.initialCategory,
            navParam.initialCompanyNodeId,
            navParam.initialCompanyName
        )
    }

    // 系统返回拦截：若处于输入落地页且已有结果，则收起到结果页；其余情况退出
    PlatformBackHandler(enabled = true) {
        if (isSearchInputActive) {
            if (uiState.isSubmitted && uiState.keyword.isNotBlank()) {
                isSearchInputActive = false
                keyboardController?.hide()
            } else {
                onBack()
            }
        } else {
            onBack()
        }
    }

    // 搜索会话变更时滚动回顶部
    LaunchedEffect(uiState.searchSessionId, uiState.items.isEmpty(), uiState.userItems.isEmpty()) {
        if (userGridState.layoutInfo.totalItemsCount > 0) userGridState.scrollToItem(0)
        if (resultGridState.layoutInfo.totalItemsCount > 0) resultGridState.scrollToItem(0)
        if (albumStaggeredGridState.layoutInfo.totalItemsCount > 0) albumStaggeredGridState.scrollToItem(0)
    }

    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val density = LocalDensity.current
    // 实测搜索栏高度仅用于折叠行程与位移，内容边距取估高与实测的最大值避免遮盖
    var searchBarHeightPx by remember { mutableFloatStateOf(with(density) { 56.dp.toPx() }) }
    val searchBarEstimatedDp = 56.dp
    val tabRowEstimatedDp = 48.dp
    val estimatedTopBarDp = searchBarEstimatedDp + tabRowEstimatedDp
    val measuredTopBarDp = with(density) { searchBarHeightPx.toDp() } + tabRowEstimatedDp
    val topBarTotalHeight = statusBarTopPadding + if (measuredTopBarDp > estimatedTopBarDp) measuredTopBarDp else estimatedTopBarDp

    // 顶部判定按当前页签拆分订阅，避免同时监听三个列表状态
    val isAtTopUser by remember {
        derivedStateOf {
            userGridState.firstVisibleItemIndex == 0 && userGridState.firstVisibleItemScrollOffset == 0
        }
    }
    val isAtTopAlbum by remember {
        derivedStateOf {
            albumStaggeredGridState.firstVisibleItemIndex == 0 && albumStaggeredGridState.firstVisibleItemScrollOffset == 0
        }
    }
    val isAtTopResult by remember {
        derivedStateOf {
            resultGridState.firstVisibleItemIndex == 0 && resultGridState.firstVisibleItemScrollOffset == 0
        }
    }
    val isAtTop = when (uiState.selectedTab) {
        SearchTab.USER -> isAtTopUser
        SearchTab.ALBUM -> isAtTopAlbum
        else -> isAtTopResult
    }

    val scrollBehavior = rememberExitUntilCollapsedScrollBehavior(
        initialHeightOffsetLimit = -searchBarHeightPx,
        canScroll = { uiState.isSubmitted && !isSearchInputActive },
        isAtTop = { isAtTop }
    )

    LaunchedEffect(uiState.selectedTab, uiState.searchSessionId) {
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
    }

    LaunchedEffect(searchBarHeightPx) {
        if (searchBarHeightPx > 0f) {
            scrollBehavior.state.heightOffsetLimit = -searchBarHeightPx
        }
    }

    LaunchedEffect(isSearchInputActive) {
        if (isSearchInputActive) {
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
            viewModel.onSearchExpanded()
        }
    }

    val onPerformSearchAction: (String) -> Unit = { query ->
        keyboardController?.hide()
        isSearchInputActive = false
        viewModel.onSearch(query)
    }

    if (isSearchInputActive || !uiState.isSubmitted) {
        // 模式 A：搜索输入与联想落地页态
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            // 顶栏
            SearchTopBar(
                query = uiState.keyword,
                onQueryChange = {
                    viewModel.onKeywordChange(it)
                    if (!isSearchInputActive) isSearchInputActive = true
                },
                onSearch = onPerformSearchAction,
                onBackClick = {
                    if (isSearchInputActive && uiState.isSubmitted && uiState.keyword.isNotBlank()) {
                        isSearchInputActive = false
                        keyboardController?.hide()
                    } else {
                        onBack()
                    }
                },
                onClearClick = {
                    viewModel.onClearKeyword()
                    isSearchInputActive = true
                },
                placeholderText = "搜索",
                showFilterButton = false
            )

            // 落地页内容区 (支持小屏单列与大屏双栏响应式排布)
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. 实时联想建议 (若有输入且建议列表非空)
                if (uiState.keyword.isNotBlank() && uiState.suggestions.isNotEmpty()) {
                    SearchSuggestionsList(
                        suggestions = uiState.suggestions,
                        query = uiState.keyword,
                        onSelectWord = onPerformSearchAction
                    )
                }

                // 2. 核心内容排布：有搜索记录且大屏时采用左右双栏；无搜索记录时“大家在找”拓展至全宽
                if (isWideScreen && uiState.searchHistory.isNotEmpty()) {
                    // 大屏双栏排版：左边大家在找，右边搜索历史
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        // 左栏：大家在找 (流式 chip 同款布局)
                        Box(modifier = Modifier.weight(1f)) {
                            EveryoneSearchingSection(
                                hotwords = uiState.hotwordsMap[uiState.selectedTab.categoryId].orEmpty(),
                                isLoading = uiState.isLoadingHotwords,
                                onRefresh = { viewModel.refreshHotwords() },
                                onSelectWord = onPerformSearchAction
                            )
                        }

                        // 右栏：搜索历史
                        Box(modifier = Modifier.weight(1f)) {
                            SearchHistorySection(
                                historyList = uiState.searchHistory,
                                onSelectWord = onPerformSearchAction,
                                onDeleteItem = { word -> viewModel.onRemoveHistory(word) },
                                onClearAll = { viewModel.onClearHistory() }
                            )
                        }
                    }
                } else {
                    // 无搜索记录或小屏单流排版：搜索历史（若有）+ 大家在找（拓展至全宽）
                    if (uiState.searchHistory.isNotEmpty()) {
                        SearchHistorySection(
                            historyList = uiState.searchHistory,
                            onSelectWord = onPerformSearchAction,
                            onDeleteItem = { word -> viewModel.onRemoveHistory(word) },
                            onClearAll = { viewModel.onClearHistory() }
                        )
                    }

                    EveryoneSearchingSection(
                        hotwords = uiState.hotwordsMap[uiState.selectedTab.categoryId].orEmpty(),
                        isLoading = uiState.isLoadingHotwords,
                        onRefresh = { viewModel.refreshHotwords() },
                        onSelectWord = onPerformSearchAction
                    )
                }
            }
        }
    } else {
        // 模式 B：搜索结果列表浏览态（内容边距使用固定估高，不叠加滚动偏移避免抖动）
        val topContentPadding = topBarTotalHeight

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 2.1 结果列表内容
            SearchResultsBody(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                uiState = uiState,
                userGridState = userGridState,
                resultGridState = resultGridState,
                albumStaggeredGridState = albumStaggeredGridState,
                topContentPadding = topContentPadding,
                navBarBottomPadding = navBarBottomPadding,
                onRefresh = { viewModel.onRefresh() },
                onLoadMore = { viewModel.onLoadMore() },
                onItemClick = { item ->
                    when (uiState.selectedTab) {
                        SearchTab.HOBBY -> {
                            onNavigateToDetail(
                                DetailNavParam(
                                    itemId = item.actualId,
                                    initialName = item.displayName,
                                    initialCover = item.cover
                                )
                            )
                        }
                        SearchTab.ALBUM -> {
                            onNavigateToAlbum(
                                AlbumNavParam(
                                    albumId = item.actualId,
                                    initialTitle = item.displayName,
                                    initialCover = item.cover
                                )
                            )
                        }
                        SearchTab.COMPANY -> {
                            val companyId = if (item.itemId > 0) item.itemId else item.actualId
                            val companyNodeId = if (item.id > 0) item.id else item.itemNodeId
                            if (onNavigateToVendor != null && companyId > 0) {
                                onNavigateToVendor(
                                    VendorNavParam(
                                        companyId = companyId,
                                        companyNodeId = companyNodeId,
                                        initialName = item.displayName,
                                        initialCover = item.cover
                                    )
                                )
                            } else {
                                platformActions.showToast("${item.displayName} 详情页即将上线")
                            }
                        }
                        SearchTab.SERIES -> {
                            val seriesId = if (item.itemId > 0) item.itemId else item.actualId
                            val seriesNodeId = if (item.id > 0) item.id else item.itemNodeId
                            if (onNavigateToSeries != null && seriesId > 0) {
                                onNavigateToSeries(
                                    SeriesNavParam(
                                        seriesId = seriesId,
                                        seriesNodeId = seriesNodeId,
                                        initialName = item.displayName,
                                        initialCover = item.cover
                                    )
                                )
                            } else {
                                platformActions.showToast("${item.displayName} 详情页即将上线")
                            }
                        }
                        SearchTab.PERSON -> {
                            val personId = if (item.itemId > 0) item.itemId else item.actualId
                            val personNodeId = if (item.id > 0) item.id else item.itemNodeId
                            if (onNavigateToPerson != null && personId > 0) {
                                onNavigateToPerson(
                                    PersonNavParam(
                                        personId = personId,
                                        personNodeId = personNodeId,
                                        initialName = item.displayName,
                                        initialCover = item.cover
                                    )
                                )
                            } else {
                                platformActions.showToast("${item.displayName} 详情页即将上线")
                            }
                        }
                        SearchTab.WORKS -> {
                            val worksId = if (item.itemId > 0) item.itemId else item.actualId
                            if (onNavigateToWorks != null && worksId > 0) {
                                onNavigateToWorks(
                                    WorksNavParam(
                                        worksId = worksId,
                                        initialName = item.displayName,
                                        initialCover = item.cover
                                    )
                                )
                            } else {
                                platformActions.showToast("${item.displayName} 详情页即将上线")
                            }
                        }
                        SearchTab.WORKS_ELEMENT -> {
                            val charactarId = if (item.itemId > 0) item.itemId else item.actualId
                            val charactarNodeId = if (item.id > 0) item.id else item.itemNodeId
                            if (onNavigateToCharacter != null && charactarId > 0) {
                                onNavigateToCharacter(
                                    CharacterNavParam(
                                        characterId = charactarId,
                                        characterNodeId = charactarNodeId,
                                        initialName = item.displayName,
                                        initialCover = item.cover
                                    )
                                )
                            } else {
                                platformActions.showToast("${item.displayName} 详情页即将上线")
                            }
                        }
                        else -> {
                            platformActions.showToast("${item.displayName} 详情页即将上线")
                        }
                    }
                },
                onUserClick = { user ->
                    platformActions.showToast("${user.nickname ?: "用户"} 的个人主页即将上线")
                }
            )

            // 2.2 顶部协同操作区 (SearchTopBar + TabRow，实测仅用于折叠上限与位移)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .offset { IntOffset(x = 0, y = scrollBehavior.state.heightOffset.roundToInt()) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .onSizeChanged { size ->
                            if (size.height > 0) {
                                searchBarHeightPx = size.height.toFloat()
                                scrollBehavior.state.heightOffsetLimit = -searchBarHeightPx
                            }
                        }
                ) {
                    SearchTopBar(
                        query = uiState.keyword,
                        onQueryChange = {
                            viewModel.onKeywordChange(it)
                            isSearchInputActive = true
                        },
                        onSearch = onPerformSearchAction,
                        onBackClick = onBack,
                        onClearClick = {
                            viewModel.onClearKeyword()
                            isSearchInputActive = true
                        },
                        placeholderText = "搜索",
                        readOnly = true,
                        onSearchBarClick = {
                            isSearchInputActive = true
                        },
                        showFilterButton = isTabFilterable(uiState.selectedTab),
                        isFilterActive = uiState.isFilterActive,
                        onFilterClick = { showFilterSheet = true }
                    )
                }

                PrimaryScrollableTabRow(
                    selectedTabIndex = SearchTab.entries.indexOf(uiState.selectedTab),
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {},
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    SearchTab.entries.forEach { tab ->
                        val isSelected = uiState.selectedTab == tab
                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.onSelectTab(tab) },
                            text = {
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            // 2.3 状态栏遮罩
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .align(Alignment.TopCenter)
            ) {}
        }
    }

    // 自适应结果筛选与排序面板
    if (showFilterSheet) {
        val hobbyState = remember(
            uiState.hobbyCategories,
            uiState.selectedHobbyCategory,
            uiState.availableCompanies,
            uiState.selectedCompany,
            uiState.availableYears,
            uiState.selectedYear
        ) {
            HobbyFilterState(
                categories = uiState.hobbyCategories,
                selectedCategory = uiState.selectedHobbyCategory,
                companies = uiState.availableCompanies,
                selectedCompany = uiState.selectedCompany,
                years = uiState.availableYears,
                selectedYear = uiState.selectedYear
            )
        }
        val albumState = remember(uiState.selectedAlbumCategory) {
            AlbumFilterState(selectedCategory = uiState.selectedAlbumCategory)
        }
        val elementsState = remember(
            uiState.selectedElementsCategory,
            uiState.attributeMap,
            uiState.selectedAttribute
        ) {
            ElementsFilterState(
                selectedCategory = uiState.selectedElementsCategory,
                attributePairs = uiState.attributeMap[uiState.selectedElementsCategory.categoryId].orEmpty().map { it.id to it.name },
                selectedAttribute = uiState.selectedAttribute
            )
        }

        SearchFilterAdaptiveSheet(
            isWideScreen = isWideScreen,
            selectedTab = uiState.selectedTab,
            order = uiState.order,
            onSortChange = { viewModel.onSortChange(it) },
            hobbyState = hobbyState,
            onHobbyCategorySelect = { viewModel.onHobbyCategoryChange(it) },
            onCompanySelect = { viewModel.onFilterChange(it, uiState.selectedYear) },
            onYearSelect = { viewModel.onFilterChange(uiState.selectedCompany, it) },
            albumState = albumState,
            onAlbumCategorySelect = { viewModel.onAlbumCategoryChange(it) },
            elementsState = elementsState,
            onElementsCategorySelect = { viewModel.onElementsCategoryChange(it) },
            onAttributeSelect = { viewModel.onAttributeChange(it) },
            onReset = {
                when (uiState.selectedTab) {
                    SearchTab.HOBBY -> {
                        viewModel.onSortChange("")
                        viewModel.onHobbyCategoryChange(HobbyAllCategory.id)
                        viewModel.onFilterChange(null, null)
                    }
                    SearchTab.ALBUM -> {
                        viewModel.onSortChange("")
                        viewModel.onAlbumCategoryChange(AlbumSubCategory.ALL)
                    }
                    SearchTab.WORKS_ELEMENT -> {
                        viewModel.onElementsCategoryChange(ElementsSubCategory.ALL)
                        viewModel.onAttributeChange(0)
                    }
                    else -> {}
                }
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}

/**
 * 搜索结果列表主体
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchResultsBody(
    modifier: Modifier = Modifier,
    uiState: SearchUiState,
    userGridState: LazyGridState,
    resultGridState: LazyGridState,
    albumStaggeredGridState: LazyStaggeredGridState,
    topContentPadding: Dp = 0.dp,
    navBarBottomPadding: Dp = 0.dp,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (SearchResultItem) -> Unit,
    onUserClick: (SearchUserItem) -> Unit
) {
    // 触底自动加载检测
    val shouldLoadMoreUser by remember {
        derivedStateOf {
            val totalItems = userGridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = userGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 4
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = resultGridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = resultGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 4
        }
    }

    val shouldLoadMoreAlbum by remember {
        derivedStateOf {
            val totalItems = albumStaggeredGridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = albumStaggeredGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMoreUser) {
        if (shouldLoadMoreUser && uiState.hasMore && !uiState.isLoadingMore) {
            onLoadMore()
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && uiState.hasMore && !uiState.isLoadingMore) {
            onLoadMore()
        }
    }

    LaunchedEffect(shouldLoadMoreAlbum) {
        if (shouldLoadMoreAlbum && uiState.hasMore && !uiState.isLoadingMore) {
            onLoadMore()
        }
    }

    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullRefreshState,
                isRefreshing = uiState.isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topContentPadding)
            )
        }
    ) {
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            when {
                uiState.isLoading && !uiState.isRefreshing -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topContentPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                uiState.errorMessage != null && uiState.items.isEmpty() && uiState.userItems.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topContentPadding)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = uiState.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("重试")
                            }
                        }
                    }
                }

                uiState.selectedTab == SearchTab.USER -> {
                    if (uiState.userItems.isEmpty()) {
                        EmptyResultPlaceholder(modifier = Modifier.padding(top = topContentPadding))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 440.dp),
                            state = userGridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 14.dp, top = topContentPadding + 14.dp, end = 14.dp, bottom = 14.dp + navBarBottomPadding),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.userItems, key = { it.userId }, contentType = { "search_user" }) { user ->
                                SearchUserCard(
                                    user = user,
                                    onClick = { onUserClick(user) }
                                )
                            }

                            if (uiState.isLoadingMore) {
                                item(span = { GridItemSpan(maxLineSpan) }, contentType = "search_user_footer") {
                                    LoadingMoreFooter()
                                }
                            }
                        }
                    }
                }

                uiState.selectedTab == SearchTab.ALBUM -> {
                    if (uiState.items.isEmpty()) {
                        EmptyResultPlaceholder(modifier = Modifier.padding(top = topContentPadding))
                    } else {
                        LazyVerticalStaggeredGrid(
                            columns = rememberAlbumStaggeredGridColumns(),
                            state = albumStaggeredGridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 8.dp, top = topContentPadding + 8.dp, end = 8.dp, bottom = 8.dp + navBarBottomPadding),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalItemSpacing = 8.dp
                        ) {
                            items(uiState.items, key = { it.actualId }, contentType = { "search_album" }) { item ->
                                AlbumStaggeredCard(
                                    item = item,
                                    onClick = { onItemClick(item) }
                                )
                            }

                            if (uiState.isLoadingMore) {
                                item(span = StaggeredGridItemSpan.FullLine, contentType = "search_album_footer") {
                                    LoadingMoreFooter()
                                }
                            }
                        }
                    }
                }

                else -> {
                    if (uiState.items.isEmpty()) {
                        EmptyResultPlaceholder(modifier = Modifier.padding(top = topContentPadding))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(
                                minSize = if (uiState.selectedTab == SearchTab.HOBBY) 380.dp else 440.dp
                            ),
                            state = resultGridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 14.dp, top = topContentPadding + 14.dp, end = 14.dp, bottom = 14.dp + navBarBottomPadding),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.items, key = { it.actualId }, contentType = { uiState.selectedTab }) { item ->
                                when (uiState.selectedTab) {
                                    SearchTab.HOBBY -> {
                                        HobbySearchCard(
                                            item = item,
                                            onClick = { onItemClick(item) }
                                        )
                                    }
                                    else -> {
                                        GeneralSearchCard(
                                            item = item,
                                            onClick = { onItemClick(item) }
                                        )
                                    }
                                }
                            }

                            if (uiState.isLoadingMore) {
                                item(span = { GridItemSpan(maxLineSpan) }, contentType = "search_result_footer") {
                                    LoadingMoreFooter()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
