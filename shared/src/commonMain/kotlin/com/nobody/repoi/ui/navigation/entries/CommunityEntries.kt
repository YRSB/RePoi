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
                backStack.navigateToZoneDetail(
                    nodeId = nodeId,
                    initialName = name,
                    initialIcon = icon,
                    initialCover = cover
                )
            },
            onNavigateToSettings = { backStack.navigateToZoneSettings() }
        )
    }

    entry<ZoneDetailNavKey> { key ->
        ZoneDetailScreen(
            nodeId = key.nodeId,
            initialName = key.initialName,
            initialIcon = key.initialIcon,
            initialCover = key.initialCover,
            onNavigateBack = onBack,
            onNavigateToDetail = { param -> backStack.navigateToDetail(param) },
            onNavigateToAlbum = { param -> backStack.navigateToAlbum(param) },
            onNavigateToPerson = { param -> backStack.navigateToPerson(param) },
            onNavigateToWorks = { param -> backStack.navigateToWorks(param) },
            onNavigateToVendor = { param -> backStack.navigateToVendor(param) },
            onNavigateToCharacter = { param -> backStack.navigateToCharacter(param) },
            onNavigateToSeries = { param -> backStack.navigateToSeries(param) },
            onNavigateToUser = { targetUserId -> backStack.navigateToUser(targetUserId) },
            onNavigateToPost = { postId, nodeId, title ->
                backStack.navigateToPostDetail(postId = postId, initialTitle = title, nodeId = nodeId)
            },
            onNavigateToResaleDetail = { resaleId -> backStack.navigateToResaleDetail(resaleId) },
            onNavigateToResaleMarket = { hobbyId, title -> backStack.navigateToResaleMarket(hobbyId = hobbyId, title = title) }
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
                backStack.navigateToPostDetail(postId = postId, initialTitle = title, nodeId = nodeId)
            },
            onNavigateToUser = { targetUserId -> backStack.navigateToUser(targetUserId) },
            onNavigateToSelf = { backStack.navigateToUser(0L) },
            onNavigateToMessage = { backStack.navigateToMessageCenter() },
            onNavigateToPlates = { backStack.navigateToPlateSubscribe() }
        )
    }

    entry<PlateSubscribeNavKey> {
        PlateSubscribeScreen(
            onNavigateBack = onBack,
            onNavigateToLogin = { backStack.navigateToLogin() }
        )
    }

    entry<PostDetailNavKey> { key ->
        PostDetailScreen(
            postId = key.postId,
            initialTitle = key.initialTitle,
            nodeId = key.nodeId,
            onNavigateBack = onBack,
            onNavigateToDetail = { param -> backStack.navigateToDetail(param) },
            onNavigateToZone = { nodeId -> backStack.navigateToZoneDetail(nodeId = nodeId) },
            onNavigateToUser = { targetUserId -> backStack.navigateToUser(targetUserId) },
            onNavigateToAlbum = { param -> backStack.navigateToAlbum(param) },
            onNavigateToCharacter = { param -> backStack.navigateToCharacter(param) },
            onNavigateToPost = { targetPostId -> backStack.navigateToPostDetail(postId = targetPostId) },
            onNavigateToCommentDetail = { param -> backStack.navigateToCommentDetail(param.copy(isDiscuss = true)) }
        )
    }
}
