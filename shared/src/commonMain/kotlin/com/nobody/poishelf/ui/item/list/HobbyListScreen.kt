package com.nobody.poishelf.ui.item.list

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.IntOffset
import com.nobody.poishelf.ui.common.rememberSynchronousTopAppBarScrollBehavior
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import com.nobody.poishelf.ui.home.components.ShimmerHost
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobody.poishelf.data.local.CategoryItem
import com.nobody.poishelf.ui.detail.DetailNavParam
import com.nobody.poishelf.ui.item.list.components.CategoryPickerSheet
import com.nobody.poishelf.ui.item.list.components.HobbyAdvancedFilterParam
import com.nobody.poishelf.ui.item.list.components.HobbyEmptyPlaceholder
import com.nobody.poishelf.ui.item.list.components.HobbyFilterAdaptiveSheet
import com.nobody.poishelf.ui.item.list.components.HobbyGridCard
import com.nobody.poishelf.ui.item.list.components.HobbyGridCardSkeleton
import com.nobody.poishelf.ui.item.list.components.HobbyListCard
import com.nobody.poishelf.ui.item.list.components.HobbyListCardSkeleton
import com.nobody.poishelf.ui.item.list.components.HobbyLoadingMoreFooter
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import kotlin.math.roundToInt
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * KMP 当前年份（`Calendar.getInstance().get(YEAR)` 替代）。
 */
