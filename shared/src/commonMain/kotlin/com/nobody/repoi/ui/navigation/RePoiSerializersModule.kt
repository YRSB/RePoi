package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * RePoi 全局导航多态序列化模块：
 * 将所有 [AppNavKey] 实现类统一注册到 [NavKey] 基类多态域中。
 * 供 [androidx.savedstate.serialization.SavedStateConfiguration] 与 [androidx.navigation3.runtime.rememberNavBackStack]
 * 实现 Android 进程回收恢复、横竖屏旋转及桌面端状态持久化。
 */
val RePoiSerializersModule = SerializersModule {
    polymorphic(baseClass = NavKey::class) {
        // 1. 一级底栏 Tab 路由 (5)
        subclass(HomeNavKey::class, HomeNavKey.serializer())
        subclass(ItemsNavKey::class, ItemsNavKey.serializer())
        subclass(AlbumNavKey::class, AlbumNavKey.serializer())
        subclass(FollowNavKey::class, FollowNavKey.serializer())
        subclass(ProfileNavKey::class, ProfileNavKey.serializer())

        // 2. 专区相关路由 (3)
        subclass(ZoneBrowseNavKey::class, ZoneBrowseNavKey.serializer())
        subclass(ZoneDetailNavKey::class, ZoneDetailNavKey.serializer())
        subclass(ZoneSettingsNavKey::class, ZoneSettingsNavKey.serializer())

        // 3. 讨论板与帖子路由 (3)
        subclass(ForumHomeNavKey::class, ForumHomeNavKey.serializer())
        subclass(PlateSubscribeNavKey::class, PlateSubscribeNavKey.serializer())
        subclass(PostDetailNavKey::class, PostDetailNavKey.serializer())

        // 4. 二手交易与市场路由 (2)
        subclass(ResaleDetailNavKey::class, ResaleDetailNavKey.serializer())
        subclass(ResaleMarketNavKey::class, ResaleMarketNavKey.serializer())

        // 5. 核心消费与用户资产路由 (7)
        subclass(HobbyListNavKey::class, HobbyListNavKey.serializer())
        subclass(CollectEditNavKey::class, CollectEditNavKey.serializer())
        subclass(UserCollectNavKey::class, UserCollectNavKey.serializer())
        subclass(UserCreationNavKey::class, UserCreationNavKey.serializer())
        subclass(UserHistoryNavKey::class, UserHistoryNavKey.serializer())
        subclass(HobbyDetailNavKey::class, HobbyDetailNavKey.serializer())
        subclass(FriendListNavKey::class, FriendListNavKey.serializer())

        // 6. 用户与评论互动路由 (4)
        subclass(UserInfoNavKey::class, UserInfoNavKey.serializer())
        subclass(CommentDetailNavKey::class, CommentDetailNavKey.serializer())
        subclass(HobbyCommentsNavKey::class, HobbyCommentsNavKey.serializer())
        subclass(SearchNavKey::class, SearchNavKey.serializer())

        // 7. 相册与厂商关联路由 (4)
        subclass(AlbumDetailNavKey::class, AlbumDetailNavKey.serializer())
        subclass(AlbumRankListNavKey::class, AlbumRankListNavKey.serializer())
        subclass(VendorDetailNavKey::class, VendorDetailNavKey.serializer())
        subclass(VendorActionsNavKey::class, VendorActionsNavKey.serializer())

        // 8. 人物、角色与作品关联路由 (7)
        subclass(PersonDetailNavKey::class, PersonDetailNavKey.serializer())
        subclass(CharacterNavKey::class, CharacterNavKey.serializer())
        subclass(CharacterAlbumListNavKey::class, CharacterAlbumListNavKey.serializer())
        subclass(HobbyAlbumListNavKey::class, HobbyAlbumListNavKey.serializer())
        subclass(CharacterWorksListNavKey::class, CharacterWorksListNavKey.serializer())
        subclass(WorksCollectEditNavKey::class, WorksCollectEditNavKey.serializer())
        subclass(SeriesDetailNavKey::class, SeriesDetailNavKey.serializer())

        // 9. 作品、图片与扩展图库路由 (3)
        subclass(WorksDetailNavKey::class, WorksDetailNavKey.serializer())
        subclass(PictureListNavKey::class, PictureListNavKey.serializer())
        subclass(ExHobbyListNavKey::class, ExHobbyListNavKey.serializer())

        // 10. 设置与鉴权路由 (2)
        subclass(LoginNavKey::class, LoginNavKey.serializer())
        subclass(SettingsNavKey::class, SettingsNavKey.serializer())

        // 11. 消息中心与通知路由 (4)
        subclass(MessageCenterNavKey::class, MessageCenterNavKey.serializer())
        subclass(MessageNoticeNavKey::class, MessageNoticeNavKey.serializer())
        subclass(MessageChatNavKey::class, MessageChatNavKey.serializer())
        subclass(MessageBlockNavKey::class, MessageBlockNavKey.serializer())
    }
}
