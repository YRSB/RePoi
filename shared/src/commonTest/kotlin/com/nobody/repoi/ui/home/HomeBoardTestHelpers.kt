package com.nobody.repoi.ui.home

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.ActionSubType
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.data.remote.model.ResaleSearchItem
import com.nobody.repoi.data.remote.model.ZoneEntityItem
import com.nobody.repoi.data.remote.model.ZoneHobbyFilter
import com.nobody.repoi.platform.DateTimeFormats
import com.nobody.repoi.ui.board.AlbumBoardOptions
import com.nobody.repoi.ui.board.HobbyBoardOptions
import com.nobody.repoi.ui.board.PostsBoardOptions
import com.nobody.repoi.ui.board.ResaleBoardOptions
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.board.ZoneBoardFilters
import com.nobody.repoi.ui.board.ZoneBoardPageView
import com.nobody.repoi.ui.board.itemCount

/**
 * 测试环境专用适配层：将 [HomeFeedUiState.currentBoardSnapshot] 桥接为存量单测断言格式。
 *
 * 生产代码已完全切至深模块 [com.nobody.repoi.ui.board.session.ZoneBoardSession]，
 * 本文件仅位于 commonTest，避免存量断言样板代码散落或污染 commonMain。
 */

val HomeFeedUiState.items: List<ActionItem>
    get() = (currentBoardSnapshot.content as? ZoneBoardContent.Action)?.items.orEmpty()

val HomeFeedUiState.lastTime: Long
    get() = (currentBoardSnapshot.content as? ZoneBoardContent.Action)?.items?.lastOrNull()?.actionDate?.let {
        DateTimeFormats.parseServerDateTime(it)
    } ?: 0L

val HomeFeedUiState.isInitialLoading: Boolean
    get() = currentBoardSnapshot.pageView.isLoading && currentBoardSnapshot.content.itemCount == 0

val HomeFeedUiState.isRefreshing: Boolean
    get() = currentBoardSnapshot.pageView.isLoading && currentBoardSnapshot.content.itemCount > 0

val HomeFeedUiState.isLoadingMore: Boolean
    get() = currentBoardSnapshot.pageView.isLoadingMore

val HomeFeedUiState.hasMore: Boolean
    get() = !currentBoardSnapshot.pageView.isEndReached

val HomeFeedUiState.errorMessage: String?
    get() = currentBoardSnapshot.pageView.errorMessage

val HomeFeedUiState.loadMoreError: String?
    get() = currentBoardSnapshot.pageView.loadMoreError

val HomeFeedUiState.selectedSubType: String
    get() = currentBoardSnapshot.filters.subType

val HomeFeedUiState.albumItems: List<AlbumListItem>
    get() = (currentBoardSnapshot.content as? ZoneBoardContent.Album)?.items.orEmpty()

val HomeFeedUiState.albumPage: Int
    get() = if (currentBoardSnapshot.pageView.isEndReached) 2 else 1

val HomeFeedUiState.isAlbumLoading: Boolean
    get() = currentBoardSnapshot.board == ZoneBoard.Album && currentBoardSnapshot.pageView.isLoading

val HomeFeedUiState.isLoadingMoreAlbum: Boolean
    get() = currentBoardSnapshot.board == ZoneBoard.Album && currentBoardSnapshot.pageView.isLoadingMore

val HomeFeedUiState.albumHasMore: Boolean
    get() = currentBoardSnapshot.board != ZoneBoard.Album || !currentBoardSnapshot.pageView.isEndReached

val HomeFeedUiState.albumErrorMessage: String?
    get() = if (currentBoardSnapshot.board == ZoneBoard.Album) currentBoardSnapshot.pageView.errorMessage else null

val HomeFeedUiState.albumLoadMoreError: String?
    get() = if (currentBoardSnapshot.board == ZoneBoard.Album) currentBoardSnapshot.pageView.loadMoreError else null

val HomeFeedUiState.albumSelectedOrder: String
    get() = currentBoardSnapshot.filters.albumOrder

