package com.nobody.repoi.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.platform.isLandscapeOrientation
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.navigation.UserCollectNavParam
import com.nobody.repoi.ui.navigation.UserCreationNavParam
import com.nobody.repoi.ui.navigation.rememberBottomBarContentPadding
import com.nobody.repoi.ui.profile.components.AssetShowcaseSection
import com.nobody.repoi.ui.profile.components.ExpressiveProfileHeader
import com.nobody.repoi.ui.profile.components.FinanceDetailBottomSheet
import com.nobody.repoi.ui.profile.components.PreorderShowcaseSection
import com.nobody.repoi.ui.profile.components.ProfileErrorCard
import com.nobody.repoi.ui.profile.components.ProfileQuickActionBar
import com.nobody.repoi.ui.profile.components.ResaleSummarySection
import com.nobody.repoi.ui.profile.components.UserCreationSection
import com.nobody.repoi.ui.user.creation.UserCreationCategory
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToHobby: (DetailNavParam) -> Unit,
    onNavigateToCollect: (UserCollectNavParam) -> Unit = {},
    onNavigateToCreation: (UserCreationNavParam) -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToFriendList: (userId: Long, initialType: Int) -> Unit = { _, _ -> },
    onNavigateToUserInfo: (userId: Long) -> Unit = {},
    onNavigateToMessage: () -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val adaptiveInfo = currentWindowAdaptiveInfoV2()

    val isLandscape = isLandscapeOrientation()
    val isWideScreen = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

    val isDualPane = isLandscape && isWideScreen
    val extraBottomPadding = if (isDualPane) 28.dp else 20.dp
    val bottomPadding = rememberBottomBarContentPadding(extraBottomPadding)

    var showFinanceDialog by rememberSaveable { mutableStateOf(false) }
    var financeDialogInitialTab by rememberSaveable { mutableStateOf(0) }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
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
                        errorMessage = uiState.errorMessage ?: "加载失败",
                        onRetry = { viewModel.loadProfile(isRefresh = false) },
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
                        onCheckInClick = { viewModel.checkIn() },
                        onMailClick = { if (isLoggedIn) onNavigateToMessage() else onNavigateToLogin() },
                        onSettingsClick = onNavigateToSettings,
                        onNavigateToFriendList = onNavigateToFriendList,
                        onNavigateToUserInfo = onNavigateToUserInfo,
                        onLoginClick = onNavigateToLogin
                    )

                    if (!isDualPane) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 14.dp, bottom = bottomPadding),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ProfileQuickActionBar(
                                profileId = profileId,
                                isLoggedIn = isLoggedIn,
                                isCompact = true,
                                onNavigateToCollect = onNavigateToCollect,
                                onNavigateToCreation = onNavigateToCreation,
                                onNavigateToHistory = onNavigateToHistory,
                                onShowMessage = { appMessages.show(it) },
                                onLoginClick = onNavigateToLogin
                            )

                            PreorderShowcaseSection(
                                order = uiState.order,
                                finance = uiState.finance,
                                preorderItems = uiState.preorderItems,
                                isLoggedIn = isLoggedIn,
                                onNavigateToCollect = { state ->
                                    onNavigateToCollect(
                                        UserCollectNavParam(
                                            userId = profileId,
                                            initialCollectState = state
                                        )
                                    )
                                },
                                onMoneyClick = {
                                    financeDialogInitialTab = 0
                                    showFinanceDialog = true
                                },
                                onItemClick = onNavigateToHobby,
                                onLoginClick = onNavigateToLogin
                            )

                            AssetShowcaseSection(
                                order = uiState.order,
                                finance = uiState.finance,
                                buyItems = uiState.buyItems,
                                isLoggedIn = isLoggedIn,
                                onNavigateToCollect = { state ->
                                    onNavigateToCollect(
                                        UserCollectNavParam(
                                            userId = profileId,
                                            initialCollectState = state
                                        )
                                    )
                                },
                                onMoneyClick = {
                                    financeDialogInitialTab = 1
                                    showFinanceDialog = true
                                },
                                onItemClick = onNavigateToHobby,
                                onLoginClick = onNavigateToLogin
                            )

                            UserCreationSection(
                                userAlbums = uiState.userAlbums,
                                isLoggedIn = isLoggedIn,
                                onMoreClick = {
                                    onNavigateToCreation(
                                        UserCreationNavParam(
                                            userId = profileId,
                                            initialCategory = UserCreationCategory.ALBUM.key
                                        )
                                    )
                                },
                                onAlbumClick = onNavigateToAlbum,
                                onPublishClick = {
                                    appMessages.show("相册投稿功能即将上线")
                                },
                                onLoginClick = onNavigateToLogin
                            )

                            ResaleSummarySection(
                                isLoggedIn = isLoggedIn,
                                onPublishClick = {
                                    appMessages.show("二手发布与交易即将推出")
                                },
                                onLoginClick = onNavigateToLogin
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 14.dp, bottom = bottomPadding),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                modifier = Modifier.weight(0.42f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                ProfileQuickActionBar(
                                    profileId = profileId,
                                    isLoggedIn = isLoggedIn,
                                    isCompact = false,
                                    onNavigateToCollect = onNavigateToCollect,
                                    onNavigateToCreation = onNavigateToCreation,
                                    onNavigateToHistory = onNavigateToHistory,
                                    onShowMessage = { appMessages.show(it) },
                                    onLoginClick = onNavigateToLogin
                                )

                                UserCreationSection(
                                    userAlbums = uiState.userAlbums,
                                    isLoggedIn = isLoggedIn,
                                    onMoreClick = {
                                        onNavigateToCreation(
                                            UserCreationNavParam(
                                                userId = profileId,
                                                initialCategory = UserCreationCategory.ALBUM.key
                                            )
                                        )
                                    },
                                    onAlbumClick = onNavigateToAlbum,
                                    onPublishClick = {
                                        appMessages.show("相册投稿功能即将上线")
                                    },
                                    onLoginClick = onNavigateToLogin
                                )

                                ResaleSummarySection(
                                    isLoggedIn = isLoggedIn,
                                    onPublishClick = {
                                        appMessages.show("二手发布与交易即将推出")
                                    },
                                    onLoginClick = onNavigateToLogin
                                )
                            }

                            Column(
                                modifier = Modifier.weight(0.58f),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                PreorderShowcaseSection(
                                    order = uiState.order,
                                    finance = uiState.finance,
                                    preorderItems = uiState.preorderItems,
                                    isLoggedIn = isLoggedIn,
                                    onNavigateToCollect = { state ->
                                        onNavigateToCollect(
                                            UserCollectNavParam(
                                                userId = profileId,
                                                initialCollectState = state
                                            )
                                        )
                                    },
                                    onMoneyClick = {
                                        financeDialogInitialTab = 0
                                        showFinanceDialog = true
                                    },
                                    onItemClick = onNavigateToHobby,
                                    onLoginClick = onNavigateToLogin
                                )

                                AssetShowcaseSection(
                                    order = uiState.order,
                                    finance = uiState.finance,
                                    buyItems = uiState.buyItems,
                                    isLoggedIn = isLoggedIn,
                                    onNavigateToCollect = { state ->
                                        onNavigateToCollect(
                                            UserCollectNavParam(
                                                userId = profileId,
                                                initialCollectState = state
                                            )
                                        )
                                    },
                                    onMoneyClick = {
                                        financeDialogInitialTab = 1
                                        showFinanceDialog = true
                                    },
                                    onItemClick = onNavigateToHobby,
                                    onLoginClick = onNavigateToLogin
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFinanceDialog && uiState.finance != null) {
        FinanceDetailBottomSheet(
            finance = uiState.finance!!,
            initialTab = financeDialogInitialTab,
            isExpandedScreen = isDualPane,
            onDismissRequest = { showFinanceDialog = false }
        )
    }
}
