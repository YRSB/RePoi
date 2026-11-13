package com.nobody.repoi.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import com.nobody.repoi.data.remote.model.SearchResultItem
import com.nobody.repoi.data.remote.model.SearchUserItem
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.platform.PoiShelfBackHandler
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.album.rememberAlbumStaggeredGridColumns
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
import com.nobody.repoi.ui.search.components.SearchFilterAdaptiveSheet
import com.nobody.repoi.ui.search.components.SearchHistorySection
import com.nobody.repoi.ui.search.components.SearchSuggestionsList
import com.nobody.repoi.ui.search.components.SearchTopBar
import com.nobody.repoi.ui.search.components.SearchUserCard
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

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
    val appMessages = LocalAppMessages.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 判断大屏环境以适配居中与双栏排版（达到 Expanded 宽度时为大屏双栏，其余为小屏单流）
    val isWideScreen = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

    // 是否处于搜索输入与联想落地页状态
    var isSearchInputActive by rememberSaveable {
        mutableStateOf(navParam.initialKeyword.isBlank() && navParam.initialCompanyNodeId == null)
    }

    // 筛选面板展开状态
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(navParam) {
        viewModel.initParams(
            navParam.initialKeyword,
            navParam.initialCategory,
            navParam.initialCompanyNodeId,
            navParam.initialCompanyName
        )
    }

    // 系统返回拦截：若处于输入落地页且已有结果，则收起到结果页；其余情况退出
    PoiShelfBackHandler {
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

    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 8 个搜索分类横向分页：初始页与当前分类对齐
    val pagerState = rememberPagerState(
        initialPage = SearchTab.entries.indexOf(uiState.selectedTab).coerceAtLeast(0)
    ) { SearchTab.entries.size }
    val coroutineScope = rememberCoroutineScope()

    // 顶栏筛选入口与筛选面板跟随 Pager 当前页（滑动中即时生效，不等落定）
    val activeTab = SearchTab.entries[pagerState.currentPage.coerceIn(0, SearchTab.entries.lastIndex)]

    // 翻页手势或点击 Tab 落定后，同步 ViewModel 的当前分类（相同分类由 ViewModel 幂等处理）
    LaunchedEffect(pagerState) {
        var lastSettledPage = pagerState.settledPage
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != lastSettledPage) {
                lastSettledPage = page
                viewModel.onSelectTab(SearchTab.entries[page])
            }
        }
    }

    // ViewModel 的分类变更（如启动参数指定分类）反向驱动分页器位置
    LaunchedEffect(uiState.selectedTab) {
        val targetPage = SearchTab.entries.indexOf(uiState.selectedTab)
        if (targetPage >= 0 && targetPage != pagerState.currentPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(isSearchInputActive) {
        if (isSearchInputActive) {
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
        // 模式 B：搜索结果浏览态——静态平铺顶栏 + 8 分类横向分页
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            // 1. 常驻搜索操作栏
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
                showFilterButton = isTabFilterable(activeTab),
                isFilterActive = uiState.isFilterActiveFor(activeTab),
                onFilterClick = {
                    // 面板打开后 Pager 不再变化：先把 ViewModel 选中分类对齐当前页，回调才落在正确快照上
                    if (uiState.selectedTab != activeTab) viewModel.onSelectTab(activeTab)
                    showFilterSheet = true
                }
            )

            // 2. 常驻分类页签：指示器随 Pager 拖拽进度实时插值
            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {},
                indicator = {
                    SearchTabIndicator(
                        page = { pagerState.currentPage },
                        pageOffsetFraction = { pagerState.currentPageOffsetFraction }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                SearchTab.entries.forEachIndexed { index, tab ->
                    // 选中态与指示器保持一致：以 Pager 当前页为准
                    val isSelected = pagerState.currentPage == index
                    Tab(
                        selected = isSelected,
                        onClick = {
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        },
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

            // 3. 分类分页器：未浏览的分类不提前渲染与请求
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                userScrollEnabled = true,
                beyondViewportPageCount = 0
            ) { pageIndex ->
                val tab = SearchTab.entries[pageIndex]
                // 页面滑入可见即触发该分类首屏懒加载；选中同步仍由顶层 settledPage 通道负责
                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage == pageIndex) {
                        viewModel.onTabVisible(tab)
                    }
                }
                SearchTabContent(
                    tab = tab,
                    tabState = uiState.tabStates.getValue(tab),
                    searchSessionId = uiState.searchSessionId,
                    navBarBottomPadding = navBarBottomPadding,
                    onRefresh = { viewModel.onRefresh(tab) },
                    onLoadMore = { viewModel.onLoadMore(tab) },
                    onItemClick = { item ->
                        when (tab) {
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
                                    appMessages.show("${item.displayName} 详情页即将上线")
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
                                    appMessages.show("${item.displayName} 详情页即将上线")
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
                                    appMessages.show("${item.displayName} 详情页即将上线")
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
                                    appMessages.show("${item.displayName} 详情页即将上线")
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
                                    appMessages.show("${item.displayName} 详情页即将上线")
                                }
                            }
                            else -> {
                                appMessages.show("${item.displayName} 详情页即将上线")
                            }
                        }
                    },
                    onUserClick = { user ->
                        appMessages.show("${user.nickname ?: "用户"} 的个人主页即将上线")
                    }
                )
            }
        }
    }

    // 自适应结果筛选与排序面板
    if (showFilterSheet) {
        val hobbyState = remember(uiState.hobbyCategories, uiState.hobbyFilter) {
            HobbyFilterState(
                categories = uiState.hobbyCategories,
                selectedCategory = uiState.hobbyFilter.categoryId,
                companies = uiState.hobbyFilter.availableCompanies,
                selectedCompany = uiState.hobbyFilter.company,
                years = uiState.hobbyFilter.availableYears,
                selectedYear = uiState.hobbyFilter.year
            )
        }
        val albumState = remember(uiState.albumFilter) {
            AlbumFilterState(selectedCategory = uiState.albumFilter.category)
        }
        val elementsState = remember(uiState.elementsFilter, uiState.attributeMap) {
            ElementsFilterState(
                selectedCategory = uiState.elementsFilter.category,
                attributePairs = uiState.attributeMap[uiState.elementsFilter.category.categoryId].orEmpty().map { it.id to it.name },
                selectedAttribute = uiState.elementsFilter.attribute
            )
        }

        SearchFilterAdaptiveSheet(
            isWideScreen = isWideScreen,
            selectedTab = activeTab,
            order = uiState.orderFor(activeTab),
            onSortChange = { viewModel.onSortChange(it) },
            hobbyState = hobbyState,
            onHobbyCategorySelect = { viewModel.onHobbyCategoryChange(it) },
            onCompanySelect = { viewModel.onFilterChange(it, uiState.hobbyFilter.year) },
            onYearSelect = { viewModel.onFilterChange(uiState.hobbyFilter.company, it) },
            albumState = albumState,
            onAlbumCategorySelect = { viewModel.onAlbumCategoryChange(it) },
            elementsState = elementsState,
            onElementsCategorySelect = { viewModel.onElementsCategoryChange(it) },
            onAttributeSelect = { viewModel.onAttributeChange(it) },
            onReset = { viewModel.onResetFilters() },
            onDismiss = { showFilterSheet = false }
        )
    }
}

