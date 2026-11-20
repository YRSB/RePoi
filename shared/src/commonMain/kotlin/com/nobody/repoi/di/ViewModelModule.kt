package com.nobody.repoi.di

import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.ThemeManager
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.ui.album.AlbumDetailViewModel
import com.nobody.repoi.ui.album.AlbumRankListViewModel
import com.nobody.repoi.ui.album.AlbumViewModel
import com.nobody.repoi.ui.board.session.ZoneBoardSession
import com.nobody.repoi.ui.board.session.ZoneBoardSessionImpl
import com.nobody.repoi.ui.character.CharacterAlbumListViewModel
import com.nobody.repoi.ui.character.CharacterDetailViewModel
import com.nobody.repoi.ui.character.CharacterWorksListViewModel
import com.nobody.repoi.ui.collect.CollectEditViewModel
import com.nobody.repoi.ui.collect.UserCollectViewModel
import com.nobody.repoi.ui.collect.WorksCollectEditViewModel
import com.nobody.repoi.ui.comment.CommentDetailViewModel
import com.nobody.repoi.ui.comment.HobbyCommentsViewModel
import com.nobody.repoi.ui.detail.HobbyDetailViewModel
import com.nobody.repoi.ui.detail.album.HobbyAlbumListViewModel
import com.nobody.repoi.ui.exhobby.ExHobbyListViewModel
import com.nobody.repoi.ui.follow.FollowViewModel
import com.nobody.repoi.ui.friend.FriendListViewModel
import com.nobody.repoi.ui.gallery.PictureGalleryViewModel
import com.nobody.repoi.ui.home.HomeFeedViewModel
import com.nobody.repoi.ui.item.home.ItemHomeViewModel
import com.nobody.repoi.ui.item.list.HobbyListViewModel
import com.nobody.repoi.ui.login.LoginViewModel
import com.nobody.repoi.ui.message.block.MessageBlockViewModel
import com.nobody.repoi.ui.message.center.MessageCenterViewModel
import com.nobody.repoi.ui.message.chat.MessageChatViewModel
import com.nobody.repoi.ui.message.notice.MessageNoticeViewModel
import com.nobody.repoi.ui.person.PersonDetailViewModel
import com.nobody.repoi.ui.picture.PictureListViewModel
import com.nobody.repoi.ui.post.PostDetailViewModel
import com.nobody.repoi.ui.profile.ProfileViewModel
import com.nobody.repoi.ui.resale.ResaleDetailViewModel
import com.nobody.repoi.ui.resale.ResaleMarketViewModel
import com.nobody.repoi.ui.search.SearchLandingViewModel
import com.nobody.repoi.ui.search.SearchResultViewModel
import com.nobody.repoi.ui.series.SeriesDetailViewModel
import com.nobody.repoi.ui.settings.SettingsAccountViewModel
import com.nobody.repoi.ui.settings.SettingsCacheViewModel
import com.nobody.repoi.ui.settings.SettingsPrivacyViewModel
import com.nobody.repoi.ui.settings.SettingsViewModel
import com.nobody.repoi.ui.settings.shield.SettingsShieldListViewModel
import com.nobody.repoi.ui.settings.shield.SettingsShieldViewModel
import com.nobody.repoi.ui.user.UserInfoViewModel
import com.nobody.repoi.ui.user.creation.UserCreationViewModel
import com.nobody.repoi.ui.user.history.UserHistoryViewModel
import com.nobody.repoi.ui.vendor.VendorDetailViewModel
import com.nobody.repoi.ui.vendor.actions.VendorActionsViewModel
import com.nobody.repoi.ui.works.WorksDetailViewModel
import com.nobody.repoi.ui.zone.ZoneBrowseViewModel
import com.nobody.repoi.ui.zone.ZoneDetailViewModel
import com.nobody.repoi.ui.zone.ZoneSettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * ViewModel 模块：50+ ViewModel 注册（M3 起按 NavKey 传参）。
 *
 * 构造签名不含 `/named` 限定符时统一走 `viewModelOf(::Xxx)`；需要限定符（如 `named("download")`）
 * 或显式控制解析顺序时保留 `viewModel { }` 写法。
 *
 * 关键契约：`viewModelOf` 对应的 Koin `new()` **逐个 `get()` 形参，不使用 Kotlin 默认值**，
 * 因此带默认值的可空参数同样必须已在容器中注册（`HomeFeedViewModel` 的 `categoryManager` /
 * `messageRepository` 即依赖 `ManagerModule` / `RepositoryModule` 注册）；未注册会启动即解析失败，
 * 而不是得到 null。
 *
 * SavedStateHandle 已删除（见各 VM 注释）：NavKey 字段经 `koinViewModel(parameters = ...)`
 * 或 `loadInitial/initParam` 传入；plain `koinViewModel()` 保持条目作用域。
 */
