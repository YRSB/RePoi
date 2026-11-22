package com.nobody.repoi.ui.collect.subpage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.remote.model.UserCollectCommonItem
import com.nobody.repoi.ui.collect.SubPageData
import com.nobody.repoi.ui.collect.components.CollectCommonOrderTabs
import com.nobody.repoi.ui.collect.components.CollectOrderTabRow
import com.nobody.repoi.ui.collect.components.EmptyCollectView
import com.nobody.repoi.ui.collect.components.ErrorCollectView
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import androidx.compose.foundation.shape.CircleShape
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

internal const val COLLECT_EMPTY_TEXT = "未收藏内容"

internal const val COLLECT_CONTENT_HIDDEN_TEXT = "该用户已设为隐藏此内容"

@Composable
internal fun rememberCommonCollectGridColumns(): StaggeredGridCells {
    return remember { StaggeredGridCells.Adaptive(minSize = 140.dp) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommonCollectSubPage(
    category: UserCollectCategory,
    subPageData: SubPageData<UserCollectCommonItem>,
    commonOrder: String,
    isListView: Boolean,
    navBarBottomPadding: Dp,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onSwitchOrder: (String) -> Unit,
    onToggleViewMode: () -> Unit,
    onItemClick: (UserCollectCommonItem) -> Unit,
    onSeriesSubPageClick: ((UserCollectCommonItem) -> Unit)? = null,
    onCharacterClick: ((UserCollectCommonItem) -> Unit)? = null,
    onRetry: () -> Unit
) {

    val listState = rememberKeyedLazyGridState("common_list", commonOrder)
    val gridState = rememberKeyedLazyStaggeredGridState("common_grid", commonOrder)

    val shouldLoadMore by remember(listState, gridState, isListView) {
        derivedStateOf {
            val totalCount = if (isListView) {
                listState.layoutInfo.totalItemsCount
            } else {
                gridState.layoutInfo.totalItemsCount
            }
            val lastVisibleIndex = if (isListView) {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            } else {
                gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            }
            totalCount > 0 && lastVisibleIndex >= totalCount - 4
        }
    }

    LaunchedEffect(shouldLoadMore, subPageData.items.size) {
        if (shouldLoadMore && subPageData.hasMore && !subPageData.isPaging && !subPageData.isLoading && !subPageData.isRefreshing) {
            onLoadNextPage()
        }
    }

    val handleItemClick: (UserCollectCommonItem) -> Unit = { target ->
        when {
            target.isSeriesDetailRoute() && onSeriesSubPageClick != null -> onSeriesSubPageClick(target)
            category == UserCollectCategory.CHARACTER && onCharacterClick != null -> onCharacterClick(target)
            else -> onItemClick(target)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CollectOrderTabRow(
                titles = CollectCommonOrderTabs,
                selected = commonOrder,
                onSelect = onSwitchOrder,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleViewMode) {
                Icon(
                    painter = painterResource(if (isListView) PoiIcons.GridView else PoiIcons.AutoMirrored.Filled.List),
                    contentDescription = if (isListView) "切换为网格视图" else "切换为列表视图",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = subPageData.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                subPageData.isLoading && subPageData.items.isEmpty() -> {
                    PoiLoadingView()
                }

                subPageData.errorMessage != null && subPageData.items.isEmpty() && !subPageData.isContentHidden -> {
                    ErrorCollectView(errorMessage = subPageData.errorMessage, onRetry = onRetry)
                }

                subPageData.isContentHidden && subPageData.items.isEmpty() -> {
                    EmptyCollectView(title = COLLECT_CONTENT_HIDDEN_TEXT)
                }

                subPageData.items.isEmpty() -> {
                    EmptyCollectView(title = COLLECT_EMPTY_TEXT)
                }

                isListView -> {

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 420.dp),
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 8.dp,
                            end = 12.dp,
                            bottom = 16.dp + navBarBottomPadding
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = subPageData.items,
                            key = { item -> "${category.name}_${item.actualItemId}_${item.id}" }
                        ) { item ->
                            CommonCollectListCard(
                                item = item,
                                category = category,
                                onClick = { handleItemClick(item) }
                            )
                        }
                        item(key = "common_paging_footer", span = { GridItemSpan(maxLineSpan) }) {
                            CommonCollectPagingFooter(
                                isPaging = subPageData.isPaging,
                                hasMore = subPageData.hasMore,
                                errorMessage = subPageData.errorMessage,
                                onRetryPaging = onLoadNextPage
                            )
                        }
                    }
                }

                else -> {
                    LazyVerticalStaggeredGrid(
                        columns = rememberCommonCollectGridColumns(),
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 8.dp,
                            end = 12.dp,
                            bottom = 16.dp + navBarBottomPadding
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 8.dp,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = subPageData.items,
                            key = { item -> "${category.name}_${item.actualItemId}_${item.id}" }
                        ) { item ->
                            CommonCollectGridCard(
                                item = item,
                                onClick = { handleItemClick(item) }
                            )
                        }
                        item(span = StaggeredGridItemSpan.FullLine, key = "common_grid_footer") {
                            CommonCollectPagingFooter(
                                isPaging = subPageData.isPaging,
                                hasMore = subPageData.hasMore,
                                errorMessage = subPageData.errorMessage,
                                onRetryPaging = onLoadNextPage
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommonCollectPagingFooter(
    isPaging: Boolean,
    hasMore: Boolean,
    errorMessage: String?,
    onRetryPaging: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isPaging -> LoadingIndicator(
                modifier = Modifier.size(28.dp)
            )

            errorMessage != null -> Surface(
                onClick = onRetryPaging,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = "${errorMessage}，点击重试",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            !hasMore -> Text(
                text = "已展示全部收藏",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
