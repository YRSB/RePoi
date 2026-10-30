package com.nobody.repoi.ui.message.center

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.MessageContactItem
import com.nobody.repoi.data.remote.model.PushCountCategory
import com.nobody.repoi.data.repository.MessageRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 消息中心主页状态管理 ViewModel。
 */
class MessageCenterViewModel(
    private val repository: MessageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessageCenterUiState())
    val uiState: StateFlow<MessageCenterUiState> = _uiState.asStateFlow()

    init {
        loadData(initial = true)
    }

    /**
     * 加载未读消息统计与私信联系人列表。
     */
    fun loadData(initial: Boolean = false, refresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = initial,
                    isRefreshing = refresh,
                    errorMessage = null
                )
            }

            coroutineScope {
                val countDeferred = async { repository.getUnreadPushCount() }
                val contactsDeferred = async { repository.getMessageContacts(page = 1) }

                val countResult = countDeferred.await()
                val contactsResult = contactsDeferred.await()

                val newCounts = countResult.getOrElse { _uiState.value.unreadCounts }
                val newContacts = contactsResult.getOrElse { emptyList() }

                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        isRefreshing = false,
                        unreadCounts = newCounts,
                        contacts = newContacts,
                        page = 1,
                        hasMore = newContacts.size >= 20,
                        errorMessage = if (countResult.isFailure && contactsResult.isFailure) {
                            contactsResult.exceptionOrNull()?.message ?: "加载消息中心失败"
                        } else null
                    )
                }
            }
        }
    }

    /**
     * 分页加载更多私信联系人。
     */
    fun loadMoreContacts() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore || state.isLoading || state.isRefreshing) return

        val nextPage = state.page + 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val result = repository.getMessageContacts(page = nextPage)
            result.onSuccess { list ->
                _uiState.update { current ->
                    current.copy(
                        isLoadingMore = false,
                        contacts = current.contacts + list,
                        page = nextPage,
                        hasMore = list.size >= 20
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        actionMessage = err.message ?: "加载更多失败"
                    )
                }
            }
        }
    }

    /**
     * 切换联系人置顶状态。
     */
    fun toggleContactTop(item: MessageContactItem) {
        val nextState = if (item.isTop) 0 else 1
        viewModelScope.launch {
            val result = repository.updateContactTop(id = item.id, state = nextState)
            result.onSuccess {
                _uiState.update { current ->
                    val updatedList = current.contacts.map { contact ->
                        if (contact.id == item.id) {
                            contact.copy(topTime = if (nextState == 1) "top" else null)
                        } else {
                            contact
                        }
                    }.sortedWith(compareByDescending<MessageContactItem> { it.isTop })
                    current.copy(
                        contacts = updatedList,
                        actionMessage = if (nextState == 1) "已置顶会话" else "已取消置顶"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(actionMessage = err.message ?: "更新置顶失败") }
            }
        }
    }

    /**
     * 删除私信会话。
     */
    fun deleteContact(item: MessageContactItem) {
        viewModelScope.launch {
            val result = repository.deleteContact(id = item.id, userNodeId = item.otherId)
            result.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        contacts = current.contacts.filterNot { it.id == item.id },
                        actionMessage = "会话已删除"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(actionMessage = err.message ?: "删除会话失败") }
            }
        }
    }

    /**
     * 一键全部已读。
     */
    fun markAllRead() {
        viewModelScope.launch {
            val result = repository.markPushRead(type = 0)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        unreadCounts = PushCountCategory(),
                        actionMessage = "所有通知已标为已读"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(actionMessage = err.message ?: "操作失败") }
            }
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }
}
