package com.nobody.repoi.ui.user.creation

import com.nobody.repoi.data.remote.model.UserCollectAlbumItem
import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.data.remote.model.PicItem

enum class UserCreationCategory(val key: Int, val label: String) {
    ALBUM(70000, "相册"),
    PICTURE(90000, "图片");

    companion object {
        fun fromKey(key: Int): UserCreationCategory = entries.firstOrNull { it.key == key } ?: ALBUM
    }
}

enum class UserCreationOrder(val key: String, val label: String) {
    ADD_TIME("addTime", "最新发布"),
    PRAISE_COUNT("praiseCount", "最多喜欢");

    companion object {
        fun fromKey(key: String): UserCreationOrder = entries.firstOrNull { it.key == key } ?: ADD_TIME
    }
}

data class CreationTabState<T>(
    val items: List<T> = emptyList(),
    val page: Int = 1,
    val order: String = UserCreationOrder.ADD_TIME.key,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isPaging: Boolean = false,
    val hasMore: Boolean = true,
    val errorMessage: String? = null
)

data class UserCreationUiState(
    val userId: Long = 0L,
    val isSelf: Boolean = false,
    val isInitialized: Boolean = false,
    val selectedCategory: UserCreationCategory = UserCreationCategory.ALBUM,
    val albumState: CreationTabState<UserCollectAlbumItem> = CreationTabState(),
    val picState: CreationTabState<UserCollectPicItem> = CreationTabState()
)
