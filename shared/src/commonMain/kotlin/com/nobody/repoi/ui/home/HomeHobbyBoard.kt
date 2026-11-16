package com.nobody.repoi.ui.home

import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.ZoneHobbyFilter
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.data.repository.ItemRepository
import com.nobody.repoi.ui.board.HobbyBoardOptions
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.board.ZoneBoardFilters
import com.nobody.repoi.ui.board.ZoneBoardPageView
import com.nobody.repoi.ui.board.applyTo
import com.nobody.repoi.ui.board.toAdvancedFilterParam
import com.nobody.repoi.ui.item.list.components.HobbyAdvancedFilterParam
import com.nobody.repoi.util.paging.PageChunk
import com.nobody.repoi.util.paging.PagedListLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 首页产品板块的独立状态快照（issue #159）。
 *
 * 首页 `HomeFeedUiState` 只挂一份本快照，所有板块逻辑放在 [HomeHobbyBoardController]（独立文件），
 * 避免首页 ViewModel 继续膨胀（#159 并发纪律：板块状态类 / loader / 请求构造放新文件）。
 *
 * @param filter 已选筛选值（排序 / 年份 / 月份 / 分类 / 关键词 / 4 个高级维度）
 * @param isGridMode 网格（true）/ 列表（false），来自 `ViewPreferencesManager.isHobbyListGridModeFlow`
 * @param items 已按 `actualItemId` 去重的条目
 * @param page 当前已加载到的页码（1-based）
 * @param hasMore 是否还有下一页（服务端 `query-v2` 无 total，按整页启发式）
 * @param isLoaded 当前作用域下是否已发起过首屏请求（懒加载去重 / 切走切回不重复请求）
 * @param showFilterSheet 高级筛选弹层显隐（弹层由共享 pane 渲染）
 * @param showCategoryPickerSheet 分类级联弹层显隐
 */
data class HomeHobbyBoardState(
    val filter: ZoneHobbyFilter = ZoneHobbyFilter(),
    val isGridMode: Boolean = true,
    val items: List<HobbyItemSummary> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isLoaded: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreError: String? = null,
    val showFilterSheet: Boolean = false,
    val showCategoryPickerSheet: Boolean = false
) {
    /** 当前板块条目数（首页四态 / 触底翻页共用）。 */
    val itemCount: Int get() = items.size

    /** 高级筛选是否生效（悬浮入口选中态）。 */
    val hasAdvancedFilters: Boolean get() = filter.hasAdvancedFilters

    /** 高级筛选弹层入参。 */
    val advancedFilterParam: HobbyAdvancedFilterParam get() = filter.toAdvancedFilterParam()

    /** 传给共享面板的筛选快照。 */
    val filters: ZoneBoardFilters
        get() = ZoneBoardFilters(
            hobby = filter,
            showHobbyFilterSheet = showFilterSheet,
            showHobbyCategoryPickerSheet = showCategoryPickerSheet
        )

    /** 传给共享面板的内容快照（列表 / 网格由 [isGridMode] 决定）。 */
    val content: ZoneBoardContent.Hobby
        get() = ZoneBoardContent.Hobby(
            items = items,
            isGridMode = isGridMode,
            page = ZoneBoardPageView(
                isLoading = isLoading,
                isLoadingMore = isLoadingMore,
                errorMessage = errorMessage,
                loadMoreError = loadMoreError,
                isEndReached = !hasMore
            )
        )
}

/** 产品板块一次请求的参数快照（作用域 + 全部筛选值）。 */
private data class HobbyBoardQuery(
    val scope: ZoneQueryScope,
    val filter: ZoneHobbyFilter
)

