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
    backStack: NavBackStack<NavKey>,
    onBack: () -> Unit
) {
    entry<FriendListNavKey> { key ->
        FriendListScreen(
            userId = key.param.userId,
            initialType = key.param.initialType,
            onNavigateBack = onBack,
            onNavigateToUser = { targetUserId -> backStack.navigateToUser(targetUserId) },
            onNavigateToItem = { itemId, itemType ->
                if (itemType == "album") {
                    backStack.navigateToAlbum(itemId)
                } else {
                    backStack.navigateToDetail(itemId)
                }
            }
        )
    }

    entry<UserInfoNavKey> { key ->
        UserInfoScreen(
            userId = key.param.userId,
            onNavigateBack = onBack,
            onNavigateToFriendList = { targetUserId, initialType ->
                backStack.navigateToFriendList(userId = targetUserId, initialType = initialType)
            },
            onNavigateToCollect = { collectParam -> backStack.navigateToUserCollect(collectParam) },
            onNavigateToAlbum = { albumParam -> backStack.navigateToAlbum(albumParam) },
            onNavigateToHobby = { itemId -> backStack.navigateToDetail(itemId) },
            onNavigateToUser = { targetUserId -> backStack.navigateToUser(targetUserId) },
            onNavigateToChat = { targetUserId, targetUserName, targetUserHeader ->
                backStack.navigateToMessageChat(
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
            onNavigateToHobby = { hobbyParam -> backStack.navigateToDetail(hobbyParam) },
            onNavigateToVendor = { vendorParam -> backStack.navigateToVendor(vendorParam) },
            onNavigateToSeries = { seriesParam -> backStack.navigateToSeries(seriesParam) },
            onNavigateToWorks = { worksParam -> backStack.navigateToWorks(worksParam) },
            onNavigateToPerson = { personParam -> backStack.navigateToPerson(personParam) },
            onNavigateToCharacter = { characterParam -> backStack.navigateToCharacter(characterParam) },
            onNavigateToAlbum = { albumParam -> backStack.navigateToAlbum(albumParam) }
        )
    }

    entry<UserCreationNavKey> { key ->
        UserCreationScreen(
            param = key.param,
            onNavigateBack = onBack,
            onNavigateToAlbum = { albumId -> backStack.navigateToAlbum(albumId) }
        )
    }

    entry<UserHistoryNavKey> {
        UserHistoryScreen(
            onBackClick = onBack,
            onNavigateToHobbyDetail = { itemId -> backStack.navigateToDetail(itemId) },
            onNavigateToAlbumDetail = { albumId -> backStack.navigateToAlbum(albumId) }
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
