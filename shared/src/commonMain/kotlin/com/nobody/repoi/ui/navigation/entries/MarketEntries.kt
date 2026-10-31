package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.resale.ResaleDetailScreen
import com.nobody.repoi.ui.resale.ResaleMarketScreen

/**
 * 注册二手交易详情与同款二手市场路由。
 */
fun EntryProviderScope<NavKey>.marketEntries(
    navigator: RePoiNavigator,
    onBack: () -> Unit
) {
    entry<ResaleDetailNavKey> { key ->
        ResaleDetailScreen(
            resaleId = key.resaleId,
            onBack = onBack,
            onNavigateToHobby = { hobbyId -> navigator.navigateToDetail(hobbyId) },
            onNavigateToUser = { targetUserId -> navigator.navigateToUser(targetUserId) }
        )
    }

    entry<ResaleMarketNavKey> { key ->
        ResaleMarketScreen(
            hobbyId = key.hobbyId,
            title = key.title,
            onBack = onBack,
            onNavigateToResaleDetail = { resaleId -> navigator.navigateToResaleDetail(resaleId) },
            onNavigateToHobby = { hobbyId -> navigator.navigateToDetail(hobbyId) },
            onNavigateToUser = { targetUserId -> navigator.navigateToUser(targetUserId) },
            onNavigateToChat = { targetUserId, nickname ->
                navigator.navigateToMessageChat(targetUserId = targetUserId, targetUserName = nickname)
            }
        )
    }
}

