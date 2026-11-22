package com.nobody.repoi.ui.picture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.PicItem
import com.nobody.repoi.data.repository.PictureListRepository
import com.nobody.repoi.ui.navigation.PictureListNavParam
import com.nobody.repoi.util.paging.PagedListLoader
import com.nobody.repoi.util.paging.pagedListLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PictureListUiState(
    val items: List<PicItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isAppending: Boolean = false,
    val hasMore: Boolean = true,
    val currentPage: Int = 1,
    val errorMessage: String? = null,
    val loadMoreError: String? = null
)

class PictureListViewModel(
    private val repository: PictureListRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PictureListUiState())
    val uiState: StateFlow<PictureListUiState> = _uiState.asStateFlow()

    private var currentParam: PictureListNavParam? = null
    private val pageSize = 20

    private val loader: PagedListLoader<PicItem> = pagedListLoader(
        scope = viewModelScope,
        pageSize = pageSize,
        keySelector = { if (it.id == 0L) null else it.id }
    ) { page, size ->
        val param = currentParam ?: return@pagedListLoader Result.success(emptyList())
        repository.getPictureList(
            itemId = param.itemId,
            itemType = param.itemType,
            subType = param.subType,
            page = page,
            pageSize = size,
            order = "top",
            nodeId = param.nodeId
        )
    }

    init {
        viewModelScope.launch {
            loader.state.collect { listState ->
                _uiState.update { current ->
                    current.copy(
                        items = listState.items,
                        isLoading = listState.isLoading,
                        isRefreshing = listState.isRefreshing,
                        isAppending = listState.isLoadingMore,
                        hasMore = listState.hasMore,
                        currentPage = listState.page,
                        errorMessage = listState.errorMessage,
                        loadMoreError = listState.loadMoreError
                    )
                }
            }
        }
    }

    fun loadInitial(param: PictureListNavParam) {
        if (_uiState.value.items.isNotEmpty() || _uiState.value.isLoading) return
        currentParam = param
        loader.loadInitial()
    }

    fun refresh(param: PictureListNavParam) {
        if (_uiState.value.isRefreshing) return
        currentParam = param
        loader.refresh()
    }

    fun retry(param: PictureListNavParam) {
        currentParam = param
        loader.retry()
    }

    fun loadMore(param: PictureListNavParam, isSilent: Boolean = false) {
        currentParam = param
        loader.loadMore(isSilent = isSilent)
    }

    fun loadMoreSilently(param: PictureListNavParam) {
        loadMore(param, isSilent = true)
    }
}