@OptIn(ExperimentalTime::class)
private fun currentYearKmp(): Int =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HobbyListScreen(
    param: HobbyListNavParam,
    onBack: () -> Unit,
    onNavigateToDetail: (DetailNavParam) -> Unit,
    showBackButton: Boolean = true,
    viewModel: HobbyListViewModel = koinViewModel()
) {
    LaunchedEffect(param) {
        viewModel.initParams(param)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    // 搜索激活态（顶栏内嵌搜索 Inline Morphing）
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var showCategoryPickerSheet by rememberSaveable { mutableStateOf(false) }

    // 退出搜索激活态时清空焦点
    fun exitSearch() {
        isSearchActive = false
        focusManager.clearFocus()
    }

    // 系统返回键：优先退出搜索激活态，否则正常退出页面
    BackHandler(enabled = isSearchActive) {
        exitSearch()
    }

    val density = LocalDensity.current
    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val defaultTopBarPx = with(density) { (statusBarTopPadding + 64.dp).toPx() }
    val defaultQuickFilterPx = with(density) { 48.dp.toPx() }

    var topBarHeightPx by remember { mutableFloatStateOf(defaultTopBarPx) }
    var quickFilterBarHeightPx by remember { mutableFloatStateOf(defaultQuickFilterPx) }

    // 无缝同步滚动控制器：当有列表数据且未处于搜索/弹窗面板状态时启用 1:1 协同位移
    val scrollBehavior = rememberSynchronousTopAppBarScrollBehavior(
        initialHeightOffsetLimit = -defaultQuickFilterPx,
        canScroll = {
            !uiState.isLoading &&
            uiState.items.isNotEmpty() &&
            !isSearchActive &&
            !uiState.showFilterSheet &&
            !showCategoryPickerSheet
        }
    )

    // 搜索激活时强制展开，防止顶栏被折叠
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            scrollBehavior.state.heightOffset = 0f
        }
    }

    // 筛选栏高度确定后动态贴合折叠行程上限
    LaunchedEffect(quickFilterBarHeightPx) {
        if (quickFilterBarHeightPx > 0f) {
            scrollBehavior.state.heightOffsetLimit = -quickFilterBarHeightPx
        }
    }

    // 筛选条件变化时重置折叠偏移
    LaunchedEffect(uiState.categoryId, uiState.subCategoryId, uiState.order, uiState.releaseYear, uiState.releaseMonth, uiState.keyword) {
        scrollBehavior.state.heightOffset = 0f
    }

    // 分页触底检测状态
    val gridState = rememberLazyGridState()
    val listGridState = rememberLazyGridState()

    val shouldLoadMoreGrid by remember {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val ahead = maxOf(4, gridState.layoutInfo.visibleItemsInfo.size)
            total > 0 && lastVisible >= total - ahead
        }
    }
    val shouldLoadMoreList by remember {
        derivedStateOf {
            val total = listGridState.layoutInfo.totalItemsCount
            val lastVisible = listGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val ahead = maxOf(3, listGridState.layoutInfo.visibleItemsInfo.size)
            total > 0 && lastVisible >= total - ahead
        }
    }

    LaunchedEffect(shouldLoadMoreGrid, shouldLoadMoreList, uiState.isGridMode, uiState.isLoading, uiState.isLoadingMore, uiState.isEndReached) {
        if (uiState.isLoading || uiState.isLoadingMore || uiState.isEndReached) return@LaunchedEffect
        if (uiState.isGridMode && shouldLoadMoreGrid) {
            viewModel.loadNextPage()
        } else if (!uiState.isGridMode && shouldLoadMoreList) {
            viewModel.loadNextPage()
        }
    }

    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scope = rememberCoroutineScope()

    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isRefreshing) {
        if (wasRefreshing && !uiState.isRefreshing && uiState.errorMessage == null) {
            if (uiState.isGridMode) {
                gridState.scrollToItem(0)
            } else {
                listGridState.scrollToItem(0)
            }
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
        wasRefreshing = uiState.isRefreshing
    }

    val topBarHeightDp = with(density) { topBarHeightPx.toDp() }

    // 2 行结构固定估高：内容边距不再回灌实测高度，实测仅用于位移锚点与折叠上限
    // （严禁在此加上动态 scrollOffsetDp，否则将引发 Remeasure 抖动死循环）
    val quickFilterMeasuredDp = with(density) { quickFilterBarHeightPx.toDp() }
    val topContentPadding = topBarHeightDp + if (quickFilterMeasuredDp > 48.dp) quickFilterMeasuredDp else 48.dp
    val pullRefreshState = rememberPullToRefreshState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 主界面内容区（受顶栏协同滚动联动）
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. 底层：数据列表与下拉刷新主体（铺满全屏，受 topContentPadding 约束）
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                scope.launch {
                    if (uiState.isGridMode) {
                        gridState.scrollToItem(0)
                    } else {
                        listGridState.scrollToItem(0)
                    }
                    scrollBehavior.state.heightOffset = 0f
                    scrollBehavior.state.contentOffset = 0f
                }
                viewModel.refresh()
            },
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
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            ) {
                when {
                uiState.isLoading && !uiState.isRefreshing -> {
                    // 1:1 动态骨架屏（按固定类型复用，避免与正式卡片混用复用池）
                    if (uiState.isGridMode) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 120.dp),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topContentPadding + 8.dp, bottom = 16.dp + navBarBottomPadding),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = false
                        ) {
                            items(
                                8,
                                key = { index -> "hobby_grid_skeleton_$index" },
                                contentType = { "hobby_grid_skeleton" }
                            ) {
                                ShimmerHost { HobbyGridCardSkeleton() }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 420.dp),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topContentPadding + 8.dp, bottom = 16.dp + navBarBottomPadding),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = false
                        ) {
                            items(
                                6,
                                key = { index -> "hobby_list_skeleton_$index" },
                                contentType = { "hobby_list_skeleton" }
                            ) {
                                ShimmerHost { HobbyListCardSkeleton() }
                            }
                        }
                    }
                }

                uiState.errorMessage != null && uiState.items.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topContentPadding, start = 32.dp, end = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = uiState.errorMessage ?: "加载失败",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(
                                onClick = { viewModel.refresh() },
                                shape = ExpressiveTokens.PillShape
                            ) {
                                Text("重试")
                            }
                        }
                    }
                }

                uiState.items.isEmpty() -> {
                    val hasActiveFilters = uiState.hasAdvancedFilters ||
                            uiState.keyword.isNotBlank() ||
                            (uiState.releaseYear ?: 0) > 0 ||
                            (uiState.releaseMonth ?: 0) > 0 ||
                            uiState.categoryId != 10000

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topContentPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        HobbyEmptyPlaceholder(
                            hasActiveFilters = hasActiveFilters,
                            onResetFilters = { viewModel.resetAllFilters() }
                        )
                    }
                }

                uiState.isGridMode -> {
                    // 自适应封面网格模式
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        state = gridState,
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topContentPadding + 8.dp, bottom = 16.dp + navBarBottomPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.items, key = { it.actualItemId }, contentType = { "hobby_grid" }) { item ->
                            val click = remember(item, onNavigateToDetail) {
                                {
                                    onNavigateToDetail(
                                        DetailNavParam(
                                            itemId = item.actualItemId,
                                            initialName = item.displayName,
                                            initialCover = item.cover
                                        )
                                    )
                                }
                            }
                            HobbyGridCard(
                                item = item,
                                onClick = click
                            )
                        }
                        if (uiState.isLoadingMore || uiState.loadMoreError != null || uiState.isEndReached) {
                            item(span = { GridItemSpan(maxLineSpan) }, contentType = "hobby_grid_footer") {
                                HobbyLoadingMoreFooter(
                                    isLoadingMore = uiState.isLoadingMore,
                                    loadMoreError = uiState.loadMoreError,
                                    isEndReached = uiState.isEndReached,
                                    onRetry = { viewModel.loadNextPage() }
                                )
                            }
                        }
                    }
                }

                else -> {
                    // 1 列图文详情卡片列表模式（大屏自动自适应为双列）
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 420.dp),
                        state = listGridState,
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = topContentPadding + 8.dp, bottom = 16.dp + navBarBottomPadding),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.items, key = { it.actualItemId }, contentType = { "hobby_list" }) { item ->
                            val click = remember(item, onNavigateToDetail) {
                                {
                                    onNavigateToDetail(
                                        DetailNavParam(
                                            itemId = item.actualItemId,
                                            initialName = item.displayName,
                                            initialCover = item.cover
                                        )
                                    )
                                }
                            }
                            HobbyListCard(
                                item = item,
                                onClick = click
                            )
                        }

                        if (uiState.isLoadingMore || uiState.loadMoreError != null || uiState.isEndReached) {
                            item(span = { GridItemSpan(maxLineSpan) }, contentType = "hobby_list_footer") {
                                HobbyLoadingMoreFooter(
                                    isLoadingMore = uiState.isLoadingMore,
                                    loadMoreError = uiState.loadMoreError,
                                    isEndReached = uiState.isEndReached,
                                    onRetry = { viewModel.loadNextPage() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

        // 2. 中层：随动快捷筛选栏（吸顶于顶栏下方，随列表滚动同步折叠）
        // 实测高度仅用于位移锚点与折叠上限，不回灌内容边距
        val categoryTree by viewModel.categoryTree.collectAsStateWithLifecycle()
        val subCategories = remember(uiState.categoryId, categoryTree) { viewModel.getSubCategories(uiState.categoryId) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topBarHeightDp)
                .offset { IntOffset(0, scrollBehavior.state.heightOffset.roundToInt()) }
                .onSizeChanged { size ->
                    val height = size.height.toFloat()
                    if (height > 0f) {
                        quickFilterBarHeightPx = height
                        scrollBehavior.state.heightOffsetLimit = -height
                    }
                }
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            QuickFilterBar(
                uiState = uiState,
                onYearSelected = { viewModel.setYear(it) },
                onMonthSelected = { viewModel.setMonth(it) },
                onOrderSelected = { viewModel.setOrder(it) },
                onOpenCategoryPicker = { showCategoryPickerSheet = true },
                isCategoryPickerOpen = showCategoryPickerSheet,
                subCategories = subCategories
            )
        }

        // 3. 顶层：动态变形主控顶栏（普通模式 / 搜索激活模式），Z 轴最上层
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    val height = size.height.toFloat()
                    if (height > 0f) {
                        topBarHeightPx = height
                    }
                }
                .background(MaterialTheme.colorScheme.surface)
        ) {
            HobbyListTopBar(
                title = FilterLabels.composeTitle(
                    uiState.title,
                    uiState.sex,
                    uiState.specs,
                    uiState.scale,
                    uiState.r18,
                    uiState.tag
                ),
                onBack = onBack,
                showBackButton = showBackButton,
                isSearchActive = isSearchActive,
                keyword = uiState.keyword,
                onKeywordChange = { viewModel.updateKeyword(it) },
                onSearch = {
                    focusManager.clearFocus()
                    viewModel.submitSearch()
                },
                onToggleSearch = { isSearchActive = !isSearchActive },
                onExitSearch = { exitSearch() },
                isGridMode = uiState.isGridMode,
                onToggleViewMode = { viewModel.toggleViewMode() },
                hasAdvancedFilters = uiState.hasAdvancedFilters,
                onFilterClick = { viewModel.openFilterSheet() }
            )
        }
    }

        // 4. 高级筛选自适应抽屉（大屏 SideSheet / 手机 BottomSheet，独立于主列表的滚动行为树之外）
        if (uiState.showFilterSheet) {
            HobbyFilterAdaptiveSheet(
                filterParam = HobbyAdvancedFilterParam(
                    sex = uiState.sex,
                    specs = uiState.specs,
                    scale = uiState.scale,
                    r18 = uiState.r18
                ),
                onDismiss = { viewModel.closeFilterSheet() },
                onApply = { filterParam ->
                    viewModel.applyAdvancedFilters(filterParam)
                },
                onReset = {
                    viewModel.resetAdvancedFilters()
                }
            )
        }

        // 5. 双栏级联分类选择面板（独立于主列表的滚动行为树之外）
        if (showCategoryPickerSheet) {
            CategoryPickerSheet(
                selectedCategoryId = uiState.categoryId,
                selectedSubCategoryId = uiState.subCategoryId,
                getSubCategories = { viewModel.getSubCategories(it) },
                onCategorySelected = { catId, subId ->
                    viewModel.selectCategory(catId, subId)
                },
                onDismiss = { showCategoryPickerSheet = false }
            )
        }
    }
}

