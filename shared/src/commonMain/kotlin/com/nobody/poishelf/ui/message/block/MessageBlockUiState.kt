package com.nobody.poishelf.ui.message.block

import com.nobody.poishelf.data.remote.model.BlockItem

/**
 * 黑名单管理页面 UI 状态。
 */
data class MessageBlockUiState(
    val blockList: List<BlockItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMore: Boolean = false,
    val currentPage: Int = 1,
    val actionMessage: String? = null,
    val errorMessage: String? = null,
    val unblockTarget: BlockItem? = null
)
