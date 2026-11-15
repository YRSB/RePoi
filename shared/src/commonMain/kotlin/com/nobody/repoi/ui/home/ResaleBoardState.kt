package com.nobody.repoi.ui.home

import com.nobody.repoi.data.remote.model.ResaleSearchItem
import com.nobody.repoi.data.repository.ResaleRepository
import com.nobody.repoi.ui.board.ResaleBoardOptions
import com.nobody.repoi.util.paging.KeyedListLoader
import com.nobody.repoi.util.paging.PageChunk
import com.nobody.repoi.util.paging.PagedListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * 首页二手（`resale_item`）板块状态（issue #160）。
 *
 * 与动态/分享板块一样按「板块懒加载」：首次切到二手才请求，切走再切回直接复用。
 * 该类型只承载**可观察状态**（items + 分页 + 三级筛选值），请求构造与分页状态机由
 * [ResaleBoardLoader] 封装；`HomeFeedViewModel` 只做投影与委派，避免中心 VM 继续膨胀。
 *
 * @param type 交易类型：`1` 出售 / `2` 求购（见 [ResaleBoardOptions.TYPES]）
 * @param purity 成色键；`null` = 全部成色（省略 `puritys` 参数）
 * @param keyword 搜索关键词（仅详情专属搜索栏会产生值；首页恒为空）
 * @param isLoaded 是否已发起过首屏请求（懒加载去重 + 失败/空态后不自动重试）
 */
data class HomeResaleBoardState(
    val items: List<ResaleSearchItem> = emptyList(),
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val loadMoreError: String? = null,
    val type: Int = ResaleBoardOptions.DEFAULT_TYPE,
    val purity: String? = null,
    val keyword: String = "",
    val isLoaded: Boolean = false
)

/** 二手板块单次请求的参数快照（作用域 + 三级筛选）。 */
internal data class ResaleQuery(
    val zones: String?,
    val type: Int,
    val purity: String?,
    val keyword: String
)

/**
 * 二手板块游标分页控制器：封装 [KeyedListLoader] 与 `POST api/resale/search` 请求拼参。
 *
 * 游标 = 上一批末条 `addDate`；`hasMore` 判定沿用专区详情既有实现
 * （整页 24 条且末条 `addDate` 可解析），去重键为二手条目 id。
 *
 * 作用域参数由调用方经 [ResaleQuery.zones] 传入（[com.nobody.repoi.data.remote.model.ZoneQueryScope.zonesParam]）：
 * 全部聚合为 `null`（省略）、单专区为 nodeId 字符串；关注聚合不可用，调用方必须在请求前短路。
 */
internal class ResaleBoardLoader(
    scope: CoroutineScope,
    private val repository: ResaleRepository
) {
    private var query = ResaleQuery(
        zones = null,
        type = ResaleBoardOptions.DEFAULT_TYPE,
        purity = null,
        keyword = ""
    )

    private val loader = KeyedListLoader<ResaleSearchItem, String?>(
        scope = scope,
        initialKey = null,
        keySelector = { it.id },
        nextKeyProvider = { _, state -> state.items.lastOrNull()?.addDate },
        fetcher = { cursor ->
            repository.searchResale(
                zones = query.zones,
                type = query.type,
                keyword = query.keyword.takeIf { it.isNotBlank() },
                puritys = query.purity,
                lastTime = cursor,
                pageSize = ResaleBoardOptions.PAGE_SIZE
            ).map { payload ->
                val list = payload.list
                PageChunk(
                    items = list,
                    hasMore = list.size >= ResaleBoardOptions.PAGE_SIZE &&
                        !list.lastOrNull()?.addDate.isNullOrBlank()
                )
            }
        }
    )

    /** 分页状态机（由 ViewModel 收集并投影进 [HomeResaleBoardState]）。 */
    val state: StateFlow<PagedListState<ResaleSearchItem>> = loader.state

    /** 首屏 / 筛选变化重载：清空列表并回第一页。 */
    fun loadInitial(query: ResaleQuery) {
        this.query = query
        loader.loadInitial()
    }

    /** 下拉刷新：保持列表可见，成功后替换。 */
    fun refresh(query: ResaleQuery) {
        this.query = query
        loader.refresh()
    }

    /** 触底翻页 / 页脚重试：沿用当前参数快照。 */
    fun loadMore() {
        loader.loadMore()
    }

    /** 作用域切换：取消在途请求并清空列表。 */
    fun reset() {
        loader.reset(clearItems = true)
    }
}

/**
 * 作用域切换时重置二手板块：列表/分页/错误/懒加载标记全部失效。
 *
 * 交易类型与成色属板块级偏好（不随专区变化），故保留；懒加载标记归零，等下次切到二手再请求。
 */
internal fun HomeFeedUiState.resetResaleForScopeChange(): HomeFeedUiState = copy(
    resale = resale.copy(
        items = emptyList(),
        hasMore = true,
        isLoading = false,
        isLoadingMore = false,
        errorMessage = null,
        loadMoreError = null,
        isLoaded = false
    )
)
