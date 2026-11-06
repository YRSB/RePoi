package com.nobody.repoi.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.repoi.data.local.ThemeManager
import com.nobody.repoi.data.local.ThemeMode
import com.nobody.repoi.data.local.ThemeSeed
import com.nobody.repoi.data.local.orAvailable
import com.nobody.repoi.data.local.toColors
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.ktx.from
import com.materialkolor.ktx.toneColor
import com.materialkolor.material3.dynamicColorScheme
import com.materialkolor.palettes.TonalPalette
import com.nobody.repoi.platform.AppInfo
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.platform.PoiShelfBackHandler
import com.nobody.repoi.platform.isDynamicSeedAvailable
import com.nobody.repoi.platform.shareLogFile
import com.nobody.repoi.platform.supportsDynamicColor
import com.nobody.repoi.ui.profile.ProfileViewModel
import com.nobody.repoi.ui.settings.shield.SettingsShieldViewModel
import com.nobody.repoi.ui.settings.shield.SettingsShieldUiState
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.ui.theme.FormFactorPreviews
import com.nobody.repoi.ui.theme.PoiShelfTheme
import com.nobody.repoi.util.AppLogger
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private enum class SettingsGroup(val title: String, val icon: ImageVector) {
    Appearance("外观与主题", Icons.Filled.Palette),
    Content("浏览与内容", Icons.Filled.Shield),
    Account("账号与安全", Icons.Filled.Person)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "未知"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}





