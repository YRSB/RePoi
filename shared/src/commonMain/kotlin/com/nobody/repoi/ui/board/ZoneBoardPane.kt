package com.nobody.repoi.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.local.CategoryItem
import com.nobody.repoi.ui.album.components.AlbumFeedCard
import com.nobody.repoi.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.repoi.ui.board.components.ZoneEmptyPlaceholder
import com.nobody.repoi.ui.board.components.ZoneErrorPlaceholder
import com.nobody.repoi.ui.board.components.ZonePaginationFooter
import com.nobody.repoi.ui.home.components.ActionCard
import com.nobody.repoi.ui.item.list.components.CategoryPickerSheet
import com.nobody.repoi.ui.item.list.components.HobbyFilterAdaptiveSheet
import com.nobody.repoi.ui.item.list.components.HobbyGridCard
import com.nobody.repoi.ui.item.list.components.HobbyListCard

/**
 * 板块共享面板：首页与专区详情共用的「唯一内容渲染入口」。
 *
 * 已实现 [ZoneBoard.Action]（动态）、[ZoneBoard.Album]（分享/相册，#157）与 [ZoneBoard.Hobby]
 * （产品，#159）；其余板块先走防御占位——专区详情其余板块本阶段仍走 `ZoneDetailScreen` 原内联分支。
 *
 * 硬约束（后续改动请勿破坏，否则首页/详情会再次漂移）：
 * 1. 本组件**不包** `Scaffold` / `TopAppBar` / `PullToRefreshBox` / `FAB` / `TabRow`，不持有 header 高度；
 *    内容边距只通过 [contentPadding] 由宿主注入（详情 = `totalHeaderHeightDp + 8dp` / 底部安全区；
 *    首页 = 顶栏 + 二级 TabRow + 筛选行 / 底部栏）。
 * 2. 本组件**不注入**仓储 / 偏好 / `LocalAppMessages`；一切交互经 [actions] 与 [itemActions]。
 * 3. 内部只允许存在**一个** `LazyVerticalStaggeredGrid`；[gridState] 由宿主持有并传入（切换板块时
 *    复用同一滚动状态），本组件不新建状态。
 * 4. 列表项 `key` / `contentType` 必须原样保留（动态为 `contentType = "feed_action"`），
 *    不得在 pane 内去重或重算游标，`hasMore` 由宿主投影进 [ZoneBoardPageView]。
 * 5. 内部用 `when(board)` 分派而不是泛型化，保留各板块类型安全；迁移新板块时在此加分支并在
 *    [ZoneBoardContent] / [ZoneBoardFilters] / [ZoneBoardActions] 补对应字段与方法。
 * 6. 产品板块的两个弹层（高级筛选 / 分类级联）由本面板按 [filters] 的可见性自渲染，
 *    首页与专区详情都不再各自复制一份（见 #159）。
 *
 * @param board 当前板块
 * @param scope 当前查询作用域（首页 All/Follow/Single；详情固定 Single），面板不自行拼参
 * @param content 当前板块内容快照
 * @param filters 当前板块筛选值快照（产品含两个弹层显隐）
 * @param gridState 宿主持有的瀑布流滚动状态
 * @param contentPadding 宿主注入的内容边距
 * @param actions 筛选 / 分页动作
 * @param itemActions 条目点击 / 图片浏览动作
 * @param columns 宿主算好的列数策略（产品与相册按各自规则在面板内计算，不使用该参数）
 * @param hobbyCategoryTree 产品分类树（`parentId -> 子分类`），供分类级联弹层读取；聚合态可传空
 * @param showPinnedPosts 讨论板块是否渲染置顶帖（聚合态建议 false；当前阶段未消费）
 * @param modifier 宿主修饰符（通常 `Modifier.fillMaxSize()`）
 */
