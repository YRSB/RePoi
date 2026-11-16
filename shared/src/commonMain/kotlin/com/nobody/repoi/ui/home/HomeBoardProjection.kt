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
 * 两屏渲染口径仍统一由 [ZoneBoardContent] / [ZoneBoardFilters] 约束。
 *
 * 关键约束：**所有按板块分派的字段只在 [boardView] 一处 `when`**，各公开投影 getter 只读同一次
 * 映射结果。新增板块只在 [boardView] 增加一个分支，不再出现 9 个平行 `when`（Repeated Switches）。
 */

/** 当前板块的全部 UI 投影字段（一次 `when` 计算）。 */
private data class HomeBoardView(
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
        page = pageView(
            isLoading = posts.isInitialLoading,
            isLoadingMore = posts.isLoadingMore,
            errorMessage = posts.errorMessage,
            loadMoreError = posts.loadMoreError,
            isEndReached = !posts.hasMore
        )
    )

/** 实体板块的只读状态快照（非实体板块取默认值，调用方仅在实体板块消费）。 */
private val HomeFeedUiState.currentEntityBoardState: EntityBoardState
    get() = entityBoards[selectedBoard] ?: EntityBoardState()

/**
 * 当前板块的投影快照。
 *
 * 实体 5 类共用同一套分页字段，讨论 / 分享 / 二手 / 产品各自持有独立 loader 状态，
 * 动态板块沿用全局字段；此处把差异集中到唯一 `when`，其余 getter 只做字段转发。
 */
private val HomeFeedUiState.boardView: HomeBoardView
    get() = when (selectedBoard) {
        ZoneBoard.Album -> HomeBoardView(
            content = ZoneBoardContent.Album(
                items = albumItems,
                page = pageView(
                    isLoading = isAlbumLoading,
                    isLoadingMore = isLoadingMoreAlbum,
                    errorMessage = albumErrorMessage,
                    loadMoreError = albumLoadMoreError,
                    isEndReached = !albumHasMore
                )
            ),
            filters = ZoneBoardFilters(
                albumOrder = albumSelectedOrder,
                albumCategory = albumSelectedCategory
            ),
            itemCount = albumItems.size,
            hasMore = albumHasMore,
            isLoadingMore = isLoadingMoreAlbum,
            loadMoreError = albumLoadMoreError,
            isRefreshing = isRefreshing,
            isLoading = isAlbumLoading && albumItems.isEmpty(),
            errorMessage = albumErrorMessage
        )

        ZoneBoard.Posts -> HomeBoardView(
            content = postsBoardContent,
            filters = ZoneBoardFilters(subType = selectedSubType, postsOrder = posts.order),
            itemCount = posts.items.size + posts.pinnedPosts.size,
            hasMore = posts.hasMore,
            isLoadingMore = posts.isLoadingMore,
            loadMoreError = posts.loadMoreError,
            isRefreshing = isRefreshing,
            // 懒加载：尚未请求过首屏（且无错误）时也按加载态处理，避免闪一帧空态卡片
            isLoading = (posts.isInitialLoading || !posts.isLoaded) && posts.errorMessage == null,
            errorMessage = posts.errorMessage
        )

        ZoneBoard.Resale -> HomeBoardView(
            content = ZoneBoardContent.Resale(
                items = resale.items,
                page = pageView(
                    isLoading = resale.isLoading,
                    isLoadingMore = resale.isLoadingMore,
                    errorMessage = resale.errorMessage,
                    loadMoreError = resale.loadMoreError,
                    isEndReached = !resale.hasMore
                )
            ),
            filters = ZoneBoardFilters(
                resaleType = resale.type,
                resalePurity = resale.purity,
                resaleKeyword = resale.keyword
            ),
            itemCount = resale.items.size,
            hasMore = resale.hasMore,
            isLoadingMore = resale.isLoadingMore,
            loadMoreError = resale.loadMoreError,
            isRefreshing = isRefreshing,
            // 同样按懒加载处理，避免切到二手瞬间闪一帧空态卡片
            isLoading = (resale.isLoading || !resale.isLoaded) && resale.errorMessage == null,
            errorMessage = resale.errorMessage
        )

        ZoneBoard.Hobby -> HomeBoardView(
            content = hobbyBoard.content,
            filters = hobbyBoard.filters,
            itemCount = hobbyBoard.itemCount,
            hasMore = hobbyBoard.hasMore,
            isLoadingMore = hobbyBoard.isLoadingMore,
            loadMoreError = hobbyBoard.loadMoreError,
            isRefreshing = hobbyBoard.isRefreshing,
            isLoading = hobbyBoard.isLoading && hobbyBoard.items.isEmpty(),
            errorMessage = hobbyBoard.errorMessage
        )

        else -> {
            if (selectedBoard.isEntity) {
                val entityState = currentEntityBoardState
                HomeBoardView(
                    content = ZoneBoardContent.Entity(
                        items = entityState.items,
                        page = pageView(
                            isLoading = entityState.isLoading,
                            isLoadingMore = entityState.isLoadingMore,
                            errorMessage = entityState.errorMessage,
                            loadMoreError = entityState.loadMoreError,
                            isEndReached = entityState.isEndReached
                        )
                    ),
                    filters = ZoneBoardFilters(),
                    itemCount = entityState.items.size,
                    hasMore = !entityState.isEndReached,
                    isLoadingMore = entityState.isLoadingMore,
                    loadMoreError = entityState.loadMoreError,
                    isRefreshing = isRefreshing,
                    isLoading = entityState.isLoading && entityState.items.isEmpty(),
                    errorMessage = entityState.errorMessage
                )
            } else {
                HomeBoardView(
                    content = ZoneBoardContent.Action(
                        items = items,
                        page = pageView(
                            isLoading = isInitialLoading,
                            isLoadingMore = isLoadingMore,
                            errorMessage = errorMessage,
                            loadMoreError = loadMoreError,
                            isEndReached = !hasMore
                        )
                    ),
                    filters = ZoneBoardFilters(subType = selectedSubType),
                    itemCount = items.size,
                    hasMore = hasMore,
                    isLoadingMore = isLoadingMore,
                    loadMoreError = loadMoreError,
                    isRefreshing = isRefreshing,
                    isLoading = isInitialLoading,
                    errorMessage = errorMessage
                )
            }
        }
    }

