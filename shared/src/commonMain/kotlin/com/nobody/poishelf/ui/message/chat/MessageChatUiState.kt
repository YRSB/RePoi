package com.nobody.poishelf.ui.message.chat

import com.nobody.poishelf.data.remote.model.ChatMessageItem

/**
 * 1v1 私信聊天 UI 状态。
 */
data class MessageChatUiState(
    val targetUserId: Long = 0L,
    val targetUserName: String = "",
    val targetUserHeader: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val messages: List<ChatMessageItem> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val inputContent: String = "",
    val isSending: Boolean = false,
    val isUploadingPicture: Boolean = false,
    val isEmojiPanelVisible: Boolean = false,
    val canSendState: Int = 1,
    val isBlocked: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)
