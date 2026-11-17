package com.nobody.repoi.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
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
import com.nobody.repoi.data.remote.model.PostItem
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
import com.nobody.repoi.ui.zone.components.ZoneEntityCard
import com.nobody.repoi.ui.zone.components.ZonePinnedPostsBar
import com.nobody.repoi.ui.zone.components.ZonePostCard
import com.nobody.repoi.ui.zone.components.ZoneResaleCard

/**
 * 板块共享面板：首页与专区详情共用的「唯一内容渲染入口」。
 *
 * 已实现 [ZoneBoard.Action]（动态）、[ZoneBoard.Album]（分享/相册，#157）、[ZoneBoard.Posts]
 * （讨论，#158）、[ZoneBoard.Hobby]（产品，#159）、[ZoneBoard.Resale]（二手，#160）与实体 5 类
 * （厂商/系列/作品/角色/人物，共用 `api/zone/items`，#161）分支——覆盖客户端白名单全部 10 类，
 * 因此**没有「建设中」兜底分支**（白名单外类型在能力矩阵已被丢弃，ADR-0022 决策 6「不做假入口」）。
 * 空态 / 错误态统一使用 [com.nobody.repoi.ui.board.components.ZoneEmptyPlaceholder] /
 * [com.nobody.repoi.ui.board.components.ZoneErrorPlaceholder]（卡片式，§3.6），与首页同一基元。
 *
 * 硬约束（后续改动请勿破坏，否则首页/详情会再次漂移）：
 * 1. 本组件**不包** `Scaffold` / `TopAppBar` / `PullToRefreshBox` / `FAB` / `TabRow`，不持有 header 高度；
 *    内容边距只通过 [contentPadding] 由宿主注入（详情 = `totalHeaderHeightDp + 8dp` / 底部安全区；
 *    首页 = 顶栏 + 二级 TabRow + 筛选行 / 底部栏）。
 * 2. 本组件**不注入**仓储 / 偏好 / `LocalAppMessages`；一切交互经 [actions] 与 [itemActions]。
 * 3. 内部只允许存在**一个** `LazyVerticalStaggeredGrid`；[gridState] 由宿主持有并传入（切换板块时
 *    复用同一滚动状态），本组件不新建状态。
 * 4. 列表项 `key` / `contentType` 必须原样保留（动态为 `contentType = "feed_action"`、
 *    分享为 `contentType = "album_feed"`、讨论为 `contentType = "zone_post"`、二手为
 *    `contentType = "zone_resale"`、产品为 `hobby_grid` / `hobby_list`、实体为
 *    `contentType = "zone_entity_<type>"`），不得在 pane 内去重或重算游标，`hasMore` 由宿主
 *    投影进 [ZoneBoardPageView]。
 * 5. 内部用 `when(board)` 分派而不是泛型化，保留各板块类型安全；迁移新板块时在此加分支并在
 *    [ZoneBoardContent] / [ZoneBoardFilters] / [ZoneBoardActions] 补对应字段与方法。
 * 6. 产品板块的两个弹层（高级筛选 / 分类级联）**不在本面板内**：面板只在其「有内容」时存在，
 *    而首页在空/错/加载态用卡片或加载视图顶替面板，弹层若挂在面板内会让筛选入口在空结果时点击无响应
 *    （并因显隐标志残留导致后续自行弹出）。宿主须在四态之外无条件组合 [ZoneBoardHobbySheets]（见 #159）。
 * 7. 六个板块的空/错/加载页脚骨架统一收在 [BoardGridContainer]（#150 审查 B6），
 *    避免新增板块时再复制一份「错误卡片 + 空卡片 + 分页页脚」。
 *
 * @param board 当前板块
 * @param content 当前板块内容快照
 * @param gridState 宿主持有的瀑布流滚动状态
 * @param contentPadding 宿主注入的内容边距
 * @param actions 筛选 / 分页动作
 * @param itemActions 条目点击 / 图片浏览动作
 * @param columns 宿主算好的列数策略（产品与相册按各自规则在面板内计算，不使用该参数）
 * @param showPinnedPosts 讨论板块是否渲染置顶帖条：单专区 true、聚合态（全部）必须 false
 *   （置顶是单专区语义；关注聚合已由 `homeBoardsFor` 隐藏整个讨论板块）
 * @param modifier 宿主修饰符（通常 `Modifier.fillMaxSize()`）
 */