@Composable
fun ZoneBoardPane(
    board: ZoneBoard,
    scope: ZoneQueryScope,
    content: ZoneBoardContent,
    filters: ZoneBoardFilters,
    gridState: LazyStaggeredGridState,
    contentPadding: PaddingValues,
    actions: ZoneBoardActions,
    itemActions: ZoneBoardItemActions,
    columns: StaggeredGridCells,
    hobbyCategoryTree: Map<Int, List<CategoryItem>> = emptyMap(),
    showPinnedPosts: Boolean = true,
    modifier: Modifier = Modifier
) {
    when (board) {
        ZoneBoard.Action -> {
            val actionContent = content as? ZoneBoardContent.Action
            if (actionContent == null) {
                // 防御：板块与内容类型不匹配时不崩溃，保持与原空态一致的视觉
                ZoneEmptyPlaceholder("暂无相关动态")
            } else {
                LazyVerticalStaggeredGrid(
                    columns = columns,
                    state = gridState,
                    modifier = modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp
                ) {
                    val page = actionContent.page
                    val errorMessage = page.errorMessage
                    when {
                        errorMessage != null && actionContent.items.isEmpty() -> {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneErrorPlaceholder(
                                    message = errorMessage,
                                    onRetry = { actions.onRetryInitial() }
                                )
                            }
                        }
                        actionContent.items.isEmpty() -> {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneEmptyPlaceholder("暂无相关动态")
                            }
                        }
                        else -> {
                            items(
                                items = actionContent.items,
                                key = { item -> "zone_feed_${item.id}_${item.actionDate}" },
                                contentType = { "feed_action" }
                            ) { item ->
                                ActionCard(
                                    actionItem = item,
                                    onActionClick = itemActions.onActionClick,
                                    onImageClick = itemActions.onActionImageClick
                                )
                            }

                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZonePaginationFooter(
                                    isLoadingMore = page.isLoadingMore,
                                    loadMoreError = page.loadMoreError,
                                    isEndReached = page.isEndReached,
                                    onRetry = { actions.onLoadMore() }
                                )
                            }
                        }
                    }
                }
            }
        }
        ZoneBoard.Album -> {
            val albumContent = content as? ZoneBoardContent.Album
            if (albumContent == null) {
                // 防御：板块与内容类型不匹配时不崩溃，保持与原空态一致的视觉
                ZoneEmptyPlaceholder("暂无相关分享")
            } else {
                // 相册沿用既有列数策略（手机 2 列 / 平板 3-4 列 / 桌面 5 列），不按动态瀑布流的列数渲染
                val albumColumns = rememberAlbumStaggeredGridColumns()
                LazyVerticalStaggeredGrid(
                    columns = albumColumns,
                    state = gridState,
                    modifier = modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp
                ) {
                    val page = albumContent.page
                    val errorMessage = page.errorMessage
                    when {
                        errorMessage != null && albumContent.items.isEmpty() -> {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneErrorPlaceholder(
                                    message = errorMessage,
                                    onRetry = { actions.onRetryInitial() }
                                )
                            }
                        }
                        albumContent.items.isEmpty() -> {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneEmptyPlaceholder("暂无相关分享")
                            }
                        }
                        else -> {
                            items(
                                items = albumContent.items,
                                key = { item -> "zone_album_${item.targetAlbumId}" },
                                contentType = { "album_feed" }
                            ) { item ->
                                AlbumFeedCard(
                                    item = item,
                                    showTime = true,
                                    onClick = { album -> itemActions.onAlbumClick?.invoke(album) }
                                )
                            }

                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZonePaginationFooter(
                                    isLoadingMore = page.isLoadingMore,
                                    loadMoreError = page.loadMoreError,
                                    isEndReached = page.isEndReached,
                                    onRetry = { actions.onLoadMore() }
                                )
                            }
                        }
                    }
                }
            }
        }
        ZoneBoard.Hobby -> {
            val hobbyContent = content as? ZoneBoardContent.Hobby
            if (hobbyContent == null) {
                // 防御：板块与内容类型不匹配时不崩溃，保持与原空态一致的视觉
                ZoneEmptyPlaceholder("暂无相关产品")
            } else {
                // 产品沿用专区详情既有列数规则：网格 = 自适应 120dp；列表 = 自适应 420dp
                val hobbyColumns = remember(hobbyContent.isGridMode) {
                    if (hobbyContent.isGridMode) {
                        StaggeredGridCells.Adaptive(minSize = 120.dp)
                    } else {
                        StaggeredGridCells.Adaptive(minSize = 420.dp)
                    }
                }
                Box(modifier = modifier.fillMaxSize()) {
                    LazyVerticalStaggeredGrid(
                        columns = hobbyColumns,
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = contentPadding,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 8.dp
                    ) {
                        val page = hobbyContent.page
                        val errorMessage = page.errorMessage
                        when {
                            errorMessage != null && hobbyContent.items.isEmpty() -> {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    ZoneErrorPlaceholder(
                                        message = errorMessage,
                                        onRetry = { actions.onRetryInitial() }
                                    )
                                }
                            }
                            hobbyContent.items.isEmpty() -> {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    ZoneEmptyPlaceholder("暂无相关产品")
                                }
                            }
                            else -> {
                                if (hobbyContent.isGridMode) {
                                    items(
                                        items = hobbyContent.items,
                                        key = { item -> "zone_hobby_grid_${item.actualItemId}" },
                                        contentType = { "hobby_grid" }
                                    ) { item ->
                                        HobbyGridCard(
                                            item = item,
                                            onClick = { itemActions.onHobbyClick?.invoke(item) }
                                        )
                                    }
                                } else {
                                    items(
                                        items = hobbyContent.items,
                                        key = { item -> "zone_hobby_list_${item.actualItemId}" },
                                        contentType = { "hobby_list" }
                                    ) { item ->
                                        HobbyListCard(
                                            item = item,
                                            onClick = { itemActions.onHobbyClick?.invoke(item) }
                                        )
                                    }
                                }

                                item(span = StaggeredGridItemSpan.FullLine) {
                                    ZonePaginationFooter(
                                        isLoadingMore = page.isLoadingMore,
                                        loadMoreError = page.loadMoreError,
                                        isEndReached = page.isEndReached,
                                        onRetry = { actions.onLoadMore() }
                                    )
                                }
                            }
                        }
                    }

                    // 两个产品筛选弹层由面板统一渲染：可见性来自 [filters]，宿主（首页/详情）无需复制。
                    // 确定 / 重置会触发筛选 reload，此时列表进入加载态、面板可能被宿主的加载视图替换，
                    // 故这两个动作同时关闭弹层，避免重新装载后面板复挂载时弹层「回弹」。
                    if (filters.showHobbyFilterSheet) {
                        HobbyFilterAdaptiveSheet(
                            filterParam = filters.hobby.toAdvancedFilterParam(),
                            onDismiss = { actions.onHobbyFilterSheetVisible(false) },
                            onApply = { filterParam ->
                                actions.onHobbyAdvancedApply(filterParam)
                                actions.onHobbyFilterSheetVisible(false)
                            },
                            onReset = {
                                actions.onHobbyAdvancedReset()
                                actions.onHobbyFilterSheetVisible(false)
                            }
                        )
                    }
                    if (filters.showHobbyCategoryPickerSheet) {
                        CategoryPickerSheet(
                            selectedCategoryId = filters.hobby.categoryId,
                            selectedSubCategoryId = filters.hobby.subCategoryId,
                            getSubCategories = { parentId -> hobbyCategoryTree[parentId].orEmpty() },
                            onCategorySelected = { categoryId, subCategoryId ->
                                actions.onHobbyCategory(categoryId, subCategoryId)
                            },
                            onDismiss = { actions.onHobbyCategoryPickerVisible(false) }
                        )
                    }
                }
            }
        }
        else -> {
            // 其余板块尚未迁移到共享面板：仅一行防御占位（详情本阶段仍走原内联分支）
            ZoneEmptyPlaceholder("该分类正在建设中...")
        }
    }
}
