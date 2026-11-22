package com.nobody.repoi.ui.vendor.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import com.nobody.repoi.ui.common.loading.PoiLoadingView
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
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.home.components.ActionCard
import com.nobody.repoi.ui.home.rememberFeedStaggeredGridColumns
import com.nobody.repoi.ui.navigation.VendorActionsNavParam
import com.nobody.repoi.util.paging.LoadStage
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorActionsScreen(
    navParam: VendorActionsNavParam,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onActionClick: ((ActionItem) -> Unit)? = null,
    viewModel: VendorActionsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagedState = uiState.pagedState
    val imageViewer = LocalImageViewer.current

    LaunchedEffect(navParam) {
        viewModel.init(navParam)
    }

    val onCardImageClick = remember(imageViewer) {
        { rawUrls: List<String>, initialIndex: Int ->
            imageViewer.show(rawUrls, initialIndex)
        }
    }

    val feedColumns = rememberFeedStaggeredGridColumns()

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
                                painter = painterResource(PoiIcons.AutoMirrored.Filled.ArrowBack),
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {

                        Box {
                            IconButton(onClick = { viewModel.toggleCategoryMenu(true) }) {
                                Icon(
                                    painter = painterResource(PoiIcons.FilterList),
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
                                                        painter = painterResource(PoiIcons.Check),
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
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(PaddingValues(top = innerPadding.calculateTopPadding()))
        ) {
            when {
                pagedState.stage == LoadStage.InitialLoading && pagedState.items.isEmpty() -> {
                    PoiLoadingView()
                }

                pagedState.items.isEmpty() && pagedState.stage != LoadStage.InitialLoading && pagedState.initialError != null -> {
                    VendorActionsErrorState(
                        errorMessage = pagedState.initialError,
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
                            bottom = 16.dp + innerPadding.calculateBottomPadding()
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 8.dp,
                        modifier = Modifier
                            .fillMaxSize()
                            .consumeWindowInsets(PaddingValues(bottom = innerPadding.calculateBottomPadding()))
                    ) {
                        items(
                            items = pagedState.items,
                            key = { item -> "${item.id}_${item.actionDate}" }
                        ) { action ->
                            ActionCard(
                                actionItem = action.toActionItem(),
                                onActionClick = onActionClick,
                                onImageClick = onCardImageClick
                            )
                        }

                        item(span = StaggeredGridItemSpan.FullLine) {
                            when {
                                pagedState.stage == LoadStage.Appending -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LoadingIndicator(
                                            modifier = Modifier.size(28.dp)
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
                    painter = painterResource(PoiIcons.Refresh),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("点击重试")
            }
        }
    }
}
