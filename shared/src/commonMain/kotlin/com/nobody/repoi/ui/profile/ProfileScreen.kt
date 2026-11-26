package com.nobody.repoi.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import com.nobody.repoi.ui.common.PoiPullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.platform.isLandscapeOrientation
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.collect.UserCollectScreen
import com.nobody.repoi.ui.common.PoiIcons
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.friend.FriendListScreen
import com.nobody.repoi.ui.navigation.CharacterNavParam
import com.nobody.repoi.ui.navigation.FriendListNavParam
import com.nobody.repoi.ui.navigation.PictureGalleryNavParam
import com.nobody.repoi.ui.navigation.RePoiNavDisplay
import com.nobody.repoi.ui.navigation.UserCollectNavParam
import com.nobody.repoi.ui.navigation.UserCreationNavParam
import com.nobody.repoi.ui.navigation.rememberBottomBarContentPadding
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.profile.components.ExpressiveProfileHeader
import com.nobody.repoi.ui.profile.components.FigureAssetHubSection
import com.nobody.repoi.ui.profile.components.FinanceDetailBottomSheet
import com.nobody.repoi.ui.profile.components.ProfileErrorCard
import com.nobody.repoi.ui.profile.components.ProfileQuickAction
import com.nobody.repoi.ui.profile.components.ProfileQuickActionBar
import com.nobody.repoi.ui.profile.navigation.ProfileCollectNavKey
import com.nobody.repoi.ui.profile.navigation.ProfileCreationNavKey
import com.nobody.repoi.ui.profile.navigation.ProfileFriendListNavKey
import com.nobody.repoi.ui.profile.navigation.ProfileHistoryNavKey
import com.nobody.repoi.ui.profile.navigation.ProfileListNavKey
import com.nobody.repoi.ui.profile.navigation.ProfileSubNavKey
import com.nobody.repoi.ui.profile.navigation.ProfileSubNavSerializersModule
import com.nobody.repoi.ui.profile.navigation.openProfileSubRoute
import com.nobody.repoi.ui.profile.navigation.popProfileSubRoute
import com.nobody.repoi.ui.profile.navigation.resetProfileSubStack
import com.nobody.repoi.ui.profile.navigation.resolveProfileSessionTracker
import com.nobody.repoi.ui.profile.navigation.toQuickAction
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.user.creation.UserCreationScreen
import com.nobody.repoi.ui.user.history.UserHistoryScreen
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ProfileScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToHobby: (DetailNavParam) -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToUserInfo: (userId: Long) -> Unit = {},
    onNavigateToMessage: () -> Unit = {},
    onNavigateToVendor: (VendorNavParam) -> Unit = {},
    onNavigateToSeries: (SeriesNavParam) -> Unit = {},
    onNavigateToWorks: (WorksNavParam) -> Unit = {},
    onNavigateToPerson: (PersonNavParam) -> Unit = {},
    onNavigateToCharacter: (CharacterNavParam) -> Unit = {},
    onNavigateToCollectAlbum: (AlbumNavParam) -> Unit = { onNavigateToAlbum(it.albumId) },
    onNavigateToPictureGallery: (PictureGalleryNavParam) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val isWide = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
    val isFinanceExpanded = isLandscapeOrientation() &&
        adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val appMessages = LocalAppMessages.current

    LaunchedEffect(uiState.errorMessage) {
        val error = uiState.errorMessage
        if (error != null && uiState.profile != null) {
            appMessages.show(error)
        }
    }

    LaunchedEffect(uiState.infoMessage) {
        val info = uiState.infoMessage
        if (info != null) {
            appMessages.show(info)
            viewModel.clearInfoMessage()
        }
    }

    ProfileListDetailContent(
        uiState = uiState,
        isWide = isWide,
        isFinanceExpanded = isFinanceExpanded,
        onRefresh = { viewModel.refresh() },
        onRetryLoad = { viewModel.loadProfile(isRefresh = false) },
        onCheckInClick = { viewModel.checkIn() },
        onShowMessage = { appMessages.show(it) },
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToLogin = onNavigateToLogin,
        onNavigateToUserInfo = onNavigateToUserInfo,
        onNavigateToMessage = onNavigateToMessage,
        collectPaneContent = { param, showBackButton, onBack ->
            UserCollectScreen(
                param = param,
                onNavigateBack = onBack,
                onNavigateToHobby = onNavigateToHobby,
                onNavigateToVendor = onNavigateToVendor,
                onNavigateToSeries = onNavigateToSeries,
                onNavigateToWorks = onNavigateToWorks,
                onNavigateToPerson = onNavigateToPerson,
                onNavigateToCharacter = onNavigateToCharacter,
                onNavigateToAlbum = onNavigateToCollectAlbum,
                onNavigateToPictureGallery = onNavigateToPictureGallery,
                showBackButton = showBackButton
            )
        },
        creationPaneContent = { param, showBackButton, onBack ->
            UserCreationScreen(
                param = param,
                onNavigateBack = onBack,
                onNavigateToAlbum = onNavigateToAlbum,
                showBackButton = showBackButton
            )
        },
        historyPaneContent = { showBackButton, onBack ->
            UserHistoryScreen(
                onBackClick = onBack,
                onNavigateToHobbyDetail = { itemId ->
                    onNavigateToHobby(DetailNavParam(itemId = itemId, itemType = "hobby"))
                },
                onNavigateToAlbumDetail = onNavigateToAlbum,
                showBackButton = showBackButton
            )
        },
        friendListPaneContent = { param, showBackButton, onBack ->
            FriendListScreen(
                userId = param.userId,
                initialType = param.initialType,
                onNavigateBack = onBack,
                onNavigateToUser = onNavigateToUserInfo,
                onNavigateToItem = { itemId, itemType ->
                    if (itemType == "album") {
                        onNavigateToAlbum(itemId)
                    } else {
                        onNavigateToHobby(DetailNavParam(itemId = itemId, itemType = itemType ?: "hobby"))
                    }
                },
                showBackButton = showBackButton
            )
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun ProfileListDetailContent(
    uiState: ProfileUiState,
    isWide: Boolean,
    isFinanceExpanded: Boolean = isWide,
    onRefresh: () -> Unit,
    onRetryLoad: () -> Unit,
    onCheckInClick: () -> Unit,
    onShowMessage: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToUserInfo: (userId: Long) -> Unit,
    onNavigateToMessage: () -> Unit,
    collectPaneContent: @Composable (param: UserCollectNavParam, showBackButton: Boolean, onBack: () -> Unit) -> Unit,
    creationPaneContent: @Composable (param: UserCreationNavParam, showBackButton: Boolean, onBack: () -> Unit) -> Unit,
    historyPaneContent: @Composable (showBackButton: Boolean, onBack: () -> Unit) -> Unit,
    friendListPaneContent: @Composable (param: FriendListNavParam, showBackButton: Boolean, onBack: () -> Unit) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = remember(windowAdaptiveInfo, isWide) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo).copy(
            maxHorizontalPartitions = if (isWide) 2 else 1,
            horizontalPartitionSpacerSize = 0.dp
        )
    }
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)

    val savedStateConfiguration = remember {
        SavedStateConfiguration {
            serializersModule = ProfileSubNavSerializersModule
        }
    }
    val backStack = rememberNavBackStack(savedStateConfiguration, ProfileListNavKey)

    val profile = uiState.profile
    val profileId = profile?.id ?: 0L
    val isAuthenticated = uiState.isLoggedIn

    var lastAuthenticatedUserId by rememberSaveable {
        mutableStateOf<Long?>(if (isAuthenticated && profileId > 0L) profileId else null)
    }
    LaunchedEffect(isAuthenticated, profileId) {
        val decision = resolveProfileSessionTracker(
            lastAuthenticatedUserId = lastAuthenticatedUserId,
            isLoggedIn = isAuthenticated,
            currentUserId = profileId
        )
        lastAuthenticatedUserId = decision.nextAuthenticatedUserId
        if (decision.shouldResetSubStack) {
            resetProfileSubStack(backStack)
        }
    }

    var showFinanceDialog by rememberSaveable { mutableStateOf(false) }
    var financeDialogInitialTab by rememberSaveable { mutableStateOf(0) }

    val localOnBack: () -> Unit = {
        popProfileSubRoute(backStack)
    }

    val selectedAction = if (isWide) {
        (backStack.lastOrNull() as? ProfileSubNavKey).toQuickAction()
    } else {
        null
    }

    val entryProvider = entryProvider<NavKey> {
        entry<ProfileListNavKey>(
            metadata = ListDetailSceneStrategy.listPane(
                detailPlaceholder = {
                    ProfileDetailPlaceholder(
                        isLoggedIn = isAuthenticated,
                        onLoginClick = onNavigateToLogin
                    )
                }
            )
        ) {
            ProfileListPane(
                uiState = uiState,
                isWide = isWide,
                selectedAction = selectedAction,
                onRefresh = onRefresh,
                onRetryLoad = onRetryLoad,
                onCheckInClick = onCheckInClick,
                onMailClick = { if (isAuthenticated && profile != null) onNavigateToMessage() else onNavigateToLogin() },
                onSettingsClick = onNavigateToSettings,
                onOpenFriendList = { userId, initialType ->
                    openProfileSubRoute(
                        backStack,
                        ProfileFriendListNavKey(FriendListNavParam(userId = userId, initialType = initialType)),
                        isWide = isWide
                    )
                },
                onNavigateToUserInfo = onNavigateToUserInfo,
                onLoginClick = onNavigateToLogin,
                onOpenCollect = { param ->
                    openProfileSubRoute(backStack, ProfileCollectNavKey(param), isWide = isWide)
                },
                onOpenCreation = { param ->
                    openProfileSubRoute(backStack, ProfileCreationNavKey(param), isWide = isWide)
                },
                onOpenHistory = {
                    openProfileSubRoute(backStack, ProfileHistoryNavKey, isWide = isWide)
                },
                onOpenFinanceDialog = { tabIndex ->
                    financeDialogInitialTab = tabIndex
                    showFinanceDialog = true
                },
                onShowMessage = onShowMessage
            )
        }

        entry<ProfileCollectNavKey>(
            clazzContentKey = { "profile_collect" },
            metadata = ListDetailSceneStrategy.detailPane()
        ) { key ->
            collectPaneContent(key.param, !isWide, localOnBack)
        }

        entry<ProfileCreationNavKey>(
            clazzContentKey = { "profile_creation" },
            metadata = ListDetailSceneStrategy.detailPane()
        ) { key ->
            creationPaneContent(key.param, !isWide, localOnBack)
        }

        entry<ProfileHistoryNavKey>(
            metadata = ListDetailSceneStrategy.detailPane()
        ) {
            historyPaneContent(!isWide, localOnBack)
        }

        entry<ProfileFriendListNavKey>(
            clazzContentKey = { "profile_friend_list" },
            metadata = ListDetailSceneStrategy.detailPane()
        ) { key ->
            friendListPaneContent(key.param, !isWide, localOnBack)
        }
    }

    RePoiNavDisplay(
        backStack = backStack,
        onBack = localOnBack,
        sceneStrategies = listOf(listDetailStrategy),
        entryProvider = entryProvider,
        modifier = modifier.fillMaxSize()
    )

    if (showFinanceDialog && uiState.finance != null) {
        FinanceDetailBottomSheet(
            finance = uiState.finance,
            initialTab = financeDialogInitialTab,
            isExpandedScreen = isFinanceExpanded,
            onDismissRequest = { showFinanceDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileListPane(
    uiState: ProfileUiState,
    isWide: Boolean,
    selectedAction: ProfileQuickAction?,
    onRefresh: () -> Unit,
    onRetryLoad: () -> Unit,
    onCheckInClick: () -> Unit,
    onMailClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onOpenFriendList: (userId: Long, initialType: Int) -> Unit,
    onNavigateToUserInfo: (userId: Long) -> Unit,
    onLoginClick: () -> Unit,
    onOpenCollect: (UserCollectNavParam) -> Unit,
    onOpenCreation: (UserCreationNavParam) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenFinanceDialog: (Int) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val extraBottomPadding = if (isWide) 28.dp else 20.dp
    val bottomPadding = rememberBottomBarContentPadding(extraBottomPadding)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        PoiPullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                if ((uiState.isLoading || uiState.isRefreshing) && uiState.profile == null && uiState.isLoggedIn) {
                    Box(modifier = Modifier.statusBarsPadding())
                    PoiLoadingView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 120.dp, bottom = 48.dp)
                    )
                } else if (uiState.errorMessage != null && uiState.profile == null && uiState.isLoggedIn) {
                    Box(modifier = Modifier.statusBarsPadding())
                    ProfileErrorCard(
                        errorMessage = uiState.errorMessage,
                        onRetry = onRetryLoad,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    val profile = uiState.profile
                    val profileId = profile?.id ?: 0L
                    val isLoggedIn = uiState.isLoggedIn && profile != null

                    ExpressiveProfileHeader(
                        profile = profile,
                        fellow = uiState.fellow,
                        userMp = uiState.userMp,
                        isSignedInToday = uiState.isSignedInToday,
                        isCheckingIn = uiState.isCheckingIn,
                        hasUnreadNotice = uiState.hasUnreadNotice,
                        onCheckInClick = onCheckInClick,
                        onMailClick = onMailClick,
                        onSettingsClick = onSettingsClick,
                        onNavigateToFriendList = onOpenFriendList,
                        onNavigateToUserInfo = onNavigateToUserInfo,
                        onLoginClick = onLoginClick
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 10.dp, bottom = bottomPadding),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        FigureAssetHubSection(
                            order = uiState.order,
                            finance = uiState.finance,
                            isLoggedIn = isLoggedIn,
                            onNavigateToCollect = { state ->
                                onOpenCollect(
                                    UserCollectNavParam(
                                        userId = profileId,
                                        initialCollectState = state
                                    )
                                )
                            },
                            onMoneyClick = onOpenFinanceDialog,
                            onLoginClick = onLoginClick
                        )

                        ProfileQuickActionBar(
                            profileId = profileId,
                            isLoggedIn = isLoggedIn,
                            isCompact = !isWide,
                            selectedAction = selectedAction,
                            onNavigateToCollect = onOpenCollect,
                            onNavigateToCreation = onOpenCreation,
                            onNavigateToHistory = onOpenHistory,
                            onShowMessage = onShowMessage,
                            onLoginClick = onLoginClick
                        )
                    }
                }
            }
        }

        if (isWide) {
            VerticalDivider(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ProfileDetailPlaceholder(
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(
                            if (isLoggedIn) PoiIcons.Outlined.CollectionsBookmark else PoiIcons.Outlined.Person
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isLoggedIn) "收藏、发布与浏览历史" else "登录后查看你的收藏与发布",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (isLoggedIn) {
                    "点击左侧对应入口，即可在这里展开查看"
                } else {
                    "可同步预定清单、已入收藏、个人发布与浏览历史"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!isLoggedIn) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = onLoginClick) {
                    Text(
                        text = "去登录",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
