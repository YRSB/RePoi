package com.nobody.poishelf.ui.message.block

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.poishelf.data.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nobody.poishelf.data.remote.model.BlockItem

/**
 * 黑名单管理 ViewModel。
 */
class MessageBlockViewModel(
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessageBlockUiState())
    val uiState: StateFlow<MessageBlockUiState> = _uiState.asStateFlow()

    private val pageSize = 50

    init {
        loadData(refresh = false)
    }

    /**
     * 加载黑名单列表。
     */
    fun loadData(refresh: Boolean = false) {
        viewModelScope.launch {
            if (refresh) {
                _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            } else {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }

            val targetPage = 1
            messageRepository.getBlockList(page = targetPage, pageSize = pageSize)
                .onSuccess { list ->
                    _uiState.update { state ->
                        state.copy(
                            blockList = list,
                            isLoading = false,
                            isRefreshing = false,
                            currentPage = targetPage,
                            hasMore = list.size >= pageSize
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = throwable.message ?: "获取黑名单列表失败"
                        )
                    }
                }
        }
    }

    /**
     * 加载下一页黑名单。
     */
    fun loadMore() {
        val currentState = _uiState.value
        if (currentState.isLoading || currentState.isRefreshing || !currentState.hasMore) return

        viewModelScope.launch {
            val nextPage = currentState.currentPage + 1
            _uiState.update { it.copy(isLoading = true) }

            messageRepository.getBlockList(page = nextPage, pageSize = pageSize)
                .onSuccess { list ->
                    _uiState.update { state ->
                        val combined = (state.blockList + list).distinctBy { it.id }
                        state.copy(
                            blockList = combined,
                            isLoading = false,
                            currentPage = nextPage,
                            hasMore = list.size >= pageSize
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "加载更多黑名单失败"
                        )
                    }
                }
        }
    }

    /**
     * 弹出解除屏蔽确认弹窗。
     */
    fun confirmUnblock(item: BlockItem) {
        _uiState.update { it.copy(unblockTarget = item) }
    }

    /**
     * 关闭解除屏蔽确认弹窗。
     */
    fun dismissUnblockDialog() {
        _uiState.update { it.copy(unblockTarget = null) }
    }

    /**
     * 执行解除屏蔽操作。
     */
    fun executeUnblock() {
        val target = _uiState.value.unblockTarget ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(unblockTarget = null) }
            messageRepository.updateUserBlock(userId = target.id, blockState = 0)
                .onSuccess {
                    _uiState.update { state ->
                        val updatedList = state.blockList.filterNot { it.id == target.id }
                        state.copy(
                            blockList = updatedList,
                            actionMessage = "已解除对「${target.name.ifBlank { "该用户" }}」的屏蔽"
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            errorMessage = throwable.message ?: "解除屏蔽失败"
                        )
                    }
                }
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
