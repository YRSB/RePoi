package com.nobody.repoi.ui.navigation.entries

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.navigation.HobbyDetailNavKey
import com.nobody.repoi.ui.navigation.MessageChatNavKey
import com.nobody.repoi.ui.navigation.MessageChatNavParam
import com.nobody.repoi.ui.navigation.ResaleDetailNavKey
import com.nobody.repoi.ui.navigation.ResaleMarketNavKey
import com.nobody.repoi.ui.navigation.UserInfoNavKey
import com.nobody.repoi.ui.navigation.UserInfoNavParam
import com.nobody.repoi.ui.resale.ResaleDetailScreen
import com.nobody.repoi.ui.resale.ResaleMarketScreen

/**
 * 注册二手交易详情与同款二手市场路由。
 */
fun EntryProviderScope<NavKey>.marketEntries(
    backStack: NavBackStack<NavKey>,
    onBack: () -> Unit
) {
    entry<ResaleDetailNavKey> { key ->
        ResaleDetailScreen(
            resaleId = key.resaleId,
            onBack = onBack,
            onNavigateToHobby = { hobbyId -> backStack.add(HobbyDetailNavKey(DetailNavParam(itemId = hobbyId))) },
            onNavigateToUser = { targetUserId -> backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId))) }
        )
    }

    entry<ResaleMarketNavKey> { key ->
        ResaleMarketScreen(
            hobbyId = key.hobbyId,
            title = key.title,
            onBack = onBack,
            onNavigateToResaleDetail = { resaleId -> backStack.add(ResaleDetailNavKey(resaleId = resaleId)) },
            onNavigateToHobby = { hobbyId -> backStack.add(HobbyDetailNavKey(DetailNavParam(itemId = hobbyId))) },
            onNavigateToUser = { targetUserId -> backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId))) },
            onNavigateToChat = { targetUserId, nickname ->
                backStack.add(
                    MessageChatNavKey(
                        param = MessageChatNavParam(
                            targetUserId = targetUserId,
                            targetUserName = nickname
                        )
                    )
                )
            }
        )
    }
}
