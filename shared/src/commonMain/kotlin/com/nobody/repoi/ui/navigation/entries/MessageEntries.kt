package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.comment.CommentDetailNavParam
import com.nobody.repoi.ui.message.block.MessageBlockScreen
import com.nobody.repoi.ui.message.center.MessageCenterScreen
import com.nobody.repoi.ui.message.chat.MessageChatScreen
import com.nobody.repoi.ui.message.notice.MessageNoticeScreen

/**
 * 注册消息主中心、分类通知、1v1 私信聊天及黑名单管理路由。
 */
fun EntryProviderScope<NavKey>.messageEntries(
    backStack: NavBackStack<NavKey>,
    onBack: () -> Unit
) {
    entry<MessageCenterNavKey> {
        MessageCenterScreen(
            onNavigateBack = onBack,
            onNavigateToNotice = { type, typeNumber, title ->
                backStack.navigateToMessageNotice(type = type, typeNumber = typeNumber, title = title)
            },
            onNavigateToChat = { targetUserId, targetUserName, targetUserHeader ->
                backStack.navigateToMessageChat(
                    targetUserId = targetUserId,
                    targetUserName = targetUserName,
                    targetUserHeader = targetUserHeader
                )
            },
            onNavigateToBlock = { backStack.navigateToMessageBlock() }
        )
    }

    entry<MessageNoticeNavKey> { key ->
        MessageNoticeScreen(
            param = key.param,
            onNavigateBack = onBack,
            onNavigateToUser = { targetUserId -> backStack.navigateToUser(targetUserId) },
            onNavigateToHobby = { itemId -> backStack.navigateToDetail(itemId) },
            onNavigateToAlbum = { albumId -> backStack.navigateToAlbum(albumId) },
            onNavigateToVendor = { vendorId -> backStack.navigateToVendor(vendorId) },
            onNavigateToPerson = { personId -> backStack.navigateToPerson(personId) },
            onNavigateToWorks = { worksId -> backStack.navigateToWorks(worksId) },
            onNavigateToCharacter = { characterId -> backStack.navigateToCharacter(characterId) },
            onNavigateToCommentDetail = { commentId, relateItemId ->
                backStack.navigateToCommentDetail(
                    CommentDetailNavParam(
                        commentId = commentId,
                        relateNodeId = relateItemId
                    )
                )
            }
        )
    }

    entry<MessageChatNavKey> { key ->
        MessageChatScreen(
            param = key.param,
            onNavigateBack = onBack,
            onNavigateToUser = { targetUserId -> backStack.navigateToUser(targetUserId) },
            onNavigateToHobby = { itemId -> backStack.navigateToDetail(itemId) }
        )
    }

    entry<MessageBlockNavKey> {
        MessageBlockScreen(
            onNavigateBack = onBack,
            onNavigateToUser = { targetUserId -> backStack.navigateToUser(targetUserId) }
        )
    }
}
