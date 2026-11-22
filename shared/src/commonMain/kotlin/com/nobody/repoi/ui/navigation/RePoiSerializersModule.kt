package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

val RePoiSerializersModule = SerializersModule {
    polymorphic(baseClass = NavKey::class) {

        subclass(MainNavKey::class, MainNavKey.serializer())

        subclass(HomeNavKey::class, HomeNavKey.serializer())
        subclass(ItemsNavKey::class, ItemsNavKey.serializer())
        subclass(AlbumNavKey::class, AlbumNavKey.serializer())
        subclass(FollowNavKey::class, FollowNavKey.serializer())
        subclass(ProfileNavKey::class, ProfileNavKey.serializer())

        subclass(ZoneBrowseNavKey::class, ZoneBrowseNavKey.serializer())
        subclass(ZoneDetailNavKey::class, ZoneDetailNavKey.serializer())
        subclass(ZoneSettingsNavKey::class, ZoneSettingsNavKey.serializer())

        subclass(PostDetailNavKey::class, PostDetailNavKey.serializer())

        subclass(ResaleDetailNavKey::class, ResaleDetailNavKey.serializer())
        subclass(ResaleMarketNavKey::class, ResaleMarketNavKey.serializer())

        subclass(HobbyListNavKey::class, HobbyListNavKey.serializer())
        subclass(CollectEditNavKey::class, CollectEditNavKey.serializer())
        subclass(UserCollectNavKey::class, UserCollectNavKey.serializer())
        subclass(UserCreationNavKey::class, UserCreationNavKey.serializer())
        subclass(UserHistoryNavKey::class, UserHistoryNavKey.serializer())
        subclass(HobbyDetailNavKey::class, HobbyDetailNavKey.serializer())
        subclass(FriendListNavKey::class, FriendListNavKey.serializer())

        subclass(UserInfoNavKey::class, UserInfoNavKey.serializer())
        subclass(CommentDetailNavKey::class, CommentDetailNavKey.serializer())
        subclass(HobbyCommentsNavKey::class, HobbyCommentsNavKey.serializer())
        subclass(SearchNavKey::class, SearchNavKey.serializer())
        subclass(SearchResultNavKey::class, SearchResultNavKey.serializer())

        subclass(AlbumDetailNavKey::class, AlbumDetailNavKey.serializer())
        subclass(AlbumRankListNavKey::class, AlbumRankListNavKey.serializer())
        subclass(VendorDetailNavKey::class, VendorDetailNavKey.serializer())
        subclass(VendorActionsNavKey::class, VendorActionsNavKey.serializer())

        subclass(PersonDetailNavKey::class, PersonDetailNavKey.serializer())
        subclass(CharacterNavKey::class, CharacterNavKey.serializer())
        subclass(CharacterAlbumListNavKey::class, CharacterAlbumListNavKey.serializer())
        subclass(HobbyAlbumListNavKey::class, HobbyAlbumListNavKey.serializer())
        subclass(CharacterWorksListNavKey::class, CharacterWorksListNavKey.serializer())
        subclass(WorksCollectEditNavKey::class, WorksCollectEditNavKey.serializer())
        subclass(SeriesDetailNavKey::class, SeriesDetailNavKey.serializer())

        subclass(WorksDetailNavKey::class, WorksDetailNavKey.serializer())
        subclass(PictureListNavKey::class, PictureListNavKey.serializer())
        subclass(ImageViewerNavKey::class, ImageViewerNavKey.serializer())
        subclass(PictureGalleryNavKey::class, PictureGalleryNavKey.serializer())
        subclass(ExHobbyListNavKey::class, ExHobbyListNavKey.serializer())

        subclass(LoginNavKey::class, LoginNavKey.serializer())
        subclass(SettingsNavKey::class, SettingsNavKey.serializer())
        subclass(SettingsShieldListNavKey::class, SettingsShieldListNavKey.serializer())

        subclass(MessageCenterNavKey::class, MessageCenterNavKey.serializer())
        subclass(MessageNoticeNavKey::class, MessageNoticeNavKey.serializer())
        subclass(MessageChatNavKey::class, MessageChatNavKey.serializer())
        subclass(MessageBlockNavKey::class, MessageBlockNavKey.serializer())
    }
}
