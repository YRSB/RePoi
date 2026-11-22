package com.nobody.repoi.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data class CollectEditNavParam(
    val itemId: Long,
    val nodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null,
)

@Serializable
data class UserCollectNavParam(
    val userId: Long,
    val initialCollectState: String? = null,
    val initialOrder: String? = null,
    val initialCategory: Int? = null,

    val initialSubCategoryId: Int? = null,

    val initialTag: String? = null,

    val albumType: String = "fav",
)

@Serializable
data class UserCreationNavParam(
    val userId: Long,
    val initialCategory: Int = 70000,
)

@Serializable
data class FriendListNavParam(
    val userId: Long,
    val initialType: Int = 0,
)

@Serializable
data class UserInfoNavParam(
    val userId: Long = 0L,
)

@Serializable
data class VendorActionsNavParam(
    val companyNodeId: Long,
    val companyName: String? = null,
)

@Serializable
data class CharacterNavParam(
    val characterId: Long,
    val characterNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null,
)

@Serializable
data class CharacterAlbumListNavParam(
    val characterId: Long,
    val title: String = "相关相册",
)

@Serializable
data class HobbyAlbumListNavParam(
    val nodeId: Long,
    val itemType: String = "hobby",
    val title: String = "关联相册",
)

@Serializable
data class CharacterWorksListNavParam(
    val characterId: Long,
    val title: String = "登场作品",
)

@Serializable
data class PictureGalleryNavParam(
    val userId: Long,
    val picId: Long,
    val initialIndex: Int = 0,
    val order: String? = null,
)

@Serializable
data class PictureListNavParam(
    val itemId: Long,
    val itemType: String = "hobby",
    val subType: String? = "official",
    val title: String = "图片列表",
    val nodeId: Long? = null,
)

@Serializable
data class MessageNoticeNavParam(
    val type: String,
    val typeNumber: Int = 1,
    val title: String = "消息通知",
)

@Serializable
data class MessageChatNavParam(
    val targetUserId: Long,
    val targetUserName: String = "",
    val targetUserHeader: String? = null,
    val resaleId: Long? = null,
)

@Serializable
data class ImageViewerNavParam(
    val images: List<String> = emptyList(),
    val initialIndex: Int = 0,
)
