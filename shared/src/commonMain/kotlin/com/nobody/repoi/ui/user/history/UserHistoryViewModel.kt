package com.nobody.repoi.ui.user.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.UserHistoryItem
import com.nobody.repoi.data.repository.CoverFreshnessRepository
import com.nobody.repoi.data.repository.UserHistoryRepository
import com.nobody.repoi.util.paging.PagedListLoader
import com.nobody.repoi.util.paging.pagedListLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class UserHistoryViewModel(
    private val userHistoryRepository: UserHistoryRepository,
    private val coverFreshnessRepository: CoverFreshnessRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserHistoryUiState())
    val uiState: StateFlow<UserHistoryUiState> = _uiState.asStateFlow()

    private val coverRepairGate = Semaphore(COVER_REPAIR_CONCURRENCY)

    private val coverRepairAttempts = mutableMapOf<String, Int>()

    private val hobbyLoader: PagedListLoader<UserHistoryItem> = pagedListLoader(
        scope = viewModelScope,
        pageSize = 10,
        keySelector = { it.id }
    ) { page, _ ->
        userHistoryRepository.getUserHistoryList(UserHistoryCategory.HOBBY.itemType, page)
    }

    private val albumLoader: PagedListLoader<UserHistoryItem> = pagedListLoader(
        scope = viewModelScope,
        pageSize = 10,
        keySelector = { it.id }
    ) { page, _ ->
        userHistoryRepository.getUserHistoryList(UserHistoryCategory.ALBUM.itemType, page)
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

    fun refresh(category: UserHistoryCategory = _uiState.value.selectedCategory) {
        val loader = if (category == UserHistoryCategory.HOBBY) hobbyLoader else albumLoader
        loader.refresh()
    }

    fun loadMore(category: UserHistoryCategory = _uiState.value.selectedCategory) {
        val loader = if (category == UserHistoryCategory.HOBBY) hobbyLoader else albumLoader
        loader.loadMore()
    }

    fun toggleManageMode(enable: Boolean? = null) {
        _uiState.update { current ->
            val nextMode = enable ?: !current.isManageMode
            current.copy(
                isManageMode = nextMode,
                selectedIds = if (nextMode) current.selectedIds else emptySet()
            )
        }
    }

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

    fun deleteSelected() {
        val idsToDelete = _uiState.value.selectedIds.toList()
        if (idsToDelete.isEmpty()) return

        _uiState.update { it.copy(isDeleting = true) }

        viewModelScope.launch {
            val result = userHistoryRepository.deleteUserHistory(idsToDelete)
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

    fun consumeUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun onCoverLoadFailed(rowId: Long, detailId: Long, category: UserHistoryCategory) {
        if (detailId <= 0L) return
        val entityType = category.toCoverEntityType()
        val attemptKey = "${entityType.name}:$detailId"
        val attempts = coverRepairAttempts[attemptKey] ?: 0
        if (attempts >= MAX_COVER_REPAIR_ATTEMPTS) return
        coverRepairAttempts[attemptKey] = attempts + 1

        viewModelScope.launch {
            coverRepairGate.withPermit {

                val freshCover = coverFreshnessRepository
                    .freshCover(detailId, entityType, forceRefresh = true)
                    .getOrNull()
                if (freshCover.isNullOrBlank()) return@withPermit
                val loader = if (category == UserHistoryCategory.HOBBY) hobbyLoader else albumLoader

                loader.updateItem(predicate = { it.id == rowId }) { item ->
                    item.copy(cover = freshCover)
                }

                coverRepairAttempts.remove(attemptKey)
            }
        }
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

    private fun UserHistoryCategory.toCoverEntityType(): CoverFreshnessRepository.EntityType =
        when (this) {
            UserHistoryCategory.HOBBY -> CoverFreshnessRepository.EntityType.HOBBY
            UserHistoryCategory.ALBUM -> CoverFreshnessRepository.EntityType.ALBUM
        }

    private companion object {

        const val COVER_REPAIR_CONCURRENCY = 3

        const val MAX_COVER_REPAIR_ATTEMPTS = 2
    }
}
