package com.nobody.poishelf.ui.message.notice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.poishelf.data.remote.model.PushMessageItem
import com.nobody.poishelf.data.repository.MessageRepository
import com.nobody.poishelf.ui.navigation.MessageNoticeNavParam
import com.nobody.poishelf.util.paging.PagedListLoader
import com.nobody.poishelf.util.paging.pagedListLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 分类通知列表 ViewModel。
 *
 * 通过 [PagedListLoader] 委托管理分页加载、在途取消与列表项局部变动。
 */
class MessageNoticeViewModel(
    private val repository: MessageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessageNoticeUiState())
    val uiState: StateFlow<MessageNoticeUiState> = _uiState.asStateFlow()

    private var currentType: String = ""
    private var currentTypeNumber: Int = 1
    private val pageSize = 20

    private val loader: PagedListLoader<PushMessageItem> = pagedListLoader(
        scope = viewModelScope,
        pageSize = pageSize,
        keySelector = { it.id }
    ) { page, size ->
        repository.getPushMessages(
            type = currentType,
            page = page,
            pageSize = size
        )
    }

    init {
        viewModelScope.launch {
            loader.state.collect { listState ->
                _uiState.update { current ->
                    current.copy(
                        messages = listState.items,
                        isLoading = listState.isLoading,
                        isRefreshing = listState.isRefreshing,
                        isLoadingMore = listState.isLoadingMore,
                        hasMore = listState.hasMore,
                        page = listState.page,
                        errorMessage = listState.errorMessage,
                        actionMessage = listState.loadMoreError ?: current.actionMessage
                    )
                }
            }
        }
    }

    fun loadInitial(param: MessageNoticeNavParam) {
        if (currentType == param.type && _uiState.value.messages.isNotEmpty()) {
            return
        }
        currentType = param.type
        currentTypeNumber = param.typeNumber

        _uiState.update {
            it.copy(
                type = param.type,
                typeNumber = param.typeNumber,
                title = param.title,
                isManagerMode = false,
                selectedIds = emptySet()
            )
        }
        loader.loadInitial()
    }

    fun refresh() {
        if (currentType.isEmpty()) return
        _uiState.update { it.copy(selectedIds = emptySet()) }
        loader.refresh()
    }

    fun loadMore() {
        loader.loadMore()
    }

    fun markItemRead(item: PushMessageItem) {
        if (!item.isUnread) return
        viewModelScope.launch {
            repository.markPushRead(type = currentTypeNumber, messageId = item.id)
            loader.updateItem({ it.id == item.id }) { it.copy(state = 1) }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            val result = repository.markPushRead(type = currentTypeNumber)
            result.onSuccess {
                loader.mutateItems { items -> items.map { it.copy(state = 1) } }
                _uiState.update { current ->
                    current.copy(actionMessage = "已标为全部已读")
                }
            }.onFailure { err ->
                _uiState.update { it.copy(actionMessage = err.message ?: "操作失败") }
            }
        }
    }

    fun deleteItem(item: PushMessageItem) {
        viewModelScope.launch {
            val result = repository.deletePushMessage(messageId = item.id, pushType = currentTypeNumber)
            result.onSuccess {
                loader.removeItem { it.id == item.id }
                _uiState.update { current ->
                    current.copy(
                        selectedIds = current.selectedIds - item.id,
                        actionMessage = "通知已删除"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(actionMessage = err.message ?: "删除失败") }
            }
        }
    }

    fun toggleManagerMode() {
        _uiState.update {
            it.copy(
                isManagerMode = !it.isManagerMode,
                selectedIds = emptySet()
            )
        }
    }

    fun toggleSelectId(id: Long) {
        _uiState.update { current ->
            val newSelection = if (current.selectedIds.contains(id)) {
                current.selectedIds - id
            } else {
                current.selectedIds + id
            }
            current.copy(selectedIds = newSelection)
        }
    }

    fun toggleSelectAll() {
        _uiState.update { current ->
            if (current.isAllSelected) {
                current.copy(selectedIds = emptySet())
            } else {
                current.copy(selectedIds = current.messages.map { it.id }.toSet())
            }
        }
    }

    /**
     * 反选当前已加载的所有条目。
     */
    fun invertSelection() {
        _uiState.update { current ->
            val allIds = current.messages.map { it.id }.toSet()
            val inverted = allIds - current.selectedIds
            current.copy(selectedIds = inverted)
        }
    }

    fun batchDeleteSelected() {
        val state = _uiState.value
        val idsToDelete = state.selectedIds.toList()
        if (idsToDelete.isEmpty()) return

        viewModelScope.launch {
            val isAll = state.isAllSelected
            val unreadSelectedCount = state.messages.filter { state.selectedIds.contains(it.id) && it.isUnread }.size
            val result = repository.batchDeletePushMessages(
                pushType = currentTypeNumber,
                isAll = isAll,
                ids = if (isAll) null else idsToDelete,
                readCount = unreadSelectedCount
            )
            result.onSuccess {
                val deleteSet = idsToDelete.toSet()
                loader.removeItem { it.id in deleteSet }
                _uiState.update { current ->
                    current.copy(
                        selectedIds = emptySet(),
                        isManagerMode = false,
                        actionMessage = "已成功删除 ${idsToDelete.size} 条通知"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(actionMessage = err.message ?: "批量删除失败") }
            }
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }
}
