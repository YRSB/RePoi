package com.nobody.repoi.ui.detail.album

import androidx.compose.runtime.Immutable
import com.nobody.repoi.data.remote.model.HobbyAlbumItem

enum class HobbyAlbumSortOrder(val title: String, val orderKey: String) {
    HOT("最热", "hits"),
    NEW("最新", "addTime"),
    MOST_PICS("最多图", "picCount")
}

@Immutable
data class HobbyAlbumListUiState(
    val title: String = "关联相册",
    val albums: List<HobbyAlbumItem> = emptyList(),
    val selectedOrder: HobbyAlbumSortOrder = HobbyAlbumSortOrder.HOT,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val page: Int = 1,
    val errorMessage: String? = null
)
