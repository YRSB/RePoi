package com.nobody.repoi.ui.resale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.ResaleSearchItem
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.ResaleRepository
import com.nobody.repoi.util.paging.KeyedListLoader
import com.nobody.repoi.util.paging.PageChunk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nobody.repoi.data.remote.model.CollectConstants

/**
 * 同款二手市场 UI 状态。
 */
data class ResaleMarketUiState(
    val hobbyId: Long = 0L,
    val title: String = "二手商品",
    val type: Int = 1, // 1 出售, 2 求购
    val purity: String? = null,
    val purityLabel: String = "成色",
    val regionNodeId: String? = null,
    val regionLabel: String = "区域",
    val category: String? = null,
    val categoryLabel: String = "分类",
    val keyword: String = "",
    val isSearchVisible: Boolean = false,
    val items: List<ResaleSearchItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null
)

/**
 * 同款二手市场 ViewModel。
 *
 * 遵循架构规范，使用 [KeyedListLoader] 管理游标分页加载，
 * 管理针对特定手办 [hobbyId] 的二手交易列表，支持“查看全部”全站流切换、出售/求购多级下拉筛选与关键词搜索。
 */
class ResaleMarketViewModel(
    private val resaleRepository: ResaleRepository,
    private val collectRepository: CollectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResaleMarketUiState())
    val uiState: StateFlow<ResaleMarketUiState> = _uiState.asStateFlow()

    private var initialized = false

    // 基于时间戳游标的通用分页加载器
    private val resaleLoader = KeyedListLoader<ResaleSearchItem, String?>(
        scope = viewModelScope,
        initialKey = null,
        keySelector = { it.id },
        nextKeyProvider = { _, state -> state.items.lastOrNull()?.addDate },
        fetcher = { cursor ->
            val cur = _uiState.value
            resaleRepository.searchResale(
                itemId = cur.hobbyId.takeIf { it > 0L },
                type = cur.type,
                keyword = cur.keyword.takeIf { it.isNotBlank() },
                puritys = cur.purity,
                regionNodeId = cur.regionNodeId,
                category = cur.category,
                lastTime = cursor,
                pageSize = 24
            ).map { payload ->
                val list = payload.list
                PageChunk(
                    items = list,
                    hasMore = list.size >= 24 && !list.lastOrNull()?.addDate.isNullOrBlank()
                )
            }
        }
    )

    init {
        viewModelScope.launch {
            resaleLoader.state.collect { listState ->
                _uiState.update { state ->
                    state.copy(
                        items = listState.items,
                        isLoading = listState.isLoading,
                        isRefreshing = listState.isRefreshing,
                        isLoadingMore = listState.isLoadingMore,
                        hasMore = listState.hasMore,
                        errorMessage = listState.errorMessage
                    )
                }
            }
        }
    }

    /**
     * 初始化手办 ID 与标题。
     */
    fun init(hobbyId: Long, title: String?) {
        if (initialized && _uiState.value.hobbyId == hobbyId) return
        initialized = true
        _uiState.update {
            it.copy(
                hobbyId = hobbyId,
                title = if (!title.isNullOrBlank()) "$title - 同款二手" else "二手商品"
            )
        }
        resaleLoader.loadInitial()
    }

    /**
     * 查看全部：清除当前条目限制，进入全站二手商品流（对应官方 TabResaleActivity 查看全部规范）。
     */
    fun clearHobbyFilter() {
        if (_uiState.value.hobbyId == 0L) return
        _uiState.update {
            it.copy(
                hobbyId = 0L,
                title = "二手商品"
            )
        }
        resaleLoader.loadInitial()
    }

    /**
     * 切换交易类型（1=出售，2=收购）。
     */
    fun setType(type: Int) {
        if (_uiState.value.type == type) return
        _uiState.update { it.copy(type = type) }
        resaleLoader.loadInitial()
    }

    /**
     * 切换成色筛选（null=全部）。
     */
    fun setPurity(purity: String?, label: String? = null) {
        val effectiveLabel = label ?: if (purity == null) "成色" else purity
        if (_uiState.value.purity == purity) return
        _uiState.update { it.copy(purity = purity, purityLabel = effectiveLabel) }
        resaleLoader.loadInitial()
    }

    /**
     * 切换区域筛选（null=全部）。
     */
    fun setRegion(regionId: String?, label: String? = null) {
        val effectiveLabel = label ?: if (regionId == null) "区域" else regionId
        if (_uiState.value.regionNodeId == regionId) return
        _uiState.update { it.copy(regionNodeId = regionId, regionLabel = effectiveLabel) }
        resaleLoader.loadInitial()
    }

    /**
     * 切换分类筛选（null=全部）。
     */
    fun setCategory(category: String?, label: String? = null) {
        val effectiveLabel = label ?: if (category == null) "分类" else category
        if (_uiState.value.category == category) return
        _uiState.update { it.copy(category = category, categoryLabel = effectiveLabel) }
        resaleLoader.loadInitial()
    }

    /**
     * 展开或收起搜索栏。
     */
    fun toggleSearch() {
        _uiState.update { it.copy(isSearchVisible = !it.isSearchVisible) }
    }

    /**
     * 更新关键词并触发搜索。
     */
    fun updateKeyword(kw: String) {
        _uiState.update { it.copy(keyword = kw) }
    }

    fun submitSearch() {
        resaleLoader.loadInitial()
    }

    /**
     * 首次加载或下拉刷新。
     */
    fun loadInitial(isRefresh: Boolean = false) {
        if (isRefresh) {
            resaleLoader.refresh()
        } else {
            resaleLoader.loadInitial()
        }
    }

    /**
     * 加载下一页游标数据。
     */
    fun loadNextPage() {
        resaleLoader.loadMore()
    }

    /**
     * 提交举报。
     */
    fun submitReport(resaleId: Long, reason: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            collectRepository.submitReport(
                nodeId = resaleId,
                content = reason,
                type = CollectConstants.REPORT_TYPE_RESALE
            ).onSuccess {
                onResult(true, "举报成功，感谢您的反馈")
            }.onFailure { err ->
                onResult(false, err.message ?: "举报提交失败，请稍后重试")
            }
        }
    }
}
