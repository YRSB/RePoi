package com.nobody.poishelf.ui.message.notice

import com.nobody.poishelf.data.remote.model.PushMessageItem

/**
 * 分类通知列表 UI 状态。
 */
data class MessageNoticeUiState(
    val type: String = "system",
    val typeNumber: Int = 1,
    val title: String = "消息通知",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val messages: List<PushMessageItem> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isManagerMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val errorMessage: String? = null,
    val actionMessage: String? = null
) {
    val selectedCount: Int
        get() = selectedIds.size

    val isAllSelected: Boolean
        get() = messages.isNotEmpty() && selectedIds.size == messages.size
}
