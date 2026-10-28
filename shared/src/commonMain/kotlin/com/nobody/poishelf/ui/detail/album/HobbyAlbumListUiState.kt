package com.nobody.poishelf.ui.detail.album

import androidx.compose.runtime.Immutable
import com.nobody.poishelf.data.remote.model.HobbyAlbumItem

/**
 * 手办相关相册排序模式。
 */
enum class HobbyAlbumSortOrder(val title: String, val orderKey: String) {
    HOT("最热", "hits"),
    NEW("最新", "addTime"),
    MOST_PICS("最多图", "picCount")
}

/**
 * 手办相关相册列表页 UI 状态。
 */
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