@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenUserInfo: (Long) -> Unit,
    onOpenShieldList: () -> Unit = {},
    onOpenBlocklist: () -> Unit = {},
    themeManager: ThemeManager = koinInject(),
    profileViewModel: ProfileViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    cacheViewModel: SettingsCacheViewModel = koinViewModel(),
    privacyViewModel: SettingsPrivacyViewModel = koinViewModel(),
    accountViewModel: SettingsAccountViewModel = koinViewModel(),
    shieldViewModel: SettingsShieldViewModel = koinViewModel()
) {
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by themeManager.themeModeFlow.collectAsStateWithLifecycle(initialValue = themeManager.getInitialThemeMode())
    val dynamicColor by themeManager.dynamicColorFlow.collectAsStateWithLifecycle(initialValue = themeManager.getInitialDynamicColor())
    val themeSeed by themeManager.themeSeedFlow.collectAsStateWithLifecycle(initialValue = themeManager.getInitialThemeSeed())
    val amoled by themeManager.amoledFlow.collectAsStateWithLifecycle(initialValue = themeManager.getInitialAmoled())
    val expressiveColor by themeManager.expressiveColorFlow.collectAsStateWithLifecycle(initialValue = themeManager.getInitialExpressiveColor())
    val isExHobbyEnabled by settingsViewModel.isExHobbyEnabled.collectAsStateWithLifecycle()
    val cacheState by cacheViewModel.uiState.collectAsStateWithLifecycle()
    val privacyState by privacyViewModel.uiState.collectAsStateWithLifecycle()
    val accountState by accountViewModel.uiState.collectAsStateWithLifecycle()
    val shieldState by shieldViewModel.uiState.collectAsStateWithLifecycle()

    val appMessages = LocalAppMessages.current
    val scope = rememberCoroutineScope()

    val onThemeModeChange: (ThemeMode) -> Unit = { mode -> scope.launch { themeManager.setThemeMode(mode) } }
    val onDynamicColorChange: (Boolean) -> Unit = { enabled -> scope.launch { themeManager.setDynamicColor(enabled) } }
    val onThemeSeedChange: (ThemeSeed) -> Unit = { seed -> scope.launch { themeManager.setThemeSeed(seed) } }
    val onAmoledChange: (Boolean) -> Unit = { enabled -> scope.launch { themeManager.setAmoled(enabled) } }
    val onExpressiveColorChange: (Boolean) -> Unit = { enabled -> scope.launch { themeManager.setExpressiveColor(enabled) } }

    var selectedGroup by rememberSaveable { mutableStateOf(SettingsGroup.Appearance.name) }
    var narrowGroup by rememberSaveable { mutableStateOf("") }
    var showLogoutConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showClearCacheConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }

    val wide = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(narrowGroup, selectedGroup) {
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
    }

    LaunchedEffect(cacheState.actionMessage) {
        cacheState.actionMessage?.let {
            appMessages.show(it)
            cacheViewModel.consumeMessage()
        }
    }

    LaunchedEffect(shieldState.errorMessage) {
        shieldState.errorMessage?.let {
            appMessages.show(it)
            shieldViewModel.clearErrorMessage()
        }
    }

    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = {
                Text(
                    text = "退出登录",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "确定要退出当前 Hpoi 账号吗？退出后将无法同步预定与已入手办手账。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirmDialog = false
                        profileViewModel.logout()
                        onBack()
                    }
                ) {
                    Text("退出", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showClearCacheConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirmDialog = false },
            title = {
                Text(
                    text = "清理图片缓存",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "确定要清理全部图片缓存吗？当前已占用 ${formatBytes(cacheState.sizeBytes)}，清理后图片需要重新加载。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheConfirmDialog = false
                        cacheViewModel.clearCache()
                    }
                ) {
                    Text("清理", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text(
                    text = "关于 RePoi",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "RePoi 跨平台客户端",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "版本：${AppInfo.versionName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "基于 Kotlin Multiplatform 开发的 Hpoi 手办维基跨平台客户端，支持 Android 与 Windows Desktop 端。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("确定", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    val onExportLog: () -> Unit = {
        scope.launch {
            try {
                val (fileName, bytes) = AppLogger.exportLogBytes()
                shareLogFile(fileName, bytes.decodeToString())
            } catch (e: Exception) {
                AppLogger.w("设置", "日志导出失败：${e.message}")
                appMessages.show("日志导出失败请重试")
            }
        }
    }

    PoiShelfBackHandler(enabled = !wide && narrowGroup.isNotEmpty()) {
        narrowGroup = ""
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val currentTitle = if (!wide && narrowGroup.isNotEmpty()) {
                runCatching { SettingsGroup.valueOf(narrowGroup).title }.getOrDefault("系统设置")
            } else {
                "系统设置"
            }
            LargeTopAppBar(
                title = {
                    Text(
                        text = currentTitle,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!wide && narrowGroup.isNotEmpty()) narrowGroup = ""
                        else onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                SettingsGroupList(
                    selectedGroup = runCatching { SettingsGroup.valueOf(selectedGroup) }.getOrDefault(SettingsGroup.Appearance),
                    isLoggedIn = profileState.isLoggedIn,
                    cacheState = cacheState,
                    onSelectGroup = {
                        selectedGroup = it.name
                        narrowGroup = it.name
                    },
                    onRequestClearCache = { showClearCacheConfirmDialog = true },
                    onExportLog = onExportLog,
                    onRequestAbout = { showAboutDialog = true },
                    onRequestLogout = { showLogoutConfirmDialog = true },
                    modifier = Modifier.fillMaxHeight().weight(0.38f)
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Box(modifier = Modifier.fillMaxHeight().weight(0.62f)) {
                    SettingsDetailPane(
                        group = runCatching { SettingsGroup.valueOf(selectedGroup) }.getOrDefault(SettingsGroup.Appearance),
                        isLoggedIn = profileState.isLoggedIn,
                        themeMode = themeMode,
                        dynamicColor = dynamicColor,
                        themeSeed = themeSeed,
                        amoled = amoled,
                        expressiveColor = expressiveColor,
                        isExHobbyEnabled = isExHobbyEnabled,
                        shieldState = shieldState,
                        privacyState = privacyState,
                        accountState = accountState,
                        onThemeModeChange = onThemeModeChange,
                        onDynamicColorChange = onDynamicColorChange,
                        onThemeSeedChange = onThemeSeedChange,
                        onAmoledChange = onAmoledChange,
                        onExpressiveColorChange = onExpressiveColorChange,
                        onExHobbyEnabledChange = { settingsViewModel.setExHobbyEnabled(it) },
                        onNsfwChange = { shieldViewModel.setNsfwVisible(it) },
                        onPrivacyOptionChange = { key, value -> privacyViewModel.setOption(key, value) },
                        onOpenShieldList = onOpenShieldList,
                        onOpenBlocklist = onOpenBlocklist,
                        onOpenProfile = {
                            val id = profileState.profile?.id ?: 0L
                            if (id > 0L) onOpenUserInfo(id) else scope.launch { appMessages.show("请先登录") }
                        },
                        onRefreshAccount = { accountViewModel.refresh() },
                        onRequestLogout = { showLogoutConfirmDialog = true }
                    )
                }
            }
        } else {
            val narrowSelected = narrowGroup.takeIf { it.isNotEmpty() }?.let { runCatching { SettingsGroup.valueOf(it) }.getOrNull() }
            if (narrowSelected != null) {
                SettingsDetailPane(
                    group = narrowSelected,
                    isLoggedIn = profileState.isLoggedIn,
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    themeSeed = themeSeed,
                    amoled = amoled,
                    expressiveColor = expressiveColor,
                    isExHobbyEnabled = isExHobbyEnabled,
                    shieldState = shieldState,
                    privacyState = privacyState,
                    accountState = accountState,
                    onThemeModeChange = onThemeModeChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onThemeSeedChange = onThemeSeedChange,
                    onAmoledChange = onAmoledChange,
                    onExpressiveColorChange = onExpressiveColorChange,
                    onExHobbyEnabledChange = { settingsViewModel.setExHobbyEnabled(it) },
                    onNsfwChange = { shieldViewModel.setNsfwVisible(it) },
                    onPrivacyOptionChange = { key, value -> privacyViewModel.setOption(key, value) },
                    onOpenShieldList = onOpenShieldList,
                    onOpenBlocklist = onOpenBlocklist,
                    onOpenProfile = {
                        val id = profileState.profile?.id ?: 0L
                        if (id > 0L) onOpenUserInfo(id) else scope.launch { appMessages.show("请先登录") }
                    },
                    onRefreshAccount = { accountViewModel.refresh() },
                    onRequestLogout = { showLogoutConfirmDialog = true },
                    modifier = Modifier.padding(innerPadding)
                )
            } else {
                SettingsGroupList(
                    selectedGroup = null,
                    isLoggedIn = profileState.isLoggedIn,
                    cacheState = cacheState,
                    onSelectGroup = {
                        selectedGroup = it.name
                        narrowGroup = it.name
                    },
                    onRequestClearCache = { showClearCacheConfirmDialog = true },
                    onExportLog = onExportLog,
                    onRequestAbout = { showAboutDialog = true },
                    onRequestLogout = { showLogoutConfirmDialog = true },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun SettingsGroupList(
    selectedGroup: SettingsGroup?,
    isLoggedIn: Boolean,
    cacheState: SettingsCacheUiState,
    onSelectGroup: (SettingsGroup) -> Unit,
    onRequestClearCache: () -> Unit,
    onExportLog: () -> Unit,
    onRequestAbout: () -> Unit,
    onRequestLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = SettingsGroup.entries.toList()
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header-preferences", contentType = "header") {
            Text(
                text = "偏好设置",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
        item(key = "segment-preferences", contentType = "segment") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                groups.forEachIndexed { index, group ->
                    val selected = selectedGroup == group
                    SegmentedListItem(
                        selected = selected,
                        onClick = { onSelectGroup(group) },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = groups.size),
                        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        leadingContent = {
                            Icon(
                                imageVector = group.icon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        },
                        content = {
                            Text(
                                text = group.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                }
            }
        }

        item(key = "header-general", contentType = "header") {
            Text(
                text = "系统与通用",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
        item(key = "segment-general", contentType = "segment") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                SettingsMenuItem(
                    icon = Icons.Filled.CleaningServices,
                    title = "清理图片缓存",
                    subtitle = if (cacheState.isLoading) "正在统计…" else if (cacheState.isClearing) "正在清理…" else "已占用 ${formatBytes(cacheState.sizeBytes)}",
                    index = 0,
                    count = 3,
                    onClick = onRequestClearCache
                )
                SettingsMenuItem(
                    icon = Icons.Filled.Share,
                    title = "导出应用日志",
                    subtitle = "打包近期运行日志并分享便于排查问题",
                    index = 1,
                    count = 3,
                    onClick = onExportLog
                )
                SettingsMenuItem(
                    icon = Icons.Filled.Info,
                    title = "关于 RePoi",
                    subtitle = "版本 ${AppInfo.versionName}",
                    index = 2,
                    count = 3,
                    onClick = onRequestAbout
                )
            }
        }

        if (isLoggedIn) {
            item(key = "logout", contentType = "logout") {
                ElevatedCard(
                    onClick = onRequestLogout,
                    shape = ExpressiveTokens.CardShape,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "退出当前账号",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDetailPane(
    group: SettingsGroup,
    isLoggedIn: Boolean,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    themeSeed: ThemeSeed,
    amoled: Boolean,
    isExHobbyEnabled: Boolean,
    shieldState: SettingsShieldUiState,
    privacyState: SettingsPrivacyUiState,
    accountState: SettingsAccountSecurityUiState,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onThemeSeedChange: (ThemeSeed) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    expressiveColor: Boolean,
    onExpressiveColorChange: (Boolean) -> Unit,
    onExHobbyEnabledChange: (Boolean) -> Unit,
    onNsfwChange: (Boolean) -> Unit,
    onPrivacyOptionChange: (String, Int) -> Unit,
    onOpenShieldList: () -> Unit,
    onOpenBlocklist: () -> Unit,
    onOpenProfile: () -> Unit,
    onRefreshAccount: () -> Unit,
    onRequestLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (group) {
            SettingsGroup.Appearance -> SettingsAppearanceContent(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                themeSeed = themeSeed,
                amoled = amoled,
                onThemeModeChange = onThemeModeChange,
                onDynamicColorChange = onDynamicColorChange,
                onThemeSeedChange = onThemeSeedChange,
                onAmoledChange = onAmoledChange,
                expressiveColor = expressiveColor,
                onExpressiveColorChange = onExpressiveColorChange,
                modifier = Modifier.fillMaxSize()
            )
            SettingsGroup.Content -> SettingsContentContent(
                shieldState = shieldState,
                isExHobbyEnabled = isExHobbyEnabled,
                privacyState = privacyState,
                onNsfwChange = onNsfwChange,
                onExHobbyEnabledChange = onExHobbyEnabledChange,
                onOpenShieldList = onOpenShieldList,
                onOpenBlocklist = onOpenBlocklist,
                onPrivacyOptionChange = onPrivacyOptionChange,
                modifier = Modifier.fillMaxSize()
            )
            SettingsGroup.Account -> SettingsAccountContent(
                isLoggedIn = isLoggedIn,
                state = accountState,
                onOpenProfile = onOpenProfile,
                onRefreshAccount = onRefreshAccount,
                onRequestLogout = onRequestLogout,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsAppearanceContent(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    themeSeed: ThemeSeed,
    amoled: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onThemeSeedChange: (ThemeSeed) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    expressiveColor: Boolean,
    onExpressiveColorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val seeds = listOf(
        ThemeSeed.Dynamic,
        ThemeSeed.Lotus,
        ThemeSeed.Coral,
        ThemeSeed.Amber,
        ThemeSeed.Pink,
        ThemeSeed.Mint,
        ThemeSeed.Grape,
        ThemeSeed.Tricolor
    )
    val dynamicColors = MaterialTheme.colorScheme
    val palettes: Map<ThemeSeed, List<TonalPalette>> = remember(expressiveColor, dynamicColors) {
        seeds.associateWith { seed ->
            if (seed == ThemeSeed.Dynamic) {
                listOf(
                    TonalPalette.from(dynamicColors.primary),
                    TonalPalette.from(dynamicColors.secondary),
                    TonalPalette.from(dynamicColors.tertiary)
                )
            } else {
                val (primary, secondary, tertiary) = seed.toColors()
                val scheme = dynamicColorScheme(
                    seedColor = primary!!,
                    isDark = false,
                    isAmoled = false,
                    secondary = secondary,
                    tertiary = tertiary,
                    style = if (expressiveColor) PaletteStyle.Expressive else PaletteStyle.TonalSpot,
                    specVersion = if (expressiveColor) ColorSpec.SpecVersion.SPEC_2025 else ColorSpec.SpecVersion.SPEC_2021
                )
                listOf(
                    TonalPalette.from(scheme.primary),
                    TonalPalette.from(scheme.secondary),
                    TonalPalette.from(scheme.tertiary)
                )
            }
        }
    }

    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "header-theme", contentType = "header") {
            Text(
                text = "主题",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        item(key = "mode-segmented", contentType = "mode") {
            val modes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        label = {
                            Text(
                                text = when (mode) {
                                    ThemeMode.LIGHT -> "浅色"
                                    ThemeMode.DARK -> "深色"
                                    ThemeMode.SYSTEM -> "跟随系统"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (themeMode == mode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }

        item(key = "seeds-flow", contentType = "seeds") {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(seeds, key = { it.name }, contentType = { "seed" }) { seed ->
                    val isSelected = if (dynamicColor) {
                        seed == ThemeSeed.Dynamic
                    } else {
                        seed == themeSeed.orAvailable()
                    }
                    val accents = palettes[seed] ?: listOf(
                        TonalPalette.from(dynamicColors.primary),
                        TonalPalette.from(dynamicColors.secondary),
                        TonalPalette.from(dynamicColors.tertiary)
                    )
                    PodAuraThemeCard(
                        seed = seed,
                        accents = accents,
                        isSelected = isSelected,
                        onClick = {
                            if (seed == ThemeSeed.Dynamic) {
                                onDynamicColorChange(true)
                                onThemeSeedChange(ThemeSeed.Dynamic)
                            } else {
                                onDynamicColorChange(false)
                                onThemeSeedChange(seed)
                            }
                        }
                    )
                }
            }
        }

        item(key = "switches", contentType = "segment") {
            val count = if (supportsDynamicColor) 3 else 2
            var itemIndex = 0
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                if (supportsDynamicColor) {
                    SettingsSwitchItem(
                        icon = Icons.Filled.Colorize,
                        title = "动态主题",
                        subtitle = "将壁纸颜色应用于主题",
                        checked = dynamicColor,
                        index = itemIndex++,
                        count = count,
                        onCheckedChange = { enabled ->
                            onDynamicColorChange(enabled)
                            if (enabled) {
                                onThemeSeedChange(ThemeSeed.Dynamic)
                            } else if (themeSeed == ThemeSeed.Dynamic) {
                                onThemeSeedChange(ThemeSeed.Lotus)
                            }
                        }
                    )
                }
                SettingsSwitchItem(
                    icon = Icons.Filled.Palette,
                    title = "富有表现力的颜色",
                    subtitle = "增强色彩对比与层次表现力",
                    checked = expressiveColor,
                    index = itemIndex++,
                    count = count,
                    onCheckedChange = onExpressiveColorChange
                )
                SettingsSwitchItem(
                    icon = Icons.Filled.NightsStay,
                    title = "Amoled 深色",
                    subtitle = "深色模式下使用纯黑背景省电护眼",
                    checked = amoled,
                    index = itemIndex++,
                    count = count,
                    onCheckedChange = onAmoledChange
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PodAuraThemeCard(
    seed: ThemeSeed,
    accents: List<TonalPalette>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(text = seed.title)
            }
        },
        state = rememberTooltipState()
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick)
        ) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.inverseOnSurface)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(50.dp),
                    shape = CircleShape,
                    color = accents[0].toneColor(36)
                ) {
                    Box {
                        Surface(
                            modifier = Modifier
                                .size(50.dp)
                                .offset((-25).dp, 25.dp),
                            color = accents[1].toneColor(80)
                        ) {}
                        Surface(
                            modifier = Modifier
                                .size(50.dp)
                                .offset(25.dp, 25.dp),
                            color = accents[2].toneColor(65)
                        ) {}
                    }
                }
            }

            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 2.dp,
                            color = accents[0].toneColor(50),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(15.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsContentContent(
    shieldState: SettingsShieldUiState,
    isExHobbyEnabled: Boolean,
    privacyState: SettingsPrivacyUiState,
    onNsfwChange: (Boolean) -> Unit,
    onExHobbyEnabledChange: (Boolean) -> Unit,
    onOpenShieldList: () -> Unit,
    onOpenBlocklist: () -> Unit,
    onPrivacyOptionChange: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val visibilityOptions = listOf("所有人可见", "互相关注可见", "仅自己可见")
    val strangerOptions = listOf("可随便私聊我", "只能私聊一次", "不能私聊我")
    val editing = editingKey

    if (editing != null) {
        val isStranger = editing == PrivacySettingKeys.STRANGER_MESSAGE
        val options = if (isStranger) strangerOptions else visibilityOptions
        val current = privacyState.values[editing] ?: 0
        Dialog(onDismissRequest = { editingKey = null }) {
            Surface(
                shape = ExpressiveTokens.CardShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = privacyTitle(editing),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { editingKey = null }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭")
                        }
                    }
                    options.forEachIndexed { index, label ->
                        val selected = index == current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ExpressiveTokens.MediumCardShape)
                                .clickable {
                                    editingKey = null
                                    onPrivacyOptionChange(editing, index)
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    editingKey = null
                                    onPrivacyOptionChange(editing, index)
                                }
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header-content", contentType = "header") {
            Text(
                text = "内容与扩展",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        item(key = "segment-content", contentType = "segment") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                SettingsSwitchItem(
                    icon = Icons.Filled.Visibility,
                    title = "R18 内容",
                    subtitle = if (shieldState.isLoading) "正在读取…" else if (shieldState.nsfwVisible) "显示" else "不显示",
                    checked = shieldState.nsfwVisible,
                    index = 0,
                    count = 2,
                    onCheckedChange = { onNsfwChange(!shieldState.nsfwVisible) }
                )
                SettingsSwitchItem(
                    icon = Icons.Filled.PhotoLibrary,
                    title = "ExHobby 扩展图库",
                    subtitle = "在手办与相册详情页展示第三方扩展图集（含 R18 扩展内容）",
                    checked = isExHobbyEnabled,
                    index = 1,
                    count = 2,
                    onCheckedChange = onExHobbyEnabledChange
                )
            }
        }

        item(key = "header-shield", contentType = "header") {
            Text(
                text = "名单管理",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        item(key = "segment-shield", contentType = "segment") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                SettingsMenuItem(
                    icon = Icons.Filled.VisibilityOff,
                    title = "屏蔽列表",
                    subtitle = "用户 / 厂商 / 条目",
                    index = 0,
                    count = 2,
                    onClick = onOpenShieldList
                )
                SettingsMenuItem(
                    icon = Icons.Filled.PersonOff,
                    title = "黑名单",
                    subtitle = "被屏蔽的用户将无法向您发送私信",
                    index = 1,
                    count = 2,
                    onClick = onOpenBlocklist
                )
            }
        }

        item(key = "header-collect", contentType = "header") {
            Text(
                text = "收藏信息可见性",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        item(key = "segment-collect", contentType = "segment") {
            if (privacyState.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                ) {
                    SettingsMenuItem(
                        icon = Icons.Filled.Category,
                        title = "周边条目(手办、动漫、模型、布偶、娃娃)",
                        subtitle = visibilityOptions.getOrElse(privacyState.values[PrivacySettingKeys.COLLECT_HOBBY] ?: 0) { visibilityOptions[0] },
                        index = 0,
                        count = 4,
                        onClick = { editingKey = PrivacySettingKeys.COLLECT_HOBBY }
                    )
                    SettingsMenuItem(
                        icon = Icons.Filled.PhotoLibrary,
                        title = "收藏内容(相册、图片、文章)",
                        subtitle = visibilityOptions.getOrElse(privacyState.values[PrivacySettingKeys.COLLECT_ALBUM] ?: 0) { visibilityOptions[0] },
                        index = 1,
                        count = 4,
                        onClick = { editingKey = PrivacySettingKeys.COLLECT_ALBUM }
                    )
                    SettingsMenuItem(
                        icon = Icons.Filled.BarChart,
                        title = "收藏统计(历史消费、入手数量、待补款等)",
                        subtitle = visibilityOptions.getOrElse(privacyState.values[PrivacySettingKeys.COLLECT_STATISTICS] ?: 0) { visibilityOptions[0] },
                        index = 2,
                        count = 4,
                        onClick = { editingKey = PrivacySettingKeys.COLLECT_STATISTICS }
                    )
                    SettingsMenuItem(
                        icon = Icons.Filled.Domain,
                        title = "其他内容(系列、厂商、角色、人物、作品)",
                        subtitle = visibilityOptions.getOrElse(privacyState.values[PrivacySettingKeys.COLLECT_OTHER] ?: 0) { visibilityOptions[0] },
                        index = 3,
                        count = 4,
                        onClick = { editingKey = PrivacySettingKeys.COLLECT_OTHER }
                    )
                }
            }
        }

        item(key = "header-other", contentType = "header") {
            Text(
                text = "动态与社交可见性",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        item(key = "segment-other", contentType = "segment") {
            if (!privacyState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                ) {
                    SettingsMenuItem(
                        icon = Icons.Filled.DynamicFeed,
                        title = "个人主页动态",
                        subtitle = visibilityOptions.getOrElse(privacyState.values[PrivacySettingKeys.OWN_ACTION] ?: 0) { visibilityOptions[0] },
                        index = 0,
                        count = 3,
                        onClick = { editingKey = PrivacySettingKeys.OWN_ACTION }
                    )
                    SettingsMenuItem(
                        icon = Icons.Filled.People,
                        title = "关注、粉丝",
                        subtitle = visibilityOptions.getOrElse(privacyState.values[PrivacySettingKeys.PRIVACY_FRIEND] ?: 0) { visibilityOptions[0] },
                        index = 1,
                        count = 3,
                        onClick = { editingKey = PrivacySettingKeys.PRIVACY_FRIEND }
                    )
                    SettingsMenuItem(
                        icon = Icons.Filled.Forum,
                        title = "讨论板个人主页",
                        subtitle = visibilityOptions.getOrElse(privacyState.values[PrivacySettingKeys.PRIVACY_POSTS] ?: 0) { visibilityOptions[0] },
                        index = 2,
                        count = 3,
                        onClick = { editingKey = PrivacySettingKeys.PRIVACY_POSTS }
                    )
                }
            }
        }

        item(key = "header-message", contentType = "header") {
            Text(
                text = "私信设置",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        item(key = PrivacySettingKeys.STRANGER_MESSAGE, contentType = "row") {
            if (!privacyState.isLoading) {
                SettingsMenuItem(
                    icon = Icons.Filled.MarkEmailRead,
                    title = "陌生人私信设置",
                    subtitle = strangerOptions.getOrElse(privacyState.values[PrivacySettingKeys.STRANGER_MESSAGE] ?: 0) { strangerOptions[0] },
                    index = 0,
                    count = 1,
                    onClick = { editingKey = PrivacySettingKeys.STRANGER_MESSAGE }
                )
            }
        }
    }
}

private fun privacyTitle(key: String): String = when (key) {
    PrivacySettingKeys.COLLECT_HOBBY -> "周边条目(手办、动漫、模型、布偶、娃娃)"
    PrivacySettingKeys.COLLECT_ALBUM -> "收藏内容(相册、图片、文章)"
    PrivacySettingKeys.COLLECT_STATISTICS -> "收藏统计(历史消费、入手数量、待补款等)"
    PrivacySettingKeys.COLLECT_OTHER -> "其他内容(系列、厂商、角色、人物、作品)"
    PrivacySettingKeys.OWN_ACTION -> "个人主页动态"
    PrivacySettingKeys.PRIVACY_FRIEND -> "关注、粉丝"
    PrivacySettingKeys.PRIVACY_POSTS -> "讨论板个人主页"
    PrivacySettingKeys.STRANGER_MESSAGE -> "陌生人私信设置"
    else -> "隐私设置"
}

@Composable
private fun SettingsAccountContent(
    isLoggedIn: Boolean,
    state: SettingsAccountSecurityUiState,
    onOpenProfile: () -> Unit,
    onRefreshAccount: () -> Unit,
    onRequestLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header-profile", contentType = "header") {
            Text(
                text = "基本信息",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        item(key = "profile", contentType = "row") {
            SettingsMenuItem(
                icon = Icons.Filled.Person,
                title = "个人信息",
                subtitle = "头像昵称与个人主页",
                index = 0,
                count = 1,
                onClick = onOpenProfile
            )
        }

        item(key = "header-security", contentType = "header") {
            Text(
                text = "账号安全与绑定",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        if (state.isLoading) {
            item(key = "loading", contentType = "status") {
                ElevatedCard(
                    shape = ExpressiveTokens.CardShape,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        } else if (state.loadFailed) {
            item(key = "failed", contentType = "status") {
                ElevatedCard(
                    onClick = onRefreshAccount,
                    shape = ExpressiveTokens.CardShape,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "账号状态加载失败，点击重试",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        } else {
            val bindings = state.bindings()
            item(key = "bindings", contentType = "segment") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                ) {
                    bindings.forEachIndexed { index, binding ->
                        SettingsMenuItem(
                            icon = Icons.Filled.AccountCircle,
                            title = binding.title,
                            subtitle = binding.statusText,
                            index = index,
                            count = bindings.size
                        )
                    }
                }
            }
        }

        if (isLoggedIn) {
            item(key = "logout", contentType = "logout") {
                ElevatedCard(
                    onClick = onRequestLogout,
                    shape = ExpressiveTokens.CardShape,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "退出当前账号",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    index: Int,
    count: Int,
    onClick: (() -> Unit)? = null
) {
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val label: @Composable () -> Unit = {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    val supporting: @Composable () -> Unit = {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    val leading: @Composable () -> Unit = {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }
    val trailing: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.outline
        )
    }
    val click = onClick
    if (click != null) {
        SegmentedListItem(
            onClick = click,
            shapes = shapes,
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            content = label
        )
    } else {
        SegmentedListItem(
            onClick = {},
            shapes = shapes,
            enabled = false,
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            leadingContent = leading,
            supportingContent = supporting,
            content = label
        )
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    index: Int,
    count: Int,
    onCheckedChange: (Boolean) -> Unit
) {
    SegmentedListItem(
        onClick = { onCheckedChange(!checked) },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                thumbContent = {
                    if (checked) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    )
}

@FormFactorPreviews
@Composable
private fun SettingsContentPreview() {
    PoiShelfTheme {
        SettingsGroupList(
            selectedGroup = SettingsGroup.Appearance,
            isLoggedIn = true,
            cacheState = SettingsCacheUiState(sizeBytes = 12_582_912L, isLoading = false),
            onSelectGroup = {},
            onRequestClearCache = {},
            onExportLog = {},
            onRequestAbout = {},
            onRequestLogout = {}
        )
    }
}

@Composable
fun ThemeSelectionDialog(
    currentMode: ThemeMode,
    onSelectMode: (ThemeMode) -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = ExpressiveTokens.CardShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选择主题模式",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "关闭")
                    }
                }
                ThemeMode.entries.forEach { mode ->
                    val isSelected = mode == currentMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ExpressiveTokens.MediumCardShape)
                            .clickable { onSelectMode(mode) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(selected = isSelected, onClick = { onSelectMode(mode) })
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Text(
                    text = "跟随系统 / 浅色 / 深色",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
            }
        }
    }
}
