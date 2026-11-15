package com.nobody.repoi.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.ui.album.components.AlbumFeedCard
import com.nobody.repoi.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.repoi.ui.board.components.ZoneEmptyPlaceholder
import com.nobody.repoi.ui.board.components.ZoneErrorPlaceholder
import com.nobody.repoi.ui.board.components.ZonePaginationFooter
import com.nobody.repoi.ui.home.components.ActionCard
import com.nobody.repoi.ui.zone.components.ZonePinnedPostsBar
import com.nobody.repoi.ui.zone.components.ZonePostCard
import com.nobody.repoi.ui.zone.components.ZoneResaleCard

/**
 * 板块共享面板：首页与专区详情共用的「唯一内容渲染入口」。
 *
 * 已实现 [ZoneBoard.Action]（动态）、[ZoneBoard.Album]（分享/相册，#157）、[ZoneBoard.Posts]
 * （讨论，#158）与 [ZoneBoard.Resale]（二手，#160）四个分支；其余板块先走防御占位——
 * 专区详情其余板块本阶段仍走 `ZoneDetailScreen` 原内联分支，不经过本面板。
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
 *    `contentType = "zone_resale"`），不得在 pane 内去重或重算游标，`hasMore` 由宿主投影进
 *    [ZoneBoardPageView]。
 * 5. 内部用 `when(board)` 分派而不是泛型化，保留各板块类型安全；迁移新板块时在此加分支并在
 *    [ZoneBoardContent] / [ZoneBoardFilters] / [ZoneBoardActions] 补对应字段与方法。
 *
 * @param board 当前板块
 * @param scope 当前查询作用域（首页 All/Follow/Single；详情固定 Single），面板不自行拼参
 * @param content 当前板块内容快照
 * @param filters 当前板块筛选值快照（动态消费 `subType`、分享消费 `albumOrder` / `albumCategory`、
 *   讨论消费 `postsOrder`、二手消费 `resaleType` / `resalePurity`）
 * @param gridState 宿主持有的瀑布流滚动状态
 * @param contentPadding 宿主注入的内容边距
 * @param actions 筛选 / 分页动作
 * @param itemActions 条目点击 / 图片浏览动作
 * @param columns 宿主算好的列数策略
 * @param showPinnedPosts 讨论板块是否渲染置顶帖条：单专区 true、聚合态（全部）必须 false
 *   （置顶是单专区语义；关注聚合已由 `homeBoardsFor` 隐藏整个讨论板块）
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
        ZoneBoard.Posts -> {
            val postsContent = content as? ZoneBoardContent.Posts
            if (postsContent == null) {
                // 防御：板块与内容类型不匹配时不崩溃，保持与原空态一致的视觉
                ZoneEmptyPlaceholder("暂无相关讨论")
            } else {
                val onPostClick: (PostItem) -> Unit = itemActions.onPostClick ?: {}
                val onPostImageClick: (List<String>, Int) -> Unit = itemActions.onPostImageClick ?: { _, _ -> }
                LazyVerticalStaggeredGrid(
                    columns = columns,
                    state = gridState,
                    modifier = modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp
                ) {
                    val page = postsContent.page
                    val pinnedPosts = postsContent.pinnedPosts
                    val errorMessage = page.errorMessage
                    when {
                        errorMessage != null && postsContent.items.isEmpty() && pinnedPosts.isEmpty() -> {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneErrorPlaceholder(
                                    message = errorMessage,
                                    onRetry = { actions.onRetryInitial() }
                                )
                            }
                        }
                        postsContent.items.isEmpty() && pinnedPosts.isEmpty() -> {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneEmptyPlaceholder("暂无相关讨论")
                            }
                        }
                        else -> {
                            // 置顶帖条只在单专区渲染：聚合态（全部）无置顶语义，关注态已隐藏整个板块
                            if (showPinnedPosts && pinnedPosts.isNotEmpty()) {
                                item(key = "zone_pinned_posts", span = StaggeredGridItemSpan.FullLine) {
                                    ZonePinnedPostsBar(
                                        pinnedPosts = pinnedPosts,
                                        onPostClick = onPostClick
                                    )
                                }
                            }

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
        ZoneBoard.Resale -> {
            val resaleContent = content as? ZoneBoardContent.Resale
            if (resaleContent == null) {
                // 防御：板块与内容类型不匹配时不崩溃，保持与原空态一致的视觉
                ZoneEmptyPlaceholder("暂无相关二手条目")
            } else {
                LazyVerticalStaggeredGrid(
                    columns = columns,
                    state = gridState,
                    modifier = modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp
                ) {
                    val page = resaleContent.page
                    val errorMessage = page.errorMessage
                    when {
                        errorMessage != null && resaleContent.items.isEmpty() -> {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneErrorPlaceholder(
                                    message = errorMessage,
                                    onRetry = { actions.onRetryInitial() }
                                )
                            }
                        }
                        resaleContent.items.isEmpty() -> {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneEmptyPlaceholder("暂无相关二手条目")
                            }
                        }
                        else -> {
                            items(
                                items = resaleContent.items,
                                key = { item -> "zone_resale_${item.id}" },
                                contentType = { "zone_resale" }
                            ) { item ->
                                ZoneResaleCard(
                                    item = item,
                                    onClick = { itemActions.onResaleClick?.invoke(item) },
                                    onUserClick = itemActions.onResaleUserClick,
                                    onContactClick = { itemActions.onResaleContactClick?.invoke(item) },
                                    onMoreClick = { itemActions.onResaleMoreClick?.invoke(item) }
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
        else -> {
            // 其余板块尚未迁移到共享面板：仅一行防御占位（详情本阶段仍走原内联分支）
            ZoneEmptyPlaceholder("该分类正在建设中...")
        }
    }
}
