package com.nobody.repoi.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import com.nobody.repoi.platform.LocalAppMessages
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import com.nobody.repoi.ui.common.PoiPullToRefreshBox
import androidx.compose.runtime.Composable
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.nobody.repoi.platform.isLandscapeOrientation
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import coil3.compose.AsyncImage
import com.nobody.repoi.data.local.ThemeMode
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.CollectTopItem
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.navigation.UserCollectNavParam
import com.nobody.repoi.ui.navigation.UserCreationNavParam
import com.nobody.repoi.ui.navigation.rememberBottomBarContentPadding
import com.nobody.repoi.ui.theme.LocalIsDarkTheme
import com.nobody.repoi.ui.user.creation.UserCreationCategory
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.util.PriceFormatter
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource

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
    val isDark = LocalIsDarkTheme.current
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
    val scope = rememberCoroutineScope()

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
        PoiPullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                if (!uiState.isLoggedIn) {

                    UnauthenticatedProfileContent(
                        isDark = isDark,
                        isDualPane = isDualPane,
                        bottomPadding = bottomPadding,
                        onLoginClick = onNavigateToLogin,
                        onSettingsClick = onNavigateToSettings,
                        onMailClick = onNavigateToLogin,
                        onToggleTheme = { viewModel.toggleTheme(isDark) }
                    )
                } else {

                    if ((uiState.isLoading || uiState.isRefreshing) && uiState.profile == null) {
                        Box(modifier = Modifier.statusBarsPadding())
                        PoiLoadingView(modifier = Modifier.fillMaxWidth().padding(top = 120.dp, bottom = 48.dp))
                    } else if (uiState.errorMessage != null && uiState.profile == null) {
                        Box(modifier = Modifier.statusBarsPadding())
                        ProfileErrorCard(
                            isDark = isDark,
                            errorMessage = uiState.errorMessage ?: "加载失败",
                            onRetry = { viewModel.loadProfile(isRefresh = false) },
                            modifier = Modifier.padding(16.dp)
                        )
                    } else if (uiState.profile != null) {
                        val profile = uiState.profile!!

                        ProfileImmersiveHeader(
                            profile = profile,
                            fellow = uiState.fellow,
                            userMp = uiState.userMp,
                            isSignedInToday = uiState.isSignedInToday,
                            isCheckingIn = uiState.isCheckingIn,
                            isDark = isDark,
                            hasUnreadNotice = uiState.hasUnreadNotice,
                            onToggleTheme = { viewModel.toggleTheme(isDark) },
                            onCheckInClick = { viewModel.checkIn() },
                            onMailClick = onNavigateToMessage,
                            onSettingsClick = onNavigateToSettings,
                            onNavigateToFriendList = onNavigateToFriendList,
                            onNavigateToUserInfo = onNavigateToUserInfo
                        )

                        if (!isDualPane) {

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .padding(bottom = bottomPadding),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {

                                ProfileQuickActionGrid(
                                    profileId = profile.id,
                                    isCompact = true,
                                    onNavigateToCollect = onNavigateToCollect,
                                    onNavigateToCreation = onNavigateToCreation,
                                    onNavigateToHistory = onNavigateToHistory,
                                    onShowMessage = { msg ->
                                        appMessages.show(msg)
                                    }
                                )

                                PreorderShowcaseCard(
                                    order = uiState.order,
                                    finance = uiState.finance,
                                    preorderItems = uiState.preorderItems,
                                    isDark = isDark,
                                    onNavigateToCollect = { state ->
                                        onNavigateToCollect(
                                            UserCollectNavParam(
                                                userId = profile.id,
                                                initialCollectState = state
                                            )
                                        )
                                    },
                                    onMoneyClick = {
                                        financeDialogInitialTab = 0
                                        showFinanceDialog = true
                                    },
                                    onItemClick = onNavigateToHobby
                                )

                                BuyShowcaseCard(
                                    order = uiState.order,
                                    finance = uiState.finance,
                                    buyItems = uiState.buyItems,
                                    isDark = isDark,
                                    onNavigateToCollect = { state ->
                                        onNavigateToCollect(
                                            UserCollectNavParam(
                                                userId = profile.id,
                                                initialCollectState = state
                                            )
                                        )
                                    },
                                    onMoneyClick = {
                                        financeDialogInitialTab = 1
                                        showFinanceDialog = true
                                    },
                                    onItemClick = onNavigateToHobby
                                )

                                ProfileAlbumShowcaseCard(
                                    userAlbums = uiState.userAlbums,
                                    isDark = isDark,
                                    onMoreClick = {
                                        onNavigateToCreation(
                                            UserCreationNavParam(
                                                userId = profile.id,
                                                initialCategory = UserCreationCategory.ALBUM.key
                                            )
                                        )
                                    },
                                    onAlbumClick = onNavigateToAlbum,
                                    onPublishClick = {
                                        appMessages.show("相册投稿功能即将上线")
                                    }
                                )

                                ProfileResaleCard(
                                    onPublishClick = {
                                        appMessages.show("二手发布与交易即将推出")
                                    }
                                )
                            }
                        } else {

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                                    .padding(bottom = bottomPadding),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {

                                Column(
                                    modifier = Modifier.weight(0.42f),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    ProfileQuickActionGrid(
                                        profileId = profile.id,
                                        isCompact = false,
                                        onNavigateToCollect = onNavigateToCollect,
                                        onNavigateToCreation = onNavigateToCreation,
                                        onNavigateToHistory = onNavigateToHistory,
                                        onShowMessage = { msg ->
                                            appMessages.show(msg)
                                        }
                                    )

                                    ProfileAlbumShowcaseCard(
                                        userAlbums = uiState.userAlbums,
                                        isDark = isDark,
                                        onMoreClick = {
                                            onNavigateToCreation(
                                                UserCreationNavParam(
                                                    userId = profile.id,
                                                    initialCategory = UserCreationCategory.ALBUM.key
                                                )
                                            )
                                        },
                                        onAlbumClick = onNavigateToAlbum,
                                        onPublishClick = {
                                            appMessages.show("相册投稿功能即将上线")
                                        }
                                    )

                                    ProfileResaleCard(
                                        onPublishClick = {
                                            appMessages.show("二手发布与交易即将推出")
                                        }
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(0.58f),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    PreorderShowcaseCard(
                                        order = uiState.order,
                                        finance = uiState.finance,
                                        preorderItems = uiState.preorderItems,
                                        isDark = isDark,
                                        onNavigateToCollect = { state ->
                                            onNavigateToCollect(
                                                UserCollectNavParam(
                                                    userId = profile.id,
                                                    initialCollectState = state
                                                )
                                            )
                                        },
                                        onMoneyClick = {
                                            financeDialogInitialTab = 0
                                            showFinanceDialog = true
                                        },
                                        onItemClick = onNavigateToHobby
                                    )

                                    BuyShowcaseCard(
                                        order = uiState.order,
                                        finance = uiState.finance,
                                        buyItems = uiState.buyItems,
                                        isDark = isDark,
                                        onNavigateToCollect = { state ->
                                            onNavigateToCollect(
                                                UserCollectNavParam(
                                                    userId = profile.id,
                                                    initialCollectState = state
                                                )
                                            )
                                        },
                                        onMoneyClick = {
                                            financeDialogInitialTab = 1
                                            showFinanceDialog = true
                                        },
                                        onItemClick = onNavigateToHobby
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    if (showFinanceDialog && uiState.finance != null) {
        SupplementedMoneyDialog(
            finance = uiState.finance!!,
            initialTab = financeDialogInitialTab,
            onDismissRequest = { showFinanceDialog = false }
        )
    }
}

@Composable
private fun ProfileImmersiveHeader(
    profile: UserProfile,
    fellow: com.nobody.repoi.data.remote.model.FellowCountData?,
    userMp: Int,
    isSignedInToday: Boolean,
    isCheckingIn: Boolean,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onCheckInClick: () -> Unit,
    onMailClick: () -> Unit,
    onSettingsClick: () -> Unit,
    hasUnreadNotice: Boolean = false,
    onNavigateToFriendList: (userId: Long, initialType: Int) -> Unit = { _, _ -> },
    onNavigateToUserInfo: (userId: Long) -> Unit = {}
) {
    val bannerUrl = remember(profile.bnr) {
        MediaPipeline.Default.resolveUrl(profile.bnr, MediaCategory.BANNER, ImagePurpose.CARD_STANDARD)
    }
    val avatarUrl = remember(profile.header) {
        MediaPipeline.Default.resolveUrl(profile.header, MediaCategory.AVATAR, ImagePurpose.CARD_STANDARD)
    }

    Box(modifier = Modifier.fillMaxWidth()) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
        ) {
            if (bannerUrl.isNotBlank()) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = "用户横幅",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.40f),
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMailClick) {
                    BadgedBox(
                        badge = {
                            if (hasUnreadNotice) {
                                Badge(containerColor = MaterialTheme.colorScheme.error)
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(PoiIcons.Filled.Mail),
                            contentDescription = "消息中心",
                            tint = Color.White
                        )
                    }
                }

                IconButton(onClick = onToggleTheme) {
                    Icon(
                        painter = painterResource(if (isDark) PoiIcons.Filled.WbSunny else PoiIcons.Filled.NightsStay),
                        contentDescription = "切换深浅色主题",
                        tint = Color.White
                    )
                }

                IconButton(onClick = onSettingsClick) {
                    Icon(
                        painter = painterResource(PoiIcons.Filled.Settings),
                        contentDescription = "设置",
                        tint = Color.White
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .border(width = 2.dp, color = Color.White.copy(alpha = 0.9f), shape = CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { onNavigateToUserInfo(profile.id) },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "头像",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            painter = painterResource(PoiIcons.Filled.AccountBox),
                            contentDescription = "默认头像",
                            modifier = Modifier.size(36.dp),
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onNavigateToUserInfo(profile.id) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            painter = painterResource(PoiIcons.Filled.ChevronRight),
                            contentDescription = "用户资料",
                            modifier = Modifier.size(18.dp),
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 1.dp)
                        ) {
                            Text(
                                text = "Lv.${profile.level}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFFB300),
                                modifier = Modifier.size(16.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "H",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Black
                                    )
                                }
                            }
                            Text(
                                text = if (userMp > 0) userMp.toString() else "0",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.92f)
                            )
                        }

                        Icon(
                            painter = painterResource(PoiIcons.Filled.QrCode),
                            contentDescription = "个人二维码",
                            modifier = Modifier.size(16.dp),
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (isSignedInToday) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(enabled = !isSignedInToday && !isCheckingIn) {
                        onCheckInClick()
                    }
                ) {
                    Text(
                        text = if (isSignedInToday) "已签到" else if (isCheckingIn) "签到中…" else "签到",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSignedInToday) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SocialStatColumn(
                    count = fellow?.friendCount ?: 0,
                    label = "关注",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToFriendList(profile.id, 0) }
                )

                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight(0.55f)
                        .width(1.dp),
                    color = Color.White.copy(alpha = 0.25f)
                )

                SocialStatColumn(
                    count = fellow?.fanCount ?: 0,
                    label = "粉丝",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToFriendList(profile.id, 1) }
                )

                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight(0.55f)
                        .width(1.dp),
                    color = Color.White.copy(alpha = 0.25f)
                )

                SocialStatColumn(
                    count = fellow?.praiseCount ?: 0,
                    label = "获赞",
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToFriendList(profile.id, 3) }
                )
            }
        }
    }
}

