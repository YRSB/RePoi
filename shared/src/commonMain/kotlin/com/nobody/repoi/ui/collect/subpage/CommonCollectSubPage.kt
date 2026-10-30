package com.nobody.repoi.ui.collect.subpage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.nobody.repoi.ui.collect.components.LoadingCollectView
import com.nobody.repoi.ui.theme.ExpressiveTokens

// ── 通用实体子页（厂商/系列/角色/人物） ─────────────────────

/** 原版空态文案 `R.string.text_empty_collect_item` */
internal const val COLLECT_EMPTY_TEXT = "未收藏内容"

/** 原版「用户已隐藏内容」空态文案 `R.string.text_empty_user_hide` */
internal const val COLLECT_CONTENT_HIDDEN_TEXT = "该用户已设为隐藏此内容"

/**
 * 通用 4 页网格列数：`StaggeredGridCells.Adaptive(140.dp)`。
 *
 * 横屏 / 平板会自动加列；列表形态用 `Adaptive(420.dp)`（与条目页「查看更多」一致）。
 */
@Composable
internal fun rememberCommonCollectGridColumns(): StaggeredGridCells {
    return remember { StaggeredGridCells.Adaptive(minSize = 140.dp) }
}

/**
 * 通用 4 页（厂商 / 系列 / 角色 / 人物）子页。
 *
 * 对标原版 `UserCollectItemSubPageFragment.java`：
 * - 顶部 2 档二级排序 Tab（收藏时间 `actionDate` / 人气 `collect`）；
 * - 右上角 `btn_action_view` 网格 ↔ 列表双模切换（列表恒定单列，网格为瀑布流）；
 * - 下拉刷新 + 触底分页 + 空态 / 错误态 / 「用户已隐藏内容」空态。
 *
 * @param commonOrder 当前 2 档排序 key
 * @param isListView true = 列表单列，false = 网格瀑布流
 * @param onSeriesSubPageClick 系列页 `itemType=="series" && pageState==1` 时的专属路由回调，
 *        缺省时回落到 [onItemClick]
 * @param onCharacterClick 角色页点击回调（§2.6 P0：原版有跳转，PoiShelf 曾是 `else -> Unit`），
 *        缺省时回落到 [onItemClick]
 */
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
    val listState = rememberLazyGridState()
    val gridState = rememberLazyStaggeredGridState()

    // 触底分页：单个布尔派生收敛滚动位置判断，空列表、已到底时不触发
    val shouldLoadMore by remember(isListView) {
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

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && subPageData.hasMore && !subPageData.isPaging && !subPageData.isLoading && !subPageData.isRefreshing) {
            onLoadNextPage()
        }
    }

    // 点击分流：系列专属路由 > 角色页专属回调 > 通用回调
    val handleItemClick: (UserCollectCommonItem) -> Unit = { target ->
        when {
            target.isSeriesDetailRoute() && onSeriesSubPageClick != null -> onSeriesSubPageClick(target)
            category == UserCollectCategory.CHARACTER && onCharacterClick != null -> onCharacterClick(target)
            else -> onItemClick(target)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部：2 档二级排序 Tab + 网格/列表切换（原版 btn_action_view）
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
                    imageVector = if (isListView) Icons.Default.GridView else Icons.AutoMirrored.Filled.List,
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
                    LoadingCollectView()
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
                    // 列表用 Adaptive(420.dp)：手机 1 列、横屏/平板自动 2 列，
                    // 与条目页「查看更多」(HobbyListScreen) 一致。
                    // 早前用 LazyColumn，构造上永远只能 1 列，横屏下大片留白。
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

/**
 * 通用 4 页的流式分页脚（列表 / 网格两模共用一个实现）。
 */
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
            isPaging -> CircularProgressIndicator(
                modifier = Modifier.padding(8.dp),
                color = MaterialTheme.colorScheme.primary
            )

            errorMessage != null -> Surface(
                onClick = onRetryPaging,
                shape = ExpressiveTokens.ButtonShape,
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