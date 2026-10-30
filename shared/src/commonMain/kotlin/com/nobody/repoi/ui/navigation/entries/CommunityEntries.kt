package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.forum.ForumHomeScreen
import com.nobody.repoi.ui.forum.PlateSubscribeScreen
import com.nobody.repoi.ui.post.PostDetailScreen
import com.nobody.repoi.ui.zone.ZoneBrowseScreen
import com.nobody.repoi.ui.zone.ZoneDetailScreen
import com.nobody.repoi.ui.zone.ZoneSettingsScreen

/**
 * 注册专区、讨论板与帖子详情等社区领域路由。
 */
fun EntryProviderScope<NavKey>.communityEntries(
    backStack: NavBackStack<NavKey>,
    onBack: () -> Unit
) {
    entry<ZoneBrowseNavKey> {
        ZoneBrowseScreen(
            onNavigateBack = onBack,
            onNavigateToZoneDetail = { nodeId, name, icon, cover ->
                backStack.add(
                    ZoneDetailNavKey(
                        nodeId = nodeId,
                        initialName = name,
                        initialIcon = icon,
                        initialCover = cover
                    )
                )
            },
            onNavigateToSettings = { backStack.add(ZoneSettingsNavKey) }
        )
    }

    entry<ZoneDetailNavKey> { key ->
        ZoneDetailScreen(
            nodeId = key.nodeId,
            initialName = key.initialName,
            initialIcon = key.initialIcon,
            initialCover = key.initialCover,
            onNavigateBack = onBack,
            onNavigateToDetail = { param -> backStack.add(HobbyDetailNavKey(param)) },
            onNavigateToAlbum = { param -> backStack.add(AlbumDetailNavKey(param)) },
            onNavigateToPerson = { param -> backStack.add(PersonDetailNavKey(param)) },
            onNavigateToWorks = { param -> backStack.add(WorksDetailNavKey(param)) },
            onNavigateToVendor = { param -> backStack.add(VendorDetailNavKey(param)) },
            onNavigateToCharacter = { param -> backStack.add(CharacterNavKey(param)) },
            onNavigateToSeries = { param -> backStack.add(SeriesDetailNavKey(param)) },
            onNavigateToUser = { targetUserId -> backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId))) },
            onNavigateToPost = { postId, nodeId, title ->
                backStack.add(PostDetailNavKey(postId = postId, nodeId = nodeId, initialTitle = title))
            },
            onNavigateToResaleDetail = { resaleId -> backStack.add(ResaleDetailNavKey(resaleId = resaleId)) },
            onNavigateToResaleMarket = { hobbyId, title -> backStack.add(ResaleMarketNavKey(hobbyId = hobbyId, title = title)) }
        )
    }

    entry<ZoneSettingsNavKey> {
        ZoneSettingsScreen(
            onNavigateBack = onBack
        )
    }

    entry<ForumHomeNavKey> {
        ForumHomeScreen(
            onNavigateBack = onBack,
            onNavigateToPost = { postId, nodeId, title ->
                backStack.add(PostDetailNavKey(postId = postId, nodeId = nodeId, initialTitle = title))
            },
            onNavigateToUser = { targetUserId -> backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId))) },
            onNavigateToSelf = { backStack.add(UserInfoNavKey(UserInfoNavParam(userId = 0L))) },
            onNavigateToMessage = { backStack.add(MessageCenterNavKey()) },
            onNavigateToPlates = { backStack.add(PlateSubscribeNavKey) }
        )
    }

    entry<PlateSubscribeNavKey> {
        PlateSubscribeScreen(
            onNavigateBack = onBack,
            onNavigateToLogin = { backStack.add(LoginNavKey) }
        )
    }

    entry<PostDetailNavKey> { key ->
        PostDetailScreen(
            postId = key.postId,
            initialTitle = key.initialTitle,
            nodeId = key.nodeId,
            onNavigateBack = onBack,
            onNavigateToDetail = { param -> backStack.add(HobbyDetailNavKey(param)) },
            onNavigateToZone = { nodeId -> backStack.add(ZoneDetailNavKey(nodeId = nodeId)) },
            onNavigateToUser = { targetUserId -> backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId))) },
            onNavigateToAlbum = { param -> backStack.add(AlbumDetailNavKey(param)) },
            onNavigateToCharacter = { param -> backStack.add(CharacterNavKey(param)) },
            onNavigateToPost = { targetPostId -> backStack.add(PostDetailNavKey(postId = targetPostId)) },
            onNavigateToCommentDetail = { param -> backStack.add(CommentDetailNavKey(param.copy(isDiscuss = true))) }
        )
    }
}
