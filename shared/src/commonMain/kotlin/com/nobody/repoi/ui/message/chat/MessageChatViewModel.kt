package com.nobody.repoi.ui.message.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.ChatMessageItem
import com.nobody.repoi.data.repository.MessageRepository
import com.nobody.repoi.ui.navigation.MessageChatNavParam
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MessageChatViewModel(
    private val repository: MessageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessageChatUiState())
    val uiState: StateFlow<MessageChatUiState> = _uiState.asStateFlow()

    private var targetUserId: Long = 0L
    private var pollingJob: Job? = null
    private var maxKnownMessageId: Long = 0L
    private val pageSize = 20

    fun loadInitial(param: MessageChatNavParam) {
        if (targetUserId == param.targetUserId && _uiState.value.messages.isNotEmpty()) {
            return
        }
        targetUserId = param.targetUserId

        _uiState.update {
            it.copy(
                targetUserId = param.targetUserId,
                targetUserName = param.targetUserName,
                targetUserHeader = param.targetUserHeader,
                isLoading = true,
                errorMessage = null,
                page = 1,
                hasMore = true
            )
        }

        viewModelScope.launch {
            val result = repository.getChatMessages(fromId = param.targetUserId, page = 1, pageSize = pageSize)
            result.onSuccess { payload ->
                val list = payload.messages.sortedBy { it.date }
                maxKnownMessageId = list.maxOfOrNull { it.actualMessageId } ?: 0L
                val isBlocked = payload.direction == 1 || payload.direction == 3

                _uiState.update {
                    it.copy(
                        messages = list,
                        isLoading = false,
                        canSendState = payload.canSendState,
                        isBlocked = isBlocked,
                        hasMore = list.size >= pageSize
                    )
                }
                startPolling()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "加载聊天记录失败"
                    )
                }
            }
        }
    }

    fun startPolling() {
        if (pollingJob?.isActive == true || targetUserId <= 0L) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(3000L)
                val latestResult = repository.getLatestMsgId(userId = targetUserId)
                latestResult.onSuccess { latestId ->
                    if (latestId > maxKnownMessageId) {

                        val refreshResult = repository.getChatMessages(fromId = targetUserId, page = 1, pageSize = pageSize)
                        refreshResult.onSuccess { payload ->
                            val updatedList = payload.messages
                            maxKnownMessageId = updatedList.maxOfOrNull { it.actualMessageId } ?: maxKnownMessageId
                            _uiState.update { current ->
                                current.copy(messages = mergeMessages(current.messages, updatedList))
                            }
                        }
                    }
                }
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun mergeMessages(
        currentList: List<ChatMessageItem>,
        incomingList: List<ChatMessageItem>
    ): List<ChatMessageItem> {
        val messageMap = currentList.associateBy { it.actualMessageId }.toMutableMap()
        incomingList.forEach { messageMap[it.actualMessageId] = it }
        return messageMap.values.toList().sortedBy { it.date }
    }

    fun loadMoreHistory() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore || state.isLoading) return

        val nextPage = state.page + 1
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val result = repository.getChatMessages(fromId = targetUserId, page = nextPage, pageSize = pageSize)
            result.onSuccess { payload ->
                val oldList = payload.messages
                _uiState.update { current ->
                    current.copy(
                        messages = mergeMessages(current.messages, oldList),
                        isLoadingMore = false,
                        page = nextPage,
                        hasMore = oldList.size >= pageSize
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        actionMessage = err.message ?: "加载更多历史失败"
                    )
                }
            }
        }
    }

    fun onInputContentChange(content: String) {
        _uiState.update { it.copy(inputContent = content) }
    }

    fun toggleEmojiPanel() {
        _uiState.update { it.copy(isEmojiPanelVisible = !it.isEmojiPanelVisible) }
    }

    fun hideEmojiPanel() {
        _uiState.update { it.copy(isEmojiPanelVisible = false) }
    }

    fun appendEmoji(emoji: String) {
        _uiState.update { it.copy(inputContent = it.inputContent + emoji) }
    }

    fun sendTextMessage() {
        val content = _uiState.value.inputContent.trim()
        if (content.isEmpty() || targetUserId <= 0L) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            val result = repository.sendTextMessage(toId = targetUserId, content = content)
            result.onSuccess {
                _uiState.update { it.copy(inputContent = "", isSending = false) }

                val refreshResult = repository.getChatMessages(fromId = targetUserId, page = 1, pageSize = pageSize)
                refreshResult.onSuccess { payload ->
                    _uiState.update { current ->
                        current.copy(messages = mergeMessages(current.messages, payload.messages))
                    }
                    maxKnownMessageId = payload.messages.maxOfOrNull { it.actualMessageId } ?: maxKnownMessageId
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSending = false,
                        actionMessage = err.message ?: "发送失败"
                    )
                }
            }
        }
    }

    fun sendPicture(fileName: String, fileBytes: ByteArray, mimeType: String = "image/jpeg") {
        if (targetUserId <= 0L) return

        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPicture = true) }
            val result = repository.uploadChatMessagePicture(
                toId = targetUserId,
                fileName = fileName,
                fileBytes = fileBytes,
                mimeType = mimeType,
            )
            result.onSuccess {
                _uiState.update { it.copy(isUploadingPicture = false, actionMessage = "图片已发送") }
                val refreshResult = repository.getChatMessages(fromId = targetUserId, page = 1, pageSize = pageSize)
                refreshResult.onSuccess { payload ->
                    _uiState.update { current ->
                        current.copy(messages = mergeMessages(current.messages, payload.messages))
                    }
                    maxKnownMessageId = payload.messages.maxOfOrNull { it.actualMessageId } ?: maxKnownMessageId
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isUploadingPicture = false,
                        actionMessage = err.message ?: "图片发送失败"
                    )
                }
            }
        }
    }

    fun withdrawMessage(message: ChatMessageItem) {
        viewModelScope.launch {
            val result = repository.withdrawMessage(messageId = message.actualMessageId)
            result.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        messages = current.messages.filterNot { it.actualMessageId == message.actualMessageId },
                        actionMessage = "消息已撤回"
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(actionMessage = err.message ?: "撤回失败") }
            }
        }
    }

    fun blockUser() {
        viewModelScope.launch {
            val result = repository.updateUserBlock(userId = targetUserId, blockState = 1)
            result.onSuccess {
                _uiState.update { it.copy(isBlocked = true, actionMessage = "已将该用户加入黑名单") }
            }.onFailure { err ->
                _uiState.update { it.copy(actionMessage = err.message ?: "操作失败") }
            }
        }
    }

    fun unblockUser() {
        viewModelScope.launch {
            val result = repository.updateUserBlock(userId = targetUserId, blockState = 0)
            result.onSuccess {
                _uiState.update { it.copy(isBlocked = false, actionMessage = "已解除黑名单屏蔽") }
            }.onFailure { err ->
                _uiState.update { it.copy(actionMessage = err.message ?: "操作失败") }
            }
        }
    }

    fun clearActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        stopPolling()
    }
}
