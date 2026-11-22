package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.platform.newNavId
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.album.AlbumRankListNavParam
import com.nobody.repoi.ui.comment.CommentDetailNavParam
import com.nobody.repoi.ui.comment.HobbyCommentsNavParam
import com.nobody.repoi.ui.collect.WorksCollectEditNavParam
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.item.list.HobbyListNavParam
import com.nobody.repoi.ui.search.SearchNavParam
import com.nobody.repoi.ui.search.SearchResultNavParam
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam
import kotlinx.serialization.Serializable

sealed interface AppNavKey : NavKey

@Serializable
data object MainNavKey : AppNavKey

@Serializable
data object HomeNavKey : AppNavKey

@Serializable
data object ItemsNavKey : AppNavKey
@Serializable
data object AlbumNavKey : AppNavKey

@Serializable
data object FollowNavKey : AppNavKey

@Serializable
data object ProfileNavKey : AppNavKey

@Serializable
data object ZoneBrowseNavKey : AppNavKey

@Serializable
data class ZoneDetailNavKey(
    val nodeId: Long,
    val initialName: String? = null,
    val initialIcon: String? = null,
    val initialCover: String? = null,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data object ZoneSettingsNavKey : AppNavKey

@Serializable
data class PostDetailNavKey(
    val postId: Long,
    val initialTitle: String? = null,
    val nodeId: Long? = null,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class ResaleDetailNavKey(
    val resaleId: Long,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class ResaleMarketNavKey(
    val hobbyId: Long = 0L,
    val title: String? = null,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class HobbyListNavKey(
    val param: HobbyListNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class CollectEditNavKey(
    val param: CollectEditNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class UserCollectNavKey(
    val param: UserCollectNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class UserCreationNavKey(
    val param: UserCreationNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class UserHistoryNavKey(
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class HobbyDetailNavKey(
    val param: DetailNavParam,
    val id: String = newNavId()
) : AppNavKey {
    constructor(itemId: Long) : this(DetailNavParam(itemId = itemId))
}

@Serializable
data class FriendListNavKey(
    val param: FriendListNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class UserInfoNavKey(
    val param: UserInfoNavParam = UserInfoNavParam(),
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class CommentDetailNavKey(
    val param: CommentDetailNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class HobbyCommentsNavKey(
    val param: HobbyCommentsNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class SearchNavKey(
    val param: SearchNavParam = SearchNavParam(),
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class SearchResultNavKey(
    val param: SearchResultNavParam = SearchResultNavParam(),
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class AlbumDetailNavKey(
    val param: AlbumNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class AlbumRankListNavKey(
    val param: AlbumRankListNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class VendorDetailNavKey(
    val param: VendorNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class VendorActionsNavKey(
    val param: VendorActionsNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class PersonDetailNavKey(
    val param: PersonNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class CharacterNavKey(
    val param: CharacterNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class CharacterAlbumListNavKey(
    val param: CharacterAlbumListNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class HobbyAlbumListNavKey(
    val param: HobbyAlbumListNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class CharacterWorksListNavKey(
    val param: CharacterWorksListNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class WorksCollectEditNavKey(
    val param: WorksCollectEditNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class SeriesDetailNavKey(
    val param: SeriesNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class WorksDetailNavKey(
    val param: WorksNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class PictureListNavKey(
    val param: PictureListNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class PictureGalleryNavKey(
    val param: PictureGalleryNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class ExHobbyListNavKey(
    val itemId: Long,
    val title: String = "ExHobby",
    val id: String = newNavId()
) : AppNavKey

@Serializable
data object LoginNavKey : AppNavKey

@Serializable
data object SettingsNavKey : AppNavKey

@Serializable
data class MessageCenterNavKey(
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class MessageNoticeNavKey(
    val param: MessageNoticeNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class MessageChatNavKey(
    val param: MessageChatNavParam,
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class MessageBlockNavKey(
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class SettingsShieldListNavKey(
    val id: String = newNavId()
) : AppNavKey

@Serializable
data class ImageViewerNavKey(
    val param: ImageViewerNavParam,
    val id: String = newNavId()
) : AppNavKey