/**
 * 单个搜索分类页：只消费本页 [tabState]，独立持有滚动状态、触底加载与下拉刷新结构。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTabContent(
    tab: SearchTab,
    tabState: TabSearchState,
    searchSessionId: Int,
    navBarBottomPadding: Dp = 0.dp,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (SearchResultItem) -> Unit,
    onUserClick: (SearchUserItem) -> Unit
) {
    val gridState = rememberLazyGridState()
    val albumStaggeredGridState = rememberLazyStaggeredGridState()

    // 新搜索会话回到顶部：只响应会话真正变化，页面重入恢复滚动位置时不得复位
    var handledSessionId by rememberSaveable { mutableIntStateOf(searchSessionId) }
    LaunchedEffect(searchSessionId) {
        if (handledSessionId == searchSessionId) return@LaunchedEffect
        handledSessionId = searchSessionId
        if (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0) {
            gridState.scrollToItem(0)
        }
        if (albumStaggeredGridState.firstVisibleItemIndex > 0 ||
            albumStaggeredGridState.firstVisibleItemScrollOffset > 0
        ) {
            albumStaggeredGridState.scrollToItem(0)
        }
    }

    // 触底自动加载检测（本页独立订阅）
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
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

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && tabState.hasMore && !tabState.isLoadingMore) {
            onLoadMore()
        }
    }

    LaunchedEffect(shouldLoadMoreAlbum) {
        if (shouldLoadMoreAlbum && tabState.hasMore && !tabState.isLoadingMore) {
            onLoadMore()
        }
    }

    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = tabState.isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullRefreshState,
                isRefreshing = tabState.isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                (tabState.isLoading || !tabState.isLoaded) && tabState.errorMessage == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                tabState.errorMessage != null && tabState.items.isEmpty() && tabState.userItems.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = tabState.errorMessage,
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

                tab == SearchTab.USER -> {
                    if (tabState.userItems.isEmpty()) {
                        EmptyResultPlaceholder(modifier = Modifier.fillMaxSize())
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 440.dp),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 14.dp,
                                top = 14.dp,
                                end = 14.dp,
                                bottom = 14.dp + navBarBottomPadding
                            ),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(tabState.userItems, key = { it.userId }, contentType = { "search_user" }) { user ->
                                SearchUserCard(
                                    user = user,
                                    onClick = { onUserClick(user) }
                                )
                            }

                            if (tabState.isLoadingMore) {
                                item(
                                    key = "search_user_footer",
                                    span = { GridItemSpan(maxLineSpan) },
                                    contentType = "search_user_footer"
                                ) {
                                    LoadingMoreFooter()
                                }
                            }
                        }
                    }
                }

                tab == SearchTab.ALBUM -> {
                    if (tabState.items.isEmpty()) {
                        EmptyResultPlaceholder(modifier = Modifier.fillMaxSize())
                    } else {
                        LazyVerticalStaggeredGrid(
                            columns = rememberAlbumStaggeredGridColumns(),
                            state = albumStaggeredGridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                top = 8.dp,
                                end = 8.dp,
                                bottom = 8.dp + navBarBottomPadding
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalItemSpacing = 8.dp
                        ) {
                            items(tabState.items, key = { it.actualId }, contentType = { "search_album" }) { item ->
                                AlbumStaggeredCard(
                                    item = item,
                                    onClick = { onItemClick(item) }
                                )
                            }

                            if (tabState.isLoadingMore) {
                                item(
                                    key = "search_album_footer",
                                    span = StaggeredGridItemSpan.FullLine,
                                    contentType = "search_album_footer"
                                ) {
                                    LoadingMoreFooter()
                                }
                            }
                        }
                    }
                }

                else -> {
                    if (tabState.items.isEmpty()) {
                        EmptyResultPlaceholder(modifier = Modifier.fillMaxSize())
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(
                                minSize = if (tab == SearchTab.HOBBY) 380.dp else 440.dp
                            ),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 14.dp,
                                top = 14.dp,
                                end = 14.dp,
                                bottom = 14.dp + navBarBottomPadding
                            ),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(tabState.items, key = { it.actualId }, contentType = { tab.itemType }) { item ->
                                if (tab == SearchTab.HOBBY) {
                                    HobbySearchCard(
                                        item = item,
                                        onClick = { onItemClick(item) }
                                    )
                                } else {
                                    GeneralSearchCard(
                                        item = item,
                                        onClick = { onItemClick(item) }
                                    )
                                }
                            }

                            if (tabState.isLoadingMore) {
                                item(
                                    key = "search_result_footer",
                                    span = { GridItemSpan(maxLineSpan) },
                                    contentType = "search_result_footer"
                                ) {
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
