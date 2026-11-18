package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.data.remote.model.ResaleSearchItem
import com.nobody.repoi.data.remote.model.ZoneEntityItem

/**
 * 单个板块内容的分页 / 错误视图（只读快照）。
 *
 * 宿主机把各 loader（`PagedListLoader` / `KeyedListLoader` / 手写 Job）的状态投影成该结构；
 * pane 不持有 loader、不自行去重、不自行判定 `hasMore`，只按快照渲染。
 *
 * @param isLoading 首屏加载中（宿主通常已在外层用 `PoiLoadingView` 接管，pane 一般不消费）
 * @param isLoadingMore 底部追加加载中
 * @param errorMessage 首屏错误（仅在条目为空时渲染错误占位）
 * @param loadMoreError 追加错误（页脚渲染重试）
 * @param isEndReached 是否已经到底（页脚渲染「已经到底啦」）
 */
data class ZoneBoardPageView(
    val page: Int = 1,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreError: String? = null,
    val isEndReached: Boolean = false
)

/**
 * 每板块内容快照（sealed，保留类型安全）。
 *
 * 首页与专区详情把各自的 ViewModel 状态投影为同一结构后传给 `ZoneBoardPane`，保证两端只维护一套
 * 内容渲染逻辑。`ZoneBoardPane` 已覆盖白名单全部 10 类板块：本节所有变体都由两处宿主投影后消费
 * （新增板块时同步补齐 pane 分支与两处投影，不要另起一条渲染路径）。
 */
sealed interface ZoneBoardContent {
    /** 分页 / 错误视图（所有板块共有）。 */
    val page: ZoneBoardPageView

    /** 动态板块：游标分页（`lastTime`），条目与去重键 `id + actionDate` 由宿主维护。 */
    data class Action(
        val items: List<ActionItem>,
        override val page: ZoneBoardPageView
    ) : ZoneBoardContent

    /** 分享板块：页码分页，宿主已按 `targetAlbumId` 去重。 */
    data class Album(
        val items: List<AlbumListItem>,
        override val page: ZoneBoardPageView
    ) : ZoneBoardContent

    /** 讨论板块：页码分页；置顶帖是否展示由宿主的 `showPinnedPosts` 决定。 */
    data class Posts(
        val pinnedPosts: List<PostItem>,
        val items: List<PostItem>,
        override val page: ZoneBoardPageView
    ) : ZoneBoardContent

    /** 产品板块：网格 / 列表双形态。 */
    data class Hobby(
        val items: List<HobbyItemSummary>,
        val isGridMode: Boolean,
        override val page: ZoneBoardPageView
    ) : ZoneBoardContent

    /** 二手板块：游标分页（`addDate`）。 */
    data class Resale(
        val items: List<ResaleSearchItem>,
        override val page: ZoneBoardPageView
    ) : ZoneBoardContent

    /** 实体 5 类：页码分页，宿主按板块类型各自持有 loader。 */
    data class Entity(
        val items: List<ZoneEntityItem>,
        override val page: ZoneBoardPageView
    ) : ZoneBoardContent
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
