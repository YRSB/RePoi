package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.EntryProviderScope
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
    navigator: RePoiNavigator,
    onBack: () -> Unit
) {
    entry<MessageCenterNavKey> {
        MessageCenterScreen(
            onNavigateBack = onBack,
            onNavigateToNotice = { type, typeNumber, title ->
                navigator.navigateToMessageNotice(type = type, typeNumber = typeNumber, title = title)
            },
            onNavigateToChat = { targetUserId, targetUserName, targetUserHeader ->
                navigator.navigateToMessageChat(
                    targetUserId = targetUserId,
                    targetUserName = targetUserName,
                    targetUserHeader = targetUserHeader
                )
            },
            onNavigateToBlock = { navigator.navigateToMessageBlock() }
        )
    }

    entry<MessageNoticeNavKey> { key ->
        MessageNoticeScreen(
            param = key.param,
            onNavigateBack = onBack,
            onNavigateToUser = { targetUserId -> navigator.navigateToUser(targetUserId) },
            onNavigateToHobby = { itemId -> navigator.navigateToDetail(itemId) },
            onNavigateToAlbum = { albumId -> navigator.navigateToAlbum(albumId) },
            onNavigateToVendor = { vendorId -> navigator.navigateToVendor(vendorId) },
            onNavigateToPerson = { personId -> navigator.navigateToPerson(personId) },
            onNavigateToWorks = { worksId -> navigator.navigateToWorks(worksId) },
            onNavigateToCharacter = { characterId -> navigator.navigateToCharacter(characterId) },
            onNavigateToCommentDetail = { commentId, relateItemId ->
                navigator.navigateToCommentDetail(
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
            onNavigateToUser = { targetUserId -> navigator.navigateToUser(targetUserId) },
            onNavigateToHobby = { itemId -> navigator.navigateToDetail(itemId) }
        )
    }

    entry<MessageBlockNavKey> {
        MessageBlockScreen(
            onNavigateBack = onBack,
            onNavigateToUser = { targetUserId -> navigator.navigateToUser(targetUserId) }
        )
    }
}

