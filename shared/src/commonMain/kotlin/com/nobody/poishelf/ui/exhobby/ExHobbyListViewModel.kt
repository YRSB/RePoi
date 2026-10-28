package com.nobody.poishelf.ui.exhobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.poishelf.data.repository.ExHobbyRepository
import com.nobody.poishelf.util.paging.PageChunk
import com.nobody.poishelf.util.paging.PagedListLoader
import com.nobody.poishelf.util.paging.pagedChunkLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ExHobby 扩展图库二级列表页面 UI 状态。
 */
data class ExHobbyListUiState(
    val items: List<ExHobbyImageItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isAppending: Boolean = false,
    val hasMore: Boolean = true,
    val currentPage: Int = 1,
    val errorMessage: String? = null,
    val loadMoreError: String? = null
)

class ExHobbyListViewModel(
    private val repository: ExHobbyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExHobbyListUiState())
    val uiState: StateFlow<ExHobbyListUiState> = _uiState.asStateFlow()

    private var currentItemId: Long = 0L

    private val loader: PagedListLoader<ExHobbyImageItem> = pagedChunkLoader(
        scope = viewModelScope,
        keySelector = { it.path }
    ) { page, _ ->
        val itemId = currentItemId
        if (itemId <= 0L) return@pagedChunkLoader Result.success(PageChunk(emptyList(), false))
        repository.getPictures(id = itemId, page = page).fold(
            onSuccess = { list ->
                Result.success(PageChunk(items = list, hasMore = list.isNotEmpty()))
            },
            onFailure = {
                Result.failure(Exception("加载失败，请重试"))
            }
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

    /**
     * 初始加载第 1 页。
     */
    fun loadInitial(itemId: Long) {
        if (_uiState.value.items.isNotEmpty() || _uiState.value.isLoading) return
        currentItemId = itemId
        loader.loadInitial()
    }

    /**
     * 下拉刷新重置列表。
     */
    fun refresh(itemId: Long) {
        if (_uiState.value.isRefreshing) return
        currentItemId = itemId
        repository.clearCache(itemId)
        loader.refresh()
    }

    /**
     * 错误重试。
     */
    fun retry(itemId: Long) {
        currentItemId = itemId
        loader.retry()
    }

    /**
     * 触底追加或全屏画廊静默追加下一页。
     */
    fun loadMore(itemId: Long, isSilent: Boolean = false) {
        val state = _uiState.value
        if (!state.hasMore || state.isAppending || state.isLoading || state.isRefreshing) return

        currentItemId = itemId
        loader.loadMore(isSilent = isSilent)
    }

    /**
     * 全屏画廊翻页临近末尾时静默预加载下一页。
     */
    fun loadMoreSilently(itemId: Long) {
        loadMore(itemId, isSilent = true)
    }
}
