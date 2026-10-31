package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.NavDisplay
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.album.AlbumScreen
import com.nobody.repoi.ui.follow.FollowScreen
import com.nobody.repoi.ui.home.HomeFeedScreen
import com.nobody.repoi.ui.item.home.ItemHomeScreen
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.profile.ProfileScreen
import com.nobody.repoi.ui.search.SearchNavParam

internal val TopLevelTransitionMetadata: Map<String, Any> = metadata {
    put(NavDisplay.TransitionKey) {
        MaterialSharedAxis.Fade.TransitionSpec
    }
    put(NavDisplay.PopTransitionKey) {
        MaterialSharedAxis.Fade.TransitionSpec
    }
}

/**
 * 注册顶层 5 个主 Tab 路由（动态、条目、相册、关注、我的）。
 */
fun EntryProviderScope<NavKey>.topLevelEntries(navigator: RePoiNavigator) {
    entry<HomeNavKey>(metadata = TopLevelTransitionMetadata) {
        HomeFeedScreen(
            onNavigateToDetail = { param -> navigator.navigateToDetail(param) },
            onNavigateToAlbum = { param -> navigator.navigateToAlbum(param) },
            onNavigateToPerson = { param -> navigator.navigateToPerson(param) },
            onNavigateToWorks = { param -> navigator.navigateToWorks(param) },
            onNavigateToVendor = { param -> navigator.navigateToVendor(param) },
            onNavigateToCharacter = { param -> navigator.navigateToCharacter(param) },
            onNavigateToSeries = { param -> navigator.navigateToSeries(param) },
            onNavigateToUser = { targetUserId -> navigator.navigateToUser(targetUserId) },
            onNavigateToSearch = { param -> navigator.navigateToSearch(param) },
            onNavigateToZoneBrowse = { navigator.navigateToZoneBrowse() },
            onNavigateToForumHome = { navigator.navigateToForumHome() }
        )
    }

    entry<ItemsNavKey>(metadata = TopLevelTransitionMetadata) {
        ItemHomeScreen(
            onNavigateToDetail = { param -> navigator.navigateToDetail(param) },
            onNavigateToHobbyList = { param -> navigator.navigateToHobbyList(param) },
            onNavigateToSearch = { categoryId ->
                navigator.navigateToSearch(SearchNavParam(initialCategory = categoryId))
            }
        )
    }

    entry<AlbumNavKey>(metadata = TopLevelTransitionMetadata) {
        AlbumScreen(
            onNavigateToAlbum = { param -> navigator.navigateToAlbum(param) },
            onNavigateToLogin = { navigator.navigateToLogin() },
            onNavigateToRankList = { param -> navigator.navigateToRankList(param) }
        )
    }

    entry<FollowNavKey>(metadata = TopLevelTransitionMetadata) {
        FollowScreen(
            onNavigateToDetail = { param -> navigator.navigateToDetail(param) },
            onNavigateToAlbum = { param -> navigator.navigateToAlbum(param) },
            onNavigateToPerson = { param -> navigator.navigateToPerson(param) },
            onNavigateToWorks = { param -> navigator.navigateToWorks(param) },
            onNavigateToVendor = { param -> navigator.navigateToVendor(param) },
            onNavigateToCharacter = { param -> navigator.navigateToCharacter(param) },
            onNavigateToSeries = { param -> navigator.navigateToSeries(param) },
            onNavigateToUser = { targetUserId -> navigator.navigateToUser(targetUserId) },
            onNavigateToLogin = { navigator.navigateToLogin() }
        )
    }

    entry<ProfileNavKey>(metadata = TopLevelTransitionMetadata) {
        ProfileScreen(
            onNavigateToSettings = { navigator.navigateToSettings() },
            onNavigateToLogin = { navigator.navigateToLogin() },
            onNavigateToHobby = { param ->
                if (param.itemType == "person") {
                    navigator.navigateToPerson(
                        PersonNavParam(
                            personId = param.itemId,
                            initialName = param.initialName,
                            initialCover = param.initialCover
                        )
                    )
                } else {
                    navigator.navigateToDetail(param)
                }
            },
            onNavigateToCollect = { param -> navigator.navigateToUserCollect(param) },
            onNavigateToCreation = { creationParam -> navigator.navigateToUserCreation(creationParam) },
            onNavigateToHistory = { navigator.navigateToUserHistory() },
            onNavigateToAlbum = { albumId -> navigator.navigateToAlbum(AlbumNavParam(albumId = albumId)) },
            onNavigateToFriendList = { userId, initialType ->
                navigator.navigateToFriendList(FriendListNavParam(userId = userId, initialType = initialType))
            },
            onNavigateToUserInfo = { userId -> navigator.navigateToUser(userId) },
            onNavigateToMessage = { navigator.navigateToMessageCenter() }
        )
    }
}

/**
 * 单栈兼容重载。
 */
fun EntryProviderScope<NavKey>.topLevelEntries(backStack: NavBackStack<NavKey>) {
    topLevelEntries(RePoiNavigator(backStack))
}
