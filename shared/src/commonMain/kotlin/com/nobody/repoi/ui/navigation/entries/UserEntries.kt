package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.collect.CollectEditScreen
import com.nobody.repoi.ui.collect.UserCollectScreen
import com.nobody.repoi.ui.collect.WorksCollectEditScreen
import com.nobody.repoi.ui.friend.FriendListScreen
import com.nobody.repoi.ui.user.UserInfoScreen
import com.nobody.repoi.ui.user.creation.UserCreationScreen
import com.nobody.repoi.ui.user.history.UserHistoryScreen

/**
 * 注册社交好友、用户信息、收藏管理、用户创作与足迹历史等用户资产路由。
 */
fun EntryProviderScope<NavKey>.userEntries(
    navigator: RePoiNavigator,
    onBack: () -> Unit
) {
    entry<FriendListNavKey> { key ->
        FriendListScreen(
            userId = key.param.userId,
            initialType = key.param.initialType,
            onNavigateBack = onBack,
            onNavigateToUser = { targetUserId -> navigator.navigateToUser(targetUserId) },
            onNavigateToItem = { itemId, itemType ->
                if (itemType == "album") {
                    navigator.navigateToAlbum(itemId)
                } else {
                    navigator.navigateToDetail(itemId)
                }
            }
        )
    }

    entry<UserInfoNavKey> { key ->
        UserInfoScreen(
            userId = key.param.userId,
            onNavigateBack = onBack,
            onNavigateToFriendList = { targetUserId, initialType ->
                navigator.navigateToFriendList(userId = targetUserId, initialType = initialType)
            },
            onNavigateToCollect = { collectParam -> navigator.navigateToUserCollect(collectParam) },
            onNavigateToAlbum = { albumParam -> navigator.navigateToAlbum(albumParam) },
            onNavigateToHobby = { itemId -> navigator.navigateToDetail(itemId) },
            onNavigateToUser = { targetUserId -> navigator.navigateToUser(targetUserId) },
            onNavigateToChat = { targetUserId, targetUserName, targetUserHeader ->
                navigator.navigateToMessageChat(
                    targetUserId = targetUserId,
                    targetUserName = targetUserName,
                    targetUserHeader = targetUserHeader
                )
            }
        )
    }

    entry<UserCollectNavKey> { key ->
        UserCollectScreen(
            param = key.param,
            onNavigateBack = onBack,
            onNavigateToHobby = { hobbyParam -> navigator.navigateToDetail(hobbyParam) },
            onNavigateToVendor = { vendorParam -> navigator.navigateToVendor(vendorParam) },
            onNavigateToSeries = { seriesParam -> navigator.navigateToSeries(seriesParam) },
            onNavigateToWorks = { worksParam -> navigator.navigateToWorks(worksParam) },
            onNavigateToPerson = { personParam -> navigator.navigateToPerson(personParam) },
            onNavigateToCharacter = { characterParam -> navigator.navigateToCharacter(characterParam) },
            onNavigateToAlbum = { albumParam -> navigator.navigateToAlbum(albumParam) }
        )
    }

    entry<UserCreationNavKey> { key ->
        UserCreationScreen(
            param = key.param,
            onNavigateBack = onBack,
            onNavigateToAlbum = { albumId -> navigator.navigateToAlbum(albumId) }
        )
    }

    entry<UserHistoryNavKey> {
        UserHistoryScreen(
            onBackClick = onBack,
            onNavigateToHobbyDetail = { itemId -> navigator.navigateToDetail(itemId) },
            onNavigateToAlbumDetail = { albumId -> navigator.navigateToAlbum(albumId) }
        )
    }

    entry<CollectEditNavKey> { key ->
        CollectEditScreen(
            param = key.param,
            onBack = onBack
        )
    }

    entry<WorksCollectEditNavKey> { key ->
        WorksCollectEditScreen(
            param = key.param,
            onBack = onBack
        )
    }
}

/**
 * 单栈兼容重载。
 */
fun EntryProviderScope<NavKey>.userEntries(
    backStack: NavBackStack<NavKey>,
    onBack: () -> Unit
) {
    userEntries(RePoiNavigator(backStack), onBack)
}
