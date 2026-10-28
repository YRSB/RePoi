package com.nobody.poishelf.ui.message.center

import com.nobody.poishelf.data.remote.model.MessageContactItem
import com.nobody.poishelf.data.remote.model.PushCountCategory

/**
 * 消息中心主页 UI 状态。
 */
data class MessageCenterUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val unreadCounts: PushCountCategory = PushCountCategory(),
    val contacts: List<MessageContactItem> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null
)
