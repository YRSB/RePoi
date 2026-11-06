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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Style
import com.nobody.repoi.ui.settings.components.SettingDropdownItem
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Wallpaper
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
import androidx.compose.material3.SegmentedListItem
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
import androidx.compose.runtime.mutableFloatStateOf
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
import com.nobody.repoi.data.local.AllPaletteStyles
import com.nobody.repoi.data.local.AllSpecVersions
import com.nobody.repoi.data.local.ThemeMode
import com.nobody.repoi.data.local.ThemeSeed
import com.nobody.repoi.data.local.orAvailable
import com.nobody.repoi.data.local.toColors
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.nobody.repoi.platform.AppInfo
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.platform.PoiShelfBackHandler
import com.nobody.repoi.platform.dynamicPoiShelfColorScheme
import com.nobody.repoi.platform.isDynamicSeedAvailable
import com.nobody.repoi.platform.shareLogFile
import com.nobody.repoi.platform.supportsDynamicColor
import com.nobody.repoi.ui.profile.ProfileViewModel
import com.nobody.repoi.ui.settings.shield.SettingsShieldViewModel
import com.nobody.repoi.ui.settings.shield.SettingsShieldUiState
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.ui.theme.FormFactorPreviews
import com.nobody.repoi.ui.theme.PoiShelfTheme
import com.nobody.repoi.ui.theme.ThemeCardColors
import com.nobody.repoi.ui.theme.getOrCreateThemeCardColors
import com.nobody.repoi.ui.theme.themeCardColorsFromScheme
import com.nobody.repoi.util.AppLogger
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.repoi.ui.settings.components.ButtonGroupOption
import com.nobody.repoi.ui.settings.components.SettingButtonGroup
import com.nobody.repoi.ui.settings.components.SettingGroupTitle
import com.nobody.repoi.ui.settings.components.SettingItem
import com.nobody.repoi.ui.settings.components.SettingItemGroup
import com.nobody.repoi.ui.settings.components.SettingSliderItem
import com.nobody.repoi.ui.settings.components.SettingSwitchItem
import kotlin.math.roundToInt

private enum class SettingsGroup(val title: String, val description: String, val icon: ImageVector) {
    Appearance("外观与主题", "主题颜色、深色模式与壁纸取色", Icons.Outlined.Palette),
    Content("浏览与内容", "屏蔽过滤、R18 限制与隐私可见性", Icons.Outlined.Tune),
    Account("账号与安全", "当前账号状态、个人主页与手账同步", Icons.Outlined.ManageAccounts)
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

private val CacheLimitValues: List<Long> = listOf(
    100L * 1024L * 1024L,       // 0: 100 MB
    200L * 1024L * 1024L,       // 1: 200 MB
    300L * 1024L * 1024L,       // 2: 300 MB
    500L * 1024L * 1024L,       // 3: 500 MB (默认)
    750L * 1024L * 1024L,       // 4: 750 MB
    1024L * 1024L * 1024L,      // 5: 1.0 GB
    1280L * 1024L * 1024L,      // 6: 1.25 GB
    1536L * 1024L * 1024L,      // 7: 1.5 GB
    2048L * 1024L * 1024L,      // 8: 2.0 GB
    Long.MAX_VALUE              // 9: 无限制
)

private fun formatCacheLimit(bytes: Long): String = when (bytes) {
    100L * 1024L * 1024L -> "100 MB"
    200L * 1024L * 1024L -> "200 MB"
    300L * 1024L * 1024L -> "300 MB"
    500L * 1024L * 1024L -> "500 MB"
    750L * 1024L * 1024L -> "750 MB"
    1024L * 1024L * 1024L -> "1.0 GB"
    1280L * 1024L * 1024L -> "1.25 GB"
    1536L * 1024L * 1024L -> "1.5 GB"
    2048L * 1024L * 1024L -> "2.0 GB"
    Long.MAX_VALUE -> "无限制"
    else -> "500 MB"
}

private fun cacheLimitToIndex(bytes: Long): Int {
    val idx = CacheLimitValues.indexOf(bytes)
    return if (idx >= 0) idx else 3
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
    val paletteStyle by themeManager.paletteStyleFlow.collectAsStateWithLifecycle(initialValue = themeManager.getInitialPaletteStyle())
    val specVersion by themeManager.specVersionFlow.collectAsStateWithLifecycle(initialValue = themeManager.getInitialSpecVersion())
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
    val onPaletteStyleChange: (PaletteStyle) -> Unit = { style -> scope.launch { themeManager.setPaletteStyle(style) } }
    val onSpecVersionChange: (ColorSpec.SpecVersion) -> Unit = { version -> scope.launch { themeManager.setSpecVersion(version) } }
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
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                    onUpdateCacheLimit = { cacheViewModel.updateMaxSizeBytes(it) },
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
                        paletteStyle = paletteStyle,
                        onPaletteStyleChange = onPaletteStyleChange,
                        specVersion = specVersion,
                        onSpecVersionChange = onSpecVersionChange,
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
                    paletteStyle = paletteStyle,
                    onPaletteStyleChange = onPaletteStyleChange,
                    specVersion = specVersion,
                    onSpecVersionChange = onSpecVersionChange,
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
                    onUpdateCacheLimit = { cacheViewModel.updateMaxSizeBytes(it) },
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
    onUpdateCacheLimit: (Long) -> Unit,
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
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "segment-preferences", contentType = "segment") {
            SettingItemGroup(title = "偏好设置") {
                groups.forEach { group ->
                    val selected = selectedGroup == group
                    SettingItem(
                        title = group.title,
                        subtitle = group.description,
                        icon = group.icon,
                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        onClick = { onSelectGroup(group) }
                    )
                }
            }
        }

