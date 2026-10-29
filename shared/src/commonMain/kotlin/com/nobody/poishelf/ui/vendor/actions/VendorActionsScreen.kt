package com.nobody.poishelf.ui.vendor.actions

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import com.nobody.poishelf.platform.LocalAppMessages
import com.nobody.poishelf.ui.home.components.ShimmerHost
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.poishelf.data.remote.model.ActionItem
import com.nobody.poishelf.ui.album.AlbumNavParam
import com.nobody.poishelf.ui.navigation.CharacterNavParam
import com.nobody.poishelf.ui.common.LocalImageViewer
import com.nobody.poishelf.ui.detail.DetailNavParam
import com.nobody.poishelf.ui.home.components.ActionCard
import com.nobody.poishelf.ui.home.components.ActionCardSkeleton
import com.nobody.poishelf.ui.home.rememberFeedStaggeredGridColumns
import com.nobody.poishelf.ui.navigation.VendorActionsNavParam
import com.nobody.poishelf.ui.person.PersonNavParam
import com.nobody.poishelf.ui.series.SeriesNavParam
import com.nobody.poishelf.ui.vendor.VendorNavParam
import com.nobody.poishelf.ui.works.WorksNavParam
import com.nobody.poishelf.util.FeedActionDispatcher
import com.nobody.poishelf.util.paging.LoadStage

/**
 * 厂商全量动态二级页面。
 *
 * 样式体系严格与 PoiShelf 首页动态 (`HomeFeedScreen`) 和关注动态 (`FollowScreen`) 保持一致：
 * 采用 [rememberFeedStaggeredGridColumns] 自适应瀑布流网格布局，动态条目统一使用 [ActionCard]，
 * 初始加载态采用 [ActionCardSkeleton]，全屏大图接入 [LocalImageViewer]，路由跳转统一由 [FeedActionDispatcher] 分发。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorActionsScreen(
    navParam: VendorActionsNavParam,
    onBack: () -> Unit,
    onNavigateToHobby: (DetailNavParam) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToAlbum: (AlbumNavParam) -> Unit = {},
    onNavigateToVendor: (VendorNavParam) -> Unit = {},
    onNavigateToCharacter: (CharacterNavParam) -> Unit = {},
    onNavigateToSeries: (SeriesNavParam) -> Unit = {},
    onNavigateToWorks: (WorksNavParam) -> Unit = {},
    onNavigateToPerson: (PersonNavParam) -> Unit = {},
    onNavigateToUser: (Long) -> Unit = {},
    viewModel: VendorActionsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagedState = uiState.pagedState
    val messages = LocalAppMessages.current
    val imageViewer = LocalImageViewer.current

    LaunchedEffect(navParam) {
        viewModel.init(navParam)
    }

    val onCardImageClick = remember(imageViewer) {
        { rawUrls: List<String>, initialIndex: Int ->
            imageViewer.show(rawUrls, initialIndex)
        }
    }

    val onActionClick: (ActionItem) -> Unit = remember(
        onNavigateToHobby,
        onNavigateToAlbum,
        onNavigateToVendor,
        onNavigateToCharacter,
        onNavigateToPerson,
        onNavigateToWorks,
        onNavigateToSeries,
        onNavigateToUser
    ) {
        { item ->
            FeedActionDispatcher.dispatch(
                actionItem = item,
                onMessage = { messages.show(it) },
                onNavigateToDetail = onNavigateToHobby,
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToVendor = onNavigateToVendor,
                onNavigateToCharacter = onNavigateToCharacter,
                onNavigateToPerson = onNavigateToPerson,
                onNavigateToWorks = onNavigateToWorks,
                onNavigateToSeries = onNavigateToSeries,
                onNavigateToUser = onNavigateToUser
            )
        }
    }

    val feedColumns = rememberFeedStaggeredGridColumns()
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = if (uiState.companyName.isNullOrBlank()) "厂商动态" else "${uiState.companyName} 动态",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        // 右侧品类筛选按钮与下拉菜单
                        Box {
                            IconButton(onClick = { viewModel.toggleCategoryMenu(true) }) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "品类筛选",
                                    tint = if (uiState.selectedCategoryId != 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            DropdownMenu(
                                expanded = uiState.isCategoryMenuOpen,
                                onDismissRequest = { viewModel.toggleCategoryMenu(false) }
                            ) {
                                VendorActionsViewModel.CATEGORY_OPTIONS.forEach { option ->
                                    val isSelected = option.id == uiState.selectedCategoryId
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = option.label,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectCategory(option.id)
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // 7 个动态分类 Tab
                SecondaryScrollableTabRow(
                    selectedTabIndex = uiState.selectedTabIndex,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    VendorActionsViewModel.TABS.forEachIndexed { index, tab ->
                        val selected = uiState.selectedTabIndex == index
                        Tab(
                            selected = selected,
                            onClick = { viewModel.selectTab(index) },
                            text = {
                                Text(
                                    text = tab.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = pagedState.stage == LoadStage.Refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                pagedState.stage == LoadStage.InitialLoading && pagedState.items.isEmpty() -> {
                    LazyVerticalStaggeredGrid(
                        columns = feedColumns,
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = navBarBottomPadding + 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 8.dp,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(6) { ShimmerHost { ActionCardSkeleton() } }
                    }
                }

                pagedState.items.isEmpty() && pagedState.stage != LoadStage.InitialLoading && pagedState.initialError != null -> {
                    VendorActionsErrorState(
                        errorMessage = pagedState.initialError ?: "加载动态失败",
                        onRetry = { viewModel.retry() }
                    )
                }

                pagedState.items.isEmpty() && pagedState.stage != LoadStage.InitialLoading -> {
                    VendorActionsEmptyState(
                        tabName = VendorActionsViewModel.TABS.getOrNull(uiState.selectedTabIndex)?.name ?: "全部"
                    )
                }

                else -> {
                    val gridState = rememberLazyStaggeredGridState()

                    // 触底加载防重检测（与 FollowScreen 保持一致）
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val totalItemCount = gridState.layoutInfo.totalItemsCount
                            totalItemCount > 0 && lastVisibleItemIndex >= totalItemCount - 2
                        }
                    }

                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore && pagedState.hasMore && pagedState.stage == LoadStage.Idle) {
                            viewModel.loadMore()
                        }
                    }

                    LazyVerticalStaggeredGrid(
                        columns = feedColumns,
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = navBarBottomPadding + 16.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 8.dp,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = pagedState.items,
                            key = { item -> "${item.id}_${item.actionDate}" }
                        ) { action ->
                            ActionCard(
                                actionItem = action.toActionItem(),
                                onItemClick = onActionClick,
                                onUserClick = onNavigateToUser,
                                onImageClick = onCardImageClick
                            )
                        }

                        // 底部加载更多或错误提示
                        item(span = StaggeredGridItemSpan.FullLine) {
                            when {
                                pagedState.stage == LoadStage.Appending -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 2.5.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                pagedState.appendError != null -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.retry() }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${pagedState.appendError}，点击重试 ↻",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                !pagedState.hasMore && pagedState.items.isNotEmpty() -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "—— 已经到底啦 (´･ω･`) ——",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
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

/**
 * 空状态。
 */
@Composable
private fun VendorActionsEmptyState(tabName: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "暂无【$tabName】分类动态",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
            Text(
                text = "厂商发布最新出荷、开订或制作决定时将在此展示",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 错误重试状态。
 */
@Composable
private fun VendorActionsErrorState(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = errorMessage,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("点击重试")
            }
        }
    }
}
