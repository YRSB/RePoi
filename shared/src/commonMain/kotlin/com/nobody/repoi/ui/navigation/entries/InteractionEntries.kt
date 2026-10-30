package com.nobody.repoi.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.NavDisplay
import com.nobody.repoi.ui.comment.CommentDetailScreen
import com.nobody.repoi.ui.comment.HobbyCommentsScreen
import com.nobody.repoi.ui.exhobby.ExHobbyListScreen
import com.nobody.repoi.ui.item.list.HobbyListScreen
import com.nobody.repoi.ui.picture.PictureListScreen
import com.nobody.repoi.ui.search.SearchScreen

/**
 * 注册搜索中心、手办筛选列表、评论列表/楼中楼、图片查看及 ExHobby 扩展等通用交互路由。
 */
fun EntryProviderScope<NavKey>.interactionEntries(
    backStack: NavBackStack<NavKey>,
    onBack: () -> Unit
) {
    entry<HobbyListNavKey> { key ->
        HobbyListScreen(
            param = key.param,
            onBack = onBack,
            onNavigateToDetail = { hobbyParam -> backStack.add(HobbyDetailNavKey(hobbyParam)) }
        )
    }

    entry<SearchNavKey>(
        metadata = metadata {
            put(NavDisplay.TransitionKey) {
                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
            }
            put(NavDisplay.PopTransitionKey) {
                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(200))
            }
            put(NavDisplay.PredictivePopTransitionKey) {
                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(200))
            }
        }
    ) { key ->
        SearchScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToDetail = { hobbyParam -> backStack.add(HobbyDetailNavKey(hobbyParam)) },
            onNavigateToAlbum = { albumParam -> backStack.add(AlbumDetailNavKey(albumParam)) },
            onNavigateToVendor = { vendorParam -> backStack.add(VendorDetailNavKey(vendorParam)) },
            onNavigateToSeries = { seriesParam -> backStack.add(SeriesDetailNavKey(seriesParam)) },
            onNavigateToPerson = { personParam -> backStack.add(PersonDetailNavKey(personParam)) },
            onNavigateToWorks = { worksParam -> backStack.add(WorksDetailNavKey(worksParam)) },
            onNavigateToCharacter = { characterParam -> backStack.add(CharacterNavKey(characterParam)) }
        )
    }

    entry<CommentDetailNavKey>(
        metadata = metadata {
            put(NavDisplay.TransitionKey) {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300)
                ) togetherWith fadeOut(animationSpec = tween(150))
            }
            put(NavDisplay.PopTransitionKey) {
                fadeIn(animationSpec = tween(150)) togetherWith slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(250)
                )
            }
            put(NavDisplay.PredictivePopTransitionKey) {
                fadeIn(animationSpec = tween(150)) togetherWith slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(250)
                )
            }
        }
    ) { key ->
        CommentDetailScreen(
            navParam = key.param,
            onBack = onBack
        )
    }

    entry<HobbyCommentsNavKey>(
        metadata = metadata {
            put(NavDisplay.TransitionKey) {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300)
                ) togetherWith fadeOut(animationSpec = tween(150))
            }
            put(NavDisplay.PopTransitionKey) {
                fadeIn(animationSpec = tween(150)) togetherWith slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(250)
                )
            }
            put(NavDisplay.PredictivePopTransitionKey) {
                fadeIn(animationSpec = tween(150)) togetherWith slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(250)
                )
            }
        }
    ) { key ->
        HobbyCommentsScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToCommentDetail = { commentParam -> backStack.add(CommentDetailNavKey(commentParam)) }
        )
    }

    entry<PictureListNavKey> { key ->
        PictureListScreen(
            navParam = key.param,
            onBack = onBack
        )
    }

    entry<ExHobbyListNavKey> { key ->
        ExHobbyListScreen(
            navParam = key,
            onBack = onBack
        )
    }
}