/**
 * 动态变形主控顶栏：
 * - 普通模式：返回键 + 复合标题 + 搜索图标 + 视图切换 + 高级筛选（BadgedBox）
 * - 搜索激活模式：取消按钮 + 搜索输入框（含清空）+ 搜索按钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HobbyListTopBar(
    title: String,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    isSearchActive: Boolean,
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onToggleSearch: () -> Unit,
    onExitSearch: () -> Unit,
    isGridMode: Boolean,
    onToggleViewMode: () -> Unit,
    hasAdvancedFilters: Boolean,
    onFilterClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    // 搜索激活时自动聚焦输入框
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    CenterAlignedTopAppBar(
        title = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "TopBarContentMorph"
            ) { searchActive ->
                if (searchActive) {
                    // 搜索激活态：内嵌搜索输入框（PillShape）
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .padding(end = 4.dp),
                        shape = ExpressiveTokens.PillShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            BasicTextField(
                                value = keyword,
                                onValueChange = onKeywordChange,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (keyword.isEmpty()) {
                                            Text(
                                                text = "请在此结果中搜索…",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                            if (keyword.isNotEmpty()) {
                                IconButton(
                                    onClick = { onKeywordChange("") },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "清空",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // 普通模式：复合标题文本
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            // 搜索激活时作为"退出搜索"，普通模式作为"返回上一级"
            if (isSearchActive) {
                IconButton(onClick = onExitSearch) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "退出搜索",
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        actions = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "TopBarActionsContent"
            ) { searchActive ->
                if (searchActive) {
                    // 搜索激活模式：搜索提交按钮
                    TextButton(
                        onClick = onSearch,
                        shape = ExpressiveTokens.PillShape
                    ) {
                        Text(
                            text = "搜索",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    // 普通模式：搜索 + 视图切换 + 高级筛选（带角标）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 1. 搜索按钮
                        IconButton(onClick = onToggleSearch) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "展开搜索",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        // 2. 视图切换按钮（独享 48dp 标准触控目标）
                        IconButton(onClick = onToggleViewMode) {
                            Icon(
                                imageVector = if (isGridMode) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView,
                                contentDescription = if (isGridMode) "切换至列表视图" else "切换至网格视图",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        // 3. 高级筛选按钮（激活时点亮 Badge）
                        IconButton(
                            onClick = onFilterClick,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            BadgedBox(
                                badge = {
                                    if (hasAdvancedFilters) {
                                        Badge()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FilterList,
                                    contentDescription = "高级筛选",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * 快捷下拉过滤栏：单行横向惯性滚动，平铺年、月、排序、分类 Chip
 * 视图切换按钮已提升至顶栏 Action 区域。
 */
