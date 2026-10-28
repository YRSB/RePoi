package com.nobody.poishelf.ui.user.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.poishelf.data.remote.model.UserHistoryItem
import com.nobody.poishelf.data.repository.UserRepository
import com.nobody.poishelf.util.paging.PagedListLoader
import com.nobody.poishelf.util.paging.pagedListLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UserHistoryViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserHistoryUiState())
    val uiState: StateFlow<UserHistoryUiState> = _uiState.asStateFlow()

    private val hobbyLoader: PagedListLoader<UserHistoryItem> = pagedListLoader(
        scope = viewModelScope,
        pageSize = 10,
        keySelector = { it.id }
    ) { page, _ ->
        userRepository.getUserHistoryList(UserHistoryCategory.HOBBY.itemType, page)
    }

    private val albumLoader: PagedListLoader<UserHistoryItem> = pagedListLoader(
        scope = viewModelScope,
        pageSize = 10,
        keySelector = { it.id }
    ) { page, _ ->
        userRepository.getUserHistoryList(UserHistoryCategory.ALBUM.itemType, page)
    }

    init {
        viewModelScope.launch {
            hobbyLoader.state.collect { listState ->
                updateTabState(UserHistoryCategory.HOBBY) { tab ->
                    tab.copy(
                        items = listState.items,
                        groups = groupHistoryItemsByDay(listState.items),
                        page = listState.page,
                        isLoading = listState.isLoading,
                        isRefreshing = listState.isRefreshing,
                        isLoadingMore = listState.isLoadingMore,
                        hasMore = listState.hasMore,
                        errorMessage = listState.errorMessage,
                        loadMoreError = listState.loadMoreError
                    )
                }
            }
        }
        viewModelScope.launch {
            albumLoader.state.collect { listState ->
                updateTabState(UserHistoryCategory.ALBUM) { tab ->
                    tab.copy(
                        items = listState.items,
                        groups = groupHistoryItemsByDay(listState.items),
                        page = listState.page,
                        isLoading = listState.isLoading,
                        isRefreshing = listState.isRefreshing,
                        isLoadingMore = listState.isLoadingMore,
                        hasMore = listState.hasMore,
                        errorMessage = listState.errorMessage,
                        loadMoreError = listState.loadMoreError
                    )
                }
            }
        }
        hobbyLoader.loadInitial()
    }

    /**
     * 切换历史分类 Tab。
     */
    fun selectCategory(category: UserHistoryCategory) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update { it.copy(selectedCategory = category) }

        val targetState = if (category == UserHistoryCategory.HOBBY) {
            _uiState.value.hobbyState
        } else {
            _uiState.value.albumState
        }

        if (targetState.items.isEmpty() && !targetState.isLoading) {
            val loader = if (category == UserHistoryCategory.HOBBY) hobbyLoader else albumLoader
            loader.loadInitial()
        }
    }

    /**
     * 下拉刷新当前或指定分类。
     */
    fun refresh(category: UserHistoryCategory = _uiState.value.selectedCategory) {
        val loader = if (category == UserHistoryCategory.HOBBY) hobbyLoader else albumLoader
        loader.refresh()
    }

    /**
     * 加载更多下一页历史数据。
     */
    fun loadMore(category: UserHistoryCategory = _uiState.value.selectedCategory) {
        val loader = if (category == UserHistoryCategory.HOBBY) hobbyLoader else albumLoader
        loader.loadMore()
    }

    /**
     * 进入或退出多选管理模式。
     */
    fun toggleManageMode(enable: Boolean? = null) {
        _uiState.update { current ->
            val nextMode = enable ?: !current.isManageMode
            current.copy(
                isManageMode = nextMode,
                selectedIds = if (nextMode) current.selectedIds else emptySet()
            )
        }
    }

    /**
     * 勾选或反选单个历史项。
     */
    fun toggleSelectItem(id: Long) {
        _uiState.update { current ->
            val newSelected = current.selectedIds.toMutableSet()
            if (newSelected.contains(id)) {
                newSelected.remove(id)
            } else {
                newSelected.add(id)
            }
            current.copy(
                isManageMode = true,
                selectedIds = newSelected
            )
        }
    }

    /**
     * 全选或取消全选当前 Tab 的所有历史项。
     */
    fun toggleSelectAll() {
        _uiState.update { current ->
            val currentItems = current.currentTabState.items
            val currentIds = currentItems.map { it.id }.toSet()
            val newSelected = if (current.isAllSelected) {
                current.selectedIds - currentIds
            } else {
                current.selectedIds + currentIds
            }
            current.copy(selectedIds = newSelected)
        }
    }

    /**
     * 执行批量删除选中的历史足迹。
     */
    fun deleteSelected() {
        val idsToDelete = _uiState.value.selectedIds.toList()
        if (idsToDelete.isEmpty()) return

        _uiState.update { it.copy(isDeleting = true) }

        viewModelScope.launch {
            val result = userRepository.deleteUserHistory(idsToDelete)
            result.onSuccess {
                val deletedSet = idsToDelete.toSet()
                hobbyLoader.removeItem { it.id in deletedSet }
                albumLoader.removeItem { it.id in deletedSet }
                _uiState.update { current ->
                    current.copy(
                        selectedIds = emptySet(),
                        isManageMode = false,
                        isDeleting = false,
                        userMessage = "已成功删除 ${idsToDelete.size} 条浏览历史"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        userMessage = err.message ?: "删除失败，请稍后重试"
                    )
                }
            }
        }
    }

    /**
     * 消费一次性操作反馈提示。
     */
    fun consumeUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun updateTabState(category: UserHistoryCategory, transform: (HistoryTabState) -> HistoryTabState) {
        _uiState.update { current ->
            if (category == UserHistoryCategory.HOBBY) {
                current.copy(hobbyState = transform(current.hobbyState))
            } else {
                current.copy(albumState = transform(current.albumState))
            }
        }
    }
}