val HomeFeedUiState.albumSelectedCategory: Int?
    get() = currentBoardSnapshot.filters.albumCategory

val HomeFeedUiState.isAlbumLoaded: Boolean
    get() = currentBoardSnapshot.board == ZoneBoard.Album && (!currentBoardSnapshot.pageView.isLoading || currentBoardSnapshot.content.itemCount > 0)

data class PostsTestState(
    val order: String = PostsBoardOptions.DEFAULT_ORDER,
    val items: List<PostItem> = emptyList(),
    val pinnedPosts: List<PostItem> = emptyList(),
    val page: Int = 1,
    val isLoaded: Boolean = false,
    val isInitialLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null,
    val loadMoreError: String? = null
)

val HomeFeedUiState.posts: PostsTestState
    get() {
        val postsContent = currentBoardSnapshot.content as? ZoneBoardContent.Posts
        return PostsTestState(
            order = currentBoardSnapshot.filters.postsOrder,
            items = postsContent?.items.orEmpty(),
            pinnedPosts = postsContent?.pinnedPosts.orEmpty(),
            page = 1,
            isLoaded = currentBoardSnapshot.board == ZoneBoard.Posts && (!currentBoardSnapshot.pageView.isLoading || currentBoardSnapshot.content.itemCount > 0),
            isInitialLoading = currentBoardSnapshot.board == ZoneBoard.Posts && currentBoardSnapshot.pageView.isLoading && currentBoardSnapshot.content.itemCount == 0,
            isLoadingMore = currentBoardSnapshot.board == ZoneBoard.Posts && currentBoardSnapshot.pageView.isLoadingMore,
            hasMore = currentBoardSnapshot.board != ZoneBoard.Posts || !currentBoardSnapshot.pageView.isEndReached,
            errorMessage = if (currentBoardSnapshot.board == ZoneBoard.Posts) currentBoardSnapshot.pageView.errorMessage else null,
            loadMoreError = if (currentBoardSnapshot.board == ZoneBoard.Posts) currentBoardSnapshot.pageView.loadMoreError else null
        )
    }

val HomeFeedUiState.postsBoardContent: ZoneBoardContent.Posts
    get() = (currentBoardSnapshot.content as? ZoneBoardContent.Posts)
        ?: ZoneBoardContent.Posts(emptyList(), emptyList(), ZoneBoardPageView())

data class ResaleTestState(
    val type: Int = ResaleBoardOptions.DEFAULT_TYPE,
    val purity: String? = null,
    val keyword: String = "",
    val items: List<ResaleSearchItem> = emptyList(),
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreError: String? = null,
    val isLoaded: Boolean = false
)

val HomeFeedUiState.resale: ResaleTestState
    get() {
        val resaleContent = currentBoardSnapshot.content as? ZoneBoardContent.Resale
        return ResaleTestState(
            type = currentBoardSnapshot.filters.resaleType,
            purity = currentBoardSnapshot.filters.resalePurity,
            keyword = currentBoardSnapshot.filters.resaleKeyword,
            items = resaleContent?.items.orEmpty(),
            hasMore = currentBoardSnapshot.board != ZoneBoard.Resale || !currentBoardSnapshot.pageView.isEndReached,
            isLoading = currentBoardSnapshot.board == ZoneBoard.Resale && currentBoardSnapshot.pageView.isLoading,
            isLoadingMore = currentBoardSnapshot.board == ZoneBoard.Resale && currentBoardSnapshot.pageView.isLoadingMore,
            errorMessage = if (currentBoardSnapshot.board == ZoneBoard.Resale) currentBoardSnapshot.pageView.errorMessage else null,
            loadMoreError = if (currentBoardSnapshot.board == ZoneBoard.Resale) currentBoardSnapshot.pageView.loadMoreError else null,
            isLoaded = currentBoardSnapshot.board == ZoneBoard.Resale && (!currentBoardSnapshot.pageView.isLoading || currentBoardSnapshot.content.itemCount > 0)
        )
    }

data class EntityBoardTestState(
    val items: List<ZoneEntityItem> = emptyList(),
    val isEndReached: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreError: String? = null
)