/**
 * 首页产品板块控制器：持有版块状态、首屏 / 分页 / 刷新请求与筛选动作。
 *
 * 设计约束：
 * - 分页状态机统一交给 [PagedListLoader]（页码递增、稳定键去重、并发防重、过期请求取消），
 *   控制器只维护筛选偏好与弹层显隐，不再手写 `isLoading/hasMore` 状态机（#150 审查）；
 * - 只在切换作用域时重置列表（[onScopeChanged]），排序等筛选值属板块偏好，保留（与分享板块一致）；
 * - 首屏懒加载：首次切到产品板块才发请求，切走再切回不重复请求；
 * - 筛选变化：清空列表、回第一页并重载；
 * - 请求一律经 `ItemRepository.queryHobbyBoard(scope, ...)`，不在这里拼 zone 参数；
 * - 关注聚合不可用：`ZoneBoardCapability.homeBoardsFor(Follow)` 已隐藏产品 Tab，[loadInitial] 仍对空关注集合短路防御。
 */
class HomeHobbyBoardController(
    private val repository: ItemRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(HomeHobbyBoardState())
    val state: StateFlow<HomeHobbyBoardState> = _state.asStateFlow()

    /** 当前请求参数：每次首屏 / 刷新前更新，fetcher 在真正发请求时读取。 */
    private var query = HobbyBoardQuery(scope = ZoneQueryScope.All, filter = ZoneHobbyFilter())

    /** 翻页前的现有条目 id 快照：还原「整页且去重后有新增才算还有下一页」的旧语义。 */
    private var existingHobbyIds: Set<Long> = emptySet()

    private val loader = PagedListLoader<HobbyItemSummary>(
        scope = scope,
        initialPage = 1,
        pageSize = HobbyBoardOptions.PAGE_SIZE,
        keySelector = { it.actualItemId }
    ) { page, pageSize ->
        repository.queryHobbyBoard(
            scope = query.scope,
            page = page,
            pageSize = pageSize,
            filters = query.filter
        ).map { items ->
            val chunk = PageChunk.of(items, pageSize)
            if (page == 1) {
                chunk
            } else {
                val hasNewItems = items.any { it.actualItemId !in existingHobbyIds }
                chunk.copy(hasMore = chunk.hasMore && hasNewItems)
            }
        }
    }

    init {
        scope.launch {
            loader.state.collect { listState ->
                _state.update { current ->
                    current.copy(
                        items = listState.items,
                        page = listState.page,
                        // 未加载 / 已到底的默认状态不应暴露「还有下一页」；非空列表才采用 loader 判定
                        hasMore = listState.hasMore && listState.items.isNotEmpty(),
                        isLoading = listState.isLoading,
                        isRefreshing = listState.isRefreshing,
                        isLoadingMore = listState.isLoadingMore,
                        errorMessage = listState.initialError,
                        loadMoreError = listState.appendError
                    )
                }
            }
        }
    }

    /** 同步网格 / 列表偏好（由宿主订阅 `isHobbyListGridModeFlow` 后转发，控制器不直接读偏好）。 */
    fun updateGridMode(isGrid: Boolean) {
        _state.update { current -> if (current.isGridMode == isGrid) current else current.copy(isGridMode = isGrid) }
    }

    /** 切换专区作用域：丢弃当前列表与错误，保留筛选偏好，等下次切到产品板块再懒加载。 */
    fun onScopeChanged() {
        loader.reset()
        _state.update {
            it.copy(
                items = emptyList(),
                page = 1,
                hasMore = false,
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false,
                isLoaded = false,
                errorMessage = null,
                loadMoreError = null,
                showFilterSheet = false,
                showCategoryPickerSheet = false
            )
        }
    }

    /** 懒加载入口：只在该作用域尚未加载 / 未在加载时发起首屏请求。 */
    fun ensureLoaded(zoneScope: ZoneQueryScope) {
        val snapshot = _state.value
        if (snapshot.isLoaded || snapshot.isLoading) return
        loadInitial(zoneScope)
    }

    /** 首屏 / 筛选变化 / 错误重试：回第一页并清空列表。 */
    fun loadInitial(zoneScope: ZoneQueryScope) {
        if (zoneScope.isFollowEmpty) {
            // 防御：关注集合为空时短路，绝不发请求（空 zones 会被服务端当作全站）
            loader.reset()
            _state.update {
                it.copy(
                    items = emptyList(),
                    page = 1,
                    hasMore = false,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    isLoaded = true,
                    errorMessage = null,
                    loadMoreError = null
                )
            }
            return
        }
        query = HobbyBoardQuery(scope = zoneScope, filter = _state.value.filter)
        _state.update { it.copy(isLoaded = true, errorMessage = null, loadMoreError = null) }
        loader.loadInitial()
    }

    /** 触底翻页：页码与去重交给 loader；筛选变化后的过期响应由 loader 取消旧请求丢弃。 */
    fun loadNextPage(zoneScope: ZoneQueryScope) {
        val snapshot = _state.value
        if (!snapshot.isLoaded || !snapshot.hasMore || zoneScope.isFollowEmpty) return
        existingHobbyIds = loader.state.value.items.map { it.actualItemId }.toSet()
        loader.loadMore()
    }

    /** 下拉刷新 / FAB 刷新：保持现有条目可见，成功后用第一页替换。 */
    fun refresh(zoneScope: ZoneQueryScope) {
        if (zoneScope.isFollowEmpty) {
            _state.update { it.copy(isRefreshing = false, isLoading = false, errorMessage = null) }
            return
        }
        query = HobbyBoardQuery(scope = zoneScope, filter = _state.value.filter)
        _state.update { it.copy(errorMessage = null, loadMoreError = null) }
        loader.refresh()
    }

    // ==================== 筛选动作 ====================

    /** 6 档排序切换（非法键忽略，不发请求）。 */
    fun selectOrder(zoneScope: ZoneQueryScope, order: String) {
        if (order !in HobbyBoardOptions.ORDER_KEYS) return
        updateFilter(zoneScope) { it.copy(order = order) }
    }

    /** 发售年份（`null` = 不限年）。 */
    fun selectYear(zoneScope: ZoneQueryScope, year: Int?) {
        updateFilter(zoneScope) { it.copy(releaseYear = year) }
    }

    /** 发售月份（`null` = 不限月）。 */
    fun selectMonth(zoneScope: ZoneQueryScope, month: Int?) {
        updateFilter(zoneScope) { it.copy(releaseMonth = month) }
    }

    /** 分类级联（一级 + 可选子分类）。 */
    fun selectCategory(zoneScope: ZoneQueryScope, categoryId: Int, subCategoryId: Int?) {
        updateFilter(zoneScope) { it.copy(categoryId = categoryId, subCategoryId = subCategoryId) }
    }

    /** 关键词输入：只改输入框值，不触发请求（回车 / 搜索按钮才提交）。 */
    fun updateKeyword(keyword: String) {
        _state.update { it.copy(filter = it.filter.copy(keyword = keyword)) }
    }

    /** 提交关键词搜索（即使关键词未变也重载，视为显式刷新）。 */
    fun submitSearch(zoneScope: ZoneQueryScope) {
        loadInitial(zoneScope)
    }

    /** 高级筛选「确定」：写回 4 个高级维度并回第一页重载。 */
    fun applyAdvanced(zoneScope: ZoneQueryScope, filterParam: HobbyAdvancedFilterParam) {
        updateFilter(zoneScope) { filterParam.applyTo(it) }
    }

    /** 高级筛选「重置」：清空 4 个高级维度并回第一页重载。 */
    fun resetAdvanced(zoneScope: ZoneQueryScope) {
        updateFilter(zoneScope) {
            it.copy(sex = null, specs = null, scale = null, r18 = null)
        }
    }

    /** 高级筛选弹层显隐。 */
    fun setFilterSheetVisible(show: Boolean) {
        _state.update { it.copy(showFilterSheet = show) }
    }

    /** 分类级联弹层显隐。 */
    fun setCategoryPickerVisible(show: Boolean) {
        _state.update { it.copy(showCategoryPickerSheet = show) }
    }

    /** 筛选值变化：清列表 + 回第一页 + 重载（筛选未变化时为 no-op）。 */
    private fun updateFilter(zoneScope: ZoneQueryScope, transform: (ZoneHobbyFilter) -> ZoneHobbyFilter) {
        val current = _state.value.filter
        val next = transform(current)
        if (next == current) return
        _state.update { it.copy(filter = next) }
        loadInitial(zoneScope)
    }
}
