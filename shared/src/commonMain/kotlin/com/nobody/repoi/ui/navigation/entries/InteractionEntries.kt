package com.nobody.repoi.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.NavDisplay
import com.nobody.repoi.ui.comment.CommentDetailScreen
import com.nobody.repoi.ui.comment.HobbyCommentsScreen
import com.nobody.repoi.ui.exhobby.ExHobbyListScreen
import com.nobody.repoi.ui.item.list.HobbyListScreen
import com.nobody.repoi.ui.picture.PictureListScreen
import com.nobody.repoi.ui.search.SearchLandingScreen
import com.nobody.repoi.ui.search.SearchNavParam
import com.nobody.repoi.ui.search.SearchResultScreen

/**
 * 注册搜索中心、手办筛选列表、评论列表/楼中楼、图片查看及 ExHobby 扩展等通用交互路由。
 */
fun EntryProviderScope<NavKey>.interactionEntries(
    navigator: RePoiNavigator,
    onBack: () -> Unit
) {
    entry<HobbyListNavKey> { key ->
        HobbyListScreen(
            param = key.param,
            onBack = onBack,
            onNavigateToDetail = { hobbyParam -> navigator.navigateToDetail(hobbyParam) }
        )
    }

    entry<SearchNavKey> { key ->
        SearchLandingScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToResult = { resultParam ->
                val stack = navigator.rootBackStack
                // 重搜栈深收敛：若前置栈为 [..., SearchResultNavKey, SearchNavKey]，弹出旧结果页与临时落地页
                if (stack.size >= 2 && stack.last() is SearchNavKey && stack[stack.size - 2] is SearchResultNavKey) {
                    stack.removeLastOrNull()
                    stack.removeLastOrNull()
                }
                navigator.navigateToSearchResult(resultParam)
            }
        )
    }

    entry<SearchResultNavKey> { key ->
        SearchResultScreen(
            navParam = key.param,
            onBack = onBack,
            onSearchFieldClick = {
                navigator.navigateToSearch(
                    SearchNavParam(
                        initialKeyword = key.param.keyword,
                        initialCategory = key.param.category,
                        initialCompanyNodeId = key.param.companyNodeId,
                        initialCompanyName = key.param.companyName
                    )
                )
            },
            onNavigateToDetail = { hobbyParam -> navigator.navigateToDetail(hobbyParam) },
            onNavigateToAlbum = { albumParam -> navigator.navigateToAlbum(albumParam) },
            onNavigateToVendor = { vendorParam -> navigator.navigateToVendor(vendorParam) },
            onNavigateToSeries = { seriesParam -> navigator.navigateToSeries(seriesParam) },
            onNavigateToPerson = { personParam -> navigator.navigateToPerson(personParam) },
            onNavigateToWorks = { worksParam -> navigator.navigateToWorks(worksParam) },
            onNavigateToCharacter = { characterParam -> navigator.navigateToCharacter(characterParam) }
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
            onNavigateToCommentDetail = { commentParam -> navigator.navigateToCommentDetail(commentParam) }
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

