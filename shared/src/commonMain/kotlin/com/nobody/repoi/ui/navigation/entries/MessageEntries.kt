package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.message.block.MessageBlockScreen
import com.nobody.repoi.ui.message.center.MessageCenterScreen
import com.nobody.repoi.ui.message.chat.MessageChatScreen
import com.nobody.repoi.ui.message.notice.MessageNoticeScreen
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam

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
                backStack.add(
                    MessageNoticeNavKey(
                        param = MessageNoticeNavParam(
                            type = type,
                            typeNumber = typeNumber,
                            title = title
                        )
                    )
                )
            },
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
            },
            onNavigateToBlock = { backStack.add(MessageBlockNavKey()) }
        )
    }

    entry<MessageNoticeNavKey> { key ->
        MessageNoticeScreen(
            param = key.param,
            onNavigateBack = onBack,
            onNavigateToUser = { targetUserId -> backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId))) },
            onNavigateToHobby = { itemId -> backStack.add(HobbyDetailNavKey(itemId = itemId)) },
            onNavigateToAlbum = { albumId -> backStack.add(AlbumDetailNavKey(AlbumNavParam(albumId = albumId))) },
            onNavigateToVendor = { vendorId -> backStack.add(VendorDetailNavKey(VendorNavParam(companyId = vendorId))) },
            onNavigateToPerson = { personId -> backStack.add(PersonDetailNavKey(PersonNavParam(personId = personId))) },
            onNavigateToWorks = { worksId -> backStack.add(WorksDetailNavKey(WorksNavParam(worksId = worksId))) },
            onNavigateToCharacter = { characterId -> backStack.add(CharacterNavKey(CharacterNavParam(characterId = characterId))) },
            onNavigateToCommentDetail = { commentId, relateItemId ->
                backStack.add(
                    CommentDetailNavKey(
                        CommentDetailNavParam(
                            commentId = commentId,
                            relateNodeId = relateItemId
                        )
                    )
                )
            }
        )
    }

    entry<MessageChatNavKey> { key ->
        MessageChatScreen(
            param = key.param,
            onNavigateBack = onBack,
            onNavigateToUser = { targetUserId -> backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId))) },
            onNavigateToHobby = { itemId -> backStack.add(HobbyDetailNavKey(itemId = itemId)) }
        )
    }

    entry<MessageBlockNavKey> {
        MessageBlockScreen(
            onNavigateBack = onBack,
            onNavigateToUser = { targetUserId -> backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId))) }
        )
    }
}