        item(key = "segment-general", contentType = "segment") {
            SettingItemGroup(title = "系统与通用") {
                var sliderIndex by remember(cacheState.maxSizeBytes) {
                    mutableFloatStateOf(cacheLimitToIndex(cacheState.maxSizeBytes).toFloat())
                }
                val currentIndex = sliderIndex.roundToInt().coerceIn(0, 9)
                val currentLimitBytes = CacheLimitValues[currentIndex]
                val isExceeded = cacheState.sizeBytes > 0 && currentLimitBytes != Long.MAX_VALUE && cacheState.sizeBytes > currentLimitBytes

                SettingSliderItem(
                    title = "图片缓存上限",
                    subtitle = if (isExceeded) {
                        "当前上限：${formatCacheLimit(currentLimitBytes)}（已超额，重启或后续自动淘汰）"
                    } else {
                        "当前上限：${formatCacheLimit(currentLimitBytes)}"
                    },
                    icon = Icons.Outlined.Storage,
                    value = sliderIndex,
                    valueRange = 0f..9f,
                    steps = 8,
                    onValueChange = { sliderIndex = it },
                    onValueChangeFinished = {
                        onUpdateCacheLimit(CacheLimitValues[sliderIndex.roundToInt().coerceIn(0, 9)])
                    }
                )
                SettingItem(
                    title = "清理图片缓存",
                    subtitle = if (cacheState.isLoading) "正在统计…" else if (cacheState.isClearing) "正在清理…" else "已占用 ${formatBytes(cacheState.sizeBytes)}",
                    icon = Icons.Outlined.DeleteSweep,
                    onClick = onRequestClearCache
                )
                SettingItem(
                    title = "导出应用日志",
                    subtitle = "打包近期运行日志并分享便于排查问题",
                    icon = Icons.Outlined.Article,
                    onClick = onExportLog
                )
                SettingItem(
                    title = "关于 RePoi",
                    subtitle = "版本 ${AppInfo.versionName}",
                    icon = Icons.Outlined.Info,
                    onClick = onRequestAbout
                )
            }
        }

