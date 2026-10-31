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
    backStack: NavBackStack<NavKey>,
    onBack: () -> Unit
) {
    entry<HobbyDetailNavKey> { key ->
        HobbyDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { detailParam -> backStack.navigateToDetail(detailParam) },
            onNavigateToAlbum = { albumId -> backStack.navigateToAlbum(albumId) },
            onNavigateToAlbumList = { albumListParam -> backStack.navigateToHobbyAlbumList(albumListParam) },
            onNavigateToVendor = { vendorParam -> backStack.navigateToVendor(vendorParam) },
            onNavigateToSeries = { seriesParam -> backStack.navigateToSeries(seriesParam) },
            onNavigateToPerson = { personParam -> backStack.navigateToPerson(personParam) },
            onNavigateToWorks = { worksParam -> backStack.navigateToWorks(worksParam) },
            onNavigateToCharacter = { characterParam -> backStack.navigateToCharacter(characterParam) },
            onNavigateToHobbyList = { listParam -> backStack.navigateToHobbyList(listParam) },
            onNavigateToCollect = { collectParam -> backStack.navigateToCollectEdit(collectParam) },
            onNavigateToCommentDetail = { commentParam -> backStack.navigateToCommentDetail(commentParam) },
            onNavigateToHobbyComments = { commentsParam -> backStack.navigateToHobbyComments(commentsParam) },
            onNavigateToPictureList = { pictureParam -> backStack.navigateToPictureList(pictureParam) },
            onNavigateToExHobbyList = { exHobbyKey -> backStack.add(exHobbyKey) }
        )
    }

    entry<AlbumDetailNavKey> { key ->
        AlbumDetailScreen(
            param = key.param,
            onBack = onBack,
            onNavigateToHobbyDetail = { hobbyParam ->
                when (hobbyParam.itemType) {
                    "person" -> {
                        backStack.navigateToPerson(
                            PersonNavParam(
                                personId = hobbyParam.itemId,
                                initialName = hobbyParam.initialName,
                                initialCover = hobbyParam.initialCover
                            )
                        )
                    }
                    "charactar", "character" -> {
                        backStack.navigateToCharacter(
                            CharacterNavParam(
                                characterId = hobbyParam.itemId,
                                initialName = hobbyParam.initialName,
                                initialCover = hobbyParam.initialCover
                            )
                        )
                    }
                    else -> {
                        backStack.navigateToDetail(hobbyParam)
                    }
                }
            },
            onNavigateToAlbum = { albumId -> backStack.navigateToAlbum(albumId) },
            onNavigateToCommentDetail = { commentParam -> backStack.navigateToCommentDetail(commentParam) },
            onNavigateToComments = { commentsParam -> backStack.navigateToHobbyComments(commentsParam) },
            onNavigateToExHobbyList = { exHobbyKey -> backStack.add(exHobbyKey) }
        )
    }

    entry<AlbumRankListNavKey> { key ->
        AlbumRankListScreen(
            param = key.param,
            onBack = onBack,
            onNavigateToAlbum = { param -> backStack.navigateToAlbum(param) }
        )
    }

    entry<VendorDetailNavKey> { key ->
        VendorDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> backStack.navigateToDetail(hobbyParam) },
            onNavigateToSeries = { seriesParam -> backStack.navigateToSeries(seriesParam) },
            onNavigateToVendorActions = { companyNodeId, companyName ->
                backStack.navigateToVendorActions(companyNodeId, companyName)
            },
            onNavigateToLogin = { backStack.navigateToLogin() },
            onNavigateToSearch = { companyNodeId ->
                backStack.navigateToSearch(
                    category = 10000,
                    companyNodeId = companyNodeId,
                    companyName = key.param.initialName
                )
            },
            onNavigateToCommentDetail = { commentParam -> backStack.navigateToCommentDetail(commentParam) },
            onNavigateToComments = { commentsParam -> backStack.navigateToHobbyComments(commentsParam) }
        )
    }

    entry<VendorActionsNavKey> { key ->
        VendorActionsScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> backStack.navigateToDetail(hobbyParam) },
            onNavigateToAlbum = { albumParam -> backStack.navigateToAlbum(albumParam) },
            onNavigateToVendor = { vendorParam -> backStack.navigateToVendor(vendorParam) },
            onNavigateToCharacter = { characterParam -> backStack.navigateToCharacter(characterParam) },
            onNavigateToSeries = { seriesParam -> backStack.navigateToSeries(seriesParam) },
            onNavigateToWorks = { worksParam -> backStack.navigateToWorks(worksParam) },
            onNavigateToPerson = { personParam -> backStack.navigateToPerson(personParam) },
            onNavigateToUser = { targetUserId -> backStack.navigateToUser(targetUserId) }
        )
    }

    entry<SeriesDetailNavKey> { key ->
        SeriesDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> backStack.navigateToDetail(hobbyParam) },
            onNavigateToAlbum = { albumParam -> backStack.navigateToAlbum(albumParam) },
            onNavigateToSeries = { seriesParam -> backStack.navigateToSeries(seriesParam) },
            onNavigateToHobbyList = { listParam -> backStack.navigateToHobbyList(listParam) },
            onNavigateToAlbumList = { albumListParam -> backStack.navigateToHobbyAlbumList(albumListParam) },
            onNavigateToSearch = { keyword ->
                backStack.navigateToSearch(keyword = keyword, category = 10000)
            },
            onNavigateToCommentDetail = { commentParam -> backStack.navigateToCommentDetail(commentParam) },
            onNavigateToComments = { commentsParam -> backStack.navigateToHobbyComments(commentsParam) }
        )
    }

    entry<PersonDetailNavKey> { key ->
        PersonDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> backStack.navigateToDetail(hobbyParam) },
            onNavigateToHobbyList = { listParam -> backStack.navigateToHobbyList(listParam) },
            onNavigateToSearch = { keyword -> backStack.navigateToSearch(keyword = keyword) },
            onNavigateToWorks = { worksParam -> backStack.navigateToWorks(worksParam) },
            onNavigateToCharacter = { characterParam -> backStack.navigateToCharacter(characterParam) },
            onNavigateToCommentDetail = { commentParam -> backStack.navigateToCommentDetail(commentParam) },
            onNavigateToComments = { commentsParam -> backStack.navigateToHobbyComments(commentsParam) }
        )
    }

    entry<WorksDetailNavKey> { key ->
        WorksDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> backStack.navigateToDetail(hobbyParam) },
            onNavigateToAlbum = { albumParam -> backStack.navigateToAlbum(albumParam) },
            onNavigateToHobbyList = { listParam -> backStack.navigateToHobbyList(listParam) },
            onNavigateToCharacter = { characterParam -> backStack.navigateToCharacter(characterParam) },
            onNavigateToSearch = { keyword ->
                backStack.navigateToSearch(keyword = keyword, category = 10000)
            },
            onNavigateToCommentDetail = { commentParam -> backStack.navigateToCommentDetail(commentParam) },
            onNavigateToComments = { commentsParam -> backStack.navigateToHobbyComments(commentsParam) }
        )
    }

    entry<CharacterNavKey> { key ->
        CharacterDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> backStack.navigateToDetail(hobbyParam) },
            onNavigateToHobbyList = { listParam -> backStack.navigateToHobbyList(listParam) },
            onNavigateToWorks = { worksParam -> backStack.navigateToWorks(worksParam) },
            onNavigateToWorksList = { worksListParam -> backStack.navigateToCharacterWorksList(worksListParam) },
            onNavigateToPerson = { personParam -> backStack.navigateToPerson(personParam) },
            onNavigateToCharacter = { characterParam -> backStack.navigateToCharacter(characterParam) },
            onNavigateToAlbum = { albumParam -> backStack.navigateToAlbum(albumParam) },
            onNavigateToAlbumList = { albumListParam -> backStack.navigateToCharacterAlbumList(albumListParam) },
            onNavigateToSearch = { keyword -> backStack.navigateToSearch(keyword = keyword) },
            onNavigateToCommentDetail = { commentParam -> backStack.navigateToCommentDetail(commentParam) },
            onNavigateToComments = { commentsParam -> backStack.navigateToHobbyComments(commentsParam) }
        )
    }

    entry<CharacterAlbumListNavKey> { key ->
        CharacterAlbumListScreen(
            param = key.param,
            onBack = onBack,
            onNavigateToAlbum = { albumParam -> backStack.navigateToAlbum(albumParam) }
        )
    }

    entry<HobbyAlbumListNavKey> { key ->
        HobbyAlbumListScreen(
            param = key.param,
            onBack = onBack,
            onNavigateToAlbum = { albumParam -> backStack.navigateToAlbum(albumParam) }
        )
    }

    entry<CharacterWorksListNavKey> { key ->
        CharacterWorksListScreen(
            param = key.param,
            onBack = onBack,
            onNavigateToWorks = { worksParam -> backStack.navigateToWorks(worksParam) },
            onNavigateToPerson = { personParam -> backStack.navigateToPerson(personParam) }
        )
    }
}
