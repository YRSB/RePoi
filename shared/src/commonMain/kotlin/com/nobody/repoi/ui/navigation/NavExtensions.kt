package com.nobody.repoi.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.album.AlbumRankListNavParam
import com.nobody.repoi.ui.comment.CommentDetailNavParam
import com.nobody.repoi.ui.comment.HobbyCommentsNavParam
import com.nobody.repoi.ui.collect.WorksCollectEditNavParam
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.item.list.HobbyListNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.search.SearchNavParam
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam

/**
 * 全局返回栈 CompositionLocal 上下文。
 */
val LocalNavBackStack = staticCompositionLocalOf<NavBackStack<NavKey>> {
    error("LocalNavBackStack 未在上下文中提供")
}

/**
 * 强类型导航入栈语义扩展函数集：收敛全局页面跳转逻辑，消除重复样板代码。
 */
fun NavBackStack<NavKey>.navigateToDetail(itemId: Long) = add(HobbyDetailNavKey(itemId))
fun NavBackStack<NavKey>.navigateToDetail(param: DetailNavParam) = add(HobbyDetailNavKey(param))
fun NavBackStack<NavKey>.navigateToAlbum(param: AlbumNavParam) = add(AlbumDetailNavKey(param))
fun NavBackStack<NavKey>.navigateToAlbum(albumId: Long) = add(AlbumDetailNavKey(AlbumNavParam(albumId = albumId)))
fun NavBackStack<NavKey>.navigateToRankList(param: AlbumRankListNavParam) = add(AlbumRankListNavKey(param))
fun NavBackStack<NavKey>.navigateToPerson(param: PersonNavParam) = add(PersonDetailNavKey(param))
fun NavBackStack<NavKey>.navigateToPerson(personId: Long) = add(PersonDetailNavKey(PersonNavParam(personId = personId)))
fun NavBackStack<NavKey>.navigateToWorks(param: WorksNavParam) = add(WorksDetailNavKey(param))
fun NavBackStack<NavKey>.navigateToWorks(worksId: Long) = add(WorksDetailNavKey(WorksNavParam(worksId = worksId)))
fun NavBackStack<NavKey>.navigateToVendor(param: VendorNavParam) = add(VendorDetailNavKey(param))
fun NavBackStack<NavKey>.navigateToVendor(vendorId: Long) = add(VendorDetailNavKey(VendorNavParam(companyId = vendorId)))
fun NavBackStack<NavKey>.navigateToVendorActions(param: VendorActionsNavParam) = add(VendorActionsNavKey(param))
fun NavBackStack<NavKey>.navigateToCharacter(param: CharacterNavParam) = add(CharacterNavKey(param))
fun NavBackStack<NavKey>.navigateToCharacter(characterId: Long) = add(CharacterNavKey(CharacterNavParam(characterId = characterId)))
fun NavBackStack<NavKey>.navigateToCharacterAlbumList(param: CharacterAlbumListNavParam) = add(CharacterAlbumListNavKey(param))
fun NavBackStack<NavKey>.navigateToHobbyAlbumList(param: HobbyAlbumListNavParam) = add(HobbyAlbumListNavKey(param))
fun NavBackStack<NavKey>.navigateToCharacterWorksList(param: CharacterWorksListNavParam) = add(CharacterWorksListNavKey(param))
fun NavBackStack<NavKey>.navigateToWorksCollectEdit(param: WorksCollectEditNavParam) = add(WorksCollectEditNavKey(param))
fun NavBackStack<NavKey>.navigateToSeries(param: SeriesNavParam) = add(SeriesDetailNavKey(param))
fun NavBackStack<NavKey>.navigateToUser(userId: Long) = add(UserInfoNavKey(UserInfoNavParam(userId = userId)))
fun NavBackStack<NavKey>.navigateToUser(param: UserInfoNavParam) = add(UserInfoNavKey(param))
fun NavBackStack<NavKey>.navigateToSearch(param: SearchNavParam = SearchNavParam()) = add(SearchNavKey(param = param))
fun NavBackStack<NavKey>.navigateToHobbyList(param: HobbyListNavParam) = add(HobbyListNavKey(param))
fun NavBackStack<NavKey>.navigateToCollectEdit(param: CollectEditNavParam) = add(CollectEditNavKey(param))
fun NavBackStack<NavKey>.navigateToUserCollect(param: UserCollectNavParam) = add(UserCollectNavKey(param))
fun NavBackStack<NavKey>.navigateToUserCreation(param: UserCreationNavParam) = add(UserCreationNavKey(param))
fun NavBackStack<NavKey>.navigateToUserHistory() = add(UserHistoryNavKey())
fun NavBackStack<NavKey>.navigateToFriendList(param: FriendListNavParam) = add(FriendListNavKey(param))
fun NavBackStack<NavKey>.navigateToCommentDetail(param: CommentDetailNavParam) = add(CommentDetailNavKey(param))
fun NavBackStack<NavKey>.navigateToHobbyComments(param: HobbyCommentsNavParam) = add(HobbyCommentsNavKey(param))
fun NavBackStack<NavKey>.navigateToZoneBrowse() = add(ZoneBrowseNavKey)
fun NavBackStack<NavKey>.navigateToZoneDetail(nodeId: Long, initialName: String? = null, initialIcon: String? = null, initialCover: String? = null) =
    add(ZoneDetailNavKey(nodeId = nodeId, initialName = initialName, initialIcon = initialIcon, initialCover = initialCover))
fun NavBackStack<NavKey>.navigateToZoneSettings() = add(ZoneSettingsNavKey)
fun NavBackStack<NavKey>.navigateToForumHome() = add(ForumHomeNavKey)
fun NavBackStack<NavKey>.navigateToPlateSubscribe() = add(PlateSubscribeNavKey)
fun NavBackStack<NavKey>.navigateToPostDetail(postId: Long, initialTitle: String? = null, nodeId: Long? = null) =
    add(PostDetailNavKey(postId = postId, initialTitle = initialTitle, nodeId = nodeId))
fun NavBackStack<NavKey>.navigateToResaleDetail(resaleId: Long) = add(ResaleDetailNavKey(resaleId = resaleId))
fun NavBackStack<NavKey>.navigateToResaleMarket(hobbyId: Long = 0L, title: String? = null) =
    add(ResaleMarketNavKey(hobbyId = hobbyId, title = title))
fun NavBackStack<NavKey>.navigateToPictureList(param: PictureListNavParam) = add(PictureListNavKey(param))
fun NavBackStack<NavKey>.navigateToExHobbyList(itemId: Long, title: String = "ExHobby") =
    add(ExHobbyListNavKey(itemId = itemId, title = title))
fun NavBackStack<NavKey>.navigateToLogin() = add(LoginNavKey)
fun NavBackStack<NavKey>.navigateToSettings() = add(SettingsNavKey)
fun NavBackStack<NavKey>.navigateToMessageCenter() = add(MessageCenterNavKey())
fun NavBackStack<NavKey>.navigateToMessageNotice(param: MessageNoticeNavParam) = add(MessageNoticeNavKey(param))
fun NavBackStack<NavKey>.navigateToMessageChat(param: MessageChatNavParam) = add(MessageChatNavKey(param))
fun NavBackStack<NavKey>.navigateToMessageBlock() = add(MessageBlockNavKey())