        if (isLoggedIn) {
            item(key = "logout", contentType = "logout") {
                SettingItemGroup {
                    SettingItem(
                        title = "退出当前账号",
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        iconTint = MaterialTheme.colorScheme.error,
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = onRequestLogout
                    )
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
    paletteStyle: PaletteStyle,
    onPaletteStyleChange: (PaletteStyle) -> Unit,
    specVersion: ColorSpec.SpecVersion,
    onSpecVersionChange: (ColorSpec.SpecVersion) -> Unit,
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
                paletteStyle = paletteStyle,
                onPaletteStyleChange = onPaletteStyleChange,
                specVersion = specVersion,
                onSpecVersionChange = onSpecVersionChange,
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
    paletteStyle: PaletteStyle,
    onPaletteStyleChange: (PaletteStyle) -> Unit,
    specVersion: ColorSpec.SpecVersion,
    onSpecVersionChange: (ColorSpec.SpecVersion) -> Unit,
    expressiveColor: Boolean,
    onExpressiveColorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val seeds = remember {
        listOf(
            ThemeSeed.Dynamic,
            ThemeSeed.Lotus,
            ThemeSeed.Coral,
            ThemeSeed.Amber,
            ThemeSeed.Pink,
            ThemeSeed.Mint,
            ThemeSeed.Grape,
            ThemeSeed.Tricolor
        )
    }
    val systemDynamicScheme = dynamicPoiShelfColorScheme(darkTheme = false)
    val dynamicCardColors = remember(systemDynamicScheme) {
        if (systemDynamicScheme != null) {
            themeCardColorsFromScheme(systemDynamicScheme)
        } else {
            getOrCreateThemeCardColors(ThemeSeed.Lotus, paletteStyle, specVersion)
        }
    }
    val staticCardColors: Map<ThemeSeed, ThemeCardColors> = remember(paletteStyle, specVersion) {
        seeds.filter { it != ThemeSeed.Dynamic }.associateWith { seed ->
            getOrCreateThemeCardColors(seed, paletteStyle, specVersion)
        }
    }

    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier,
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "theme-mode", contentType = "mode") {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingGroupTitle(text = "显示模式")
                val themeOptions = listOf(
                    ButtonGroupOption(ThemeMode.LIGHT, "浅色", Icons.Outlined.LightMode),
                    ButtonGroupOption(ThemeMode.DARK, "深色", Icons.Outlined.DarkMode),
                    ButtonGroupOption(ThemeMode.SYSTEM, "跟随系统", Icons.Outlined.BrightnessAuto)
                )
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingButtonGroup(
                        options = themeOptions,
                        selected = themeMode,
                        onSelect = onThemeModeChange
                    )
                }
            }
        }

