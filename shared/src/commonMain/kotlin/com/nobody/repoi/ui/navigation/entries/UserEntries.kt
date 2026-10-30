package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.album.AlbumNavParam
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
            onNavigateToUser = { targetUserId ->
                backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId)))
            },
            onNavigateToItem = { itemId, itemType ->
                if (itemType == "album") {
                    backStack.add(AlbumDetailNavKey(AlbumNavParam(albumId = itemId)))
                } else {
                    backStack.add(HobbyDetailNavKey(itemId = itemId))
                }
            }
        )
    }

    entry<UserInfoNavKey> { key ->
        UserInfoScreen(
            userId = key.param.userId,
            onNavigateBack = onBack,
            onNavigateToFriendList = { targetUserId, initialType ->
                backStack.add(FriendListNavKey(FriendListNavParam(userId = targetUserId, initialType = initialType)))
            },
            onNavigateToCollect = { collectParam -> backStack.add(UserCollectNavKey(collectParam)) },
            onNavigateToAlbum = { albumParam -> backStack.add(AlbumDetailNavKey(albumParam)) },
            onNavigateToHobby = { itemId -> backStack.add(HobbyDetailNavKey(itemId = itemId)) },
            onNavigateToUser = { targetUserId -> backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId))) },
            onNavigateToChat = { targetUserId, targetUserName, targetUserHeader ->
                backStack.add(
                    MessageChatNavKey(
                        param = MessageChatNavParam(
                            targetUserId = targetUserId,
                            targetUserName = targetUserName,
                            targetUserHeader = targetUserHeader
                        )
                    )
                )
            }
        )
    }

    entry<UserCollectNavKey> { key ->
        UserCollectScreen(
            param = key.param,
            onNavigateBack = onBack,
            onNavigateToHobby = { hobbyParam -> backStack.add(HobbyDetailNavKey(hobbyParam)) },
            onNavigateToVendor = { vendorParam -> backStack.add(VendorDetailNavKey(vendorParam)) },
            onNavigateToSeries = { seriesParam -> backStack.add(SeriesDetailNavKey(seriesParam)) },
            onNavigateToWorks = { worksParam -> backStack.add(WorksDetailNavKey(worksParam)) },
            onNavigateToPerson = { personParam -> backStack.add(PersonDetailNavKey(personParam)) },
            onNavigateToCharacter = { characterParam -> backStack.add(CharacterNavKey(characterParam)) },
            onNavigateToAlbum = { albumParam -> backStack.add(AlbumDetailNavKey(albumParam)) }
        )
    }

    entry<UserCreationNavKey> { key ->
        UserCreationScreen(
            param = key.param,
            onNavigateBack = onBack,
            onNavigateToAlbum = { albumId -> backStack.add(AlbumDetailNavKey(AlbumNavParam(albumId = albumId))) }
        )
    }

    entry<UserHistoryNavKey> {
        UserHistoryScreen(
            onBackClick = onBack,
            onNavigateToHobbyDetail = { itemId -> backStack.add(HobbyDetailNavKey(itemId = itemId)) },
            onNavigateToAlbumDetail = { albumId -> backStack.add(AlbumDetailNavKey(AlbumNavParam(albumId = albumId))) }
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
