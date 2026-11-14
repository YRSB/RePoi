package com.nobody.repoi.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import com.nobody.repoi.data.remote.model.SearchResultItem
import com.nobody.repoi.data.remote.model.SearchUserItem
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.navigation.CharacterNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.search.components.AlbumFilterState
import com.nobody.repoi.ui.search.components.AlbumStaggeredCard
import com.nobody.repoi.ui.search.components.ElementsFilterState
import com.nobody.repoi.ui.search.components.EmptyResultPlaceholder
import com.nobody.repoi.ui.search.components.GeneralSearchCard
import com.nobody.repoi.ui.search.components.HobbyFilterState
import com.nobody.repoi.ui.search.components.HobbySearchCard
import com.nobody.repoi.ui.search.components.LoadingMoreFooter
import com.nobody.repoi.ui.search.components.SearchFilterAdaptiveSheet
import com.nobody.repoi.ui.search.components.SearchTopBar
import com.nobody.repoi.ui.search.components.SearchUserCard
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * 专职搜索结果页：
 *
 * 承载常驻顶栏（显示当前关键词或厂商名称，点击搜索栏触发重搜编辑）、
 * 8 分类横向分页器（[HorizontalPager]）与分类自适应筛选弹窗。
 *
 * 彻底释放返回调度权给 Navigation 3 原生单根栈，零私有 BackHandler，原生支持 MaterialSharedAxis.Z 与预测性返回手势。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultScreen(
    navParam: SearchResultNavParam = SearchResultNavParam(),
    onBack: () -> Unit,
    onSearchFieldClick: (SearchNavParam) -> Unit = {},
    onNavigateToDetail: (DetailNavParam) -> Unit,
    onNavigateToAlbum: (AlbumNavParam) -> Unit,
    onNavigateToVendor: ((VendorNavParam) -> Unit)? = null,
    onNavigateToSeries: ((SeriesNavParam) -> Unit)? = null,
    onNavigateToPerson: ((PersonNavParam) -> Unit)? = null,
    onNavigateToWorks: ((WorksNavParam) -> Unit)? = null,
    onNavigateToCharacter: ((CharacterNavParam) -> Unit)? = null,
    viewModel: SearchResultViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appMessages = LocalAppMessages.current

    val isWideScreen = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(navParam) {
        viewModel.initParam(navParam)
    }

    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 8 个搜索分类横向分页：初始页与当前分类对齐
    val pagerState = rememberPagerState(
        initialPage = SearchTab.entries.indexOf(uiState.selectedTab).coerceAtLeast(0)
    ) { SearchTab.entries.size }
    val coroutineScope = rememberCoroutineScope()

    // 顶栏筛选入口跟随 Pager 当前页
    val activeTab = SearchTab.entries[pagerState.currentPage.coerceIn(0, SearchTab.entries.lastIndex)]

    // 翻页手势落定后，同步 ViewModel 的当前分类
    LaunchedEffect(pagerState) {
        var lastSettledPage = pagerState.settledPage
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != lastSettledPage) {
                lastSettledPage = page
                viewModel.onSelectTab(SearchTab.entries[page])
            }
        }
    }

    // ViewModel 的分类变更反向驱动分页器位置
    LaunchedEffect(uiState.selectedTab) {
        val targetPage = SearchTab.entries.indexOf(uiState.selectedTab)
        if (targetPage >= 0 && targetPage != pagerState.currentPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // 1. 常驻搜索操作栏：只读显示，点击输入区压入 SearchNavKey 重新编辑
        SearchTopBar(
            query = uiState.displayTitle,
            onQueryChange = {},
            onSearch = {},
            onBackClick = onBack,
            onClearClick = {},
            placeholderText = "搜索",
            readOnly = true,
            onSearchBarClick = {
                onSearchFieldClick(
                    SearchNavParam(
                        initialKeyword = uiState.keyword.ifBlank { navParam.keyword },
                        initialCategory = activeTab.categoryId,
                        initialCompanyNodeId = uiState.companyNodeId ?: navParam.companyNodeId,
                        initialCompanyName = uiState.companyName ?: navParam.companyName
                    )
                )
            },
            showFilterButton = isTabFilterable(activeTab),
            isFilterActive = uiState.isFilterActiveFor(activeTab),
            onFilterClick = {
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

        // 3. 分类分页器：按需懒加载
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = true,
            beyondViewportPageCount = 0
        ) { pageIndex ->
            val tab = SearchTab.entries[pageIndex]
            LaunchedEffect(pagerState.currentPage) {
                if (pagerState.currentPage == pageIndex) {
                    viewModel.onTabVisible(tab)
                }
            }
            SearchResultTabContent(
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

    // 4. 自适应结果筛选与排序面板
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
 * 单个搜索分类列表呈现区：独立持有滚动与分页状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchResultTabContent(
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
                    PoiLoadingView()
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
