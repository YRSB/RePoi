package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.album.AlbumDetailScreen
import com.nobody.repoi.ui.album.AlbumRankListScreen
import com.nobody.repoi.ui.character.CharacterAlbumListScreen
import com.nobody.repoi.ui.character.CharacterDetailScreen
import com.nobody.repoi.ui.character.CharacterWorksListScreen
import com.nobody.repoi.ui.detail.HobbyDetailScreen
import com.nobody.repoi.ui.detail.album.HobbyAlbumListScreen
import com.nobody.repoi.ui.person.PersonDetailScreen
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.series.SeriesDetailScreen
import com.nobody.repoi.ui.vendor.VendorDetailScreen
import com.nobody.repoi.ui.vendor.actions.VendorActionsScreen
import com.nobody.repoi.ui.works.WorksDetailScreen

/**
 * 注册手办、相册、角色、厂商、人物、系列与作品等详情链路由。
 */
fun EntryProviderScope<NavKey>.detailEntries(
    navigator: RePoiNavigator,
    onBack: () -> Unit
) {
    entry<HobbyDetailNavKey> { key ->
        HobbyDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { detailParam -> navigator.navigateToDetail(detailParam) },
            onNavigateToAlbum = { albumId -> navigator.navigateToAlbum(albumId) },
            onNavigateToAlbumList = { albumListParam -> navigator.navigateToHobbyAlbumList(albumListParam) },
            onNavigateToVendor = { vendorParam -> navigator.navigateToVendor(vendorParam) },
            onNavigateToSeries = { seriesParam -> navigator.navigateToSeries(seriesParam) },
            onNavigateToPerson = { personParam -> navigator.navigateToPerson(personParam) },
            onNavigateToWorks = { worksParam -> navigator.navigateToWorks(worksParam) },
            onNavigateToCharacter = { characterParam -> navigator.navigateToCharacter(characterParam) },
            onNavigateToHobbyList = { listParam -> navigator.navigateToHobbyList(listParam) },
            onNavigateToCollect = { collectParam -> navigator.navigateToCollectEdit(collectParam) },
            onNavigateToCommentDetail = { commentParam -> navigator.navigateToCommentDetail(commentParam) },
            onNavigateToHobbyComments = { commentsParam -> navigator.navigateToHobbyComments(commentsParam) },
            onNavigateToPictureList = { pictureParam -> navigator.navigateToPictureList(pictureParam) },
            onNavigateToExHobbyList = { exHobbyKey -> navigator.navigate(exHobbyKey) }
        )
    }

    entry<AlbumDetailNavKey> { key ->
        AlbumDetailScreen(
            param = key.param,
            onBack = onBack,
            onNavigateToHobbyDetail = { hobbyParam ->
                when (hobbyParam.itemType) {
                    "person" -> {
                        navigator.navigateToPerson(
                            PersonNavParam(
                                personId = hobbyParam.itemId,
                                initialName = hobbyParam.initialName,
                                initialCover = hobbyParam.initialCover
                            )
                        )
                    }
                    "charactar", "character" -> {
                        navigator.navigateToCharacter(
                            CharacterNavParam(
                                characterId = hobbyParam.itemId,
                                initialName = hobbyParam.initialName,
                                initialCover = hobbyParam.initialCover
                            )
                        )
                    }
                    else -> {
                        navigator.navigateToDetail(hobbyParam)
                    }
                }
            },
            onNavigateToAlbum = { albumId -> navigator.navigateToAlbum(albumId) },
            onNavigateToCommentDetail = { commentParam -> navigator.navigateToCommentDetail(commentParam) },
            onNavigateToComments = { commentsParam -> navigator.navigateToHobbyComments(commentsParam) },
            onNavigateToExHobbyList = { exHobbyKey -> navigator.navigate(exHobbyKey) }
        )
    }

    entry<AlbumRankListNavKey> { key ->
        AlbumRankListScreen(
            param = key.param,
            onBack = onBack,
            onNavigateToAlbum = { param -> navigator.navigateToAlbum(param) }
        )
    }

    entry<VendorDetailNavKey> { key ->
        VendorDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> navigator.navigateToDetail(hobbyParam) },
            onNavigateToSeries = { seriesParam -> navigator.navigateToSeries(seriesParam) },
            onNavigateToVendorActions = { companyNodeId, companyName ->
                navigator.navigateToVendorActions(companyNodeId, companyName)
            },
            onNavigateToLogin = { navigator.navigateToLogin() },
            onNavigateToSearch = { companyNodeId ->
                navigator.navigateToSearch(
                    category = 10000,
                    companyNodeId = companyNodeId,
                    companyName = key.param.initialName
                )
            },
            onNavigateToCommentDetail = { commentParam -> navigator.navigateToCommentDetail(commentParam) },
            onNavigateToComments = { commentsParam -> navigator.navigateToHobbyComments(commentsParam) }
        )
    }

    entry<VendorActionsNavKey> { key ->
        VendorActionsScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> navigator.navigateToDetail(hobbyParam) },
            onNavigateToAlbum = { albumParam -> navigator.navigateToAlbum(albumParam) },
            onNavigateToVendor = { vendorParam -> navigator.navigateToVendor(vendorParam) },
            onNavigateToCharacter = { characterParam -> navigator.navigateToCharacter(characterParam) },
            onNavigateToSeries = { seriesParam -> navigator.navigateToSeries(seriesParam) },
            onNavigateToWorks = { worksParam -> navigator.navigateToWorks(worksParam) },
            onNavigateToPerson = { personParam -> navigator.navigateToPerson(personParam) },
            onNavigateToUser = { targetUserId -> navigator.navigateToUser(targetUserId) }
        )
    }

    entry<SeriesDetailNavKey> { key ->
        SeriesDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> navigator.navigateToDetail(hobbyParam) },
            onNavigateToAlbum = { albumParam -> navigator.navigateToAlbum(albumParam) },
            onNavigateToSeries = { seriesParam -> navigator.navigateToSeries(seriesParam) },
            onNavigateToHobbyList = { listParam -> navigator.navigateToHobbyList(listParam) },
            onNavigateToAlbumList = { albumListParam -> navigator.navigateToHobbyAlbumList(albumListParam) },
            onNavigateToSearch = { keyword ->
                navigator.navigateToSearch(keyword = keyword, category = 10000)
            },
            onNavigateToCommentDetail = { commentParam -> navigator.navigateToCommentDetail(commentParam) },
            onNavigateToComments = { commentsParam -> navigator.navigateToHobbyComments(commentsParam) }
        )
    }

    entry<PersonDetailNavKey> { key ->
        PersonDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> navigator.navigateToDetail(hobbyParam) },
            onNavigateToHobbyList = { listParam -> navigator.navigateToHobbyList(listParam) },
            onNavigateToSearch = { keyword -> navigator.navigateToSearch(keyword = keyword) },
            onNavigateToWorks = { worksParam -> navigator.navigateToWorks(worksParam) },
            onNavigateToCharacter = { characterParam -> navigator.navigateToCharacter(characterParam) },
            onNavigateToCommentDetail = { commentParam -> navigator.navigateToCommentDetail(commentParam) },
            onNavigateToComments = { commentsParam -> navigator.navigateToHobbyComments(commentsParam) }
        )
    }

    entry<WorksDetailNavKey> { key ->
        WorksDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> navigator.navigateToDetail(hobbyParam) },
            onNavigateToAlbum = { albumParam -> navigator.navigateToAlbum(albumParam) },
            onNavigateToHobbyList = { listParam -> navigator.navigateToHobbyList(listParam) },
            onNavigateToCharacter = { characterParam -> navigator.navigateToCharacter(characterParam) },
            onNavigateToSearch = { keyword ->
                navigator.navigateToSearch(keyword = keyword, category = 10000)
            },
            onNavigateToCommentDetail = { commentParam -> navigator.navigateToCommentDetail(commentParam) },
            onNavigateToComments = { commentsParam -> navigator.navigateToHobbyComments(commentsParam) }
        )
    }

    entry<CharacterNavKey> { key ->
        CharacterDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> navigator.navigateToDetail(hobbyParam) },
            onNavigateToHobbyList = { listParam -> navigator.navigateToHobbyList(listParam) },
            onNavigateToWorks = { worksParam -> navigator.navigateToWorks(worksParam) },
            onNavigateToWorksList = { worksListParam -> navigator.navigateToCharacterWorksList(worksListParam) },
            onNavigateToPerson = { personParam -> navigator.navigateToPerson(personParam) },
            onNavigateToCharacter = { characterParam -> navigator.navigateToCharacter(characterParam) },
            onNavigateToAlbum = { albumParam -> navigator.navigateToAlbum(albumParam) },
            onNavigateToAlbumList = { albumListParam -> navigator.navigateToCharacterAlbumList(albumListParam) },
            onNavigateToSearch = { keyword -> navigator.navigateToSearch(keyword = keyword) },
            onNavigateToCommentDetail = { commentParam -> navigator.navigateToCommentDetail(commentParam) },
            onNavigateToComments = { commentsParam -> navigator.navigateToHobbyComments(commentsParam) }
        )
    }

    entry<CharacterAlbumListNavKey> { key ->
        CharacterAlbumListScreen(
            param = key.param,
            onBack = onBack,
            onNavigateToAlbum = { albumParam -> navigator.navigateToAlbum(albumParam) }
        )
    }

    entry<HobbyAlbumListNavKey> { key ->
        HobbyAlbumListScreen(
            param = key.param,
            onBack = onBack,
            onNavigateToAlbum = { albumParam -> navigator.navigateToAlbum(albumParam) }
        )
    }

    entry<CharacterWorksListNavKey> { key ->
        CharacterWorksListScreen(
            param = key.param,
            onBack = onBack,
            onNavigateToWorks = { worksParam -> navigator.navigateToWorks(worksParam) },
            onNavigateToPerson = { personParam -> navigator.navigateToPerson(personParam) }
        )
    }
}

/**
 * 单栈兼容重载。
 */
fun EntryProviderScope<NavKey>.detailEntries(
    backStack: NavBackStack<NavKey>,
    onBack: () -> Unit
) {
    detailEntries(RePoiNavigator(backStack), onBack)
}