@Composable
private fun SocialStatColumn(
    count: Int,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.78f)
        )
    }
}

@Composable
private fun ProfileQuickActionGrid(
    profileId: Long,
    isCompact: Boolean = true,
    onNavigateToCollect: (UserCollectNavParam) -> Unit,
    onNavigateToCreation: (UserCreationNavParam) -> Unit,
    onNavigateToHistory: () -> Unit = {},
    onShowMessage: (String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        if (isCompact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickActionButton(
                    icon = PoiIcons.Filled.Star,
                    label = "我的收藏",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onNavigateToCollect(
                            UserCollectNavParam(
                                userId = profileId,
                                initialCategory = UserCollectCategory.HOBBY.key
                            )
                        )
                    }
                )

                QuickActionButton(
                    icon = PoiIcons.Filled.PhotoLibrary,
                    label = "我的发布",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onNavigateToCreation(
                            UserCreationNavParam(
                                userId = profileId,
                                initialCategory = UserCreationCategory.ALBUM.key
                            )
                        )
                    }
                )

                QuickActionButton(
                    icon = PoiIcons.Filled.ShoppingBag,
                    label = "我的二手",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onShowMessage("二手闲置市场功能即将上线")
                    }
                )

                QuickActionButton(
                    icon = PoiIcons.Filled.History,
                    label = "浏览历史",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToHistory
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickActionButton(
                        icon = PoiIcons.Filled.Star,
                        label = "我的收藏",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onNavigateToCollect(
                                UserCollectNavParam(
                                    userId = profileId,
                                    initialCategory = UserCollectCategory.HOBBY.key
                                )
                            )
                        }
                    )
                    QuickActionButton(
                        icon = PoiIcons.Filled.PhotoLibrary,
                        label = "我的发布",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onNavigateToCreation(
                                UserCreationNavParam(
                                    userId = profileId,
                                    initialCategory = UserCreationCategory.ALBUM.key
                                )
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickActionButton(
                        icon = PoiIcons.Filled.ShoppingBag,
                        label = "我的二手",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onShowMessage("二手闲置市场功能即将上线")
                        }
                    )
                    QuickActionButton(
                        icon = PoiIcons.Filled.History,
                        label = "浏览历史",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToHistory
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionButton(icon: DrawableResource,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = tint
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PreorderShowcaseCard(
    order: com.nobody.repoi.data.remote.model.OrderCount?,
    finance: com.nobody.repoi.data.remote.model.OrderFinanceSummary?,
    preorderItems: List<CollectTopItem>,
    isDark: Boolean,
    onNavigateToCollect: (String) -> Unit,
    onMoneyClick: () -> Unit,
    onItemClick: (DetailNavParam) -> Unit
) {
    val careCount = order?.care ?: 0
    val preorderCount = order?.preorder ?: preorderItems.size
    val supplementedRmb = formatRmb(finance?.supplementedTotalRmb)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 14.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IntegratedStatColumn(
                    title = "想买",
                    value = careCount.toString(),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToCollect("want") }
                )

                IntegratedStatColumn(
                    title = "预定",
                    value = preorderCount.toString(),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToCollect("preorder") }
                )

                IntegratedStatColumn(
                    title = "待补款 ▼",
                    value = supplementedRmb,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = onMoneyClick
                )
            }

            if (preorderItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(preorderItems, key = { it.itemId }) { item ->
                        CollectFigureCard(
                            item = item,
                            isDark = isDark,
                            onClick = {
                                onItemClick(
                                    DetailNavParam(
                                        itemId = item.itemId,
                                        itemType = item.itemType ?: "hobby",
                                        initialName = item.nameCN?.ifBlank { null } ?: item.name,
                                        initialCover = item.cover
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BuyShowcaseCard(
    order: com.nobody.repoi.data.remote.model.OrderCount?,
    finance: com.nobody.repoi.data.remote.model.OrderFinanceSummary?,
    buyItems: List<CollectTopItem>,
    isDark: Boolean,
    onNavigateToCollect: (String) -> Unit,
    onMoneyClick: () -> Unit,
    onItemClick: (DetailNavParam) -> Unit
) {
    val buyCount = order?.buy ?: buyItems.size
    val historyRmb = formatCompactRmb(finance?.historyTotalRmb)
    val fluctuation = finance?.amazonFluctuation ?: "0%"
    val fluctuationColor = when {
        fluctuation.startsWith("+") -> Color(0xFFD32F2F)
        fluctuation.startsWith("-") -> Color(0xFF388E3C)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 14.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IntegratedStatColumn(
                    title = "已入",
                    value = buyCount.toString(),
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToCollect("buy") }
                )

                IntegratedStatColumn(
                    title = "总消费 ▼",
                    value = historyRmb,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = onMoneyClick
                )

                IntegratedStatColumn(
                    title = "日亚涨跌",
                    value = fluctuation,
                    accentColor = fluctuationColor,
                    modifier = Modifier.weight(1f),
                    onClick = null
                )
            }

            if (buyItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(buyItems, key = { it.itemId }) { item ->
                        CollectFigureCard(
                            item = item,
                            isDark = isDark,
                            onClick = {
                                onItemClick(
                                    DetailNavParam(
                                        itemId = item.itemId,
                                        itemType = item.itemType ?: "hobby",
                                        initialName = item.nameCN?.ifBlank { null } ?: item.name,
                                        initialCover = item.cover
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntegratedStatColumn(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProfileAlbumShowcaseCard(
    userAlbums: List<AlbumListItem>,
    isDark: Boolean,
    onMoreClick: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    onPublishClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "我的相册",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                FilledTonalButton(
                    onClick = onPublishClick,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        painter = painterResource(PoiIcons.Filled.Add),
                        contentDescription = "发布",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "发布", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (userAlbums.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无发布的相册",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(userAlbums, key = { it.targetAlbumId }) { album ->
                        ProfileAlbumCard(
                            album = album,
                            onClick = { onAlbumClick(album.targetAlbumId) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TextButton(
                        onClick = onMoreClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "查看更多相册",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            painter = painterResource(PoiIcons.Filled.ChevronRight),
                            contentDescription = "查看更多",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileAlbumCard(
    album: AlbumListItem,
    onClick: () -> Unit
) {
    val coverUrl = remember(album.cover) {
        MediaPipeline.Default.resolveUrl(album.cover, MediaCategory.HOBBY, ImagePurpose.THUMBNAIL)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.size(80.dp),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = album.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(PoiIcons.Filled.PhotoLibrary),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        painter = painterResource(PoiIcons.Filled.ThumbUp),
                        contentDescription = "获赞",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = album.praiseCountValue.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                        )
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = album.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ProfileResaleCard(
    onPublishClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        Text(
                            text = "出售",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "0",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column {
                        Text(
                            text = "收购",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "0",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onPublishClick,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        painter = painterResource(PoiIcons.Filled.Add),
                        contentDescription = "发布",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "发布", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无二手数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
        }
    }
}

@Composable
private fun CollectFigureCard(
    item: CollectTopItem,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val coverUrl = remember(item.cover) {
        MediaPipeline.Default.resolveUrl(item.cover, MediaCategory.HOBBY, ImagePurpose.THUMBNAIL)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = item.nameCN ?: item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    painter = painterResource(PoiIcons.Filled.AccountBox),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun UnauthenticatedProfileContent(
    isDark: Boolean,
    isDualPane: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onLoginClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMailClick: () -> Unit,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {

        UnauthenticatedImmersiveHeader(
            isDark = isDark,
            onLoginClick = onLoginClick,
            onMailClick = onMailClick,
            onSettingsClick = onSettingsClick,
            onToggleTheme = onToggleTheme
        )

        if (!isDualPane) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .padding(bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ProfileQuickActionGrid(
                    profileId = 0L,
                    isCompact = true,
                    onNavigateToCollect = { onLoginClick() },
                    onNavigateToCreation = { onLoginClick() },
                    onNavigateToHistory = onLoginClick,
                    onShowMessage = { onLoginClick() }
                )
                PreorderShowcaseCard(
                    order = null,
                    finance = null,
                    preorderItems = emptyList(),
                    isDark = isDark,
                    onNavigateToCollect = { onLoginClick() },
                    onMoneyClick = onLoginClick,
                    onItemClick = { onLoginClick() }
                )
                BuyShowcaseCard(
                    order = null,
                    finance = null,
                    buyItems = emptyList(),
                    isDark = isDark,
                    onNavigateToCollect = { onLoginClick() },
                    onMoneyClick = onLoginClick,
                    onItemClick = { onLoginClick() }
                )
                ProfileAlbumShowcaseCard(
                    userAlbums = emptyList(),
                    isDark = isDark,
                    onMoreClick = onLoginClick,
                    onAlbumClick = { onLoginClick() },
                    onPublishClick = onLoginClick
                )
                ProfileResaleCard(onPublishClick = onLoginClick)
            }
        } else {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .padding(bottom = bottomPadding),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(0.42f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ProfileQuickActionGrid(
                        profileId = 0L,
                        isCompact = false,
                        onNavigateToCollect = { onLoginClick() },
                        onNavigateToCreation = { onLoginClick() },
                        onNavigateToHistory = onLoginClick,
                        onShowMessage = { onLoginClick() }
                    )
                    ProfileAlbumShowcaseCard(
                        userAlbums = emptyList(),
                        isDark = isDark,
                        onMoreClick = onLoginClick,
                        onAlbumClick = { onLoginClick() },
                        onPublishClick = onLoginClick
                    )
                    ProfileResaleCard(onPublishClick = onLoginClick)
                }
                Column(
                    modifier = Modifier.weight(0.58f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PreorderShowcaseCard(
                        order = null,
                        finance = null,
                        preorderItems = emptyList(),
                        isDark = isDark,
                        onNavigateToCollect = { onLoginClick() },
                        onMoneyClick = onLoginClick,
                        onItemClick = { onLoginClick() }
                    )
                    BuyShowcaseCard(
                        order = null,
                        finance = null,
                        buyItems = emptyList(),
                        isDark = isDark,
                        onNavigateToCollect = { onLoginClick() },
                        onMoneyClick = onLoginClick,
                        onItemClick = { onLoginClick() }
                    )
                }
            }
        }
    }
}

@Composable
private fun UnauthenticatedImmersiveHeader(
    isDark: Boolean,
    onLoginClick: () -> Unit,
    onMailClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleTheme: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.60f)
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMailClick) {
                    Icon(
                        painter = painterResource(PoiIcons.Filled.Mail),
                        contentDescription = "消息中心",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        painter = painterResource(if (isDark) PoiIcons.Filled.WbSunny else PoiIcons.Filled.NightsStay),
                        contentDescription = "切换深浅色主题",
                        tint = Color.White
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        painter = painterResource(PoiIcons.Filled.Settings),
                        contentDescription = "设置",
                        tint = Color.White
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .border(width = 2.dp, color = Color.White.copy(alpha = 0.9f), shape = CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .clickable { onLoginClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(PoiIcons.Filled.Person),
                        contentDescription = "默认头像",
                        modifier = Modifier.size(36.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onLoginClick() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "点击登录",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            painter = painterResource(PoiIcons.Filled.ChevronRight),
                            contentDescription = "登录",
                            modifier = Modifier.size(18.dp),
                            tint = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "登录后同步心愿与手账",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onLoginClick() }
                ) {
                    Text(
                        text = "登录",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SocialStatColumn(
                    count = 0,
                    label = "关注",
                    modifier = Modifier.weight(1f),
                    onClick = onLoginClick
                )
                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight(0.55f)
                        .width(1.dp),
                    color = Color.White.copy(alpha = 0.25f)
                )
                SocialStatColumn(
                    count = 0,
                    label = "粉丝",
                    modifier = Modifier.weight(1f),
                    onClick = onLoginClick
                )
                VerticalDivider(
                    modifier = Modifier
                        .fillMaxHeight(0.55f)
                        .width(1.dp),
                    color = Color.White.copy(alpha = 0.25f)
                )
                SocialStatColumn(
                    count = 0,
                    label = "获赞",
                    modifier = Modifier.weight(1f),
                    onClick = onLoginClick
                )
            }
        }
    }
}

@Composable
private fun ProfileErrorCard(
    isDark: Boolean,
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "数据加载失败",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onRetry,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "重新加载",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SupplementedMoneyDialog(
    finance: com.nobody.repoi.data.remote.model.OrderFinanceSummary,
    initialTab: Int = 0,
    onDismissRequest: () -> Unit
) {
    var selectedTab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }

    val currentTotalRmb = if (selectedTab == 0) finance.supplementedTotalRmb else finance.historyTotalRmb
    val currentGroups = if (selectedTab == 0) finance.supplementedGroups else finance.historyGroups
    val currentSummaryLabel = if (selectedTab == 0) "待补款总折合" else "已入手办总折合"

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "资产与订单金额明细",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(PoiIcons.Filled.Close),
                            contentDescription = "关闭",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "待补款明细",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "已入总额明细",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = currentSummaryLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatRmb(currentTotalRmb),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "注：外币折算按近似固定汇率估算（JPY×0.06、USD×6.4、EUR×6.9、HKD×0.92）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            lineHeight = 14.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentGroups.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无对应币种明细",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        currentGroups.forEach { group ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = group.categoryName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        group.items.forEach { currencyItem ->
                                            val formattedCurrency = PriceFormatter.formatCurrency(currencyItem.currency)
                                            val moneyText = if (currencyItem.money % 1.0 == 0.0) {
                                                formatGrouped(currencyItem.money.toLong())
                                            } else {
                                                formatGrouped(currencyItem.money)
                                            }
                                            val displayText = if (formattedCurrency.isNotBlank()) {
                                                "$formattedCurrency: $moneyText"
                                            } else {
                                                moneyText
                                            }
                                            Text(
                                                text = displayText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = "折合 " + formatRmb(group.totalRmb),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismissRequest,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "知道了", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatRmb(amount: Double?): String {
    val value = amount ?: 0.0
    return if (abs(value) < 1e-5) {
        "¥0"
    } else if (value % 1.0 == 0.0) {
        "¥" + formatGrouped(value.toLong())
    } else {
        "¥" + formatGrouped(value)
    }
}

private fun formatCompactRmb(amount: Double?): String {
    val value = amount ?: 0.0
    return when {
        abs(value) < 1e-5 -> "¥0"
        value >= 10000.0 -> {
            val wan = value / 10000.0
            "¥" + PriceFormatter.formatFixed(wan, 1).trimEnd('0').trimEnd('.') + "万"
        }
        value % 1.0 == 0.0 -> "¥" + formatGrouped(value.toLong())
        else -> "¥" + formatGrouped(value)
    }
}

private fun formatGrouped(value: Long): String = formatGrouped(value.toDouble())

private fun formatGrouped(value: Double): String {
    val negative = value < 0
    val absValue = kotlin.math.abs(value)
    val intPart = absValue.toLong()
    val frac = absValue - intPart
    val intGrouped = intPart.toString().reversed().chunked(3).joinToString(",").reversed()
    if (frac < 0.005) return (if (negative) "-" else "") + intGrouped
    val fracStr = PriceFormatter.formatFixed(absValue - intPart, 2).substringAfter(".", "")
        .trimEnd('0')
    return (if (negative) "-" else "") + intGrouped + if (fracStr.isEmpty()) "" else ".$fracStr"
}
