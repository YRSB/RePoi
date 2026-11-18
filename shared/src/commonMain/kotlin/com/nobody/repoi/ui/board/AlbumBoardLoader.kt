package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.util.paging.PageChunk
import com.nobody.repoi.util.paging.PagedListLoader
import com.nobody.repoi.util.paging.PagedListState
import com.nobody.repoi.util.paging.hasNewKeysAfterDedup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * 分享（相册）板块的一次请求参数快照：作用域 + 6 档排序 + 分类。
 *
 * 请求参数唯一入口，首页与专区详情共用同一份拼参逻辑（`zones` 经 [ZoneQueryScope.zonesParam] 派生，
 * 禁止调用点散拼）。
 */
data class AlbumBoardQuery(
    val scope: ZoneQueryScope,
    val order: String = AlbumBoardOptions.DEFAULT_ORDER,
    val category: Int? = null
)

/**
 * 分享板块分页控制器（首页与专区详情共用，issue #150 审查：消除两屏各写一份首屏/翻页循环）。
 *
 * 分页状态机交给 [PagedListLoader]（页码 + 稳定键去重 + 并发防重 + 过期请求取消），
 * 宿主只负责：切排序/分类时更新 [AlbumBoardQuery] 再调 [loadInitial] / [refresh]，
 * 以及把自己的 UI 字段投影到 [state]。
 *
 * @param scope 宿主协程作用域（ViewModel 的 `viewModelScope`）
 * @param repository 相册仓储
 */
class AlbumBoardLoader(
    scope: CoroutineScope,
    private val repository: AlbumRepository
) {
    private var query = AlbumBoardQuery(scope = ZoneQueryScope.All)

    /**
     * 翻页前的现有条目快照：用于还原「整页且去重后有新增才算还有下一页」的旧语义。
     * 服务端偶尔会在末页重复返回已展示条目，只看 `page.hasMore` 会永远判不到底。
     */
    private var existingAlbums: List<AlbumListItem> = emptyList()

    private val loader = PagedListLoader<AlbumListItem>(
        scope = scope,
        initialPage = 1,
        pageSize = AlbumBoardOptions.PAGE_SIZE,
        keySelector = { it.targetAlbumId }
    ) { page, pageSize ->
        repository.getAlbums(
            page = page,
            pageSize = pageSize,
            orderString = query.order,
            zones = query.scope.zonesParam(),
            category = query.category
        ).map { payload ->
            val hasMore = payload.hasMore &&
                (page == 1 || payload.items.hasNewKeysAfterDedup(existingAlbums) { it.targetAlbumId })
            PageChunk(items = payload.items, hasMore = hasMore)
        }
    }

    /** 分页状态机（宿主收集后投影进自己的 UI 状态）。 */
    val state: StateFlow<PagedListState<AlbumListItem>> = loader.state

    /** 首屏 / 切排序 / 切分类 / 错误重试：清空列表并回第一页。 */
    fun loadInitial(query: AlbumBoardQuery) {
        this.query = query
        loader.loadInitial()
    }

    /** 下拉刷新：保持列表可见，成功后替换并回第一页。 */
    fun refresh(query: AlbumBoardQuery) {
        this.query = query
        loader.refresh()
    }

    /** 触底翻页 / 页脚重试：沿用当前参数快照，并按去重结果修正 hasMore。 */
    fun loadMore() {
        existingAlbums = loader.state.value.items
        loader.loadMore()
    }

    /** 作用域切换：取消在途请求并清空列表（视图层再按需清空分类候选等派生状态）。 */
    fun reset() {
        loader.reset()
    }
}
