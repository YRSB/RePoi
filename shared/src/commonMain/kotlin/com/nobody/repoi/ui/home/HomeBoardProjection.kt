package com.nobody.repoi.ui.home

import com.nobody.repoi.data.remote.model.ActionSubType
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.ui.board.HobbyBoardOptions
import com.nobody.repoi.ui.board.PostsBoardOptions
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.board.ZoneBoardFilters
import com.nobody.repoi.ui.board.ZoneBoardPageView
import com.nobody.repoi.ui.board.session.ZoneBoardSnapshot

/**
 * 首页板块态投影（过渡层，最终物理删除由 #169 负责）。
 */

/** 任意板块的全部 UI 投影字段。 */
internal data class HomeBoardView(
    val content: ZoneBoardContent,
    val filters: ZoneBoardFilters,
    val itemCount: Int,
    val hasMore: Boolean,
    val isLoadingMore: Boolean,
    val loadMoreError: String?,
    val isRefreshing: Boolean,
    val isLoading: Boolean,
    val errorMessage: String?
)

/** 将 [ZoneBoardSnapshot] 转换为 [HomeBoardView]（过渡适配）。 */
internal fun ZoneBoardSnapshot.toBoardView(): HomeBoardView = HomeBoardView(
    content = content,
    filters = filters,
    itemCount = content.itemCount,
    hasMore = !pageView.isEndReached,
    isLoadingMore = pageView.isLoadingMore,
    loadMoreError = pageView.loadMoreError,
    isRefreshing = content.itemCount > 0 && pageView.isLoading,
    isLoading = pageView.isLoading,
    errorMessage = pageView.errorMessage
)

/** 查询作用域（由 [HomeFeedUiState.selectedCategory] 派生）。 */
val HomeFeedUiState.zoneScope: ZoneQueryScope
    get() = selectedCategory.toZoneQueryScope()

/** 二级 Tab 文案：单专区优先服务端 `tabTitle`，空则回退枚举文案；聚合态无服务端 title。 */
fun HomeFeedUiState.boardTitle(board: ZoneBoard): String =
    boardTitles[board]?.takeIf { it.isNotBlank() } ?: board.label

/** 当前二级板块是否为讨论板块。 */
val HomeFeedUiState.isPostsBoard: Boolean
    get() = selectedBoard == ZoneBoard.Posts

/** 讨论板块内容快照（兼容）。 */
val HomeFeedUiState.postsBoardContent: ZoneBoardContent.Posts
    get() = (currentBoardSnapshot.content as? ZoneBoardContent.Posts)
        ?: ZoneBoardContent.Posts(emptyList(), emptyList(), ZoneBoardPageView())

/** 当前板块的投影快照。 */
internal val HomeFeedUiState.currentBoardView: HomeBoardView
    get() = currentBoardSnapshot.toBoardView()

/** 任意板块的投影快照（兼容）。 */
internal fun HomeFeedUiState.boardView(board: ZoneBoard): HomeBoardView =
    currentBoardSnapshot.toBoardView()

/** 当前板块内容快照。 */
val HomeFeedUiState.currentBoardContent: ZoneBoardContent
    get() = currentBoardSnapshot.content

/** 当前板块筛选值快照。 */
val HomeFeedUiState.currentBoardFilters: ZoneBoardFilters
    get() = currentBoardSnapshot.filters

/** 当前板块条目数。 */
val HomeFeedUiState.currentBoardItemCount: Int
    get() = currentBoardSnapshot.content.itemCount

/** 当前板块是否还有下一页。 */
val HomeFeedUiState.currentBoardHasMore: Boolean
    get() = !currentBoardSnapshot.pageView.isEndReached

/** 当前板块是否正在追加下一页。 */
val HomeFeedUiState.currentBoardIsLoadingMore: Boolean
    get() = currentBoardSnapshot.pageView.isLoadingMore

/** 当前板块追加失败信息。 */
val HomeFeedUiState.currentBoardLoadMoreError: String?
    get() = currentBoardSnapshot.pageView.loadMoreError

/** 当前板块是否正在刷新。 */
val HomeFeedUiState.currentBoardIsRefreshing: Boolean
    get() = currentBoardSnapshot.content.itemCount > 0 && currentBoardSnapshot.pageView.isLoading

/** 当前板块首屏加载中。 */
val HomeFeedUiState.isCurrentBoardLoading: Boolean
    get() = currentBoardSnapshot.pageView.isLoading

/** 当前板块首屏错误。 */
val HomeFeedUiState.currentBoardErrorMessage: String?
    get() = currentBoardSnapshot.pageView.errorMessage

/** 板块筛选值快照 token 化。 */
internal fun HomeFeedUiState.boardFilterToken(board: ZoneBoard): String =
    boardFilterToken(currentBoardSnapshot.filters, board)

/** 板块筛选值 token 化。 */
internal fun boardFilterToken(filters: ZoneBoardFilters, board: ZoneBoard): String = when (board) {
    ZoneBoard.Album -> "${filters.albumOrder}|${filters.albumCategory ?: "all"}"
    ZoneBoard.Posts -> filters.postsOrder ?: PostsBoardOptions.DEFAULT_ORDER
    ZoneBoard.Resale -> "${filters.resaleType}|${filters.resalePurity ?: "all"}"
    ZoneBoard.Hobby -> with(filters) {
        listOf(
            hobbyOrder ?: HobbyBoardOptions.DEFAULT_ORDER,
            hobbyCategoryId ?: 0,
            hobbySubCategoryId ?: "all",
            hobbyYear ?: "all",
            hobbyMonth ?: "all",
            hobbyKeyword.orEmpty(),
            hobbySex ?: "all",
            hobbySpecs ?: "all",
            hobbyScale ?: "all",
            hobbyR18 ?: "all"
        ).joinToString("|")
    }
    else -> filters.subType ?: ActionSubType.DEFAULT.apiValue
}

/** ZoneBoardContent 条目数量计算扩展。 */
val ZoneBoardContent.itemCount: Int
    get() = when (this) {
        is ZoneBoardContent.Action -> items.size
        is ZoneBoardContent.Album -> items.size
        is ZoneBoardContent.Posts -> items.size + pinnedPosts.size
        is ZoneBoardContent.Hobby -> items.size
        is ZoneBoardContent.Resale -> items.size
        is ZoneBoardContent.Entity -> items.size
    }

/** ZoneBoardSnapshot 条目数量扩展。 */
val ZoneBoardSnapshot.itemCount: Int
    get() = content.itemCount