/** 当前板块内容快照（传给共享面板 `ZoneBoardPane`，保证两屏同一渲染口径）。 */
val HomeFeedUiState.currentBoardContent: ZoneBoardContent
    get() = boardView.content

/** 当前板块筛选值快照（传给共享面板/筛选条）。实体 5 类暂无筛选项，保持默认值。 */
val HomeFeedUiState.currentBoardFilters: ZoneBoardFilters
    get() = boardView.filters

/** 当前板块条目数（内容区四态 / 触底翻页 / 折叠可滚动性共用）。 */
val HomeFeedUiState.currentBoardItemCount: Int
    get() = boardView.itemCount

/** 当前板块是否还有下一页。 */
val HomeFeedUiState.currentBoardHasMore: Boolean
    get() = boardView.hasMore

/** 当前板块是否正在追加下一页。 */
val HomeFeedUiState.currentBoardIsLoadingMore: Boolean
    get() = boardView.isLoadingMore

/** 当前板块追加失败信息（页脚重试用）。 */
val HomeFeedUiState.currentBoardLoadMoreError: String?
    get() = boardView.loadMoreError

/** 当前板块是否正在下拉刷新（产品刷新态由板块状态承载，其余板块共用全局 isRefreshing）。 */
val HomeFeedUiState.currentBoardIsRefreshing: Boolean
    get() = boardView.isRefreshing

/** 当前板块首屏加载中（宿主统一用 `PoiLoadingView` 展示）。 */
val HomeFeedUiState.isCurrentBoardLoading: Boolean
    get() = boardView.isLoading

/** 当前板块首屏错误（仅在列表为空时展示错误态）。 */
val HomeFeedUiState.currentBoardErrorMessage: String?
    get() = boardView.errorMessage

private fun pageView(
    isLoading: Boolean,
    isLoadingMore: Boolean,
    errorMessage: String?,
    loadMoreError: String?,
    isEndReached: Boolean
): ZoneBoardPageView = ZoneBoardPageView(
    isLoading = isLoading,
    isLoadingMore = isLoadingMore,
    errorMessage = errorMessage,
    loadMoreError = loadMoreError,
    isEndReached = isEndReached
)
