package com.nobody.repoi.ui.home

import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.board.ZoneBoardFilters
import com.nobody.repoi.ui.board.ZoneBoardPageView

/**
 * 首页板块态投影（宿主读取 UI 状态的唯一派生入口）。
 *
 * 从 `HomeFeedUiState` 数据类本体拆出：状态类只负责「状态形状」，本文件只负责「按当前板块投影」。
 * 两屏渲染口径仍统一由 [ZoneBoardContent] / [ZoneBoardFilters] 约束；新增板块时只改这里的 `when`，
 * 不再让状态类因渲染需求被动扩散（#150 审查 Divergent Change）。
 */

/** 查询作用域（由 [HomeFeedUiState.selectedCategory] 派生，避免两处状态漂移）。 */
val HomeFeedUiState.zoneScope: ZoneQueryScope
    get() = selectedCategory.toZoneQueryScope()

/** 二级 Tab 文案：单专区优先服务端 `tabTitle`，空则回退枚举文案；聚合态无服务端 title。 */
fun HomeFeedUiState.boardTitle(board: ZoneBoard): String =
    boardTitles[board]?.takeIf { it.isNotBlank() } ?: board.label

/** 当前二级板块是否为讨论板块。 */
val HomeFeedUiState.isPostsBoard: Boolean
    get() = selectedBoard == ZoneBoard.Posts

/**
 * 讨论板块 → 共享面板内容快照（宿主投影，pane 只读；请求/去重全部在 ViewModel 完成）。
 *
 * 置顶帖只在单专区投影（聚合态无置顶语义），与 `ZoneBoardPane(showPinnedPosts = ...)` 对齐。
 */
val HomeFeedUiState.postsBoardContent: ZoneBoardContent.Posts
    get() = ZoneBoardContent.Posts(
        pinnedPosts = posts.pinnedPosts,
        items = posts.items,
        page = ZoneBoardPageView(
            isLoading = posts.isInitialLoading,
            isLoadingMore = posts.isLoadingMore,
            errorMessage = posts.errorMessage,
            loadMoreError = posts.loadMoreError,
            isEndReached = !posts.hasMore
        )
    )

/** 当前板块条目数（内容区四态 / 触底翻页 / 折叠可滚动性共用，避免宿主散写 `when`）。 */
val HomeFeedUiState.currentBoardItemCount: Int
    get() = when (selectedBoard) {
        ZoneBoard.Album -> albumItems.size
        ZoneBoard.Posts -> posts.items.size + posts.pinnedPosts.size
        ZoneBoard.Resale -> resale.items.size
        ZoneBoard.Hobby -> hobbyBoard.itemCount
        else -> if (selectedBoard.isEntity) currentEntityBoardState.items.size else items.size
    }

/** 实体板块的只读状态快照（非实体板块取默认值，调用方仅在实体板块消费）。 */
private val HomeFeedUiState.currentEntityBoardState: EntityBoardState
    get() = entityBoards[selectedBoard] ?: EntityBoardState()

/** 当前板块是否还有下一页。 */
val HomeFeedUiState.currentBoardHasMore: Boolean
    get() = when (selectedBoard) {
        ZoneBoard.Album -> albumHasMore
        ZoneBoard.Posts -> posts.hasMore
        ZoneBoard.Resale -> resale.hasMore
        ZoneBoard.Hobby -> hobbyBoard.hasMore
        else -> if (selectedBoard.isEntity) !currentEntityBoardState.isEndReached else hasMore
    }

/** 当前板块是否正在追加下一页。 */
val HomeFeedUiState.currentBoardIsLoadingMore: Boolean
    get() = when (selectedBoard) {
        ZoneBoard.Album -> isLoadingMoreAlbum
        ZoneBoard.Posts -> posts.isLoadingMore
        ZoneBoard.Resale -> resale.isLoadingMore
        ZoneBoard.Hobby -> hobbyBoard.isLoadingMore
        else -> if (selectedBoard.isEntity) currentEntityBoardState.isLoadingMore else isLoadingMore
    }

/** 当前板块追加失败信息（页脚重试用）。 */
val HomeFeedUiState.currentBoardLoadMoreError: String?
    get() = when (selectedBoard) {
        ZoneBoard.Album -> albumLoadMoreError
        ZoneBoard.Posts -> posts.loadMoreError
        ZoneBoard.Resale -> resale.loadMoreError
        ZoneBoard.Hobby -> hobbyBoard.loadMoreError
        else -> if (selectedBoard.isEntity) currentEntityBoardState.loadMoreError else loadMoreError
    }