val viewModelModule = module {
    viewModel { AlbumViewModel(get(), get()) }
    viewModelOf(::AlbumDetailViewModel)
    viewModel { AlbumRankListViewModel(get()) }
    viewModelOf(::CharacterDetailViewModel)
    viewModel { CharacterAlbumListViewModel(get()) }
    viewModel { CharacterWorksListViewModel(get()) }
    viewModelOf(::CollectEditViewModel)
    viewModel { UserCollectViewModel(get(), get(), get(), get()) }
    viewModelOf(::WorksCollectEditViewModel)
    viewModel { CommentDetailViewModel(get(), get(), get()) }
    viewModel { HobbyCommentsViewModel(get(), get()) }
    viewModel { HobbyDetailViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { HobbyAlbumListViewModel(get()) }
    viewModel { ExHobbyListViewModel(get()) }
    viewModel { FollowViewModel(get(), get()) }
    viewModel { FriendListViewModel(get(), get()) }
    viewModel { PictureGalleryViewModel(get(), get(named("download"))) }
    viewModel {
        HomeFeedViewModel(
            sessionFactory = get(),
            zoneRepository = get(),
            viewPreferencesManager = get(),
            userSession = get(),
            messageRepository = getOrNull(),
            categoryManager = getOrNull(),
            albumRepository = getOrNull()
        )
    }
    viewModel { ItemHomeViewModel(get(), get()) }
    viewModel { HobbyListViewModel(get(), get(), get()) }
    viewModel { LoginViewModel(get()) }
    viewModel { MessageCenterViewModel(get()) }
    viewModel { MessageNoticeViewModel(get()) }
    viewModel { MessageChatViewModel(get()) }
    viewModel { MessageBlockViewModel(get()) }
    viewModel { PersonDetailViewModel(get(), get(), get()) }
    viewModel { PictureListViewModel(get()) }
    viewModel { PostDetailViewModel(get(), get(), get()) }
    viewModel { ProfileViewModel(get(), get(), get(), get(), get()) }
    viewModel { ResaleDetailViewModel(get(), get()) }
    viewModel { ResaleMarketViewModel(get(), get()) }
    viewModelOf(::SearchLandingViewModel)
    viewModelOf(::SearchResultViewModel)
    viewModel { SeriesDetailViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { SettingsAccountViewModel(get(), get()) }
    viewModel { SettingsShieldViewModel(get(), get()) }
    viewModel { SettingsShieldListViewModel(get(), get()) }
    viewModel { SettingsPrivacyViewModel(get(), get()) }
    viewModel { SettingsCacheViewModel(get(), get(), get()) }
    viewModel { UserInfoViewModel(get(), get(), get(), get()) }
    viewModel { UserCreationViewModel(get(), get()) }
    viewModel { UserHistoryViewModel(get(), get()) }
    viewModel { VendorDetailViewModel(get(), get(), get(), get()) }
    viewModel { VendorActionsViewModel(get()) }
    viewModelOf(::WorksDetailViewModel)
    viewModel { ZoneBrowseViewModel(get()) }
    viewModelOf(::ZoneDetailViewModel)
    viewModel { ZoneSettingsViewModel(get()) }

    // ── 子板块会话控制器工厂（深模块） ──
    single<ZoneBoardSession.Factory> {
        ZoneBoardSessionImpl.Factory(
            homeRepository = get(),
            albumRepository = get(),
            zoneRepository = get(),
            itemRepository = get(),
            resaleRepository = get(),
            viewPreferencesManager = get()
        )
    }
}