@Composable
fun ZoneBoardPane(
    board: ZoneBoard,
    content: ZoneBoardContent,
    gridState: LazyStaggeredGridState,
    contentPadding: PaddingValues,
    actions: ZoneBoardActions,
    itemActions: ZoneBoardItemActions,
    columns: StaggeredGridCells,
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
                BoardGridContainer(
                    itemCount = actionContent.items.size,
                    page = actionContent.page,
                    emptyText = "暂无相关动态",
                    columns = columns,
                    gridState = gridState,
                    contentPadding = contentPadding,
                    modifier = modifier,
                    actions = actions
                ) {
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
                BoardGridContainer(
                    itemCount = albumContent.items.size,
                    page = albumContent.page,
                    emptyText = "暂无相关分享",
                    columns = albumColumns,
                    gridState = gridState,
                    contentPadding = contentPadding,
                    modifier = modifier,
                    actions = actions
                ) {
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
                }
            }
        }
        ZoneBoard.Posts -> {
            val postsContent = content as? ZoneBoardContent.Posts
            if (postsContent == null) {
                // 防御：板块与内容类型不匹配时不崩溃，保持与原空态一致的视觉
                ZoneEmptyPlaceholder("暂无相关讨论")
            } else {
                val onPostClick: (PostItem) -> Unit = itemActions.onPostClick ?: {}
                val onPostImageClick: (List<String>, Int) -> Unit = itemActions.onPostImageClick ?: { _, _ -> }
                // 只统计真正会渲染的条目：showPinnedPosts=false 时置顶条不渲染，
                // 若仍计入 itemCount，空列表会被误判为「有内容」，空态/首屏错误态被吞掉。
                val visiblePinnedCount =
                    if (showPinnedPosts) postsContent.pinnedPosts.size else 0
                BoardGridContainer(
                    itemCount = postsContent.items.size + visiblePinnedCount,
                    page = postsContent.page,
                    emptyText = "暂无相关讨论",
                    columns = columns,
                    gridState = gridState,
                    contentPadding = contentPadding,
                    modifier = modifier,
                    actions = actions,
                    header = {
                        // 置顶帖条只在单专区渲染：聚合态（全部）无置顶语义，关注态已隐藏整个板块
                        if (showPinnedPosts && postsContent.pinnedPosts.isNotEmpty()) {
                            item(key = "zone_pinned_posts", span = StaggeredGridItemSpan.FullLine) {
                                ZonePinnedPostsBar(
                                    pinnedPosts = postsContent.pinnedPosts,
                                    onPostClick = onPostClick
                                )
                            }
                        }
                    }
                ) {
                    items(
                        items = postsContent.items,
                        key = { item -> "zone_post_${item.id}" },
                        contentType = { "zone_post" }
                    ) { post ->
                        ZonePostCard(
                            post = post,
                            onPostClick = onPostClick,
                            onImageClick = onPostImageClick,
                            onUserClick = itemActions.onPostUserClick
                        )
                    }
                }
            }
        }
        ZoneBoard.Resale -> {
            val resaleContent = content as? ZoneBoardContent.Resale
            if (resaleContent == null) {
                // 防御：板块与内容类型不匹配时不崩溃，保持与原空态一致的视觉
                ZoneEmptyPlaceholder("暂无相关二手条目")
            } else {
                BoardGridContainer(
                    itemCount = resaleContent.items.size,
                    page = resaleContent.page,
                    emptyText = "暂无相关二手条目",
                    columns = columns,
                    gridState = gridState,
                    contentPadding = contentPadding,
                    modifier = modifier,
                    actions = actions
                ) {
                    items(
                        items = resaleContent.items,
                        key = { item -> "zone_resale_${item.id}" },
                        contentType = { "zone_resale" }
                    ) { item ->
                        ZoneResaleCard(
                            item = item,
                            onClick = { itemActions.onResaleClick?.invoke(item) },
                            onUserClick = itemActions.onResaleUserClick,
                            // 可空回调原样透传：卡片据 null 隐藏「联系」「更多」，避免渲染可点但无响应的假入口
                            onContactClick = itemActions.onResaleContactClick?.let { click -> { click(item) } },
                            onMoreClick = itemActions.onResaleMoreClick?.let { click -> { click(item) } }
                        )
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
                BoardGridContainer(
                    itemCount = hobbyContent.items.size,
                    page = hobbyContent.page,
                    emptyText = "暂无相关产品",
                    columns = hobbyColumns,
                    gridState = gridState,
                    contentPadding = contentPadding,
                    modifier = modifier,
                    actions = actions
                ) {
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
                }
            }
        }
        ZoneBoard.Company,
        ZoneBoard.Series,
        ZoneBoard.Works,
        ZoneBoard.Character,
        ZoneBoard.Person -> {
            val entityContent = content as? ZoneBoardContent.Entity
            if (entityContent == null) {
                // 防御：板块与内容类型不匹配时不崩溃，保持与原空态一致的视觉
                ZoneEmptyPlaceholder("暂无相关条目")
            } else {
                BoardGridContainer(
                    itemCount = entityContent.items.size,
                    page = entityContent.page,
                    emptyText = "暂无相关条目",
                    columns = columns,
                    gridState = gridState,
                    contentPadding = contentPadding,
                    modifier = modifier,
                    actions = actions
                ) {
                    // 实体 5 类共用同一卡片：key/contentType 带板块类型，避免跨板块复用旧条目；
                    // 服务端 `charactar` 拼写来自 [ZoneBoard.Character.type]，此处只透传。
                    items(
                        items = entityContent.items,
                        key = { item -> "zone_entity_${board.type}_${item.targetId}" },
                        contentType = { "zone_entity_${board.type}" }
                    ) { item ->
                        ZoneEntityCard(
                            item = item,
                            onClick = { itemActions.onEntityClick?.invoke(board, item) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 产品板块的两个弹层（高级筛选 / 分类级联），由**宿主**无条件组合（与内容四态解耦）。
 *
 * 为什么不在 [ZoneBoardPane] 内：首页在首屏加载 / 空态 / 错误态时用卡片或加载视图顶替面板，
 * 若弹层随面板一起消失，筛选条上的「高级筛选」「分类」入口就会变成点击无反馈的死入口；
 * 弹层显隐标志还会残留，等列表恢复后自行弹出。宿主必须在 `when` 四态之外调用本函数，
 * 并只在当前板块为 [ZoneBoard.Hobby] 时组合，避免切板块后残留弹层。
 *
 * @param filters 产品筛选快照（含两个弹层的显隐与已选值）
 * @param hobbyCategoryTree 产品分类树（`parentId -> 子分类`）
 */
@Composable
fun ZoneBoardHobbySheets(
    filters: ZoneBoardFilters,
    hobbyCategoryTree: Map<Int, List<CategoryItem>>,
    actions: ZoneBoardActions
) {
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

/**
 * 板块内容区的统一瀑布流骨架（#150 审查 B6：六个板块原本各有一份同构实现）。
 *
 * 只负责「空态 / 错误态 / 内容 + 分页页脚」三态骨架；条目本身由 [itemsContent] 注入
 * （条目 DSL 的 `key` / `contentType` 仍在各板块分支内原样声明），讨论板块的置顶帖条由
 * [header] 注入。空态 / 错误态用的卡片基元与首页四态完全一致（§3.6）。
 *
 * @param itemCount 当前板块条目数（含置顶帖等头部内容；为 0 时渲染空态卡片）
 * @param page 分页快照（加载更多 / 加载失败 / 已到底由页脚消费）
 * @param emptyText 空态文案（各板块措辞不同）
 * @param header 内容区顶部附加块（如讨论置顶帖条），仅在有条目时渲染
 * @param itemsContent 条目列表 DSL（调用方自行声明稳定的 key / contentType）
 */
@Composable
private fun BoardGridContainer(
    itemCount: Int,
    page: ZoneBoardPageView,
    emptyText: String,
    columns: StaggeredGridCells,
    gridState: LazyStaggeredGridState,
    contentPadding: PaddingValues,
    modifier: Modifier,
    actions: ZoneBoardActions,
    header: (LazyStaggeredGridScope.() -> Unit)? = null,
    itemsContent: LazyStaggeredGridScope.() -> Unit
) {
    LazyVerticalStaggeredGrid(
        columns = columns,
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp
    ) {
        val errorMessage = page.errorMessage
        when {
            errorMessage != null && itemCount == 0 -> {
                item(
                    key = "zone_board_error",
                    contentType = "zone_board_placeholder",
                    span = StaggeredGridItemSpan.FullLine
                ) {
                    ZoneErrorPlaceholder(
                        message = errorMessage,
                        onRetry = { actions.onRetryInitial() }
                    )
                }
            }
            itemCount == 0 -> {
                item(
                    key = "zone_board_empty",
                    contentType = "zone_board_placeholder",
                    span = StaggeredGridItemSpan.FullLine
                ) {
                    ZoneEmptyPlaceholder(
                        text = emptyText,
                        onRefresh = { actions.onRetryInitial() }
                    )
                }
            }
            else -> {
                header?.invoke(this)
                itemsContent()
                item(
                    key = "zone_board_footer",
                    contentType = "zone_board_footer",
                    span = StaggeredGridItemSpan.FullLine
                ) {
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
