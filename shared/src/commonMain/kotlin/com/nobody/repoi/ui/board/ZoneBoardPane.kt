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
import com.nobody.repoi.ui.board.session.ZoneBoardIntent
import com.nobody.repoi.ui.home.components.ActionCard
import com.nobody.repoi.ui.item.list.components.CategoryPickerSheet
import com.nobody.repoi.ui.item.list.components.HobbyFilterAdaptiveSheet
import com.nobody.repoi.ui.item.list.components.HobbyGridCard
import com.nobody.repoi.ui.item.list.components.HobbyListCard
import com.nobody.repoi.ui.zone.components.ZoneEntityCard
import com.nobody.repoi.ui.zone.components.ZonePinnedPostsBar
import com.nobody.repoi.ui.zone.components.ZonePostCard
import com.nobody.repoi.ui.zone.components.ZoneResaleCard

@Composable
fun ZoneBoardPane(
    board: ZoneBoard,
    content: ZoneBoardContent,
    gridState: LazyStaggeredGridState,
    contentPadding: PaddingValues,
    onIntent: (ZoneBoardIntent) -> Unit,
    itemActions: ZoneBoardItemActions,
    columns: StaggeredGridCells,
    showPinnedPosts: Boolean = true,
    modifier: Modifier = Modifier
) {
    when (board) {
        ZoneBoard.Action -> {
            val actionContent = content as? ZoneBoardContent.Action
            if (actionContent == null) {

                ZoneEmptyPlaceholder(board.emptyText)
            } else {
                BoardGridContainer(
                    itemCount = actionContent.items.size,
                    page = actionContent.page,
                    emptyText = board.emptyText,
                    columns = columns,
                    gridState = gridState,
                    contentPadding = contentPadding,
                    modifier = modifier,
                    onIntent = onIntent
                ) {
                    items(
                        items = actionContent.items,
                        key = { item -> "zone_feed_${item.id}_${item.actionDate}" },
                        contentType = { "feed_action" }
                    ) { item ->
                        ActionCard(
                            actionItem = item,
                            onActionClick = itemActions.onActionClick,
                            onImageClick = itemActions.onActionImageClick,
                            onUrlClick = itemActions.onUrlClick
                        )
                    }
                }
            }
        }
        ZoneBoard.Album -> {
            val albumContent = content as? ZoneBoardContent.Album
            if (albumContent == null) {

                ZoneEmptyPlaceholder(board.emptyText)
            } else {

                val albumColumns = rememberAlbumStaggeredGridColumns()
                BoardGridContainer(
                    itemCount = albumContent.items.size,
                    page = albumContent.page,
                    emptyText = board.emptyText,
                    columns = albumColumns,
                    gridState = gridState,
                    contentPadding = contentPadding,
                    modifier = modifier,
                    onIntent = onIntent
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

                ZoneEmptyPlaceholder(board.emptyText)
            } else {
                val onPostClick: (PostItem) -> Unit = itemActions.onPostClick ?: {}
                val onPostImageClick: (List<String>, Int) -> Unit = itemActions.onPostImageClick ?: { _, _ -> }

                val visiblePinnedCount =
                    if (showPinnedPosts) postsContent.pinnedPosts.size else 0
                BoardGridContainer(
                    itemCount = postsContent.items.size + visiblePinnedCount,
                    page = postsContent.page,
                    emptyText = board.emptyText,
                    columns = columns,
                    gridState = gridState,
                    contentPadding = contentPadding,
                    modifier = modifier,
                    onIntent = onIntent,
                    header = {

                        if (showPinnedPosts && postsContent.pinnedPosts.isNotEmpty()) {
                            item(
                                key = "zone_pinned_posts",
                                contentType = "zone_board_pinned",
                                span = StaggeredGridItemSpan.FullLine
                            ) {
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

                ZoneEmptyPlaceholder(board.emptyText)
            } else {
                BoardGridContainer(
                    itemCount = resaleContent.items.size,
                    page = resaleContent.page,
                    emptyText = board.emptyText,
                    columns = columns,
                    gridState = gridState,
                    contentPadding = contentPadding,
                    modifier = modifier,
                    onIntent = onIntent
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

                ZoneEmptyPlaceholder(board.emptyText)
            } else {

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
                    emptyText = board.emptyText,
                    columns = hobbyColumns,
                    gridState = gridState,
                    contentPadding = contentPadding,
                    modifier = modifier,
                    onIntent = onIntent
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

                ZoneEmptyPlaceholder(board.emptyText)
            } else {
                BoardGridContainer(
                    itemCount = entityContent.items.size,
                    page = entityContent.page,
                    emptyText = board.emptyText,
                    columns = columns,
                    gridState = gridState,
                    contentPadding = contentPadding,
                    modifier = modifier,
                    onIntent = onIntent
                ) {

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

@Composable
fun ZoneBoardHobbySheets(
    filters: ZoneBoardFilters,
    hobbyCategoryTree: Map<Int, List<CategoryItem>>,
    onIntent: (ZoneBoardIntent) -> Unit
) {
    if (filters.showHobbyFilterSheet) {
        HobbyFilterAdaptiveSheet(
            filterParam = filters.hobby.toAdvancedFilterParam(),
            onDismiss = { onIntent(ZoneBoardIntent.SetHobbyFilterSheetVisible(false)) },
            onApply = { filterParam ->
                onIntent(ZoneBoardIntent.ApplyHobbyFilter(filterParam))
                onIntent(ZoneBoardIntent.SetHobbyFilterSheetVisible(false))
            },
            onReset = {
                onIntent(ZoneBoardIntent.ResetHobbyFilter)
                onIntent(ZoneBoardIntent.SetHobbyFilterSheetVisible(false))
            }
        )
    }
    if (filters.showHobbyCategoryPickerSheet) {
        CategoryPickerSheet(
            selectedCategoryId = filters.hobby.categoryId,
            selectedSubCategoryId = filters.hobby.subCategoryId,
            getSubCategories = { parentId -> hobbyCategoryTree[parentId].orEmpty() },
            onCategorySelected = { categoryId, subCategoryId ->
                onIntent(ZoneBoardIntent.SelectHobbyCategory(categoryId, subCategoryId))
            },
            onDismiss = { onIntent(ZoneBoardIntent.SetHobbyCategoryPickerVisible(false)) }
        )
    }
}

@Composable
private fun BoardGridContainer(
    itemCount: Int,
    page: ZoneBoardPageView,
    emptyText: String,
    columns: StaggeredGridCells,
    gridState: LazyStaggeredGridState,
    contentPadding: PaddingValues,
    modifier: Modifier,
    onIntent: (ZoneBoardIntent) -> Unit,
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
                        onRetry = { onIntent(ZoneBoardIntent.RetryInitial()) }
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
                        onRefresh = { onIntent(ZoneBoardIntent.RetryInitial()) }
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
                        onRetry = { onIntent(ZoneBoardIntent.LoadMore()) }
                    )
                }
            }
        }
    }
}
