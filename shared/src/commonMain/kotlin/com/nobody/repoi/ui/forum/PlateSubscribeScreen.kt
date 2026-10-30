package com.nobody.repoi.ui.forum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.repoi.platform.isLandscapeOrientation
import com.nobody.repoi.platform.PoiShelfBackHandler
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.data.remote.model.ForumItem
import com.nobody.repoi.ui.forum.components.ForumEmptyPlaceholder
import com.nobody.repoi.ui.forum.components.ForumErrorPlaceholder
import com.nobody.repoi.ui.forum.components.ForumPaginationFooter
import com.nobody.repoi.ui.forum.components.PlateSubscribeCard
import com.nobody.repoi.ui.forum.components.PlateSubscribeCardSkeleton
import com.nobody.repoi.ui.home.components.ShimmerHost
import kotlinx.coroutines.launch

/**
 * 版块订阅管理页（对标原版 PlateSubscribeActivity）。
 * 已关注 / 未关注双 Tab，支持关注取关与已关注排序。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlateSubscribeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlateSubscribeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = uiState.selectedTab,
        pageCount = { 2 }
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.selectTab(page)
        }
    }
    LaunchedEffect(uiState.selectedTab) {
        if (pagerState.currentPage != uiState.selectedTab) {
            pagerState.scrollToPage(uiState.selectedTab)
        }
    }

    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage
        if (!message.isNullOrBlank()) {
            messages.show(message)
            viewModel.clearActionMessage()
        }
    }

    // 排序态按返回键取消排序而不保存
    PoiShelfBackHandler(enabled = uiState.isSort) {
        viewModel.cancelSort()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "版块",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
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
                    // 排序入口仅归属已关注 Tab（对标原版）
                    if (uiState.selectedTab == PlateSubscribeViewModel.TAB_FOCUS) {
                        if (uiState.isSort) {
                            TextButton(
                                onClick = viewModel::cancelSort,
                                enabled = !uiState.isSavingOrder
                            ) {
                                Text("取消")
                            }
                        }
                        TextButton(
                            onClick = viewModel::toggleSortMode,
                            enabled = !uiState.isSavingOrder
                        ) {
                            Text(if (uiState.isSort) "保存" else "排序")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = pagerState.currentPage == PlateSubscribeViewModel.TAB_FOCUS,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(PlateSubscribeViewModel.TAB_FOCUS)
                        }
                    },
                    text = {
                        Text(
                            text = "已关注",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (pagerState.currentPage == PlateSubscribeViewModel.TAB_FOCUS) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Medium
                            }
                        )
                    }
                )
                Tab(
                    selected = pagerState.currentPage == PlateSubscribeViewModel.TAB_UNFOCUS,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(PlateSubscribeViewModel.TAB_UNFOCUS)
                        }
                    },
                    text = {
                        Text(
                            text = "未关注",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (pagerState.currentPage == PlateSubscribeViewModel.TAB_UNFOCUS) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Medium
                            }
                        )
                    }
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.Top
            ) { page ->
                if (page == PlateSubscribeViewModel.TAB_FOCUS) {
                    PlateTabPage(
                        plates = uiState.focusPlates,
                        isLoading = uiState.isFocusLoading,
                        isRefreshing = uiState.isFocusRefreshing,
                        isLoadingMore = uiState.isFocusLoadingMore,
                        hasMore = uiState.focusHasMore,
                        errorMessage = uiState.focusErrorMessage,
                        loadMoreError = uiState.focusLoadMoreError,
                        isUnfocusTab = false,
                        isSort = uiState.isSort,
                        userLevel = uiState.userLevel,
                        mutatingIds = uiState.mutatingIds,
                        emptyText = "暂无关注的板块，快去关注您想要的看的板块叭~",
                        onRefresh = { viewModel.refresh(PlateSubscribeViewModel.TAB_FOCUS) },
                        onLoadMore = { viewModel.loadMore(PlateSubscribeViewModel.TAB_FOCUS) },
                        onRetry = { viewModel.retry(PlateSubscribeViewModel.TAB_FOCUS) },
                        onLogin = if (!uiState.isLoggedIn) onNavigateToLogin else null,
                        onToggleFocus = viewModel::toggleFocus,
                        onMove = viewModel::movePlate
                    )
                } else {
                    PlateTabPage(
                        plates = uiState.unfocusPlates,
                        isLoading = uiState.isUnfocusLoading,
                        isRefreshing = uiState.isUnfocusRefreshing,
                        isLoadingMore = uiState.isUnfocusLoadingMore,
                        hasMore = uiState.unfocusHasMore,
                        errorMessage = uiState.unfocusErrorMessage,
                        loadMoreError = uiState.unfocusLoadMoreError,
                        isUnfocusTab = true,
                        isSort = false,
                        userLevel = uiState.userLevel,
                        mutatingIds = uiState.mutatingIds,
                        emptyText = "您已关注了所有板块~",
                        onRefresh = { viewModel.refresh(PlateSubscribeViewModel.TAB_UNFOCUS) },
                        onLoadMore = { viewModel.loadMore(PlateSubscribeViewModel.TAB_UNFOCUS) },
                        onRetry = { viewModel.retry(PlateSubscribeViewModel.TAB_UNFOCUS) },
                        onLogin = if (!uiState.isLoggedIn) onNavigateToLogin else null,
                        onToggleFocus = viewModel::toggleFocus,
                        onMove = { _, _ -> }
                    )
                }
            }
        }
    }
}

/**
 * 版块自适应列数：横屏且宽度不小于 Medium 时双列，其余单列。
 */
