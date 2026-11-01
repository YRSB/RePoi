package com.nobody.repoi.di

import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.ThemeManager
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.ui.album.AlbumDetailViewModel
import com.nobody.repoi.ui.album.AlbumRankListViewModel
import com.nobody.repoi.ui.album.AlbumViewModel
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
import com.nobody.repoi.ui.forum.ForumHomeViewModel
import com.nobody.repoi.ui.forum.PlateSubscribeViewModel
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
import com.nobody.repoi.ui.search.SearchViewModel
import com.nobody.repoi.ui.series.SeriesDetailViewModel
import com.nobody.repoi.ui.settings.SettingsViewModel
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
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * ViewModel 模块：45 `viewModel { }`（dev-only DebugApiViewModel 已移至 androidMain；M3 起按 NavKey 传参）。
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
    viewModel { HobbyDetailViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { HobbyAlbumListViewModel(get()) }
    viewModel { ExHobbyListViewModel(get()) }
    viewModel { FollowViewModel(get(), get()) }
    viewModel { ForumHomeViewModel(get(), get()) }
    viewModel { PlateSubscribeViewModel(get(), get()) }
    viewModel { FriendListViewModel(get()) }
    viewModel { PictureGalleryViewModel(get(), get(named("download"))) }
    viewModel { HomeFeedViewModel(get(), get(), get(), get(), get()) }
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
