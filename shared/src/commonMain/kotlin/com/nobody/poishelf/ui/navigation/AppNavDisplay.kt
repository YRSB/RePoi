package com.nobody.poishelf.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.poishelf.data.local.ThemeManager
import com.nobody.poishelf.data.local.ThemeMode
import com.nobody.poishelf.ui.album.AlbumDetailScreen
import com.nobody.poishelf.ui.album.AlbumNavParam
import com.nobody.poishelf.ui.album.AlbumRankListScreen
import com.nobody.poishelf.ui.album.AlbumScreen
import com.nobody.poishelf.ui.collect.CollectEditScreen
import com.nobody.poishelf.ui.collect.WorksCollectEditScreen
import com.nobody.poishelf.ui.comment.CommentDetailScreen
import com.nobody.poishelf.ui.comment.HobbyCommentsScreen
import com.nobody.poishelf.ui.follow.FollowScreen
import com.nobody.poishelf.ui.forum.ForumHomeScreen
import com.nobody.poishelf.ui.forum.PlateSubscribeScreen
import com.nobody.poishelf.ui.detail.HobbyDetailScreen
import com.nobody.poishelf.ui.home.HomeFeedScreen
import com.nobody.poishelf.ui.item.home.ItemHomeScreen
import com.nobody.poishelf.ui.item.list.HobbyListNavParam
import com.nobody.poishelf.ui.item.list.HobbyListScreen
import com.nobody.poishelf.ui.login.LoginScreen
import com.nobody.poishelf.ui.friend.FriendListScreen
import com.nobody.poishelf.ui.user.UserInfoScreen
import com.nobody.poishelf.ui.zone.ZoneBrowseScreen
import com.nobody.poishelf.ui.zone.ZoneDetailScreen
import com.nobody.poishelf.ui.resale.ResaleDetailScreen
import com.nobody.poishelf.ui.resale.ResaleMarketScreen
import com.nobody.poishelf.ui.zone.ZoneSettingsScreen
import com.nobody.poishelf.ui.collect.UserCollectScreen
import com.nobody.poishelf.ui.user.creation.UserCreationScreen
import com.nobody.poishelf.ui.user.history.UserHistoryScreen
import com.nobody.poishelf.ui.character.CharacterDetailScreen
import com.nobody.poishelf.ui.character.CharacterAlbumListScreen
import com.nobody.poishelf.ui.detail.album.HobbyAlbumListScreen
import com.nobody.poishelf.ui.character.CharacterWorksListScreen
import com.nobody.poishelf.ui.vendor.actions.VendorActionsScreen
import com.nobody.poishelf.ui.exhobby.ExHobbyListScreen
import com.nobody.poishelf.ui.picture.PictureListScreen
import com.nobody.poishelf.ui.person.PersonDetailScreen
import com.nobody.poishelf.ui.person.PersonNavParam
import com.nobody.poishelf.ui.profile.ProfileScreen
import com.nobody.poishelf.ui.message.center.MessageCenterScreen
import com.nobody.poishelf.ui.message.notice.MessageNoticeScreen
import com.nobody.poishelf.ui.message.chat.MessageChatScreen
import com.nobody.poishelf.ui.message.block.MessageBlockScreen
import com.nobody.poishelf.ui.search.SearchNavParam
import com.nobody.poishelf.ui.search.SearchScreen
import com.nobody.poishelf.ui.series.SeriesDetailScreen
import com.nobody.poishelf.ui.settings.SettingsScreen
import com.nobody.poishelf.ui.theme.LocalIsDarkTheme
import com.nobody.poishelf.ui.theme.PoiShelfTheme
import com.nobody.poishelf.ui.vendor.VendorDetailScreen
import com.nobody.poishelf.ui.vendor.VendorNavParam
import com.nobody.poishelf.ui.comment.CommentDetailNavParam
import com.nobody.poishelf.ui.works.WorksDetailScreen
import com.nobody.poishelf.ui.works.WorksNavParam
import com.nobody.poishelf.ui.detail.DetailNavParam
import com.nobody.poishelf.ui.post.PostDetailScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val TopLevelTransitionMetadata: Map<String, Any> = metadata {
    put(NavDisplay.TransitionKey) {
        EnterTransition.None togetherWith ExitTransition.None
    }
    put(NavDisplay.PopTransitionKey) {
        EnterTransition.None togetherWith ExitTransition.None
    }
    put(NavDisplay.PredictivePopTransitionKey) {
        EnterTransition.None togetherWith ExitTransition.None
    }
}

