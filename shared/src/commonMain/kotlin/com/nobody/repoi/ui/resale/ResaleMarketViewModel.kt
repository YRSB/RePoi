package com.nobody.repoi.ui.resale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.ResaleSearchItem
import com.nobody.repoi.data.remote.model.ZoneQueryScope
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

data class ResaleMarketUiState(
    val hobbyId: Long = 0L,
    val title: String = "二手商品",
    val type: Int = 1,
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

class ResaleMarketViewModel(
    private val resaleRepository: ResaleRepository,
    private val collectRepository: CollectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResaleMarketUiState())
    val uiState: StateFlow<ResaleMarketUiState> = _uiState.asStateFlow()

    private var initialized = false

    private val resaleLoader = KeyedListLoader<ResaleSearchItem, String?>(
        scope = viewModelScope,
        initialKey = null,
        keySelector = { it.id },
        nextKeyProvider = { _, state -> state.items.lastOrNull()?.addDate },
        fetcher = { cursor ->
            val cur = _uiState.value
            resaleRepository.searchResale(

                scope = ZoneQueryScope.All,
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

    fun setType(type: Int) {
        if (_uiState.value.type == type) return
        _uiState.update { it.copy(type = type) }
        resaleLoader.loadInitial()
    }

    fun setPurity(purity: String?, label: String? = null) {
        val effectiveLabel = label ?: if (purity == null) "成色" else purity
        if (_uiState.value.purity == purity) return
        _uiState.update { it.copy(purity = purity, purityLabel = effectiveLabel) }
        resaleLoader.loadInitial()
    }

    fun setRegion(regionId: String?, label: String? = null) {
        val effectiveLabel = label ?: if (regionId == null) "区域" else regionId
        if (_uiState.value.regionNodeId == regionId) return
        _uiState.update { it.copy(regionNodeId = regionId, regionLabel = effectiveLabel) }
        resaleLoader.loadInitial()
    }

    fun setCategory(category: String?, label: String? = null) {
        val effectiveLabel = label ?: if (category == null) "分类" else category
        if (_uiState.value.category == category) return
        _uiState.update { it.copy(category = category, categoryLabel = effectiveLabel) }
        resaleLoader.loadInitial()
    }

    fun toggleSearch() {
        _uiState.update { it.copy(isSearchVisible = !it.isSearchVisible) }
    }

    fun updateKeyword(kw: String) {
        _uiState.update { it.copy(keyword = kw) }
    }

    fun submitSearch() {
        resaleLoader.loadInitial()
    }

    fun loadInitial(isRefresh: Boolean = false) {
        if (isRefresh) {
            resaleLoader.refresh()
        } else {
            resaleLoader.loadInitial()
        }
    }

    fun loadNextPage() {
        resaleLoader.loadMore()
    }

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
