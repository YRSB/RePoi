package com.nobody.poishelf.ui.detail.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.poishelf.data.repository.HobbyDetailRepository
import com.nobody.poishelf.ui.navigation.HobbyAlbumListNavParam
import com.nobody.poishelf.util.paging.PagedListLoader
import com.nobody.poishelf.util.paging.pagedListLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 手办相关相册列表 ViewModel。
 */
class HobbyAlbumListViewModel(
    private val repository: HobbyDetailRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HobbyAlbumListUiState())
    val uiState: StateFlow<HobbyAlbumListUiState> = _uiState.asStateFlow()

    private var currentNodeId: Long = 0L
    private var currentItemType: String = "hobby"
    private val pageSize = 20

    private val loader: PagedListLoader<HobbyAlbumItem> = pagedListLoader(
        scope = viewModelScope,
        pageSize = pageSize,
        keySelector = { it.id }
    ) { page, size ->
        repository.getUserAlbums(
            nodeId = currentNodeId,
            itemType = currentItemType,
            page = page,
            pageSize = size,
            order = _uiState.value.selectedOrder.orderKey
        )
    }

    init {
        viewModelScope.launch {
            loader.state.collect { listState ->
                _uiState.update { current ->
                    current.copy(
                        albums = listState.items,
                        page = listState.page,
                        isInitialLoading = listState.isLoading,
                        isRefreshing = listState.isRefreshing,
                        isLoadingMore = listState.isLoadingMore,
                        hasMore = listState.hasMore,
                        errorMessage = listState.errorMessage
                    )
                }
            }
        }
    }

    fun loadInitial(param: HobbyAlbumListNavParam) {
        if (currentNodeId == param.nodeId && currentItemType == param.itemType && _uiState.value.albums.isNotEmpty()) {
            return
        }
        currentNodeId = param.nodeId
        currentItemType = param.itemType
        _uiState.update {
            it.copy(
                title = param.title.ifBlank { "关联相册" },
                errorMessage = null
            )
        }
        loader.loadInitial()
    }

    /**
     * 切换相册排序（最热、最新、最多图）。
     */
    fun selectOrder(order: HobbyAlbumSortOrder) {
        if (_uiState.value.selectedOrder == order && _uiState.value.albums.isNotEmpty()) {
            return
        }
        _uiState.update {
            it.copy(
                selectedOrder = order,
                errorMessage = null
            )
        }
        loader.loadInitial()
    }

    fun refresh() {
        if (currentNodeId <= 0L || _uiState.value.isRefreshing) return
        loader.refresh()
    }

    fun loadMore() {
        if (currentNodeId <= 0L) return
        loader.loadMore()
    }

    fun retry() {
        if (currentNodeId > 0L) {
            loader.retry()
        }
    }
}