val HomeFeedUiState.entityBoards: Map<ZoneBoard, EntityBoardTestState>
    get() {
        val entityContent = currentBoardSnapshot.content as? ZoneBoardContent.Entity
        return if (currentBoardSnapshot.board.isEntity && entityContent != null) {
            mapOf(
                currentBoardSnapshot.board to EntityBoardTestState(
                    items = entityContent.items,
                    isEndReached = currentBoardSnapshot.pageView.isEndReached,
                    isLoading = currentBoardSnapshot.pageView.isLoading,
                    isLoadingMore = currentBoardSnapshot.pageView.isLoadingMore,
                    errorMessage = currentBoardSnapshot.pageView.errorMessage,
                    loadMoreError = currentBoardSnapshot.pageView.loadMoreError
                )
            )
        } else {
            emptyMap()
        }
    }

data class HobbyBoardTestState(
    val filter: ZoneHobbyFilter = ZoneHobbyFilter(),
    val isGridMode: Boolean = true,
    val items: List<HobbyItemSummary> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoaded: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreError: String? = null,
    val showFilterSheet: Boolean = false,
    val showCategoryPickerSheet: Boolean = false
) {
    val hasAdvancedFilters: Boolean get() = filter.hasAdvancedFilters
}

val HomeFeedUiState.hobbyBoard: HobbyBoardTestState
    get() {
        val hobbyContent = currentBoardSnapshot.content as? ZoneBoardContent.Hobby
        return HobbyBoardTestState(
            filter = currentBoardSnapshot.filters.hobby,
            isGridMode = hobbyContent?.isGridMode ?: true,
            items = hobbyContent?.items.orEmpty(),
            page = 1,
            hasMore = currentBoardSnapshot.board == ZoneBoard.Hobby && !currentBoardSnapshot.pageView.isEndReached,
            isLoading = currentBoardSnapshot.board == ZoneBoard.Hobby && currentBoardSnapshot.pageView.isLoading,
            isLoadingMore = currentBoardSnapshot.board == ZoneBoard.Hobby && currentBoardSnapshot.pageView.isLoadingMore,
            isLoaded = currentBoardSnapshot.board == ZoneBoard.Hobby && (!currentBoardSnapshot.pageView.isLoading || currentBoardSnapshot.content.itemCount > 0),
            errorMessage = if (currentBoardSnapshot.board == ZoneBoard.Hobby) currentBoardSnapshot.pageView.errorMessage else null,
            loadMoreError = if (currentBoardSnapshot.board == ZoneBoard.Hobby) currentBoardSnapshot.pageView.loadMoreError else null,
            showFilterSheet = currentBoardSnapshot.filters.showHobbyFilterSheet,
            showCategoryPickerSheet = currentBoardSnapshot.filters.showHobbyCategoryPickerSheet
        )
    }

val HomeFeedUiState.currentBoardContent: ZoneBoardContent
    get() = currentBoardSnapshot.content

val HomeFeedUiState.currentBoardFilters: ZoneBoardFilters
    get() = currentBoardSnapshot.filters

val HomeFeedUiState.currentBoardItemCount: Int
    get() = currentBoardSnapshot.content.itemCount

val HomeFeedUiState.currentBoardHasMore: Boolean
    get() = !currentBoardSnapshot.pageView.isEndReached

val HomeFeedUiState.currentBoardIsLoadingMore: Boolean
    get() = currentBoardSnapshot.pageView.isLoadingMore

val HomeFeedUiState.currentBoardLoadMoreError: String?
    get() = currentBoardSnapshot.pageView.loadMoreError

val HomeFeedUiState.currentBoardIsRefreshing: Boolean
    get() = currentBoardSnapshot.content.itemCount > 0 && currentBoardSnapshot.pageView.isLoading

val HomeFeedUiState.isCurrentBoardLoading: Boolean
    get() = currentBoardSnapshot.pageView.isLoading

val HomeFeedUiState.currentBoardErrorMessage: String?
    get() = currentBoardSnapshot.pageView.errorMessage