/**
 * 全局导航分发器：封装 Navigation 3 强类型路由拓扑与页面映射。
 */
@Composable
fun AppNavDisplay(
    backStack: NavBackStack<NavKey>,
    onBack: () -> Unit = { backStack.safePop() },
    modifier: Modifier = Modifier,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onOpenDebugScreen: () -> Unit,
    onLoginSuccess: () -> Unit,
) {
    val entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
        rememberViewModelStoreNavEntryDecorator<NavKey>()
    )

    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        entryDecorators = entryDecorators,
        entryProvider = entryProvider {
            entry<HomeNavKey>(metadata = TopLevelTransitionMetadata) {
                HomeFeedScreen(
                    onNavigateToDetail = { param ->
                        backStack.add(HobbyDetailNavKey(param))
                    },
                    onNavigateToAlbum = { param ->
                        backStack.add(AlbumDetailNavKey(param))
                    },
                    onNavigateToPerson = { param ->
                        backStack.add(PersonDetailNavKey(param))
                    },
                    onNavigateToWorks = { param ->
                        backStack.add(WorksDetailNavKey(param))
                    },
                    onNavigateToVendor = { param ->
                        backStack.add(VendorDetailNavKey(param))
                    },
                    onNavigateToCharacter = { param ->
                        backStack.add(CharacterNavKey(param))
                    },
                    onNavigateToSeries = { param ->
                        backStack.add(SeriesDetailNavKey(param))
                    },
                    onNavigateToUser = { targetUserId ->
                        backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId)))
                    },
                    onNavigateToSearch = { param ->
                        backStack.add(SearchNavKey(param = param))
                    },
                    onNavigateToZoneBrowse = {
                        backStack.add(ZoneBrowseNavKey)
                    },
                    onNavigateToForumHome = {
                        backStack.add(ForumHomeNavKey)
                    }
                )
            }
            entry<ItemsNavKey>(metadata = TopLevelTransitionMetadata) {
                ItemHomeScreen(
                    onNavigateToDetail = { param ->
                        backStack.add(HobbyDetailNavKey(param))
                    },
                    onNavigateToHobbyList = { param ->
                        backStack.add(HobbyListNavKey(param))
                    },
                    onNavigateToSearch = { categoryId ->
                        backStack.add(SearchNavKey(param = SearchNavParam(initialCategory = categoryId)))
                    }
                )
            }
            entry<AlbumNavKey>(metadata = TopLevelTransitionMetadata) {
                AlbumScreen(
                    onNavigateToAlbum = { param ->
                        backStack.add(AlbumDetailNavKey(param))
                    },
                    onNavigateToLogin = {
                        backStack.add(LoginNavKey)
                    },
                    onNavigateToRankList = { param ->
                        backStack.add(AlbumRankListNavKey(param))
                    }
                )
            }
            entry<AlbumRankListNavKey> { key ->
                AlbumRankListScreen(
                    param = key.param,
                    onBack = onBack,
                    onNavigateToAlbum = { param ->
                        backStack.add(AlbumDetailNavKey(param))
                    }
                )
            }


            entry<FollowNavKey>(metadata = TopLevelTransitionMetadata) {
                FollowScreen(
                    onNavigateToDetail = { param ->
                        backStack.add(HobbyDetailNavKey(param))
                    },
                    onNavigateToAlbum = { param ->
                        backStack.add(AlbumDetailNavKey(param))
                    },
                    onNavigateToPerson = { param ->
                        backStack.add(PersonDetailNavKey(param))
                    },
                    onNavigateToWorks = { param ->
                        backStack.add(WorksDetailNavKey(param))
                    },
                    onNavigateToVendor = { param ->
                        backStack.add(VendorDetailNavKey(param))
                    },
                    onNavigateToCharacter = { param ->
                        backStack.add(CharacterNavKey(param))
                    },
                    onNavigateToSeries = { param ->
                        backStack.add(SeriesDetailNavKey(param))
                    },
                    onNavigateToUser = { targetUserId ->
                        backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId)))
                    },
                    onNavigateToLogin = {
                        backStack.add(LoginNavKey)
                    }
                )
            }

            entry<ProfileNavKey>(metadata = TopLevelTransitionMetadata) {
                ProfileScreen(
                    onNavigateToSettings = {
                        backStack.add(SettingsNavKey)
                    },
                    onNavigateToLogin = {
                        backStack.add(LoginNavKey)
                    },
                    onNavigateToHobby = { param ->
                        if (param.itemType == "person") {
                            backStack.add(
                                PersonDetailNavKey(
                                    PersonNavParam(
                                        personId = param.itemId,
                                        initialName = param.initialName,
                                        initialCover = param.initialCover
                                    )
                                )
                            )
                        } else {
                            backStack.add(HobbyDetailNavKey(param))
                        }
                    },
                    onNavigateToCollect = { param ->
                        backStack.add(UserCollectNavKey(param))
                    },
                    onNavigateToCreation = { creationParam ->
                        backStack.add(UserCreationNavKey(creationParam))
                    },
                    onNavigateToHistory = {
                        backStack.add(UserHistoryNavKey())
                    },
                    onNavigateToAlbum = { albumId ->
                        backStack.add(AlbumDetailNavKey(AlbumNavParam(albumId = albumId)))
                    },
                    onNavigateToFriendList = { userId, initialType ->
                        backStack.add(
                            FriendListNavKey(
                                FriendListNavParam(
                                    userId = userId,
                                    initialType = initialType
                                )
                            )
                        )
                    },
                    onNavigateToUserInfo = { userId ->
                        backStack.add(
                            UserInfoNavKey(
                                UserInfoNavParam(
                                    userId = userId
                                )
                            )
                        )
                    },
                    onNavigateToMessage = {
                        backStack.add(MessageCenterNavKey())
                    }
                )
            }

            entry<FriendListNavKey> { key ->
                FriendListScreen(
                    userId = key.param.userId,
                    initialType = key.param.initialType,
                    onNavigateBack = onBack,
                    onNavigateToUser = { targetUserId ->
                        backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId)))
                    },
                    onNavigateToItem = { itemId, itemType ->
                        if (itemType == "album") {
                            backStack.add(AlbumDetailNavKey(AlbumNavParam(albumId = itemId)))
                        } else {
                            backStack.add(HobbyDetailNavKey(itemId = itemId))
                        }
                    }
                )
            }

            entry<UserInfoNavKey> { key ->
                UserInfoScreen(
                    userId = key.param.userId,
                    onNavigateBack = onBack,
                    onNavigateToFriendList = { targetUserId, initialType ->
                        backStack.add(FriendListNavKey(FriendListNavParam(userId = targetUserId, initialType = initialType)))
                    },
                    onNavigateToCollect = { collectParam ->
                        backStack.add(UserCollectNavKey(collectParam))
                    },
                    onNavigateToAlbum = { albumParam ->
                        backStack.add(AlbumDetailNavKey(albumParam))
                    },
                    onNavigateToHobby = { itemId ->
                        backStack.add(HobbyDetailNavKey(itemId = itemId))
                    },
                    onNavigateToUser = { targetUserId ->
                        backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId)))
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
                    }
                )
            }

            entry<UserCollectNavKey> { key ->
                UserCollectScreen(
                    param = key.param,
                    onNavigateBack = onBack,
                    onNavigateToHobby = { hobbyParam ->
                        backStack.add(HobbyDetailNavKey(hobbyParam))
                    },
                    onNavigateToVendor = { vendorParam ->
                        backStack.add(VendorDetailNavKey(vendorParam))
                    },
                    onNavigateToSeries = { seriesParam ->
                        backStack.add(SeriesDetailNavKey(seriesParam))
                    },
                    onNavigateToWorks = { worksParam ->
                        backStack.add(WorksDetailNavKey(worksParam))
                    },
                    onNavigateToPerson = { personParam ->
                        backStack.add(PersonDetailNavKey(personParam))
                    },
                    onNavigateToCharacter = { characterParam ->
                        backStack.add(CharacterNavKey(characterParam))
                    },
                    onNavigateToAlbum = { albumParam ->
                        backStack.add(AlbumDetailNavKey(albumParam))
                    }
                )
            }

            entry<UserCreationNavKey> { key ->
                UserCreationScreen(
                    param = key.param,
                    onNavigateBack = onBack,
                    onNavigateToAlbum = { albumId ->
                        backStack.add(AlbumDetailNavKey(AlbumNavParam(albumId = albumId)))
                    }
                )
            }

            entry<UserHistoryNavKey> {
                UserHistoryScreen(
                    onBackClick = onBack,
                    onNavigateToHobbyDetail = { itemId ->
                        backStack.add(HobbyDetailNavKey(itemId = itemId))
                    },
                    onNavigateToAlbumDetail = { albumId ->
                        backStack.add(AlbumDetailNavKey(AlbumNavParam(albumId = albumId)))
                    }
                )
            }

            entry<ZoneBrowseNavKey> {
                ZoneBrowseScreen(
                    onNavigateBack = onBack,
                    onNavigateToZoneDetail = { nodeId, name, icon, cover ->
                        backStack.add(
                            ZoneDetailNavKey(
                                nodeId = nodeId,
                                initialName = name,
                                initialIcon = icon,
                                initialCover = cover
                            )
                        )
                    },
                    onNavigateToSettings = {
                        backStack.add(ZoneSettingsNavKey)
                    }
                )
            }

            entry<ZoneDetailNavKey> { key ->
                ZoneDetailScreen(
                    nodeId = key.nodeId,
                    initialName = key.initialName,
                    initialIcon = key.initialIcon,
                    initialCover = key.initialCover,
                    onNavigateBack = onBack,
                    onNavigateToDetail = { param ->
                        backStack.add(HobbyDetailNavKey(param))
                    },
                    onNavigateToAlbum = { param ->
                        backStack.add(AlbumDetailNavKey(param))
                    },
                    onNavigateToPerson = { param ->
                        backStack.add(PersonDetailNavKey(param))
                    },
                    onNavigateToWorks = { param ->
                        backStack.add(WorksDetailNavKey(param))
                    },
                    onNavigateToVendor = { param ->
                        backStack.add(VendorDetailNavKey(param))
                    },
                    onNavigateToCharacter = { param ->
                        backStack.add(CharacterNavKey(param))
                    },
                    onNavigateToSeries = { param ->
                        backStack.add(SeriesDetailNavKey(param))
                    },
                    onNavigateToUser = { targetUserId ->
                        backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId)))
                    },
                    onNavigateToPost = { postId, nodeId, title ->
                        backStack.add(PostDetailNavKey(postId = postId, nodeId = nodeId, initialTitle = title))
                    },
                    onNavigateToResaleDetail = { resaleId ->
                        backStack.add(ResaleDetailNavKey(resaleId = resaleId))
                    },
                    onNavigateToResaleMarket = { hobbyId, title ->
                        backStack.add(ResaleMarketNavKey(hobbyId = hobbyId, title = title))
                    }
                )
            }

            entry<ResaleDetailNavKey> { key ->
                ResaleDetailScreen(
                    resaleId = key.resaleId,
                    onBack = onBack,
                    onNavigateToHobby = { hobbyId ->
                        backStack.add(HobbyDetailNavKey(DetailNavParam(itemId = hobbyId)))
                    },
                    onNavigateToUser = { targetUserId ->
                        backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId)))
                    }
                )
            }

            entry<ResaleMarketNavKey> { key ->
                ResaleMarketScreen(
                    hobbyId = key.hobbyId,
                    title = key.title,
                    onBack = onBack,
                    onNavigateToResaleDetail = { resaleId ->
                        backStack.add(ResaleDetailNavKey(resaleId = resaleId))
                    },
                    onNavigateToHobby = { hobbyId ->
                        backStack.add(HobbyDetailNavKey(DetailNavParam(itemId = hobbyId)))
                    },
                    onNavigateToUser = { targetUserId ->
                        backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId)))
                    },
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

            entry<ZoneSettingsNavKey> {
                ZoneSettingsScreen(
                    onNavigateBack = onBack
                )
            }

            entry<ForumHomeNavKey> {
                ForumHomeScreen(
                    onNavigateBack = onBack,
                    onNavigateToPost = { postId, nodeId, title ->
                        backStack.add(PostDetailNavKey(postId = postId, nodeId = nodeId, initialTitle = title))
                    },
                    onNavigateToUser = { targetUserId ->
                        backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId)))
                    },
                    onNavigateToSelf = {
                        backStack.add(UserInfoNavKey(UserInfoNavParam(userId = 0L)))
                    },
                    onNavigateToMessage = {
                        backStack.add(MessageCenterNavKey())
                    },
                    onNavigateToPlates = {
                        backStack.add(PlateSubscribeNavKey)
                    }
                )
            }

            entry<PlateSubscribeNavKey> {
                PlateSubscribeScreen(
                    onNavigateBack = onBack,
                    onNavigateToLogin = {
                        backStack.add(LoginNavKey)
                    }
                )
            }

            entry<PostDetailNavKey> { key ->
                PostDetailScreen(
                    postId = key.postId,
                    initialTitle = key.initialTitle,
                    nodeId = key.nodeId,
                    onNavigateBack = onBack,
                    onNavigateToDetail = { param ->
                        backStack.add(HobbyDetailNavKey(param))
                    },
                    onNavigateToZone = { nodeId ->
                        backStack.add(ZoneDetailNavKey(nodeId = nodeId))
                    },
                    onNavigateToUser = { targetUserId ->
                        backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId)))
                    },
                    onNavigateToAlbum = { param ->
                        backStack.add(AlbumDetailNavKey(param))
                    },
                    onNavigateToCharacter = { param ->
                        backStack.add(CharacterNavKey(param))
                    },
                    onNavigateToPost = { targetPostId ->
                        backStack.add(PostDetailNavKey(postId = targetPostId))
                    },
                    onNavigateToCommentDetail = { param ->
                        backStack.add(CommentDetailNavKey(param.copy(isDiscuss = true)))
                    }
                )
            }

            entry<HobbyListNavKey> { key ->
                HobbyListScreen(
                    param = key.param,
                    onBack = onBack,
                    onNavigateToDetail = { hobbyParam ->
                        backStack.add(HobbyDetailNavKey(hobbyParam))
                    }
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
                    onNavigateToDetail = { hobbyParam ->
                        backStack.add(HobbyDetailNavKey(hobbyParam))
                    },
                    onNavigateToAlbum = { albumParam ->
                        backStack.add(AlbumDetailNavKey(albumParam))
                    },
                    onNavigateToVendor = { vendorParam ->
                        backStack.add(VendorDetailNavKey(vendorParam))
                    },
                    onNavigateToSeries = { seriesParam ->
                        backStack.add(SeriesDetailNavKey(seriesParam))
                    },
                    onNavigateToPerson = { personParam ->
                        backStack.add(PersonDetailNavKey(personParam))
                    },
                    onNavigateToWorks = { worksParam ->
                        backStack.add(WorksDetailNavKey(worksParam))
                    },
                    onNavigateToCharacter = { characterParam ->
                        backStack.add(CharacterNavKey(characterParam))
                    }
                )
            }
            entry<HobbyDetailNavKey> { key ->
                HobbyDetailScreen(
                    navParam = key.param,
                    onBack = onBack,
                    onNavigateToHobby = { detailParam ->
                        backStack.add(HobbyDetailNavKey(detailParam))
                    },
                    onNavigateToAlbum = { albumId ->
                        backStack.add(AlbumDetailNavKey(AlbumNavParam(albumId = albumId)))
                    },
                    onNavigateToAlbumList = { albumListParam ->
                        backStack.add(HobbyAlbumListNavKey(albumListParam))
                    },
                    onNavigateToVendor = { vendorParam ->
                        backStack.add(VendorDetailNavKey(vendorParam))
                    },
                    onNavigateToSeries = { seriesParam ->
                        backStack.add(SeriesDetailNavKey(seriesParam))
                    },
                    onNavigateToPerson = { personParam ->
                        backStack.add(PersonDetailNavKey(personParam))
                    },
                    onNavigateToWorks = { worksParam ->
                        backStack.add(WorksDetailNavKey(worksParam))
                    },
                    onNavigateToCharacter = { characterParam ->
                        backStack.add(CharacterNavKey(characterParam))
                    },
                    onNavigateToHobbyList = { listParam ->
                        backStack.add(HobbyListNavKey(listParam))
                    },
                    onNavigateToCollect = { collectParam ->
                        backStack.add(CollectEditNavKey(collectParam))
                    },
                    onNavigateToCommentDetail = { commentParam ->
                        backStack.add(CommentDetailNavKey(commentParam))
                    },
                    onNavigateToHobbyComments = { commentsParam ->
                        backStack.add(HobbyCommentsNavKey(commentsParam))
                    },
                    onNavigateToPictureList = { pictureParam ->
                        backStack.add(PictureListNavKey(pictureParam))
                    },
                    onNavigateToExHobbyList = { exHobbyKey ->
                        backStack.add(exHobbyKey)
                    }
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
                    onNavigateToCommentDetail = { commentParam ->
                        backStack.add(CommentDetailNavKey(commentParam))
                    }
                )
            }

            entry<CollectEditNavKey> { key ->
                CollectEditScreen(
                    param = key.param,
                    onBack = onBack
                )
            }

            entry<WorksCollectEditNavKey> { key ->
                WorksCollectEditScreen(
                    param = key.param,
                    onBack = onBack
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
                    onNavigateToAlbum = { albumId ->
                        backStack.add(AlbumDetailNavKey(AlbumNavParam(albumId = albumId)))
                    },
                    onNavigateToCommentDetail = { commentParam ->
                        backStack.add(CommentDetailNavKey(commentParam))
                    },
                    onNavigateToComments = { commentsParam ->
                        backStack.add(HobbyCommentsNavKey(commentsParam))
                    },
                    onNavigateToExHobbyList = { exHobbyKey ->
                        backStack.add(exHobbyKey)
                    }
                )
            }

            entry<VendorDetailNavKey> { key ->
                VendorDetailScreen(
                    navParam = key.param,
                    onBack = onBack,
                    onNavigateToHobby = { hobbyParam ->
                        backStack.add(HobbyDetailNavKey(hobbyParam))
                    },
                    onNavigateToSeries = { seriesParam ->
                        backStack.add(SeriesDetailNavKey(seriesParam))
                    },
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
                    onNavigateToLogin = {
                        backStack.add(LoginNavKey)
                    },
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
                    onNavigateToCommentDetail = { commentParam ->
                        backStack.add(CommentDetailNavKey(commentParam))
                    },
                    onNavigateToComments = { commentsParam ->
                        backStack.add(HobbyCommentsNavKey(commentsParam))
                    }
                )
            }

            entry<VendorActionsNavKey> { key ->
                VendorActionsScreen(
                    navParam = key.param,
                    onBack = onBack,
                    onNavigateToHobby = { hobbyParam ->
                        backStack.add(HobbyDetailNavKey(hobbyParam))
                    },
                    onNavigateToAlbum = { albumParam ->
                        backStack.add(AlbumDetailNavKey(albumParam))
                    },
                    onNavigateToVendor = { vendorParam ->
                        backStack.add(VendorDetailNavKey(vendorParam))
                    },
                    onNavigateToCharacter = { characterParam ->
                        backStack.add(CharacterNavKey(characterParam))
                    },
                    onNavigateToSeries = { seriesParam ->
                        backStack.add(SeriesDetailNavKey(seriesParam))
                    },
                    onNavigateToWorks = { worksParam ->
                        backStack.add(WorksDetailNavKey(worksParam))
                    },
                    onNavigateToPerson = { personParam ->
                        backStack.add(PersonDetailNavKey(personParam))
                    },
                    onNavigateToUser = { targetUserId ->
                        backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId)))
                    }
                )
            }
            entry<SeriesDetailNavKey> { key ->
                SeriesDetailScreen(
                    navParam = key.param,
                    onBack = onBack,
                    onNavigateToHobby = { hobbyParam ->
                        backStack.add(HobbyDetailNavKey(hobbyParam))
                    },
                    onNavigateToAlbum = { albumParam ->
                        backStack.add(AlbumDetailNavKey(albumParam))
                    },
                    onNavigateToSeries = { seriesParam ->
                        backStack.add(SeriesDetailNavKey(seriesParam))
                    },
                    onNavigateToHobbyList = { listParam ->
                        backStack.add(HobbyListNavKey(listParam))
                    },
                    onNavigateToAlbumList = { albumListParam ->
                        backStack.add(HobbyAlbumListNavKey(albumListParam))
                    },
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
                    onNavigateToCommentDetail = { commentParam ->
                        backStack.add(CommentDetailNavKey(commentParam))
                    },
                    onNavigateToComments = { commentsParam ->
                        backStack.add(HobbyCommentsNavKey(commentsParam))
                    }
                )
            }
            entry<PersonDetailNavKey> { key ->
                PersonDetailScreen(
                    navParam = key.param,
                    onBack = onBack,
                    onNavigateToHobby = { hobbyParam ->
                        backStack.add(HobbyDetailNavKey(hobbyParam))
                    },
                    onNavigateToHobbyList = { listParam ->
                        backStack.add(HobbyListNavKey(listParam))
                    },
                    onNavigateToSearch = { keyword ->
                        backStack.add(SearchNavKey(SearchNavParam(initialKeyword = keyword)))
                    },
                    onNavigateToWorks = { worksParam ->
                        backStack.add(WorksDetailNavKey(worksParam))
                    },
                    onNavigateToCharacter = { characterParam ->
                        backStack.add(CharacterNavKey(characterParam))
                    },
                    onNavigateToCommentDetail = { commentParam ->
                        backStack.add(CommentDetailNavKey(commentParam))
                    },
                    onNavigateToComments = { commentsParam ->
                        backStack.add(HobbyCommentsNavKey(commentsParam))
                    }
                )
            }
            entry<WorksDetailNavKey> { key ->
                WorksDetailScreen(
                    navParam = key.param,
                    onBack = onBack,
                    onNavigateToHobby = { hobbyParam ->
                        backStack.add(HobbyDetailNavKey(hobbyParam))
                    },
                    onNavigateToAlbum = { albumParam ->
                        backStack.add(AlbumDetailNavKey(albumParam))
                    },
                    onNavigateToHobbyList = { listParam ->
                        backStack.add(HobbyListNavKey(listParam))
                    },
                    onNavigateToCharacter = { characterParam ->
                        backStack.add(CharacterNavKey(characterParam))
                    },
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
                    onNavigateToCommentDetail = { commentParam ->
                        backStack.add(CommentDetailNavKey(commentParam))
                    },
                    onNavigateToComments = { commentsParam ->
                        backStack.add(HobbyCommentsNavKey(commentsParam))
                    }
                )
            }
            entry<CharacterNavKey> { key ->
                CharacterDetailScreen(
                    navParam = key.param,
                    onBack = onBack,
                    onNavigateToHobby = { hobbyParam ->
                        backStack.add(HobbyDetailNavKey(hobbyParam))
                    },
                    onNavigateToHobbyList = { listParam ->
                        backStack.add(HobbyListNavKey(listParam))
                    },
                    onNavigateToWorks = { worksParam ->
                        backStack.add(WorksDetailNavKey(worksParam))
                    },
                    onNavigateToWorksList = { worksListParam ->
                        backStack.add(CharacterWorksListNavKey(worksListParam))
                    },
                    onNavigateToPerson = { personParam ->
                        backStack.add(PersonDetailNavKey(personParam))
                    },
                    onNavigateToCharacter = { characterParam ->
                        backStack.add(CharacterNavKey(characterParam))
                    },
                    onNavigateToAlbum = { albumParam ->
                        backStack.add(AlbumDetailNavKey(albumParam))
                    },
                    onNavigateToAlbumList = { albumListParam ->
                        backStack.add(CharacterAlbumListNavKey(albumListParam))
                    },
                    onNavigateToSearch = { keyword ->
                        backStack.add(SearchNavKey(SearchNavParam(initialKeyword = keyword)))
                    },
                    onNavigateToCommentDetail = { commentParam ->
                        backStack.add(CommentDetailNavKey(commentParam))
                    },
                    onNavigateToComments = { commentsParam ->
                        backStack.add(HobbyCommentsNavKey(commentsParam))
                    }
                )
            }

            entry<CharacterAlbumListNavKey> { key ->
                CharacterAlbumListScreen(
                    param = key.param,
                    onBack = onBack,
                    onNavigateToAlbum = { albumParam ->
                        backStack.add(AlbumDetailNavKey(albumParam))
                    }
                )
            }

            entry<HobbyAlbumListNavKey> { key ->
                HobbyAlbumListScreen(
                    param = key.param,
                    onBack = onBack,
                    onNavigateToAlbum = { albumParam ->
                        backStack.add(AlbumDetailNavKey(albumParam))
                    }
                )
            }

            entry<CharacterWorksListNavKey> { key ->
                CharacterWorksListScreen(
                    param = key.param,
                    onBack = onBack,
                    onNavigateToWorks = { worksParam ->
                        backStack.add(WorksDetailNavKey(worksParam))
                    },
                    onNavigateToPerson = { personParam ->
                        backStack.add(PersonDetailNavKey(personParam))
                    }
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


            entry<LoginNavKey> {
                LoginScreen(
                    onBack = onBack,
                    onLoginSuccess = {
                        onLoginSuccess()
                    }
                )
            }

            entry<SettingsNavKey> {
                SettingsScreen(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    onThemeModeChange = onThemeModeChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onOpenDebug = onOpenDebugScreen,
                    onBack = onBack
                )
            }

            // ── 消息中心与通知路由映射 ──────────────────────────────────────────
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
                    onNavigateToBlock = {
                        backStack.add(MessageBlockNavKey())
                    }
                )
            }

            entry<MessageNoticeNavKey> { key ->
                MessageNoticeScreen(
                    param = key.param,
                    onNavigateBack = onBack,
                    onNavigateToUser = { targetUserId ->
                        backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId)))
                    },
                    onNavigateToHobby = { itemId ->
                        backStack.add(HobbyDetailNavKey(itemId = itemId))
                    },
                    onNavigateToAlbum = { albumId ->
                        backStack.add(AlbumDetailNavKey(AlbumNavParam(albumId = albumId)))
                    },
                    onNavigateToVendor = { vendorId ->
                        backStack.add(VendorDetailNavKey(VendorNavParam(companyId = vendorId)))
                    },
                    onNavigateToPerson = { personId ->
                        backStack.add(PersonDetailNavKey(PersonNavParam(personId = personId)))
                    },
                    onNavigateToWorks = { worksId ->
                        backStack.add(WorksDetailNavKey(WorksNavParam(worksId = worksId)))
                    },
                    onNavigateToCharacter = { characterId ->
                        backStack.add(CharacterNavKey(CharacterNavParam(characterId = characterId)))
                    },
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
                    onNavigateToUser = { targetUserId ->
                        backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId)))
                    },
                    onNavigateToHobby = { itemId ->
                        backStack.add(HobbyDetailNavKey(itemId = itemId))
                    }
                )
            }

            entry<MessageBlockNavKey> {
                MessageBlockScreen(
                    onNavigateBack = onBack,
                    onNavigateToUser = { targetUserId ->
                        backStack.add(UserInfoNavKey(UserInfoNavParam(userId = targetUserId)))
                    }
                )
            }
        }
    )

}
