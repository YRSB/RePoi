package com.nobody.repoi.ui.navigation.entries

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.album.AlbumDetailNavKey
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.message.block.MessageBlockScreen
import com.nobody.repoi.ui.message.center.MessageCenterScreen
import com.nobody.repoi.ui.message.chat.MessageChatScreen
import com.nobody.repoi.ui.message.notice.MessageNoticeScreen
import com.nobody.repoi.ui.navigation.CharacterNavKey
import com.nobody.repoi.ui.navigation.CharacterNavParam
import com.nobody.repoi.ui.navigation.CommentDetailNavKey
import com.nobody.repoi.ui.navigation.CommentDetailNavParam
import com.nobody.repoi.ui.navigation.HobbyDetailNavKey
import com.nobody.repoi.ui.navigation.MessageBlockNavKey
import com.nobody.repoi.ui.navigation.MessageCenterNavKey
import com.nobody.repoi.ui.navigation.MessageChatNavKey
import com.nobody.repoi.ui.navigation.MessageChatNavParam
import com.nobody.repoi.ui.navigation.MessageNoticeNavKey
import com.nobody.repoi.ui.navigation.MessageNoticeNavParam
import com.nobody.repoi.ui.navigation.PersonDetailNavKey
import com.nobody.repoi.ui.navigation.UserInfoNavKey
import com.nobody.repoi.ui.navigation.UserInfoNavParam
import com.nobody.repoi.ui.navigation.VendorDetailNavKey
import com.nobody.repoi.ui.navigation.VendorNavParam
import com.nobody.repoi.ui.navigation.WorksDetailNavKey
import com.nobody.repoi.ui.navigation.WorksNavParam
import com.nobody.repoi.ui.person.PersonNavParam

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
