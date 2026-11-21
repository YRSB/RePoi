package com.nobody.repoi.ui.navigation

import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.ui.common.ImageViewerScreen
import com.nobody.repoi.ui.common.ImageViewerStateHolder
import com.nobody.repoi.ui.common.LocalImageViewer
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
                navigator.commitSearchResult(resultParam)
            }
        )
    }

    entry<SearchResultNavKey> { key ->
        SearchResultScreen(
            navParam = key.param,
            onBack = onBack,
            onSearchFieldClick = { searchParam ->
                navigator.navigateToSearch(searchParam)
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

    entry<CommentDetailNavKey> { key ->
        val uriHandler = LocalUriHandler.current
        val messages = LocalAppMessages.current
        CommentDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onUrlClick = { url -> navigator.navigateUrl(url, uriHandler, messages::show) }
        )
    }

    entry<HobbyCommentsNavKey> { key ->
        val uriHandler = LocalUriHandler.current
        val messages = LocalAppMessages.current
        HobbyCommentsScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToCommentDetail = { commentParam -> navigator.navigateToCommentDetail(commentParam) },
            onUrlClick = { url -> navigator.navigateUrl(url, uriHandler, messages::show) }
        )
    }

    entry<PictureListNavKey> { key ->
        PictureListScreen(
            navParam = key.param,
            onBack = onBack
        )
    }

    entry<ImageViewerNavKey> { key ->
        ImageViewerScreen(
            param = key.param,
            onBack = onBack,
            state = LocalImageViewer.current as? ImageViewerStateHolder
        )
    }

    entry<ExHobbyListNavKey> { key ->
        ExHobbyListScreen(
            navParam = key,
            onBack = onBack
        )
    }
}

