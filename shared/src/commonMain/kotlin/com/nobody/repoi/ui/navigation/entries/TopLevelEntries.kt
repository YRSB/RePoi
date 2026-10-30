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
fun EntryProviderScope<NavKey>.topLevelEntries(backStack: NavBackStack<NavKey>) {
    entry<HomeNavKey>(metadata = TopLevelTransitionMetadata) {
        HomeFeedScreen(
            onNavigateToDetail = { param -> backStack.navigateToDetail(param) },
            onNavigateToAlbum = { param -> backStack.navigateToAlbum(param) },
            onNavigateToPerson = { param -> backStack.navigateToPerson(param) },
            onNavigateToWorks = { param -> backStack.navigateToWorks(param) },
            onNavigateToVendor = { param -> backStack.navigateToVendor(param) },
            onNavigateToCharacter = { param -> backStack.navigateToCharacter(param) },
            onNavigateToSeries = { param -> backStack.navigateToSeries(param) },
            onNavigateToUser = { targetUserId -> backStack.navigateToUser(targetUserId) },
            onNavigateToSearch = { param -> backStack.navigateToSearch(param) },
            onNavigateToZoneBrowse = { backStack.navigateToZoneBrowse() },
            onNavigateToForumHome = { backStack.navigateToForumHome() }
        )
    }

    entry<ItemsNavKey>(metadata = TopLevelTransitionMetadata) {
        ItemHomeScreen(
            onNavigateToDetail = { param -> backStack.navigateToDetail(param) },
            onNavigateToHobbyList = { param -> backStack.navigateToHobbyList(param) },
            onNavigateToSearch = { categoryId ->
                backStack.navigateToSearch(SearchNavParam(initialCategory = categoryId))
            }
        )
    }

    entry<AlbumNavKey>(metadata = TopLevelTransitionMetadata) {
        AlbumScreen(
            onNavigateToAlbum = { param -> backStack.navigateToAlbum(param) },
            onNavigateToLogin = { backStack.navigateToLogin() },
            onNavigateToRankList = { param -> backStack.navigateToRankList(param) }
        )
    }

    entry<FollowNavKey>(metadata = TopLevelTransitionMetadata) {
        FollowScreen(
            onNavigateToDetail = { param -> backStack.navigateToDetail(param) },
            onNavigateToAlbum = { param -> backStack.navigateToAlbum(param) },
            onNavigateToPerson = { param -> backStack.navigateToPerson(param) },
            onNavigateToWorks = { param -> backStack.navigateToWorks(param) },
            onNavigateToVendor = { param -> backStack.navigateToVendor(param) },
            onNavigateToCharacter = { param -> backStack.navigateToCharacter(param) },
            onNavigateToSeries = { param -> backStack.navigateToSeries(param) },
            onNavigateToUser = { targetUserId -> backStack.navigateToUser(targetUserId) },
            onNavigateToLogin = { backStack.navigateToLogin() }
        )
    }

    entry<ProfileNavKey>(metadata = TopLevelTransitionMetadata) {
        ProfileScreen(
            onNavigateToSettings = { backStack.navigateToSettings() },
            onNavigateToLogin = { backStack.navigateToLogin() },
            onNavigateToHobby = { param ->
                if (param.itemType == "person") {
                    backStack.navigateToPerson(
                        PersonNavParam(
                            personId = param.itemId,
                            initialName = param.initialName,
                            initialCover = param.initialCover
                        )
                    )
                } else {
                    backStack.navigateToDetail(param)
                }
            },
            onNavigateToCollect = { param -> backStack.navigateToUserCollect(param) },
            onNavigateToCreation = { creationParam -> backStack.navigateToUserCreation(creationParam) },
            onNavigateToHistory = { backStack.navigateToUserHistory() },
            onNavigateToAlbum = { albumId -> backStack.navigateToAlbum(AlbumNavParam(albumId = albumId)) },
            onNavigateToFriendList = { userId, initialType ->
                backStack.navigateToFriendList(FriendListNavParam(userId = userId, initialType = initialType))
            },
            onNavigateToUserInfo = { userId -> backStack.navigateToUser(userId) },
            onNavigateToMessage = { backStack.navigateToMessageCenter() }
        )
    }
}