@Composable
private fun rememberPlateGridColumns(): StaggeredGridCells {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val isLandscape = isLandscapeOrientation()
    return remember(windowSizeClass, isLandscape) {
        if (isLandscape && windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)) {
            StaggeredGridCells.Fixed(2)
        } else {
            StaggeredGridCells.Fixed(1)
        }
    }
}

/**
 * 单个版块 Tab 的分页列表。
 */
@Composable
private fun PlateTabPage(
    plates: List<ForumItem>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    errorMessage: String?,
    loadMoreError: String?,
    isUnfocusTab: Boolean,
    isSort: Boolean,
    userLevel: Int,
    mutatingIds: Set<Long>,
    emptyText: String,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onLogin: (() -> Unit)?,
    onToggleFocus: (ForumItem) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyStaggeredGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalCount = gridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalCount > 0 && lastVisibleIndex >= totalCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, plates.size, hasMore, isLoadingMore, loadMoreError) {
        if (shouldLoadMore && plates.isNotEmpty() && hasMore && !isLoadingMore && loadMoreError == null) {
            onLoadMore()
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyVerticalStaggeredGrid(
            columns = rememberPlateGridColumns(),
            state = gridState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading && plates.isEmpty()) {
                items(
                    5,
                    key = { index -> "plate_skeleton_$index" },
                    contentType = { "plate_skeleton" }
                ) {
                    ShimmerHost { PlateSubscribeCardSkeleton() }
                }
            } else if (errorMessage != null && plates.isEmpty()) {
                item(
                    span = StaggeredGridItemSpan.FullLine,
                    key = "plates_error",
                    contentType = "plates_error"
                ) {
                    ForumErrorPlaceholder(
                        message = errorMessage,
                        onRetry = onRetry,
                        onLogin = onLogin
                    )
                }
            } else if (plates.isEmpty()) {
                item(
                    span = StaggeredGridItemSpan.FullLine,
                    key = "plates_empty",
                    contentType = "plates_empty"
                ) {
                    ForumEmptyPlaceholder(emptyText)
                }
            } else {
                items(
                    count = plates.size,
                    key = { index -> "plate_${plates[index].id}" },
                    contentType = { "plate_card" }
                ) { index ->
                    val plate = plates[index]
                    PlateSubscribeCard(
                        plate = plate,
                        showNewBadge = isUnfocusTab && plate.isNewPlate,
                        userLevel = userLevel,
                        isMutating = mutatingIds.contains(plate.id),
                        isSortMode = isSort,
                        sortIndex = index,
                        sortSize = plates.size,
                        onToggleFocus = { onToggleFocus(plate) },
                        onMoveUp = { onMove(index, index - 1) },
                        onMoveDown = { onMove(index, index + 1) }
                    )
                }

                item(
                    span = StaggeredGridItemSpan.FullLine,
                    key = "plates_footer",
                    contentType = "plates_footer"
                ) {
                    ForumPaginationFooter(
                        isLoadingMore = isLoadingMore,
                        loadMoreError = loadMoreError,
                        isEndReached = !hasMore,
                        onRetry = onLoadMore
                    )
                }
            }
        }
    }
}
