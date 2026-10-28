package com.nobody.poishelf.di

import com.nobody.poishelf.data.local.CategoryManager
import com.nobody.poishelf.data.local.ThemeManager
import com.nobody.poishelf.data.local.TokenManager
import com.nobody.poishelf.data.local.ViewPreferencesManager
import com.nobody.poishelf.ui.album.AlbumDetailViewModel
import com.nobody.poishelf.ui.album.AlbumRankListViewModel
import com.nobody.poishelf.ui.album.AlbumViewModel
import com.nobody.poishelf.ui.character.CharacterAlbumListViewModel
import com.nobody.poishelf.ui.character.CharacterDetailViewModel
import com.nobody.poishelf.ui.character.CharacterWorksListViewModel
import com.nobody.poishelf.ui.collect.CollectEditViewModel
import com.nobody.poishelf.ui.collect.UserCollectViewModel
import com.nobody.poishelf.ui.collect.WorksCollectEditViewModel
import com.nobody.poishelf.ui.comment.CommentDetailViewModel
import com.nobody.poishelf.ui.comment.HobbyCommentsViewModel
import com.nobody.poishelf.ui.debug.DebugApiViewModel
import com.nobody.poishelf.ui.detail.HobbyDetailViewModel
import com.nobody.poishelf.ui.detail.album.HobbyAlbumListViewModel
import com.nobody.poishelf.ui.exhobby.ExHobbyListViewModel
import com.nobody.poishelf.ui.follow.FollowViewModel
import com.nobody.poishelf.ui.forum.ForumHomeViewModel
import com.nobody.poishelf.ui.forum.PlateSubscribeViewModel
import com.nobody.poishelf.ui.friend.FriendListViewModel
import com.nobody.poishelf.ui.gallery.PictureGalleryViewModel
import com.nobody.poishelf.ui.home.HomeFeedViewModel
import com.nobody.poishelf.ui.item.home.ItemHomeViewModel
import com.nobody.poishelf.ui.item.list.HobbyListViewModel
import com.nobody.poishelf.ui.login.LoginViewModel
import com.nobody.poishelf.ui.message.block.MessageBlockViewModel
import com.nobody.poishelf.ui.message.center.MessageCenterViewModel
import com.nobody.poishelf.ui.message.chat.MessageChatViewModel
import com.nobody.poishelf.ui.message.notice.MessageNoticeViewModel
import com.nobody.poishelf.ui.person.PersonDetailViewModel
import com.nobody.poishelf.ui.picture.PictureListViewModel
import com.nobody.poishelf.ui.post.PostDetailViewModel
import com.nobody.poishelf.ui.profile.ProfileViewModel
import com.nobody.poishelf.ui.resale.ResaleDetailViewModel
import com.nobody.poishelf.ui.resale.ResaleMarketViewModel
import com.nobody.poishelf.ui.search.SearchViewModel
import com.nobody.poishelf.ui.series.SeriesDetailViewModel
import com.nobody.poishelf.ui.settings.SettingsViewModel
import com.nobody.poishelf.ui.user.UserInfoViewModel
import com.nobody.poishelf.ui.user.creation.UserCreationViewModel
import com.nobody.poishelf.ui.user.history.UserHistoryViewModel
import com.nobody.poishelf.ui.vendor.VendorDetailViewModel
import com.nobody.poishelf.ui.vendor.actions.VendorActionsViewModel
import com.nobody.poishelf.ui.works.WorksDetailViewModel
import com.nobody.poishelf.ui.zone.ZoneBrowseViewModel
import com.nobody.poishelf.ui.zone.ZoneDetailViewModel
import com.nobody.poishelf.ui.zone.ZoneSettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * ViewModel 模块：46 `viewModel { }`（含 dev-only DebugApiViewModel；M3 起按 NavKey 传参）。
 *
 * SavedStateHandle 已删除（见各 VM 注释）：NavKey 字段经 `koinViewModel(parameters = ...)`
 * 或 `loadInitial/initParam` 传入；plain `koinViewModel()` 保持条目作用域。
 */
val viewModelModule = module {
    viewModel { AlbumViewModel(get(), get()) }
    viewModel { AlbumDetailViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { AlbumRankListViewModel(get()) }
    viewModel { CharacterDetailViewModel(get(), get()) }
    viewModel { CharacterAlbumListViewModel(get()) }
    viewModel { CharacterWorksListViewModel(get()) }
    viewModel { CollectEditViewModel(get()) }
    viewModel { UserCollectViewModel(get(), get(), get(), get()) }
    viewModel { WorksCollectEditViewModel(get()) }
    viewModel { CommentDetailViewModel(get(), get(), get(), get()) }
    viewModel { HobbyCommentsViewModel(get(), get()) }
    viewModel { DebugApiViewModel(get()) }
    viewModel { HobbyDetailViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { HobbyAlbumListViewModel(get()) }
    viewModel { ExHobbyListViewModel(get()) }
    viewModel { FollowViewModel(get(), get()) }
    viewModel { ForumHomeViewModel(get(), get()) }
    viewModel { PlateSubscribeViewModel(get(), get()) }
    viewModel { FriendListViewModel(get()) }
    viewModel { PictureGalleryViewModel(get(), get(named("download"))) }
    viewModel { HomeFeedViewModel(get(), get(), get()) }
    viewModel { ItemHomeViewModel(get(), get()) }
    viewModel { HobbyListViewModel(get(), get(), get()) }
    viewModel { LoginViewModel(get()) }
    viewModel { MessageCenterViewModel(get()) }
    viewModel { MessageNoticeViewModel(get()) }
    viewModel { MessageChatViewModel(get()) }
    viewModel { MessageBlockViewModel(get()) }
    viewModel { PersonDetailViewModel(get(), get()) }
    viewModel { PictureListViewModel(get()) }
    viewModel { PostDetailViewModel(get(), get(), get()) }
    viewModel { ProfileViewModel(get(), get(), get()) }
    viewModel { ResaleDetailViewModel(get(), get()) }
    viewModel { ResaleMarketViewModel(get(), get()) }
    viewModel { SearchViewModel(get(), get()) }
    viewModel { SeriesDetailViewModel(get(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { UserInfoViewModel(get()) }
    viewModel { UserCreationViewModel(get(), get()) }
    viewModel { UserHistoryViewModel(get()) }
    viewModel { VendorDetailViewModel(get(), get(), get()) }
    viewModel { VendorActionsViewModel(get()) }
    viewModel { WorksDetailViewModel(get(), get()) }
    viewModel { ZoneBrowseViewModel(get()) }
    viewModel { ZoneDetailViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ZoneSettingsViewModel(get()) }
}