/** 当前板块是否正在下拉刷新（产品刷新态由板块状态承载，其余板块共用全局 [HomeFeedUiState.isRefreshing]）。 */
val HomeFeedUiState.currentBoardIsRefreshing: Boolean
    get() = when (selectedBoard) {
        ZoneBoard.Hobby -> hobbyBoard.isRefreshing
        else -> isRefreshing
    }

/** 当前板块首屏加载中（宿主统一用 `PoiLoadingView` 展示）。 */
val HomeFeedUiState.isCurrentBoardLoading: Boolean
    get() = when (selectedBoard) {
        ZoneBoard.Album -> isAlbumLoading && albumItems.isEmpty()
        // 讨论板块懒加载：尚未请求过首屏（且无错误）时也按加载态处理，避免闪一帧空态卡片
        ZoneBoard.Posts -> (posts.isInitialLoading || !posts.isLoaded) && posts.errorMessage == null
        // 二手板块同样按懒加载处理，避免切到二手瞬间闪一帧空态卡片
        ZoneBoard.Resale -> (resale.isLoading || !resale.isLoaded) && resale.errorMessage == null
        ZoneBoard.Hobby -> hobbyBoard.isLoading && hobbyBoard.items.isEmpty()
        else -> if (selectedBoard.isEntity) {
            currentEntityBoardState.isLoading && currentEntityBoardState.items.isEmpty()
        } else {
            isInitialLoading
        }
    }

/** 当前板块首屏错误（仅在列表为空时展示错误态）。 */
val HomeFeedUiState.currentBoardErrorMessage: String?
    get() = when (selectedBoard) {
        ZoneBoard.Album -> albumErrorMessage
        ZoneBoard.Posts -> posts.errorMessage
        ZoneBoard.Resale -> resale.errorMessage
        ZoneBoard.Hobby -> hobbyBoard.errorMessage
        else -> if (selectedBoard.isEntity) currentEntityBoardState.errorMessage else errorMessage
    }

/** 当前板块内容快照（传给共享面板 `ZoneBoardPane`，保证两屏同一渲染口径）。 */
val HomeFeedUiState.currentBoardContent: ZoneBoardContent
    get() = when (selectedBoard) {
        ZoneBoard.Album -> ZoneBoardContent.Album(
            items = albumItems,
            page = ZoneBoardPageView(
                isLoading = isAlbumLoading,
                isLoadingMore = isLoadingMoreAlbum,
                errorMessage = albumErrorMessage,
                loadMoreError = albumLoadMoreError,
                isEndReached = !albumHasMore
            )
        )
        ZoneBoard.Posts -> postsBoardContent
        ZoneBoard.Resale -> ZoneBoardContent.Resale(
            items = resale.items,
            page = ZoneBoardPageView(
                isLoading = resale.isLoading,
                isLoadingMore = resale.isLoadingMore,
                errorMessage = resale.errorMessage,
                loadMoreError = resale.loadMoreError,
                isEndReached = !resale.hasMore
            )
        )
        ZoneBoard.Hobby -> hobbyBoard.content
        else -> if (selectedBoard.isEntity) {
            val entityState = currentEntityBoardState
            ZoneBoardContent.Entity(
                items = entityState.items,
                page = ZoneBoardPageView(
                    isLoading = entityState.isLoading,
                    isLoadingMore = entityState.isLoadingMore,
                    errorMessage = entityState.errorMessage,
                    loadMoreError = entityState.loadMoreError,
                    isEndReached = entityState.isEndReached
                )
            )
        } else {
            ZoneBoardContent.Action(
                items = items,
                page = ZoneBoardPageView(
                    isLoading = isInitialLoading,
                    isLoadingMore = isLoadingMore,
                    errorMessage = errorMessage,
                    loadMoreError = loadMoreError,
                    isEndReached = !hasMore
                )
            )
        }
    }

/** 当前板块筛选值快照（传给共享面板/筛选条）。实体 5 类暂无筛选项，保持默认值。 */
val HomeFeedUiState.currentBoardFilters: ZoneBoardFilters
    get() = when (selectedBoard) {
        ZoneBoard.Album -> ZoneBoardFilters(
            albumOrder = albumSelectedOrder,
            albumCategory = albumSelectedCategory
        )
        ZoneBoard.Posts -> ZoneBoardFilters(subType = selectedSubType, postsOrder = posts.order)
        ZoneBoard.Resale -> ZoneBoardFilters(
            resaleType = resale.type,
            resalePurity = resale.purity,
            resaleKeyword = resale.keyword
        )
        ZoneBoard.Hobby -> hobbyBoard.filters
        else -> if (selectedBoard.isEntity) ZoneBoardFilters() else ZoneBoardFilters(subType = selectedSubType)
    }
