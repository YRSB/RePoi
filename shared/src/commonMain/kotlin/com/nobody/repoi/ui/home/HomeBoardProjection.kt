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
 * 关键约束：所有按板块分派的字段只在 [boardView] 一处 `when`，各公开 getter 复用同一份映射定义。
 * [boardView] 是即时计算属性（不缓存）：一次重组内读取多个 getter 会各计算一次，成本与拆分前一致。
 * 新增板块只需在 [boardView] 增加一个分支，不再出现 9 个平行 `when`（Repeated Switches）。
 */

/** 任意板块的全部 UI 投影字段（一次 `when` 计算；横向分页器按页取用）。 */
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

/** 当前板块的投影快照（头部高度、折叠可滚动性、刷新态读取）。 */
internal val HomeFeedUiState.currentBoardView: HomeBoardView
    get() = boardView(selectedBoard)

/**
 * 任意板块的投影快照。
 *
 * 实体 5 类共用同一套分页字段，讨论 / 分享 / 二手 / 产品各自持有独立 loader 状态，
 * 动态板块沿用全局字段；此处把差异集中到唯一 `when`，其余 getter 只做字段转发。
 *
 * 横向分页器会同时组合多个板块（当前页 + 拖动中的相邻页），因此投影必须按传入的 [board]
 * 求值，而不是读宿主选中的 `selectedBoard`。未加载过的板块统一按「首屏加载中」处理：
 * 滑动到该页时先给加载态，落定后由 `selectBoard` 发起首屏请求，避免把「尚未请求」误判成空态。
 */
internal fun HomeFeedUiState.boardView(board: ZoneBoard): HomeBoardView {
    return when (board) {
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
            // 懒加载：尚未请求过首屏（且无错误）时也算加载态，滑动到该页不会先闪一帧空态卡片
            isLoading = (isAlbumLoading || !isAlbumLoaded) &&
                albumItems.isEmpty() && albumErrorMessage == null,
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
            // 懒加载：同分享板块，未发起过首屏时按加载态处理
            isLoading = (hobbyBoard.isLoading || !hobbyBoard.isLoaded) &&
                hobbyBoard.items.isEmpty() && hobbyBoard.errorMessage == null,
            errorMessage = hobbyBoard.errorMessage
        )

        else -> {
            if (board.isEntity) {
                // 从未加载过该实体板块时 map 无键：按加载态处理，等 selectBoard 建 loader 后回填
                val entityState = entityBoards[board]
                HomeBoardView(
                    content = ZoneBoardContent.Entity(
                        items = entityState?.items ?: emptyList(),
                        page = pageView(
                            isLoading = entityState?.isLoading ?: true,
                            isLoadingMore = entityState?.isLoadingMore ?: false,
                            errorMessage = entityState?.errorMessage,
                            loadMoreError = entityState?.loadMoreError,
                            isEndReached = entityState?.isEndReached ?: false
                        )
                    ),
                    filters = ZoneBoardFilters(),
                    itemCount = entityState?.items?.size ?: 0,
                    hasMore = !(entityState?.isEndReached ?: false),
                    isLoadingMore = entityState?.isLoadingMore ?: false,
                    loadMoreError = entityState?.loadMoreError,
                    isRefreshing = isRefreshing || (entityState?.isRefreshing ?: false),
                    isLoading = (entityState == null || entityState.isLoading) &&
                        (entityState?.items?.isEmpty() ?: true),
                    errorMessage = entityState?.errorMessage
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
}

/** 当前板块内容快照（传给共享面板 `ZoneBoardPane`，保证两屏同一渲染口径）。 */
val HomeFeedUiState.currentBoardContent: ZoneBoardContent
    get() = currentBoardView.content

/** 当前板块筛选值快照（传给共享面板/筛选条）。实体 5 类暂无筛选项，保持默认值。 */
val HomeFeedUiState.currentBoardFilters: ZoneBoardFilters
    get() = currentBoardView.filters

/** 当前板块条目数（内容区四态 / 触底翻页 / 折叠可滚动性共用）。 */
val HomeFeedUiState.currentBoardItemCount: Int
    get() = currentBoardView.itemCount

/** 当前板块是否还有下一页。 */
val HomeFeedUiState.currentBoardHasMore: Boolean
    get() = currentBoardView.hasMore

/** 当前板块是否正在追加下一页。 */
val HomeFeedUiState.currentBoardIsLoadingMore: Boolean
    get() = currentBoardView.isLoadingMore

/** 当前板块追加失败信息（页脚重试用）。 */
val HomeFeedUiState.currentBoardLoadMoreError: String?
    get() = currentBoardView.loadMoreError

/** 当前板块是否正在下拉刷新（产品刷新态由板块状态承载，其余板块共用全局 isRefreshing）。 */
val HomeFeedUiState.currentBoardIsRefreshing: Boolean
    get() = currentBoardView.isRefreshing

/** 当前板块首屏加载中（宿主统一用 `PoiLoadingView` 展示）。 */
val HomeFeedUiState.isCurrentBoardLoading: Boolean
    get() = currentBoardView.isLoading

/** 当前板块首屏错误（仅在列表为空时展示错误态）。 */
val HomeFeedUiState.currentBoardErrorMessage: String?
    get() = currentBoardView.errorMessage

/**
 * 板块筛选值快照（token 化）。
 *
 * 同一板块内筛选变化 → 回顶并清空该键的滚动记忆；换板块不算筛选变化（交给按 key 的恢复逻辑）。
 * 筛选维度按板块取当前生效值（动态 = subType；分享 = 排序 + 分类；讨论 = order；
 * 二手 = 出售求购 + 成色；产品 = 全部筛选值），因此切筛选与切板块共用同一套 token 比较。
 */
internal fun HomeFeedUiState.boardFilterToken(board: ZoneBoard): String = when (board) {
    ZoneBoard.Album -> "$albumSelectedOrder|${albumSelectedCategory ?: "all"}"
    ZoneBoard.Posts -> posts.order
    ZoneBoard.Resale -> "${resale.type}|${resale.purity ?: "all"}"
    ZoneBoard.Hobby -> with(hobbyBoard.filter) {
        listOf(
            order,
            categoryId,
            subCategoryId ?: "all",
            releaseYear ?: "all",
            releaseMonth ?: "all",
            keyword,
            sex ?: "all",
            specs ?: "all",
            scale ?: "all",
            r18 ?: "all"
        ).joinToString("|")
    }
    else -> selectedSubType
}

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
