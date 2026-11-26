package com.nobody.repoi.ui.user

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.CategoryMoneyGroup
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.album.components.AlbumFeedCard
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.PoiIcons
import com.nobody.repoi.ui.common.UserAvatar
import com.nobody.repoi.ui.common.bannerAspectRatio
import com.nobody.repoi.ui.common.calculateBannerHeight
import com.nobody.repoi.ui.common.plainTextClipEntry
import com.nobody.repoi.ui.home.components.ActionCard
import com.nobody.repoi.ui.navigation.UserCollectNavParam
import com.nobody.repoi.util.PriceFormatter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun UserInfoScreen(
    userId: Long = 0L,
    onNavigateBack: () -> Unit = {},
    onNavigateToFriendList: (userId: Long, initialType: Int) -> Unit = { _, _ -> },
    onNavigateToCollect: (UserCollectNavParam) -> Unit = {},
    onNavigateToAlbum: (AlbumNavParam) -> Unit = {},
    onNavigateToHobby: (Long) -> Unit = {},
    onNavigateToUser: (Long) -> Unit = {},
    onNavigateToChat: (targetUserId: Long, targetUserName: String, targetUserHeader: String?) -> Unit = { _, _, _ -> },
    viewModel: UserInfoViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val imageViewer = LocalImageViewer.current

    val adaptiveInfo = currentWindowAdaptiveInfoV2()

    val isLandscapeState = remember { mutableStateOf(false) }
    val isWideScreen = adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

    LaunchedEffect(userId) {
        viewModel.initUserId(userId)
    }

    LaunchedEffect(uiState.saveSuccessMessage) {
        uiState.saveSuccessMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSuccessMessage()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        if (uiState.profile != null) {
            uiState.errorMessage?.let { err ->
                snackbarHostState.showSnackbar(err)
                viewModel.clearErrorMessage()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.navigationBars,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            isLandscapeState.value = maxWidth > maxHeight
            val isLandscape = isLandscapeState.value
            val isDualPane = isLandscape && isWideScreen

            when {
                uiState.isLoading && uiState.profile == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LoadingIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "正在加载个人主页...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                uiState.errorMessage != null && uiState.profile == null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(PoiIcons.Filled.Person),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.errorMessage ?: "加载主页失败",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadProfile(userId) }) {
                            Icon(painter = painterResource(PoiIcons.Filled.Refresh), contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "重新加载")
                        }
                    }
                }

                uiState.profile != null -> {
                    val profile = uiState.profile!!
                    val clipboard = LocalClipboard.current

                    val pagerState = rememberPagerState(
                        initialPage = uiState.selectedTab.coerceIn(0, 2)
                    ) { 3 }

                    LaunchedEffect(pagerState.currentPage) {
                        if (uiState.selectedTab != pagerState.currentPage) {
                            viewModel.selectTab(pagerState.currentPage)
                        }
                    }

                    LaunchedEffect(uiState.selectedTab) {
                        if (pagerState.currentPage != uiState.selectedTab) {
                            pagerState.scrollToPage(uiState.selectedTab)
                        }
                    }

                    if (isDualPane) {
                        Row(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            UserProfileIdentityPane(
                                profile = profile,
                                uiState = uiState,
                                isSelf = uiState.isSelf,
                                onBack = onNavigateBack,
                                onEdit = { viewModel.startEditing() },
                                onRefresh = { viewModel.loadProfile(profile.id) },
                                onBannerClick = {
                                    val raw = MediaPipeline.Default.resolveRawUrl(profile.bnr, MediaCategory.BANNER)
                                    if (raw.isNotBlank()) imageViewer.show(raw)
                                },
                                onAvatarClick = {
                                    val raw = MediaPipeline.Default.resolveRawUrl(profile.header, MediaCategory.AVATAR)
                                    if (raw.isNotBlank()) imageViewer.show(raw)
                                },
                                onNavigateToFriendList = { tab ->
                                    onNavigateToFriendList(profile.id, tab)
                                },
                                onToggleFollow = { viewModel.toggleFollow() },
                                onToggleIntro = { viewModel.toggleIntroExpanded() },
                                onNavigateToCollect = onNavigateToCollect,
                                onShowMoneyDialog = { viewModel.setShowMoneyDialog(true) },
                                onCopyUid = { uid ->
                                    coroutineScope.launch {
                                        clipboard.setClipEntry(plainTextClipEntry(uid.toString()))
                                        snackbarHostState.showSnackbar("UID 已复制")
                                    }
                                },
                                onNavigateToChat = onNavigateToChat,
                                modifier = Modifier
                                    .weight(0.38f)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                            )

                            VerticalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxHeight()
                            )

                            Column(
                                modifier = Modifier
                                    .weight(0.62f)
                                    .fillMaxHeight()
                                    .padding(top = 8.dp)
                            ) {
                                UserContentTabs(
                                    selectedTab = pagerState.currentPage,
                                    onSelectTab = { index ->
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxSize()
                                ) { page ->
                                    when (page) {
                                        0 -> UserAlbumPage(
                                            uiState = uiState,
                                            columns = 3,
                                            onNavigateToAlbum = onNavigateToAlbum,
                                            onSelectAlbumSubTab = { viewModel.selectAlbumSubTab(it) }
                                        )
                                        1 -> UserDynamicPage(
                                            uiState = uiState,
                                            onNavigateToAlbum = onNavigateToAlbum,
                                            onNavigateToHobby = onNavigateToHobby,
                                            onNavigateToUser = onNavigateToUser,
                                            onImageClick = { images, index ->
                                                if (images.isNotEmpty()) {
                                                    imageViewer.show(images, index)
                                                }
                                            }
                                        )
                                        2 -> UserResalePage(
                                            isSelf = uiState.isSelf
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val density = LocalDensity.current
                        val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                        val topBarHeight = statusBarTopPadding + 56.dp
                        val tabRowHeight = 48.dp

                        val topBarHeightPx = with(density) { topBarHeight.toPx() }
                        val bannerHeightDp = calculateBannerHeight(minOf(maxWidth, 720.dp))
                        val bannerHeightPx = with(density) { bannerHeightDp.toPx() }
                        val bannerThresholdPx = remember(bannerHeightPx, topBarHeightPx) {
                            (bannerHeightPx - topBarHeightPx).coerceAtLeast(1f)
                        }

                        var headerContentHeightPx by remember { mutableFloatStateOf(0f) }
                        val maxScrollUpPx by remember(headerContentHeightPx, topBarHeightPx) {
                            derivedStateOf {
                                (headerContentHeightPx - topBarHeightPx).coerceAtLeast(0f)
                            }
                        }

                        var scrollOffsetPx by remember { mutableFloatStateOf(0f) }

                        LaunchedEffect(maxScrollUpPx) {
                            if (scrollOffsetPx > maxScrollUpPx) {
                                scrollOffsetPx = maxScrollUpPx
                            }
                        }

                        val collapseProgress by remember(bannerThresholdPx) {
                            derivedStateOf {
                                if (bannerThresholdPx <= 0f) 0f
                                else (scrollOffsetPx / bannerThresholdPx).coerceIn(0f, 1f)
                            }
                        }

                        val isTitleVisible by remember(bannerHeightPx) {
                            derivedStateOf {
                                scrollOffsetPx >= bannerHeightPx
                            }
                        }

                        val nestedScrollConnection = remember(pagerState) {
                            object : NestedScrollConnection {
                                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                    if (pagerState.isScrollInProgress) {
                                        return Offset.Zero
                                    }
                                    val delta = available.y
                                    return if (delta < 0f && scrollOffsetPx < maxScrollUpPx) {
                                        val newOffset = (scrollOffsetPx - delta).coerceIn(0f, maxScrollUpPx)
                                        val consumed = -(newOffset - scrollOffsetPx)
                                        scrollOffsetPx = newOffset
                                        Offset(0f, consumed)
                                    } else {
                                        Offset.Zero
                                    }
                                }

                                override fun onPostScroll(
                                    consumed: Offset,
                                    available: Offset,
                                    source: NestedScrollSource
                                ): Offset {
                                    if (pagerState.isScrollInProgress) {
                                        return Offset.Zero
                                    }
                                    val delta = available.y
                                    return if (delta > 0f && scrollOffsetPx > 0f) {
                                        val newOffset = (scrollOffsetPx - delta).coerceIn(0f, maxScrollUpPx)
                                        val consumedY = -(newOffset - scrollOffsetPx)
                                        scrollOffsetPx = newOffset
                                        Offset(0f, consumedY)
                                    } else {
                                        Offset.Zero
                                    }
                                }
                            }
                        }

                        val headerScrollableState = rememberScrollableState { delta ->
                            val old = scrollOffsetPx
                            val target = (scrollOffsetPx - delta).coerceIn(0f, maxScrollUpPx)
                            scrollOffsetPx = target
                            -(target - old)
                        }

                        val currentTabRowYPx by remember(topBarHeightPx) {
                            derivedStateOf {
                                (headerContentHeightPx - scrollOffsetPx).coerceAtLeast(topBarHeightPx)
                            }
                        }

                        val remainingHeightDp = with(density) {
                            (maxHeight.toPx() - currentTabRowYPx).coerceAtLeast(0f).toDp()
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = 720.dp)
                                .fillMaxWidth()
                                .nestedScroll(nestedScrollConnection),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset { IntOffset(x = 0, y = -scrollOffsetPx.roundToInt()) }
                                    .scrollable(
                                        state = headerScrollableState,
                                        orientation = Orientation.Vertical
                                    )
                                    .onGloballyPositioned { coordinates ->
                                        val h = coordinates.size.height.toFloat()
                                        if (headerContentHeightPx != h) {
                                            headerContentHeightPx = h
                                        }
                                    }
                            ) {
                                UserProfileBanner(
                                    profile = profile,
                                    isSelf = uiState.isSelf,
                                    onBack = onNavigateBack,
                                    onEdit = { viewModel.startEditing() },
                                    onRefresh = { viewModel.loadProfile(profile.id) },
                                    onBannerClick = {
                                        val raw = MediaPipeline.Default.resolveRawUrl(profile.bnr, MediaCategory.BANNER)
                                        if (raw.isNotBlank()) imageViewer.show(raw)
                                    },
                                    showNavigationActions = false
                                )

                                UserProfileHeader(
                                    profile = profile,
                                    uiState = uiState,
                                    onAvatarClick = {
                                        val raw = MediaPipeline.Default.resolveRawUrl(profile.header, MediaCategory.AVATAR)
                                        if (raw.isNotBlank()) imageViewer.show(raw)
                                    },
                                    onNavigateToFriendList = { tab ->
                                        onNavigateToFriendList(profile.id, tab)
                                    },
                                    onToggleFollow = { viewModel.toggleFollow() },
                                    onToggleIntro = { viewModel.toggleIntroExpanded() },
                                    onNavigateToChat = onNavigateToChat
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                UserOrderStatsCard(
                                    profile = profile,
                                    uiState = uiState,
                                    onNavigateToCollect = onNavigateToCollect,
                                    onShowMoneyDialog = { viewModel.setShowMoneyDialog(true) }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                BasicProfileSection(
                                    profile = profile,
                                    onCopyUid = { uid ->
                                        coroutineScope.launch {
                                            clipboard.setClipEntry(plainTextClipEntry(uid.toString()))
                                            snackbarHostState.showSnackbar("UID 已复制")
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset { IntOffset(x = 0, y = currentTabRowYPx.roundToInt()) }
                                    .height(remainingHeightDp)
                            ) {
                                UserContentTabs(
                                    selectedTab = pagerState.currentPage,
                                    onSelectTab = { index ->
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                )

                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                ) { page ->
                                    when (page) {
                                        0 -> UserAlbumPage(
                                            uiState = uiState,
                                            columns = 2,
                                            onNavigateToAlbum = onNavigateToAlbum,
                                            onSelectAlbumSubTab = { viewModel.selectAlbumSubTab(it) }
                                        )
                                        1 -> UserDynamicPage(
                                            uiState = uiState,
                                            onNavigateToAlbum = onNavigateToAlbum,
                                            onNavigateToHobby = onNavigateToHobby,
                                            onNavigateToUser = onNavigateToUser,
                                            onImageClick = { images, index ->
                                                if (images.isNotEmpty()) {
                                                    imageViewer.show(images, index)
                                                }
                                            }
                                        )
                                        2 -> UserResalePage(
                                            isSelf = uiState.isSelf
                                        )
                                    }
                                }
                            }

                            UserInfoTopAppBar(
                                profile = profile,
                                isSelf = uiState.isSelf,
                                isFollowed = uiState.isFollowed,
                                collapseProgress = collapseProgress,
                                isTitleVisible = isTitleVisible,
                                onBack = onNavigateBack,
                                onEdit = { viewModel.startEditing() },
                                onRefresh = { viewModel.loadProfile(profile.id) },
                                onToggleFollow = { viewModel.toggleFollow() },
                                onAvatarClick = {
                                    val raw = MediaPipeline.Default.resolveRawUrl(profile.header, MediaCategory.AVATAR)
                                    if (raw.isNotBlank()) imageViewer.show(raw)
                                },
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.showMoneyDialog) {
        YearlyMoneyBreakdownDialog(
            totalMoneyText = uiState.totalMoneyText,
            moneyGroups = uiState.historyMoneyGroups,
            onDismiss = { viewModel.setShowMoneyDialog(false) }
        )
    }

    if (uiState.isEditing && uiState.profile != null) {
        EditProfileBottomSheet(
            profile = uiState.profile!!,
            isSaving = uiState.isSaving,
            onDismiss = { viewModel.dismissEditing() },
            onSave = { nickname, sign, intro, sex, birthday, city ->
                viewModel.updateAll(
                    nickname = nickname,
                    sign = sign,
                    intro = intro,
                    sex = sex,
                    birthday = birthday,
                    city = city
                )
            }
        )
    }
}

@Composable
private fun UserInfoTopAppBar(
    profile: UserProfile,
    isSelf: Boolean,
    isFollowed: Boolean,
    collapseProgress: Float,
    isTitleVisible: Boolean = collapseProgress >= 0.85f,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onToggleFollow: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSolid = collapseProgress >= 0.85f

    val surfaceColor = MaterialTheme.colorScheme.surface
    val backgroundColor = remember(collapseProgress, surfaceColor) {
        if (collapseProgress <= 0.02f) {
            Color.Transparent
        } else if (collapseProgress >= 0.98f) {
            surfaceColor
        } else {
            surfaceColor.copy(alpha = collapseProgress.coerceIn(0f, 1f))
        }
    }

    Surface(
        color = backgroundColor,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopBarActionIconButton(
                icon = PoiIcons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                isSolid = isSolid,
                onClick = onBack
            )

            UserInfoTopBarTitle(
                profile = profile,
                isSelf = isSelf,
                isFollowed = isFollowed,
                visible = isTitleVisible,
                onAvatarClick = onAvatarClick,
                onToggleFollow = onToggleFollow,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelf) {
                    TopBarActionIconButton(
                        icon = PoiIcons.Filled.Edit,
                        contentDescription = "编辑资料",
                        isSolid = isSolid,
                        onClick = onEdit
                    )
                }

                TopBarActionIconButton(
                    icon = PoiIcons.Filled.Refresh,
                    contentDescription = "刷新",
                    isSolid = isSolid,
                    onClick = onRefresh
                )
            }
        }
    }
}

@Composable
private fun UserInfoTopBarTitle(
    profile: UserProfile,
    isSelf: Boolean,
    isFollowed: Boolean,
    visible: Boolean,
    onAvatarClick: () -> Unit,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp)
            ) {
                UserAvatar(
                    avatarUrl = profile.header,
                    size = 32.dp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onAvatarClick)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!isSelf && !isFollowed) {
                    Spacer(modifier = Modifier.width(8.dp))
                    SmallFollowButton(
                        isFollowed = false,
                        onClick = onToggleFollow
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBarActionIconButton(
    icon: DrawableResource,
    contentDescription: String,
    isSolid: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isSolid) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.35f),
            modifier = modifier.size(36.dp)
        ) {
            IconButton(onClick = onClick) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = contentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SmallFollowButton(
    isFollowed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isFollowed) {
        Button(
            onClick = onClick,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = modifier.height(28.dp)
        ) {
            Icon(
                painter = painterResource(PoiIcons.Add),
                contentDescription = null,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "关注",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun UserAlbumPage(
    uiState: UserInfoUiState,
    columns: Int = 2,
    onNavigateToAlbum: (AlbumNavParam) -> Unit,
    onSelectAlbumSubTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "album_sub_chips", contentType = "album_sub_chips") {
            UserAlbumSubChips(
                isSelf = uiState.isSelf,
                selectedSubTab = uiState.selectedAlbumSubTab,
                onSelectSubTab = onSelectAlbumSubTab
            )
        }

        if (uiState.isAlbumLoading && uiState.albums.isEmpty()) {
            item(key = "album_loading_${uiState.selectedAlbumSubTab}", contentType = "album_loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else if (uiState.albums.isEmpty()) {
            item(key = "empty_albums_${uiState.selectedAlbumSubTab}", contentType = "empty_albums") {
                val emptyText = when (uiState.selectedAlbumSubTab) {
                    0 -> if (uiState.isSelf) "你还没有发布相册" else "该用户暂无相册"
                    1 -> if (uiState.isSelf) "你还没有收藏相册" else "该用户暂无收藏相册"
                    2 -> if (uiState.isSelf) "你还没有发布作品" else "该用户暂无作品"
                    else -> "暂无相册"
                }
                EmptyTabPlaceholder(
                    icon = PoiIcons.Filled.PhotoLibrary,
                    text = emptyText
                )
            }
        } else {
            item(key = "user_albums_waterfall_${uiState.selectedAlbumSubTab}", contentType = "user_albums_waterfall") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (colIndex in 0 until columns) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            uiState.albums.forEachIndexed { index, album ->
                                if (index % columns == colIndex) {
                                    AlbumFeedCard(
                                        item = album,
                                        onClick = {
                                            onNavigateToAlbum(
                                                AlbumNavParam(
                                                    albumId = album.targetAlbumId,
                                                    initialTitle = album.nameCN?.ifBlank { null } ?: album.name,
                                                    initialCover = album.cover
                                                )
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserDynamicPage(
    uiState: UserInfoUiState,
    onNavigateToAlbum: (AlbumNavParam) -> Unit,
    onNavigateToHobby: (Long) -> Unit,
    onNavigateToUser: (Long) -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.actions.isEmpty()) {
            item(key = "empty_actions", contentType = "empty_actions") {
                EmptyTabPlaceholder(
                    icon = PoiIcons.Filled.Timeline,
                    text = if (uiState.isSelf) "你还没有发布动态" else "该用户暂无动态"
                )
            }
        } else {
            items(
                items = uiState.actions,
                key = { it.id },
                contentType = { "action_card" }
            ) { action ->
                ActionCard(
                    actionItem = action,
                    fallbackAvatarUrl = MediaPipeline.Default.resolveUrl(
                        uiState.profile?.header,
                        MediaCategory.AVATAR,
                        ImagePurpose.CARD_STANDARD
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    onActionClick = { item ->
                        val targetRelate = item.relateItem
                        if (targetRelate != null) {
                            val isAlbum = targetRelate.isAlbum || item.actionType == 3
                            if (isAlbum) {
                                val targetAlbumId = targetRelate.actualAlbumId
                                if (targetAlbumId > 0L) {
                                    onNavigateToAlbum(
                                        AlbumNavParam(
                                            albumId = targetAlbumId,
                                            initialTitle = targetRelate.nameCN?.ifBlank { null } ?: targetRelate.name,
                                            initialCover = targetRelate.cover ?: targetRelate.path
                                        )
                                    )
                                }
                            } else {
                                val targetHobbyId = targetRelate.actualHobbyId
                                if (targetHobbyId > 0L) {
                                    onNavigateToHobby(targetHobbyId)
                                }
                            }
                        } else {
                            val targetUserId = item.user?.actualUserId ?: 0L
                            if (targetUserId > 0L) {
                                onNavigateToUser(targetUserId)
                            }
                        }
                    },
                    onImageClick = onImageClick
                )
            }
        }
    }
}

@Composable
private fun UserResalePage(
    isSelf: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "empty_resale", contentType = "empty_resale") {
            EmptyTabPlaceholder(
                icon = PoiIcons.Filled.Sell,
                text = if (isSelf) "你暂无在售二手条目" else "该用户暂无在售二手条目"
            )
        }
    }
}

@Composable
private fun UserProfileIdentityPane(
    profile: UserProfile,
    uiState: UserInfoUiState,
    isSelf: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onBannerClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onNavigateToFriendList: (Int) -> Unit,
    onToggleFollow: () -> Unit,
    onToggleIntro: () -> Unit,
    onNavigateToCollect: (UserCollectNavParam) -> Unit,
    onShowMoneyDialog: () -> Unit,
    onCopyUid: (Long) -> Unit,
    onNavigateToChat: (targetUserId: Long, targetUserName: String, targetUserHeader: String?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UserProfileBanner(
            profile = profile,
            isSelf = isSelf,
            onBack = onBack,
            onEdit = onEdit,
            onRefresh = onRefresh,
            onBannerClick = onBannerClick,
            showNavigationActions = true
        )

        UserProfileHeader(
            profile = profile,
            uiState = uiState,
            onAvatarClick = onAvatarClick,
            onNavigateToFriendList = onNavigateToFriendList,
            onToggleFollow = onToggleFollow,
            onToggleIntro = onToggleIntro,
            onNavigateToChat = onNavigateToChat
        )

        UserOrderStatsCard(
            profile = profile,
            uiState = uiState,
            onNavigateToCollect = onNavigateToCollect,
            onShowMoneyDialog = onShowMoneyDialog
        )

        BasicProfileSection(
            profile = profile,
            onCopyUid = onCopyUid
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
internal fun UserProfileBanner(
    profile: UserProfile,
    isSelf: Boolean,
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onBannerClick: () -> Unit = {},
    showNavigationActions: Boolean = true,
    modifier: Modifier = Modifier
) {
    val bannerUrl = MediaPipeline.Default.resolveUrl(profile.bnr, MediaCategory.BANNER, ImagePurpose.CARD_STANDARD)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .bannerAspectRatio()
            .testTag("user_profile_banner")
    ) {
        if (bannerUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(bannerUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "用户横幅",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onBannerClick() }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    )
            )
        }

        if (showNavigationActions) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.35f),
                    modifier = Modifier.size(36.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(PoiIcons.AutoMirrored.Filled.ArrowBack),
                            contentDescription = "返回",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isSelf) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.35f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            IconButton(onClick = onEdit) {
                                Icon(
                                    painter = painterResource(PoiIcons.Filled.Edit),
                                    contentDescription = "编辑资料",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.35f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                painter = painterResource(PoiIcons.Filled.Refresh),
                                contentDescription = "刷新",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserProfileHeader(
    profile: UserProfile,
    uiState: UserInfoUiState,
    onAvatarClick: () -> Unit,
    onNavigateToFriendList: (Int) -> Unit,
    onToggleFollow: () -> Unit,
    onToggleIntro: () -> Unit,
    onNavigateToChat: (targetUserId: Long, targetUserName: String, targetUserHeader: String?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val avatarUrl = MediaPipeline.Default.resolveUrl(profile.header, MediaCategory.AVATAR, ImagePurpose.CARD_STANDARD)
    val caresCount = uiState.fellowCount?.friendCount ?: profile.cares
    val fansCount = uiState.fellowCount?.fanCount ?: profile.fans
    val praiseCount = uiState.fellowCount?.praiseCount ?: profile.praise

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                shadowElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .size(72.dp)
                    .border(
                        width = 2.5.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    )
            ) {
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "用户头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onAvatarClick() }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(PoiIcons.Filled.Person),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserSocialStatColumn(
                    count = caresCount,
                    label = "关注",
                    onClick = { onNavigateToFriendList(0) }
                )

                VerticalDivider(
                    modifier = Modifier.height(26.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )

                UserSocialStatColumn(
                    count = fansCount,
                    label = "粉丝",
                    onClick = { onNavigateToFriendList(1) }
                )

                VerticalDivider(
                    modifier = Modifier.height(26.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )

                UserSocialStatColumn(
                    count = praiseCount,
                    label = "获赞",
                    onClick = { onNavigateToFriendList(3) }
                )
            }

            if (!uiState.isSelf) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    UserFollowButton(
                        isFollowed = uiState.isFollowed,
                        isMutual = uiState.isMutual,
                        onClick = onToggleFollow
                    )
                    OutlinedButton(
                        onClick = { onNavigateToChat(profile.id, profile.displayName, profile.header) },
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "私信",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "Lv.${profile.level}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!uiState.isSelf) {
                UserSimilarityBadge(similarity = uiState.similarity)
            }
        }

        if (profile.isVerified) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    painter = painterResource(PoiIcons.Filled.Verified),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = profile.verify.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val signText = profile.sign?.ifBlank { null } ?: "还没有信仰_(:3」∠)_"
        Text(
            text = "签名：$signText",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val introText = profile.intro?.ifBlank { null } ?: "这个人很神秘，什么都没写~"
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "介绍：$introText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (uiState.isIntroExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (introText.length > 36) {
                Text(
                    text = if (uiState.isIntroExpanded) "收起" else "展开",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable { onToggleIntro() }
                        .padding(start = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun UserSocialStatColumn(
    count: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatStatCount(count),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun UserFollowButton(
    isFollowed: Boolean,
    isMutual: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isFollowed) {
        OutlinedButton(
            onClick = onClick,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            modifier = modifier.height(32.dp)
        ) {
            if (isMutual) {
                Icon(
                    painter = painterResource(PoiIcons.SyncAlt),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = if (isMutual) "互相关注" else "已关注",
                style = MaterialTheme.typography.labelMedium,
                color = if (isMutual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    } else {
        Button(
            onClick = onClick,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = modifier.height(32.dp)
        ) {
            Icon(
                painter = painterResource(PoiIcons.Add),
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "关注",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun UserSimilarityBadge(
    similarity: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "$similarity%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "收藏相似度",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun UserOrderStatsCard(
    profile: UserProfile,
    uiState: UserInfoUiState,
    onNavigateToCollect: (UserCollectNavParam) -> Unit,
    onShowMoneyDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wantCount = uiState.orderStatic?.count?.actualWant ?: profile.wantCount
    val preorderCount = uiState.orderStatic?.count?.preorder ?: profile.preorderCount
    val buyCount = uiState.orderStatic?.count?.buy ?: profile.buyCount

    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrderStatColumn(
                title = "想买",
                value = wantCount.toString(),
                onClick = {
                    onNavigateToCollect(
                        UserCollectNavParam(
                            userId = profile.id,
                            initialCategory = 10000,
                            initialCollectState = "want",
                            initialOrder = "release"
                        )
                    )
                }
            )

            OrderStatColumn(
                title = "预定",
                value = preorderCount.toString(),
                onClick = {
                    onNavigateToCollect(
                        UserCollectNavParam(
                            userId = profile.id,
                            initialCategory = 10000,
                            initialCollectState = "preorder",
                            initialOrder = "release"
                        )
                    )
                }
            )

            OrderStatColumn(
                title = "已入",
                value = buyCount.toString(),
                onClick = {
                    onNavigateToCollect(
                        UserCollectNavParam(
                            userId = profile.id,
                            initialCategory = 10000,
                            initialCollectState = "buy",
                            initialOrder = "orderTime"
                        )
                    )
                }
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onShowMoneyDialog)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "总消费",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Icon(
                        painter = painterResource(PoiIcons.ArrowDropDown),
                        contentDescription = "查看消费明细",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = uiState.totalMoneyText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun OrderStatColumn(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun UserContentTabs(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("相册", "动态", "二手")

    PrimaryTabRow(
        selectedTabIndex = selectedTab,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        containerColor = Color.Transparent,
        divider = {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedTab == index,
                onClick = { onSelectTab(index) },
                text = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

@Composable
private fun UserAlbumSubChips(
    isSelf: Boolean,
    selectedSubTab: Int,
    onSelectSubTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val subChips = listOf(
        if (isSelf) "我的相册" else "TA的相册",
        "收藏相册",
        "作品"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        subChips.forEachIndexed { index, label ->
            FilterChip(
                selected = selectedSubTab == index,
                onClick = { onSelectSubTab(index) },
                label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
                shape = CircleShape
            )
        }
    }
}

@Composable
private fun EmptyTabPlaceholder(
    icon: DrawableResource,
    text: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun BasicProfileSection(
    profile: UserProfile,
    onCopyUid: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    painter = painterResource(PoiIcons.Filled.Info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "基本信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            ProfileInfoRow(
                icon = PoiIcons.Filled.Fingerprint,
                label = "UID",
                value = profile.id.toString(),
                trailingContent = {
                    IconButton(
                        onClick = {
                            onCopyUid(profile.id)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(PoiIcons.Filled.ContentCopy),
                            contentDescription = "复制UID",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )

            ProfileInfoRow(
                icon = PoiIcons.Filled.Wc,
                label = "性别",
                value = profile.genderText
            )

            ProfileInfoRow(
                icon = PoiIcons.Filled.Cake,
                label = "生日",
                value = profile.birth?.ifBlank { null } ?: "未设置"
            )

            ProfileInfoRow(
                icon = PoiIcons.Filled.LocationOn,
                label = "城市",
                value = profile.city?.ifBlank { null } ?: "未设置"
            )

            ProfileInfoRow(
                icon = PoiIcons.Filled.CalendarMonth,
                label = "注册时间",
                value = profile.displayRegisterTime
            )

            if (profile.isVerified) {
                ProfileInfoRow(
                    icon = PoiIcons.Filled.Verified,
                    label = "认证信息",
                    value = profile.verify.orEmpty()
                )
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: DrawableResource,
    label: String,
    value: String = "",
    customValue: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (customValue != null) {
                customValue()
            } else {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailingContent != null) {
            trailingContent()
        }
    }
}

@Composable
private fun YearlyMoneyBreakdownDialog(
    totalMoneyText: String,
    moneyGroups: List<CategoryMoneyGroup>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "手办消费统计",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "总计消费金额",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = totalMoneyText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (moneyGroups.isEmpty()) {
                    Text(
                        text = "暂无年度或分类消费数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    moneyGroups.forEach { group ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = group.categoryName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val rmbText = if (group.totalRmb >= 10000.0) {
                                "¥" + PriceFormatter.formatFixed(group.totalRmb / 10000.0, 1).removeSuffix(".0") + "万"
                            } else {
                                "¥${group.totalRmb.toInt()}"
                            }
                            Text(
                                text = rmbText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "关闭")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileBottomSheet(
    profile: UserProfile,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (nickname: String, sign: String, intro: String, sex: Int, birthday: String, city: String) -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    var nickname by remember { mutableStateOf(profile.nickname.orEmpty()) }
    var sign by remember { mutableStateOf(profile.sign.orEmpty()) }
    var intro by remember { mutableStateOf(profile.intro.orEmpty()) }
    var sex by remember { mutableIntStateOf(profile.sex ?: 2) }
    var birthday by remember { mutableStateOf(profile.birth.orEmpty()) }
    var city by remember { mutableStateOf(profile.city.orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "编辑个人资料",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("昵称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = sign,
                onValueChange = { sign = it },
                label = { Text("个性签名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = intro,
                onValueChange = { intro = it },
                label = { Text("个人简介") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            Column {
                Text(
                    text = "性别",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "男", 0 to "女", 2 to "保密").forEach { (valCode, text) ->
                        FilterChip(
                            selected = sex == valCode,
                            onClick = { sex = valCode },
                            label = { Text(text) },
                            shape = CircleShape
                        )
                    }
                }
            }

            OutlinedTextField(
                value = birthday,
                onValueChange = { birthday = it },
                label = { Text("生日 (YYYY-MM-DD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text("所在城市") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    onSave(nickname, sign, intro, sex, birthday, city)
                },
                enabled = !isSaving,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    LoadingIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存中...")
                } else {
                    Text("保存资料")
                }
            }
        }
    }
}

private fun formatStatCount(count: Int): String {
    return when {
        count >= 10000 -> PriceFormatter.formatFixed(count / 10000.0, 1).removeSuffix(".0") + "万"
        count > 0 -> count.toString()
        else -> "0"
    }
}
