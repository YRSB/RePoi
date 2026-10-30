package com.nobody.repoi.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.NavDisplay
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.album.AlbumRankListNavParam
import com.nobody.repoi.ui.album.AlbumScreen
import com.nobody.repoi.ui.follow.FollowScreen
import com.nobody.repoi.ui.home.HomeFeedScreen
import com.nobody.repoi.ui.item.home.ItemHomeScreen
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.profile.ProfileScreen
import com.nobody.repoi.ui.search.SearchNavParam

internal val TopLevelTransitionMetadata: Map<String, Any> = metadata {
    put(NavDisplay.TransitionKey) {
        EnterTransition.None togetherWith ExitTransition.None
    }
    put(NavDisplay.PopTransitionKey) {
        EnterTransition.None togetherWith ExitTransition.None
    }
    put(NavDisplay.PredictivePopTransitionKey) {
        EnterTransition.None togetherWith ExitTransition.None
    }
}

/**
 * 注册顶层 5 个主 Tab 路由（动态、条目、相册、关注、我的）。
 */
fun EntryProviderScope<NavKey>.topLevelEntries(backStack: NavBackStack<NavKey>) {
    entry<HomeNavKey>(metadata = TopLevelTransitionMetadata) {
        HomeFeedScreen(
            onNavigateToDetail = { param -> backStack.add(HobbyDetailNavKey(param)) },
            onNavigateToAlbum = { param -> backStack.add(AlbumDetailNavKey(param)) },
            onNavigateToPerson = { param -> backStack.add(PersonDetailNavKey(param)) },
            onNavigateToWorks = { param -> backStack.add(WorksDetailNavKey(param)) },
            onNavigateToVendor = { param -> backStack.add(VendorDetailNavKey(param)) },
            onNavigateToCharacter = { param -> backStack.add(CharacterNavKey(param)) },
            onNavigateToSeries = { param -> backStack.add(SeriesDetailNavKey(param)) },
            onNavigateToUser = { targetUserId -> backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId))) },
            onNavigateToSearch = { param -> backStack.add(SearchNavKey(param = param)) },
            onNavigateToZoneBrowse = { backStack.add(ZoneBrowseNavKey) },
            onNavigateToForumHome = { backStack.add(ForumHomeNavKey) }
        )
    }

    entry<ItemsNavKey>(metadata = TopLevelTransitionMetadata) {
        ItemHomeScreen(
            onNavigateToDetail = { param -> backStack.add(HobbyDetailNavKey(param)) },
            onNavigateToHobbyList = { param -> backStack.add(HobbyListNavKey(param)) },
            onNavigateToSearch = { categoryId ->
                backStack.add(SearchNavKey(param = SearchNavParam(initialCategory = categoryId)))
            }
        )
    }

    entry<AlbumNavKey>(metadata = TopLevelTransitionMetadata) {
        AlbumScreen(
            onNavigateToAlbum = { param -> backStack.add(AlbumDetailNavKey(param)) },
            onNavigateToLogin = { backStack.add(LoginNavKey) },
            onNavigateToRankList = { param -> backStack.add(AlbumRankListNavKey(param)) }
        )
    }

    entry<FollowNavKey>(metadata = TopLevelTransitionMetadata) {
        FollowScreen(
            onNavigateToDetail = { param -> backStack.add(HobbyDetailNavKey(param)) },
            onNavigateToAlbum = { param -> backStack.add(AlbumDetailNavKey(param)) },
            onNavigateToPerson = { param -> backStack.add(PersonDetailNavKey(param)) },
            onNavigateToWorks = { param -> backStack.add(WorksDetailNavKey(param)) },
            onNavigateToVendor = { param -> backStack.add(VendorDetailNavKey(param)) },
            onNavigateToCharacter = { param -> backStack.add(CharacterNavKey(param)) },
            onNavigateToSeries = { param -> backStack.add(SeriesDetailNavKey(param)) },
            onNavigateToUser = { targetUserId -> backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId))) },
            onNavigateToLogin = { backStack.add(LoginNavKey) }
        )
    }

    entry<ProfileNavKey>(metadata = TopLevelTransitionMetadata) {
        ProfileScreen(
            onNavigateToSettings = { backStack.add(SettingsNavKey) },
            onNavigateToLogin = { backStack.add(LoginNavKey) },
            onNavigateToHobby = { param ->
                if (param.itemType == "person") {
                    backStack.add(
                        PersonDetailNavKey(
                            PersonNavParam(
                                personId = param.itemId,
                                initialName = param.initialName,
                                initialCover = param.initialCover
                            )
                        )
                    )
                } else {
                    backStack.add(HobbyDetailNavKey(param))
                }
            },
            onNavigateToCollect = { param -> backStack.add(UserCollectNavKey(param)) },
            onNavigateToCreation = { creationParam -> backStack.add(UserCreationNavKey(creationParam)) },
            onNavigateToHistory = { backStack.add(UserHistoryNavKey()) },
            onNavigateToAlbum = { albumId -> backStack.add(AlbumDetailNavKey(AlbumNavParam(albumId = albumId))) },
            onNavigateToFriendList = { userId, initialType ->
                backStack.add(FriendListNavKey(FriendListNavParam(userId = userId, initialType = initialType)))
            },
            onNavigateToUserInfo = { userId -> backStack.add(UserInfoNavKey(UserInfoNavParam(userId = userId))) },
            onNavigateToMessage = { backStack.add(MessageCenterNavKey()) }
        )
    }
}
