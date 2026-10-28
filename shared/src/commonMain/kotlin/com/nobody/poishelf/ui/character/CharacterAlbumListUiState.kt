package com.nobody.poishelf.ui.character

import androidx.compose.runtime.Immutable
import com.nobody.poishelf.data.remote.model.AlbumData

/**
 * 角色相关相册列表页 UI 状态。
 */
@Immutable
data class CharacterAlbumListUiState(
    val title: String = "相关相册",
    val albums: List<AlbumData> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val page: Int = 1,
    val errorMessage: String? = null
)
