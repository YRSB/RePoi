package com.nobody.repoi.ui.home

import com.nobody.repoi.data.remote.model.ZoneEntityItem
import com.nobody.repoi.data.remote.model.ENTITY_BOARD_PAGE_SIZE
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.util.paging.PageChunk
import com.nobody.repoi.util.paging.PagedListLoader
import com.nobody.repoi.util.paging.PagedListState
import com.nobody.repoi.util.paging.pagedChunkLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 实体板块（厂商/系列/作品/角色/人物）的分页 / 错误只读快照。
 *
 * 首页把 [EntityBoardLoader] 的状态投影为该结构后放进 `HomeFeedUiState`，
 * 再由 `currentBoardContent` 转成共享面板的 [com.nobody.repoi.ui.board.ZoneBoardContent.Entity]。
 */
data class EntityBoardState(
    val items: List<ZoneEntityItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val isEndReached: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreError: String? = null
)

/**
 * 首页实体 5 类板块的懒加载与分页控制器（纯类，可脱离 Compose 测试）。
 *
 * 与专区详情 `ZoneDetailViewModel.entityLoaders` 同一模式：按 [ZoneBoard] 懒建
 * [PagedListLoader]，首次切到某板块才请求；切走再切回复用已加载状态（不重复请求）。
 *
 * 请求纪律（`docs/research/aggregate-api-verification.md` §2.4/§4.3/§5）：
 * - 5 类共用 `GET api/zone/items`，`itemType = board.type`（角色是服务端拼写 `charactar`）；
 * - 作用域只经 `scope.zonesParam()` 表达：单专区 `<nodeId>`、关注逗号串（并集）、全部省略；
 * - **绝不下发 `homeScope`**：实测非空 `homeScope` 会让服务端忽略 `zones` 退化为全站；
 * - 翻页以响应 `hasMore` 为准（`works/charactar/person` 的 `total` 服务端封顶 10000，不可用 total 推算到底）。
 *
 * @param coroutineScope 宿主协程作用域（首页为 `viewModelScope`）
 * @param repository 专区仓储
 * @param onStateChanged 板块状态变化回调（宿主写回 UI 状态）
 */
class EntityBoardLoader(
    private val coroutineScope: CoroutineScope,
    private val repository: ZoneRepository,
    private val onStateChanged: (ZoneBoard, EntityBoardState) -> Unit
) {
    /** 按板块懒建的加载器；键存在即「已发起过首屏请求」（失败/空态后不自动重试）。 */
    private val loaders = mutableMapOf<ZoneBoard, PagedListLoader<ZoneEntityItem>>()

    /** 每个 loader 的状态订阅（作用域切换时随 loader 一起取消，避免旧作用域响应回灌新列表）。 */
    private val watchers = mutableMapOf<ZoneBoard, Job>()

    /**
     * 懒加载入口：首次切到该实体板块时请求首屏。
     *
     * 关注聚合为空时短路、不发起请求（空 `zones` 会被服务端当成全站，属假「关注」内容），
     * 直接发布「已到底」空态。
     */
    fun ensureLoaded(board: ZoneBoard, scope: ZoneQueryScope) {
        if (!board.isEntity || loaders.containsKey(board)) return
        if (scope.isFollowEmpty) {
            onStateChanged(board, EntityBoardState(isEndReached = true))
            return
        }
        val loader = createLoader(board, scope)
        loaders[board] = loader
        loader.loadInitial()
        publish(board, loader)
    }

    /** 触底翻页 / 页脚「加载更多失败，点击重试」（loader 内部按 `hasMore` 与阶段守卫）。 */
    fun loadMore(board: ZoneBoard) {
        val loader = loaders[board] ?: return
        loader.loadMore()
        publish(board, loader)
    }

    /** 首屏错误态重试。 */
    fun retry(board: ZoneBoard) {
        val loader = loaders[board] ?: return
        loader.retry()
        publish(board, loader)
    }

    /** 下拉刷新：保留旧列表可见，成功后替换并回第一页。 */
    fun refresh(board: ZoneBoard) {
        val loader = loaders[board] ?: return
        loader.refresh()
        publish(board, loader)
    }

    /**
     * 作用域切换（换专区 / 换一级聚合态）：丢弃全部 loader 与在途请求。
     *
     * 不同专区的 `zones` 参数不同，旧 loader 的 fetcher 闭包仍持有旧作用域，必须整体重建。
     */
    fun reset() {
        watchers.values.forEach { it.cancel() }
        watchers.clear()
        loaders.values.forEach { it.reset(clearItems = true) }
        loaders.clear()
    }

    private fun createLoader(board: ZoneBoard, scope: ZoneQueryScope): PagedListLoader<ZoneEntityItem> {
        val loader = pagedChunkLoader<ZoneEntityItem>(
            scope = coroutineScope,
            initialPage = 1,
            pageSize = ENTITY_BOARD_PAGE_SIZE,
            keySelector = { it.targetId }
        ) { page, size ->
            repository.getZoneItems(
                scope = scope,
                itemType = board.type,
                page = page,
                pageSize = size
            ).map { payload -> PageChunk(items = payload.list, hasMore = payload.hasMore) }
        }
        watchers[board] = coroutineScope.launch {
            loader.state.collect { state -> onStateChanged(board, state.toEntityBoardState()) }
        }
        return loader
    }

    private fun publish(board: ZoneBoard, loader: PagedListLoader<ZoneEntityItem>) {
        onStateChanged(board, loader.state.value.toEntityBoardState())
    }
}

private fun PagedListState<ZoneEntityItem>.toEntityBoardState(): EntityBoardState = EntityBoardState(
    items = items,
    isLoading = isLoading,
    isLoadingMore = isLoadingMore,
    // 刷新态必须投影出去：首页 FAB/下拉刷新的指示器只读投影值，丢掉会让刷新静默完成
    isRefreshing = isRefreshing,
    isEndReached = isEndReached,
    errorMessage = errorMessage,
    loadMoreError = loadMoreError
)
