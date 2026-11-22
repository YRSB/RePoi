package com.nobody.repoi.ui.message.block

import com.nobody.repoi.data.remote.model.BlockItem

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
