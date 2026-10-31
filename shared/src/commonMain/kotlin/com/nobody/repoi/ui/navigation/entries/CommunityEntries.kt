package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.EntryProviderScope
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
    navigator: RePoiNavigator,
    onBack: () -> Unit
) {
    entry<ZoneBrowseNavKey> {
        ZoneBrowseScreen(
            onNavigateBack = onBack,
            onNavigateToZoneDetail = { nodeId, name, icon, cover ->
                navigator.navigateToZoneDetail(
                    nodeId = nodeId,
                    initialName = name,
                    initialIcon = icon,
                    initialCover = cover
                )
            },
            onNavigateToSettings = { navigator.navigateToZoneSettings() }
        )
    }

    entry<ZoneDetailNavKey> { key ->
        ZoneDetailScreen(
            nodeId = key.nodeId,
            initialName = key.initialName,
            initialIcon = key.initialIcon,
            initialCover = key.initialCover,
            onNavigateBack = onBack,
            onNavigateToDetail = { param -> navigator.navigateToDetail(param) },
            onNavigateToAlbum = { param -> navigator.navigateToAlbum(param) },
            onNavigateToPerson = { param -> navigator.navigateToPerson(param) },
            onNavigateToWorks = { param -> navigator.navigateToWorks(param) },
            onNavigateToVendor = { param -> navigator.navigateToVendor(param) },
            onNavigateToCharacter = { param -> navigator.navigateToCharacter(param) },
            onNavigateToSeries = { param -> navigator.navigateToSeries(param) },
            onNavigateToUser = { targetUserId -> navigator.navigateToUser(targetUserId) },
            onNavigateToPost = { postId, nodeId, title ->
                navigator.navigateToPostDetail(postId = postId, initialTitle = title, nodeId = nodeId)
            },
            onNavigateToResaleDetail = { resaleId -> navigator.navigateToResaleDetail(resaleId) },
            onNavigateToResaleMarket = { hobbyId, title -> navigator.navigateToResaleMarket(hobbyId = hobbyId, title = title) }
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
                navigator.navigateToPostDetail(postId = postId, initialTitle = title, nodeId = nodeId)
            },
            onNavigateToUser = { targetUserId -> navigator.navigateToUser(targetUserId) },
            onNavigateToSelf = { navigator.navigateToUser(0L) },
            onNavigateToMessage = { navigator.navigateToMessageCenter() },
            onNavigateToPlates = { navigator.navigateToPlateSubscribe() }
        )
    }

    entry<PlateSubscribeNavKey> {
        PlateSubscribeScreen(
            onNavigateBack = onBack,
            onNavigateToLogin = { navigator.navigateToLogin() }
        )
    }

    entry<PostDetailNavKey> { key ->
        PostDetailScreen(
            postId = key.postId,
            initialTitle = key.initialTitle,
            nodeId = key.nodeId,
            onNavigateBack = onBack,
            onNavigateToDetail = { param -> navigator.navigateToDetail(param) },
            onNavigateToZone = { nodeId -> navigator.navigateToZoneDetail(nodeId = nodeId) },
            onNavigateToUser = { targetUserId -> navigator.navigateToUser(targetUserId) },
            onNavigateToAlbum = { param -> navigator.navigateToAlbum(param) },
            onNavigateToCharacter = { param -> navigator.navigateToCharacter(param) },
            onNavigateToPost = { targetPostId -> navigator.navigateToPostDetail(postId = targetPostId) },
            onNavigateToCommentDetail = { param -> navigator.navigateToCommentDetail(param.copy(isDiscuss = true)) }
        )
    }
}