        item(key = "seeds-flow", contentType = "seeds") {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingGroupTitle(text = "主题配色")
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(seeds, key = { it.name }, contentType = { "seed" }) { seed ->
                        val isSelected = if (dynamicColor) {
                            seed == ThemeSeed.Dynamic
                        } else {
                            seed == themeSeed.orAvailable()
                        }
                        val cardColors = if (seed == ThemeSeed.Dynamic) {
                            dynamicCardColors
                        } else {
                            staticCardColors[seed] ?: dynamicCardColors
                        }
                        PodAuraThemeCard(
                            seed = seed,
                            colors = cardColors,
                            isSelected = isSelected,
                            onClick = { onThemeSeedChange(seed) }
                        )
                    }
                }
            }
        }

        item(key = "color-spec-group", contentType = "segment") {
            SettingItemGroup(title = "色彩算法与规范") {
                SettingDropdownItem(
                    icon = Icons.Outlined.Style,
                    title = "色彩风格",
                    selectedValue = paletteStyle,
                    values = AllPaletteStyles,
                    valueLabel = { it.name },
                    onValueChange = onPaletteStyleChange
                )
                SettingDropdownItem(
                    icon = Icons.Outlined.Tune,
                    title = "色彩标准",
                    selectedValue = specVersion,
                    values = AllSpecVersions,
                    valueLabel = { it.name },
                    onValueChange = onSpecVersionChange
                )
            }
        }

        item(key = "switches", contentType = "segment") {
            SettingItemGroup(title = "显示效果") {
                if (supportsDynamicColor) {
                    SettingSwitchItem(
                        icon = Icons.Outlined.Wallpaper,
                        title = "动态主题",
                        subtitle = "将壁纸颜色应用于主题",
                        checked = dynamicColor,
                        onCheckedChange = onDynamicColorChange
                    )
                }
                SettingSwitchItem(
                    icon = Icons.Outlined.DarkMode,
                    title = "Amoled 深色",
                    subtitle = "深色模式下使用纯黑背景省电护眼",
                    checked = amoled,
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
    colors: ThemeCardColors,
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
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(colors.primaryTone36)
                ) {
                    Box(
                        modifier = Modifier
                            .size(25.dp)
                            .align(Alignment.BottomStart)
                            .background(colors.secondaryTone80)
                    )
                    Box(
                        modifier = Modifier
                            .size(25.dp)
                            .align(Alignment.BottomEnd)
                            .background(colors.tertiaryTone65)
                    )
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
                            color = colors.primaryTone50,
                            shape = RoundedCornerShape(16.dp)
                        )
                )
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
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "segment-content", contentType = "segment") {
            SettingItemGroup(title = "内容与扩展") {
                SettingSwitchItem(
                    icon = Icons.Outlined.Visibility,
                    title = "R18 内容",
                    subtitle = if (shieldState.isLoading) "正在读取…" else if (shieldState.nsfwVisible) "显示" else "不显示",
                    checked = shieldState.nsfwVisible,
                    onCheckedChange = { onNsfwChange(!shieldState.nsfwVisible) }
                )
                SettingSwitchItem(
                    icon = Icons.Outlined.Collections,
                    title = "ExHobby 扩展图库",
                    subtitle = "在手办与相册详情页展示第三方扩展图集（含 R18 扩展内容）",
                    checked = isExHobbyEnabled,
                    onCheckedChange = onExHobbyEnabledChange
                )
            }
        }

        item(key = "segment-shield", contentType = "segment") {
            SettingItemGroup(title = "名单管理") {
                SettingItem(
                    icon = Icons.Outlined.Block,
                    title = "屏蔽列表",
                    subtitle = "用户 / 厂商 / 条目",
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    },
                    onClick = onOpenShieldList
                )
                SettingItem(
                    icon = Icons.Outlined.PersonOff,
                    title = "黑名单",
                    subtitle = "被屏蔽的用户将无法向您发送私信",
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    },
                    onClick = onOpenBlocklist
                )
            }
        }

        item(key = "segment-collect", contentType = "segment") {
            SettingItemGroup(title = "收藏信息可见性") {
                if (privacyState.isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    SettingItem(
                        icon = Icons.Outlined.Category,
                        title = "周边条目(手办、动漫、模型、布偶、娃娃)",
                        subtitle = visibilityOptions.getOrElse(privacyState.values[PrivacySettingKeys.COLLECT_HOBBY] ?: 0) { visibilityOptions[0] },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        },
                        onClick = { editingKey = PrivacySettingKeys.COLLECT_HOBBY }
                    )
                    SettingItem(
                        icon = Icons.Outlined.Bookmarks,
                        title = "收藏内容(相册、图片、文章)",
                        subtitle = visibilityOptions.getOrElse(privacyState.values[PrivacySettingKeys.COLLECT_ALBUM] ?: 0) { visibilityOptions[0] },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        },
                        onClick = { editingKey = PrivacySettingKeys.COLLECT_ALBUM }
                    )
                    SettingItem(
                        icon = Icons.Outlined.BarChart,
                        title = "收藏统计(历史消费、入手数量、待补款等)",
                        subtitle = visibilityOptions.getOrElse(privacyState.values[PrivacySettingKeys.COLLECT_STATISTICS] ?: 0) { visibilityOptions[0] },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        },
                        onClick = { editingKey = PrivacySettingKeys.COLLECT_STATISTICS }
                    )
                    SettingItem(
                        icon = Icons.Outlined.Domain,
                        title = "其他内容(系列、厂商、角色、人物、作品)",
                        subtitle = visibilityOptions.getOrElse(privacyState.values[PrivacySettingKeys.COLLECT_OTHER] ?: 0) { visibilityOptions[0] },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        },
                        onClick = { editingKey = PrivacySettingKeys.COLLECT_OTHER }
                    )
                }
            }
        }

        item(key = "segment-other", contentType = "segment") {
            if (!privacyState.isLoading) {
                SettingItemGroup(title = "动态与社交可见性") {
                    SettingItem(
                        icon = Icons.Outlined.DynamicFeed,
                        title = "个人主页动态",
                        subtitle = visibilityOptions.getOrElse(privacyState.values[PrivacySettingKeys.OWN_ACTION] ?: 0) { visibilityOptions[0] },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        },
                        onClick = { editingKey = PrivacySettingKeys.OWN_ACTION }
                    )
                    SettingItem(
                        icon = Icons.Outlined.Groups,
                        title = "关注、粉丝",
                        subtitle = visibilityOptions.getOrElse(privacyState.values[PrivacySettingKeys.PRIVACY_FRIEND] ?: 0) { visibilityOptions[0] },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        },
                        onClick = { editingKey = PrivacySettingKeys.PRIVACY_FRIEND }
                    )
                    SettingItem(
                        icon = Icons.Outlined.Forum,
                        title = "讨论板个人主页",
                        subtitle = visibilityOptions.getOrElse(privacyState.values[PrivacySettingKeys.PRIVACY_POSTS] ?: 0) { visibilityOptions[0] },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        },
                        onClick = { editingKey = PrivacySettingKeys.PRIVACY_POSTS }
                    )
                }
            }
        }

        item(key = PrivacySettingKeys.STRANGER_MESSAGE, contentType = "row") {
            if (!privacyState.isLoading) {
                SettingItemGroup(title = "私信设置") {
                    SettingItem(
                        icon = Icons.Outlined.Chat,
                        title = "陌生人私信设置",
                        subtitle = strangerOptions.getOrElse(privacyState.values[PrivacySettingKeys.STRANGER_MESSAGE] ?: 0) { strangerOptions[0] },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        },
                        onClick = { editingKey = PrivacySettingKeys.STRANGER_MESSAGE }
                    )
                }
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

private fun accountBindingIcon(title: String): ImageVector = when (title) {
    "登录密码" -> Icons.Outlined.Key
    "手机" -> Icons.Outlined.Smartphone
    "邮箱" -> Icons.Outlined.Email
    "微信" -> Icons.Outlined.Link
    else -> Icons.Outlined.AccountCircle
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
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "profile", contentType = "row") {
            SettingItemGroup(title = "基本信息") {
                SettingItem(
                    icon = Icons.Outlined.Person,
                    title = "个人信息",
                    subtitle = "头像昵称与个人主页",
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    },
                    onClick = onOpenProfile
                )
            }
        }

        item(key = "bindings", contentType = "segment") {
            SettingItemGroup(title = "账号安全与绑定") {
                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else if (state.loadFailed) {
                    SettingItem(
                        icon = Icons.Outlined.ErrorOutline,
                        title = "账号状态加载失败",
                        subtitle = "点击重试",
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = onRefreshAccount
                    )
                } else {
                    val bindings = state.bindings()
                    bindings.forEach { binding ->
                        SettingItem(
                            icon = accountBindingIcon(binding.title),
                            title = binding.title,
                            subtitle = binding.statusText
                        )
                    }
                }
            }
        }

        if (isLoggedIn) {
            item(key = "logout", contentType = "logout") {
                SettingItemGroup {
                    SettingItem(
                        title = "退出当前账号",
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        iconTint = MaterialTheme.colorScheme.error,
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = onRequestLogout
                    )
                }
            }
        }
    }
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
            onUpdateCacheLimit = {},
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
