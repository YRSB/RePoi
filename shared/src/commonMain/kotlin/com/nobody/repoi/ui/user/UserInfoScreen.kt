package com.nobody.repoi.ui.user

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nobody.repoi.ui.common.plainTextClipEntry
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.CategoryMoneyGroup
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.album.components.AlbumFeedCard
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.home.components.ActionCard
import com.nobody.repoi.ui.navigation.UserCollectNavParam
import com.nobody.repoi.ui.theme.ExpressiveElevatedCard
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.util.CdnUrlUtils
import com.nobody.repoi.util.PriceFormatter
import kotlinx.coroutines.launch

/**
 * 用户个人资料与主页展示页面。
 *
 * 遵循单流滚动设计规范（LazyColumn 平铺所有要素）：
 * - 顶部横幅（含返回、编辑/刷新及右下角节点 ID 胶囊）；
 * - 头部资料区（头像、关注/粉丝/获赞三列统计、关注按钮、昵称、普通文本等级、收藏相似度勋章、签名与可折叠介绍）；
 * - 手办 4 项统计栏（想买、预定、已入、总消费金额下拉弹窗）；
 * - 下游内容分区（相册、动态、二手）与平铺列表流；
 * - 底部详细基础信息卡片（可折叠查看全量字段并支持编辑）。
 */
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
    // KMP 无 android Configuration：横屏判定经 BoxWithConstraints 宽高比（原 orientation==LANDSCAPE 语义）
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
            // BoxWithConstraints 宽高比回写横屏态（替代 Configuration.orientation）
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
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                            imageVector = Icons.Filled.Person,
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
                            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "重新加载")
                        }
                    }
                }

                uiState.profile != null -> {
                    val profile = uiState.profile!!
                    val clipboard = LocalClipboard.current

                    if (isDualPane) {
                        // 💻 横屏大屏（Medium / Expanded）：自适应双栏（左栏档案资产 + 右栏内容流）
                        Row(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // 左栏：用户身份与资产档案
                            UserProfileIdentityPane(
                                profile = profile,
                                uiState = uiState,
                                isSelf = uiState.isSelf,
                                onBack = onNavigateBack,
                                onEdit = { viewModel.startEditing() },
                                onRefresh = { viewModel.loadProfile(profile.id) },
                                onBannerClick = {
                                    val raw = CdnUrlUtils.getRawBannerUrl(profile.bnr)
                                    if (raw.isNotBlank()) imageViewer.show(raw)
                                },
                                onAvatarClick = {
                                    val raw = CdnUrlUtils.getRawHeadUrl(profile.header)
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

                            // 分割线
                            VerticalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxHeight()
                            )

                            // 右栏：用户输出内容流（Tab 固定在顶部，内容独立滚动）
                            Column(
                                modifier = Modifier
                                    .weight(0.62f)
                                    .fillMaxHeight()
                                    .padding(top = 8.dp)
                            ) {
                                UserContentTabs(
                                    selectedTab = uiState.selectedTab,
                                    onSelectTab = { viewModel.selectTab(it) }
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 32.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    userContentFeedItems(
                                        uiState = uiState,
                                        columns = 3,
                                        onNavigateToAlbum = onNavigateToAlbum,
                                        onNavigateToHobby = onNavigateToHobby,
                                        onImageClick = { images, index ->
                                            if (images.isNotEmpty()) {
                                                imageViewer.show(images, index)
                                            }
                                        },
                                        onSelectAlbumSubTab = { viewModel.selectAlbumSubTab(it) }
                                    )
                                }
                            }
                        }
                    } else {
                        // 📱 竖屏（手机与平板竖屏）：单列流式平铺
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxHeight()
                                .widthIn(max = 720.dp)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. 顶部大横幅与悬浮操作
                            item(key = "banner") {
                                UserProfileBanner(
                                    profile = profile,
                                    isSelf = uiState.isSelf,
                                    onBack = onNavigateBack,
                                    onEdit = { viewModel.startEditing() },
                                    onRefresh = { viewModel.loadProfile(profile.id) },
                                    onBannerClick = {
                                        val raw = CdnUrlUtils.getRawBannerUrl(profile.bnr)
                                        if (raw.isNotBlank()) imageViewer.show(raw)
                                    }
                                )
                            }

                            // 2. 用户头部基础资料与社交三项统计行
                            item(key = "header_info") {
                                UserProfileHeader(
                                    profile = profile,
                                    uiState = uiState,
                                    onAvatarClick = {
                                        val raw = CdnUrlUtils.getRawHeadUrl(profile.header)
                                        if (raw.isNotBlank()) imageViewer.show(raw)
                                    },
                                    onNavigateToFriendList = { tab ->
                                        onNavigateToFriendList(profile.id, tab)
                                    },
                                    onToggleFollow = { viewModel.toggleFollow() },
                                    onToggleIntro = { viewModel.toggleIntroExpanded() },
                                    onNavigateToChat = onNavigateToChat
                                )
                            }

                            // 3. 手办 4 项统计卡片（想买、预定、已入、总消费）
                            item(key = "order_stats") {
                                UserOrderStatsCard(
                                    profile = profile,
                                    uiState = uiState,
                                    onNavigateToCollect = onNavigateToCollect,
                                    onShowMoneyDialog = { viewModel.setShowMoneyDialog(true) }
                                )
                            }

                            // 4. 基础档案常驻卡片（UID、性别、生日、城市、注册时间）
                            item(key = "basic_info") {
                                BasicProfileSection(
                                    profile = profile,
                                    onCopyUid = { uid ->
                                        coroutineScope.launch {
                                            clipboard.setClipEntry(plainTextClipEntry(uid.toString()))
                                            snackbarHostState.showSnackbar("UID 已复制")
                                        }
                                    }
                                )
                            }

                            // 5. 下游内容分类 Tab 栏（相册、动态、二手）
                            item(key = "content_tabs") {
                                UserContentTabs(
                                    selectedTab = uiState.selectedTab,
                                    onSelectTab = { viewModel.selectTab(it) }
                                )
                            }

                            // 6. 根据选中 Tab 渲染单流列表内容（2 列相册）
                            userContentFeedItems(
                                uiState = uiState,
                                columns = 2,
                                onNavigateToAlbum = onNavigateToAlbum,
                                onNavigateToHobby = onNavigateToHobby,
                                onImageClick = { images, index ->
                                    if (images.isNotEmpty()) {
                                        imageViewer.show(images, index)
                                    }
                                },
                                onSelectAlbumSubTab = { viewModel.selectAlbumSubTab(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    // 年度消费明细弹窗
    if (uiState.showMoneyDialog) {
        YearlyMoneyBreakdownDialog(
            totalMoneyText = uiState.totalMoneyText,
            moneyGroups = uiState.historyMoneyGroups,
            onDismiss = { viewModel.setShowMoneyDialog(false) }
        )
    }

    // 编辑个人资料底部弹窗
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

/**
 * 用户主页相册/动态/二手 Feed 流列表渲染项。
 */
private fun LazyListScope.userContentFeedItems(
    uiState: UserInfoUiState,
    columns: Int,
    onNavigateToAlbum: (AlbumNavParam) -> Unit,
    onNavigateToHobby: (Long) -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    onSelectAlbumSubTab: (Int) -> Unit
) {
    when (uiState.selectedTab) {
        0 -> {
            item(key = "album_sub_chips") {
                UserAlbumSubChips(
                    isSelf = uiState.isSelf,
                    selectedSubTab = uiState.selectedAlbumSubTab,
                    onSelectSubTab = onSelectAlbumSubTab
                )
            }

            if (uiState.albums.isEmpty()) {
                item(key = "empty_albums") {
                    EmptyTabPlaceholder(
                        icon = Icons.Filled.PhotoLibrary,
                        text = if (uiState.isSelf) "你还没有发布相册" else "该用户暂无相册"
                    )
                }
            } else {
                item(key = "user_albums_waterfall") {
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
                                                        albumId = album.id,
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

        1 -> {
            if (uiState.actions.isEmpty()) {
                item(key = "empty_actions") {
                    EmptyTabPlaceholder(
                        icon = Icons.Filled.Timeline,
                        text = if (uiState.isSelf) "你还没有发布动态" else "该用户暂无动态"
                    )
                }
            } else {
                items(
                    items = uiState.actions,
                    key = { it.id }
                ) { action ->
                    ActionCard(
                        actionItem = action,
                        fallbackAvatarUrl = CdnUrlUtils.getHeadUrl(uiState.profile?.header),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        onItemClick = { item ->
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
                            }
                        },
                        onImageClick = onImageClick
                    )
                }
            }
        }

        2 -> {
            item(key = "empty_resale") {
                EmptyTabPlaceholder(
                    icon = Icons.Filled.Sell,
                    text = if (uiState.isSelf) "你暂无在售二手条目" else "该用户暂无在售二手条目"
                )
            }
        }
    }
}

/**
 * 用户身份与资产档案侧边栏面板（用于横屏大屏双栏架构左侧主栏）。
 */
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
        // 1. 横幅与悬浮操作胶囊
        UserProfileBanner(
            profile = profile,
            isSelf = isSelf,
            onBack = onBack,
            onEdit = onEdit,
            onRefresh = onRefresh,
            onBannerClick = onBannerClick
        )

        // 2. 头像与社交三列统计
        UserProfileHeader(
            profile = profile,
            uiState = uiState,
            onAvatarClick = onAvatarClick,
            onNavigateToFriendList = onNavigateToFriendList,
            onToggleFollow = onToggleFollow,
            onToggleIntro = onToggleIntro,
            onNavigateToChat = onNavigateToChat
        )

        // 3. 手办四大消费统计卡片
        UserOrderStatsCard(
            profile = profile,
            uiState = uiState,
            onNavigateToCollect = onNavigateToCollect,
            onShowMoneyDialog = onShowMoneyDialog
        )

        // 4. 常驻基础信息卡片
        BasicProfileSection(
            profile = profile,
            onCopyUid = onCopyUid
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 顶部沉浸式横幅组件（含返回/编辑胶囊与右下角节点 ID）。
 */
@Composable
private fun UserProfileBanner(
    profile: UserProfile,
    isSelf: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onBannerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bannerUrl = CdnUrlUtils.getBannerUrl(profile.bnr)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        // 背景横幅图
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

        // 顶部轻量渐变暗色遮罩保障按钮全天候清晰度
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

        // 顶部操作按钮（左返回，右编辑/刷新）
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
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                                imageVector = Icons.Filled.Edit,
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
                            imageVector = Icons.Filled.Refresh,
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

/**
 * 个人资料核心头部：圆形头像、关注/粉丝/获赞三列统计、关注按钮、昵称、普通文本等级、相似度勋章、签名与简介。
 */
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
    val avatarUrl = CdnUrlUtils.getHeadUrl(profile.header)
    val caresCount = uiState.fellowCount?.friendCount ?: profile.cares
    val fansCount = uiState.fellowCount?.fanCount ?: profile.fans
    val praiseCount = uiState.fellowCount?.praiseCount ?: profile.praise

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 第一行：头像（左）+ 社交三列统计（中）+ 他人关注胶囊（右）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 圆形大头像（略微上移形成悬挂层次）
            Surface(
                shape = CircleShape,
                shadowElevation = ExpressiveTokens.ElevationLevel2,
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
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 社交三项统计行（关注 | 粉丝 | 获赞）
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

            // 若查看他人主页，展示关注胶囊操作按钮与私信按钮
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
                        shape = ExpressiveTokens.PillShape,
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

        // 第二行：用户昵称 + 普通文本等级（需求 4）+ 收藏相似度勋章
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

                // 需求 4：用户等级直接显示为普通文本（如 Lv.3），不使用胶囊
                Text(
                    text = "Lv.${profile.level}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 他人主页显示收藏相似度勋章（如 0% 收藏相似度）
            if (!uiState.isSelf) {
                UserSimilarityBadge(similarity = uiState.similarity)
            }
        }

        // 官方认证徽章
        if (profile.isVerified) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Verified,
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

        // 个性签名
        val signText = profile.sign?.ifBlank { null } ?: "还没有信仰_(:3」∠)_"
        Text(
            text = "签名：$signText",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 个人介绍（支持展开/收起）
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

/**
 * 社交统计单列（垂直居中排列数字与标签）。
 */
@Composable
private fun UserSocialStatColumn(
    count: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(ExpressiveTokens.ButtonShape)
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

/**
 * 他人主页关注胶囊按钮。
 */
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
            shape = ExpressiveTokens.PillShape,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            modifier = modifier.height(32.dp)
        ) {
            if (isMutual) {
                Icon(
                    imageVector = Icons.Default.SyncAlt,
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
            shape = ExpressiveTokens.PillShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = modifier.height(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
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

/**
 * 收藏相似度勋章组件（官方原版勋章式样）。
 */
@Composable
private fun UserSimilarityBadge(
    similarity: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = ExpressiveTokens.PillShape,
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

/**
 * 手办 4 项消费与收藏统计栏（想买、预定、已入、总消费）。
 */
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

    ExpressiveElevatedCard(
        shape = ExpressiveTokens.CardShape,
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
            // 想买
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

            // 预定
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

            // 已入
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

            // 总消费（带下拉三角，点击展示年度细分弹窗）
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(ExpressiveTokens.ButtonShape)
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
                        imageVector = Icons.Default.ArrowDropDown,
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
            .clip(ExpressiveTokens.ButtonShape)
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

/**
 * 个人主页下游内容分区 Tab 栏（相册、动态、二手）。
 */
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

/**
 * 相册子分类筛选 Chips。
 */
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
                shape = ExpressiveTokens.PillShape
            )
        }
    }
}

/**
 * 空状态提示占位。
 */
@Composable
private fun EmptyTabPlaceholder(
    icon: ImageVector,
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
            imageVector = icon,
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

/**
 * 基础信息常驻展示卡片（去重并保留 UID、性别、生日、城市与注册时间等核心档案）。
 */
@Composable
private fun BasicProfileSection(
    profile: UserProfile,
    onCopyUid: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    ExpressiveElevatedCard(
        shape = ExpressiveTokens.CardShape,
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
            // 卡片标题行（常驻展示）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
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

            // 1. UID（带快捷复制）
            ProfileInfoRow(
                icon = Icons.Filled.Fingerprint,
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
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "复制UID",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )

            // 2. 性别
            ProfileInfoRow(
                icon = Icons.Filled.Wc,
                label = "性别",
                value = profile.genderText
            )

            // 3. 生日
            ProfileInfoRow(
                icon = Icons.Filled.Cake,
                label = "生日",
                value = profile.birth?.ifBlank { null } ?: "未设置"
            )

            // 4. 城市
            ProfileInfoRow(
                icon = Icons.Filled.LocationOn,
                label = "城市",
                value = profile.city?.ifBlank { null } ?: "未设置"
            )

            // 5. 注册时间
            ProfileInfoRow(
                icon = Icons.Filled.CalendarMonth,
                label = "注册时间",
                value = profile.displayRegisterTime
            )

            // 6. 官方认证（若有）
            if (profile.isVerified) {
                ProfileInfoRow(
                    icon = Icons.Filled.Verified,
                    label = "认证信息",
                    value = profile.verify.orEmpty()
                )
            }
        }
    }
}

/**
 * 个人资料单项只读展示行。
 */
@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
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
            imageVector = icon,
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

/**
 * 年度消费细分弹窗。
 */
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
                    shape = ExpressiveTokens.CardShape,
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

/**
 * 个人资料综合编辑底栏弹窗。
 */
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
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
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
                            shape = ExpressiveTokens.PillShape
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
                shape = ExpressiveTokens.ButtonShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
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