@Composable
private fun QuickFilterBar(
    uiState: HobbyListUiState,
    onYearSelected: (Int?) -> Unit,
    onMonthSelected: (Int?) -> Unit,
    onOrderSelected: (String) -> Unit,
    onOpenCategoryPicker: () -> Unit,
    isCategoryPickerOpen: Boolean,
    subCategories: List<CategoryItem> = emptyList()
) {
    val currentYear = currentYearKmp()
    var showYearMenu by remember { mutableStateOf(false) }
    var showMonthMenu by remember { mutableStateOf(false) }
    var showOrderMenu by remember { mutableStateOf(false) }

    val yearOptions = listOf(null to "不限年") + ((currentYear + 2 downTo currentYear - 8).map { it to "${it}年" })
    val monthOptions = listOf(null to "不限月") + ((1..12).map { it to "${it}月" })
    val orderOptions = listOf(
        "hits7Day" to "7天热度",
        "hitsDay" to "1天热度",
        "hits" to "总热度",
        "add" to "最新入库",
        "release" to "发售时间",
        "rating" to "综合评分"
    )
    val categoryOptions = listOf(
        10000 to "全部周边",
        100 to "手办",
        200 to "动漫模型",
        500 to "真实模型",
        300 to "娃娃人偶",
        400 to "毛绒布偶",
        900 to "动漫周边"
    )

    // 横向惯性滚动，内容超出屏幕时可滑动
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. 排序下拉（排序放第 1 位，最常用）
        Box {
            val currentOrderLabel = orderOptions.find { it.first == uiState.order }?.second ?: "7天热度"
            FilterDropdownChip(
                label = currentOrderLabel,
                isSelected = uiState.order != "hits7Day",
                isExpanded = showOrderMenu,
                onClick = { showOrderMenu = true }
            )
            DropdownMenu(
                expanded = showOrderMenu,
                onDismissRequest = { showOrderMenu = false },
                shape = ExpressiveTokens.MediumCardShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                orderOptions.forEach { (orderKey, label) ->
                    DropdownMenuItem(
                        text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            onOrderSelected(orderKey)
                            showOrderMenu = false
                        }
                    )
                }
            }
        }

        // 2. 分类入口（单一入口，唤起双栏级联选择底板）
        val categoryChipLabel = when {
            uiState.subCategoryId != null -> {
                subCategories.find { it.id == uiState.subCategoryId }?.name ?: "子分类"
            }
            uiState.categoryId != 10000 -> {
                categoryOptions.find { it.first == uiState.categoryId }?.second ?: "分类"
            }
            else -> "分类"
        }
        val isCategoryActive = uiState.categoryId != 10000 || uiState.subCategoryId != null

        FilterDropdownChip(
            label = categoryChipLabel,
            isSelected = isCategoryActive,
            isExpanded = isCategoryPickerOpen,
            onClick = onOpenCategoryPicker
        )

        // 3. 年份下拉
        Box {
            val hasYear = (uiState.releaseYear ?: 0) > 0
            FilterDropdownChip(
                label = if (hasYear) "${uiState.releaseYear}年" else "不限年",
                isSelected = hasYear,
                isExpanded = showYearMenu,
                onClick = { showYearMenu = true }
            )
            DropdownMenu(
                expanded = showYearMenu,
                onDismissRequest = { showYearMenu = false },
                shape = ExpressiveTokens.MediumCardShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                yearOptions.forEach { (year, label) ->
                    DropdownMenuItem(
                        text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            onYearSelected(year)
                            showYearMenu = false
                        }
                    )
                }
            }
        }

        // 4. 月份下拉
        Box {
            val hasMonth = (uiState.releaseMonth ?: 0) > 0
            FilterDropdownChip(
                label = if (hasMonth) "${uiState.releaseMonth}月" else "不限月",
                isSelected = hasMonth,
                isExpanded = showMonthMenu,
                onClick = { showMonthMenu = true }
            )
            DropdownMenu(
                expanded = showMonthMenu,
                onDismissRequest = { showMonthMenu = false },
                shape = ExpressiveTokens.MediumCardShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                monthOptions.forEach { (month, label) ->
                    DropdownMenuItem(
                        text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            onMonthSelected(month)
                            showMonthMenu = false
                        }
                    )
                }
            }
        }

        // 末尾留白，避免最后一个 Chip 紧贴屏幕边缘
        Spacer(modifier = Modifier.width(2.dp))
    }
}

/**
 * 具有下拉展开指示箭头的 FilterChip
 */
@Composable
private fun FilterDropdownChip(
    label: String,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "DropdownArrowRotation"
    )

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        shape = ExpressiveTokens.ChipShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = null
    )
}
