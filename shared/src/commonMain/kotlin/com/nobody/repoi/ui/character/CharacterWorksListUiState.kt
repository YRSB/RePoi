package com.nobody.repoi.ui.character

import androidx.compose.runtime.Immutable
import com.nobody.repoi.data.remote.model.CharacterWorksItem

/**
 * 角色登场作品列表页 UI 状态。
 */
@Immutable
data class CharacterWorksListUiState(
    val title: String = "登场作品",
    val works: List<CharacterWorksItem> = emptyList(),
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val page: Int = 1,
    val errorMessage: String? = null
)
