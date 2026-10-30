package com.nobody.repoi.ui.navigation.entries

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.album.AlbumDetailNavKey
import com.nobody.repoi.ui.album.AlbumDetailScreen
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.album.AlbumRankListNavKey
import com.nobody.repoi.ui.album.AlbumRankListScreen
import com.nobody.repoi.ui.character.CharacterAlbumListNavKey
import com.nobody.repoi.ui.character.CharacterAlbumListScreen
import com.nobody.repoi.ui.character.CharacterDetailScreen
import com.nobody.repoi.ui.character.CharacterWorksListNavKey
import com.nobody.repoi.ui.character.CharacterWorksListScreen
import com.nobody.repoi.ui.detail.HobbyDetailScreen
import com.nobody.repoi.ui.detail.album.HobbyAlbumListNavKey
import com.nobody.repoi.ui.detail.album.HobbyAlbumListScreen
import com.nobody.repoi.ui.item.list.HobbyListNavKey
import com.nobody.repoi.ui.navigation.CharacterAlbumListNavParam
import com.nobody.repoi.ui.navigation.CharacterNavKey
import com.nobody.repoi.ui.navigation.CharacterNavParam
import com.nobody.repoi.ui.navigation.CharacterWorksListNavParam
import com.nobody.repoi.ui.navigation.CollectEditNavKey
import com.nobody.repoi.ui.navigation.CommentDetailNavKey
import com.nobody.repoi.ui.navigation.HobbyAlbumListNavParam
import com.nobody.repoi.ui.navigation.HobbyCommentsNavKey
import com.nobody.repoi.ui.navigation.HobbyDetailNavKey
import com.nobody.repoi.ui.navigation.LoginNavKey
import com.nobody.repoi.ui.navigation.PersonDetailNavKey
import com.nobody.repoi.ui.navigation.PictureListNavKey
import com.nobody.repoi.ui.navigation.SearchNavKey
import com.nobody.repoi.ui.navigation.SeriesDetailNavKey
import com.nobody.repoi.ui.navigation.UserInfoNavKey
import com.nobody.repoi.ui.navigation.UserInfoNavParam
import com.nobody.repoi.ui.navigation.VendorActionsNavKey
import com.nobody.repoi.ui.navigation.VendorActionsNavParam
import com.nobody.repoi.ui.navigation.VendorDetailNavKey
import com.nobody.repoi.ui.navigation.WorksDetailNavKey
import com.nobody.repoi.ui.person.PersonDetailScreen
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.search.SearchNavParam
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
            onNavigateToHobby = { detailParam -> backStack.add(HobbyDetailNavKey(detailParam)) },
            onNavigateToAlbum = { albumId -> backStack.add(AlbumDetailNavKey(AlbumNavParam(albumId = albumId))) },
            onNavigateToAlbumList = { albumListParam -> backStack.add(HobbyAlbumListNavKey(albumListParam)) },
            onNavigateToVendor = { vendorParam -> backStack.add(VendorDetailNavKey(vendorParam)) },
            onNavigateToSeries = { seriesParam -> backStack.add(SeriesDetailNavKey(seriesParam)) },
            onNavigateToPerson = { personParam -> backStack.add(PersonDetailNavKey(personParam)) },
            onNavigateToWorks = { worksParam -> backStack.add(WorksDetailNavKey(worksParam)) },
            onNavigateToCharacter = { characterParam -> backStack.add(CharacterNavKey(characterParam)) },
            onNavigateToHobbyList = { listParam -> backStack.add(HobbyListNavKey(listParam)) },
            onNavigateToCollect = { collectParam -> backStack.add(CollectEditNavKey(collectParam)) },
            onNavigateToCommentDetail = { commentParam -> backStack.add(CommentDetailNavKey(commentParam)) },
            onNavigateToHobbyComments = { commentsParam -> backStack.add(HobbyCommentsNavKey(commentsParam)) },
            onNavigateToPictureList = { pictureParam -> backStack.add(PictureListNavKey(pictureParam)) },
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
                        backStack.add(
                            PersonDetailNavKey(
                                PersonNavParam(
                                    personId = hobbyParam.itemId,
                                    initialName = hobbyParam.initialName,
                                    initialCover = hobbyParam.initialCover
                                )
                            )
                        )
                    }
                    "charactar", "character" -> {
                        backStack.add(
                            CharacterNavKey(
                                CharacterNavParam(
                                    characterId = hobbyParam.itemId,
                                    initialName = hobbyParam.initialName,
                                    initialCover = hobbyParam.initialCover
                                )
                            )
                        )
                    }
                    else -> {
                        backStack.add(HobbyDetailNavKey(hobbyParam))
                    }
                }
            },
            onNavigateToAlbum = { albumId -> backStack.add(AlbumDetailNavKey(AlbumNavParam(albumId = albumId))) },
            onNavigateToCommentDetail = { commentParam -> backStack.add(CommentDetailNavKey(commentParam)) },
            onNavigateToComments = { commentsParam -> backStack.add(HobbyCommentsNavKey(commentsParam)) },
            onNavigateToExHobbyList = { exHobbyKey -> backStack.add(exHobbyKey) }
        )
    }

    entry<AlbumRankListNavKey> { key ->
        AlbumRankListScreen(
            param = key.param,
            onBack = onBack,
            onNavigateToAlbum = { param -> backStack.add(AlbumDetailNavKey(param)) }
        )
    }

    entry<VendorDetailNavKey> { key ->
        VendorDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> backStack.add(HobbyDetailNavKey(hobbyParam)) },
            onNavigateToSeries = { seriesParam -> backStack.add(SeriesDetailNavKey(seriesParam)) },
            onNavigateToVendorActions = { companyNodeId, companyName ->
                backStack.add(
                    VendorActionsNavKey(
                        param = VendorActionsNavParam(
                            companyNodeId = companyNodeId,
                            companyName = companyName
                        )
                    )
                )
            },
            onNavigateToLogin = { backStack.add(LoginNavKey) },
            onNavigateToSearch = { companyNodeId ->
                backStack.add(
                    SearchNavKey(
                        param = SearchNavParam(
                            initialCategory = 10000,
                            initialCompanyNodeId = companyNodeId,
                            initialCompanyName = key.param.initialName
                        )
                    )
                )
            },
            onNavigateToCommentDetail = { commentParam -> backStack.add(CommentDetailNavKey(commentParam)) },
            onNavigateToComments = { commentsParam -> backStack.add(HobbyCommentsNavKey(commentsParam)) }
        )
    }

    entry<VendorActionsNavKey> { key ->
        VendorActionsScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> backStack.add(HobbyDetailNavKey(hobbyParam)) },
            onNavigateToAlbum = { albumParam -> backStack.add(AlbumDetailNavKey(albumParam)) },
            onNavigateToVendor = { vendorParam -> backStack.add(VendorDetailNavKey(vendorParam)) },
            onNavigateToCharacter = { characterParam -> backStack.add(CharacterNavKey(characterParam)) },
            onNavigateToSeries = { seriesParam -> backStack.add(SeriesDetailNavKey(seriesParam)) },
            onNavigateToWorks = { worksParam -> backStack.add(WorksDetailNavKey(worksParam)) },
            onNavigateToPerson = { personParam -> backStack.add(PersonDetailNavKey(personParam)) },
            onNavigateToUser = { targetUserId -> backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId))) }
        )
    }

    entry<SeriesDetailNavKey> { key ->
        SeriesDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> backStack.add(HobbyDetailNavKey(hobbyParam)) },
            onNavigateToAlbum = { albumParam -> backStack.add(AlbumDetailNavKey(albumParam)) },
            onNavigateToSeries = { seriesParam -> backStack.add(SeriesDetailNavKey(seriesParam)) },
            onNavigateToHobbyList = { listParam -> backStack.add(HobbyListNavKey(listParam)) },
            onNavigateToAlbumList = { albumListParam -> backStack.add(HobbyAlbumListNavKey(albumListParam)) },
            onNavigateToSearch = { keyword ->
                backStack.add(
                    SearchNavKey(
                        param = SearchNavParam(
                            initialKeyword = keyword,
                            initialCategory = 10000
                        )
                    )
                )
            },
            onNavigateToCommentDetail = { commentParam -> backStack.add(CommentDetailNavKey(commentParam)) },
            onNavigateToComments = { commentsParam -> backStack.add(HobbyCommentsNavKey(commentsParam)) }
        )
    }

    entry<PersonDetailNavKey> { key ->
        PersonDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> backStack.add(HobbyDetailNavKey(hobbyParam)) },
            onNavigateToHobbyList = { listParam -> backStack.add(HobbyListNavKey(listParam)) },
            onNavigateToSearch = { keyword -> backStack.add(SearchNavKey(SearchNavParam(initialKeyword = keyword))) },
            onNavigateToWorks = { worksParam -> backStack.add(WorksDetailNavKey(worksParam)) },
            onNavigateToCharacter = { characterParam -> backStack.add(CharacterNavKey(characterParam)) },
            onNavigateToCommentDetail = { commentParam -> backStack.add(CommentDetailNavKey(commentParam)) },
            onNavigateToComments = { commentsParam -> backStack.add(HobbyCommentsNavKey(commentsParam)) }
        )
    }

    entry<WorksDetailNavKey> { key ->
        WorksDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> backStack.add(HobbyDetailNavKey(hobbyParam)) },
            onNavigateToAlbum = { albumParam -> backStack.add(AlbumDetailNavKey(albumParam)) },
            onNavigateToHobbyList = { listParam -> backStack.add(HobbyListNavKey(listParam)) },
            onNavigateToCharacter = { characterParam -> backStack.add(CharacterNavKey(characterParam)) },
            onNavigateToSearch = { keyword ->
                backStack.add(
                    SearchNavKey(
                        param = SearchNavParam(
                            initialKeyword = keyword,
                            initialCategory = 10000
                        )
                    )
                )
            },
            onNavigateToCommentDetail = { commentParam -> backStack.add(CommentDetailNavKey(commentParam)) },
            onNavigateToComments = { commentsParam -> backStack.add(HobbyCommentsNavKey(commentsParam)) }
        )
    }

    entry<CharacterNavKey> { key ->
        CharacterDetailScreen(
            navParam = key.param,
            onBack = onBack,
            onNavigateToHobby = { hobbyParam -> backStack.add(HobbyDetailNavKey(hobbyParam)) },
            onNavigateToHobbyList = { listParam -> backStack.add(HobbyListNavKey(listParam)) },
            onNavigateToWorks = { worksParam -> backStack.add(WorksDetailNavKey(worksParam)) },
            onNavigateToWorksList = { worksListParam -> backStack.add(CharacterWorksListNavKey(worksListParam)) },
            onNavigateToPerson = { personParam -> backStack.add(PersonDetailNavKey(personParam)) },
            onNavigateToCharacter = { characterParam -> backStack.add(CharacterNavKey(characterParam)) },
            onNavigateToAlbum = { albumParam -> backStack.add(AlbumDetailNavKey(albumParam)) },
            onNavigateToAlbumList = { albumListParam -> backStack.add(CharacterAlbumListNavKey(albumListParam)) },
            onNavigateToSearch = { keyword -> backStack.add(SearchNavKey(SearchNavParam(initialKeyword = keyword))) },
            onNavigateToCommentDetail = { commentParam -> backStack.add(CommentDetailNavKey(commentParam)) },
            onNavigateToComments = { commentsParam -> backStack.add(HobbyCommentsNavKey(commentsParam)) }
        )
    }

    entry<CharacterAlbumListNavKey> { key ->
        CharacterAlbumListScreen(
            param = key.param,
            onBack = onBack,
            onNavigateToAlbum = { albumParam -> backStack.add(AlbumDetailNavKey(albumParam)) }
        )
    }

    entry<HobbyAlbumListNavKey> { key ->
        HobbyAlbumListScreen(
            param = key.param,
            onBack = onBack,
            onNavigateToAlbum = { albumParam -> backStack.add(AlbumDetailNavKey(albumParam)) }
        )
    }

    entry<CharacterWorksListNavKey> { key ->
        CharacterWorksListScreen(
            param = key.param,
            onBack = onBack,
            onNavigateToWorks = { worksParam -> backStack.add(WorksDetailNavKey(worksParam)) },
            onNavigateToPerson = { personParam -> backStack.add(PersonDetailNavKey(personParam)) }
        )
    }
}
