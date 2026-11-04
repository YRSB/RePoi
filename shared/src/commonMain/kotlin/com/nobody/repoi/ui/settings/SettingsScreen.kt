package com.nobody.repoi.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.filled.Close
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.repoi.data.local.ThemeMode
import com.nobody.repoi.platform.AppInfo
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.platform.PoiShelfBackHandler
import com.nobody.repoi.platform.shareLogFile
import com.nobody.repoi.platform.supportsDynamicColor
import com.nobody.repoi.ui.profile.ProfileViewModel
import com.nobody.repoi.ui.settings.shield.SettingsShieldContent
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.ui.theme.FormFactorPreviews
import com.nobody.repoi.ui.theme.LocalIsDarkTheme
import com.nobody.repoi.ui.theme.PoiShelfTheme
import com.nobody.repoi.util.AppLogger
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private enum class SettingsGroup(val title: String, val icon: ImageVector) {
    Account("账号", Icons.Filled.Lock),
    Privacy("隐私", Icons.Filled.Shield),
    General("通用", Icons.Filled.Tune),
    Repoi("RePoi", Icons.Filled.Palette)
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

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    dynamicColor: Boolean = true,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit = {},
    onOpenShieldList: () -> Unit = {},
    onOpenBlocklist: () -> Unit = {},
    onBack: () -> Unit,
    onOpenUserInfo: (Long) -> Unit,
    profileViewModel: ProfileViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    cacheViewModel: SettingsCacheViewModel = koinViewModel(),
    privacyViewModel: SettingsPrivacyViewModel = koinViewModel(),
    accountViewModel: SettingsAccountViewModel = koinViewModel()
) {
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val isExHobbyEnabled by settingsViewModel.isExHobbyEnabled.collectAsStateWithLifecycle()
    val cacheState by cacheViewModel.uiState.collectAsStateWithLifecycle()
    val privacyState by privacyViewModel.uiState.collectAsStateWithLifecycle()
    val accountState by accountViewModel.uiState.collectAsStateWithLifecycle()
    val appMessages = LocalAppMessages.current
    val scope = rememberCoroutineScope()
    var selectedGroup by rememberSaveable { mutableStateOf(SettingsGroup.General.name) }
    var subPage by rememberSaveable { mutableStateOf("") }
    var showLogoutConfirmDialog by rememberSaveable { mutableStateOf(false) }
    val wide = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

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

    if (!wide) {
        PoiShelfBackHandler(enabled = subPage.isNotEmpty()) {
            subPage = ""
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "系统设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!wide && subPage.isNotEmpty()) subPage = "" else onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
                    selectedGroup = SettingsGroup.valueOf(selectedGroup),
                    isLoggedIn = profileState.isLoggedIn,
                    onSelectGroup = {
                        selectedGroup = it.name
                        subPage = ""
                    },
                    onRequestLogout = { showLogoutConfirmDialog = true },
                    modifier = Modifier.fillMaxHeight().weight(0.38f)
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Box(modifier = Modifier.fillMaxHeight().weight(0.62f)) {
                    SettingsDetailPane(
                        group = SettingsGroup.valueOf(selectedGroup),
                        subPage = subPage,
                        themeMode = themeMode,
                        dynamicColor = dynamicColor,
                        isExHobbyEnabled = isExHobbyEnabled,
                        cacheState = cacheState,
                        privacyState = privacyState,
                        accountState = accountState,
                        onPrivacyOptionChange = { key, value -> privacyViewModel.setOption(key, value) },
                        onThemeModeChange = onThemeModeChange,
                        onDynamicColorChange = onDynamicColorChange,
                        onExHobbyEnabledChange = { settingsViewModel.setExHobbyEnabled(it) },
                        onExportLog = onExportLog,
                        onOpenCache = { subPage = "cache" },
                        onOpenAbout = { subPage = "about" },
                        onOpenProfile = { val id = profileState.profile?.id ?: 0L; if (id > 0L) onOpenUserInfo(id) },
                        onOpenSecurity = { subPage = "security" },
                        onOpenShieldList = onOpenShieldList,
                        onOpenBlocklist = onOpenBlocklist,
                        onClearCache = { cacheViewModel.clearCache() },
                        onRefreshAccount = { accountViewModel.refresh() },
                        onBackToGroup = { subPage = "" }
                    )
                }
            }
        } else {
            when {
                subPage == "cache" -> SettingsCacheDetail(
                    state = cacheState,
                    onClearCache = { cacheViewModel.clearCache() },
                    modifier = Modifier.padding(innerPadding)
                )
                subPage == "about" -> SettingsAboutDetail(
                    modifier = Modifier.padding(innerPadding)
                )
                subPage == "security" -> SettingsAccountSecurityDetail(
                    state = accountState,
                    onRetry = { accountViewModel.refresh() },
                    modifier = Modifier.padding(innerPadding)
                )
                else -> SettingsGroupList(
                    selectedGroup = null,
                    isLoggedIn = profileState.isLoggedIn,
                    onSelectGroup = {
                        selectedGroup = it.name
                        if (it == SettingsGroup.General || it == SettingsGroup.Repoi) {
                            subPage = ""
                        }
                    },
                    onOpenGroupDetail = { group ->
                        selectedGroup = group.name
                        if (group == SettingsGroup.Account) {
                            val id = profileState.profile?.id ?: 0L
                            if (id > 0L) {
                                selectedGroup = SettingsGroup.General.name
                                onOpenUserInfo(id)
                            }
                        }
                    },
                    onRequestLogout = { showLogoutConfirmDialog = true },
                    detailInline = { group ->
                        SettingsDetailPane(
                            group = group,
                            subPage = "",
                            themeMode = themeMode,
                            dynamicColor = dynamicColor,
                            isExHobbyEnabled = isExHobbyEnabled,
                            cacheState = cacheState,
                            privacyState = privacyState,
                            accountState = accountState,
                            onPrivacyOptionChange = { key, value -> privacyViewModel.setOption(key, value) },
                            onThemeModeChange = onThemeModeChange,
                            onDynamicColorChange = onDynamicColorChange,
                            onExHobbyEnabledChange = { settingsViewModel.setExHobbyEnabled(it) },
                            onExportLog = onExportLog,
                            onOpenCache = { subPage = "cache" },
                            onOpenAbout = { subPage = "about" },
                            onOpenProfile = { val id = profileState.profile?.id ?: 0L; if (id > 0L) onOpenUserInfo(id) },
                            onOpenSecurity = { subPage = "security" },
                            onOpenShieldList = onOpenShieldList,
                            onOpenBlocklist = onOpenBlocklist,
                            onClearCache = { cacheViewModel.clearCache() },
                            onRefreshAccount = { accountViewModel.refresh() },
                            onBackToGroup = {}
                        )
                    },
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
    onSelectGroup: (SettingsGroup) -> Unit,
    onRequestLogout: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenGroupDetail: ((SettingsGroup) -> Unit)? = null,
    detailInline: @Composable ((SettingsGroup) -> Unit)? = null
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
        items(groups, key = { it.name }, contentType = { "group" }) { group ->
            val selected = selectedGroup == group
            ElevatedCard(
                onClick = {
                    onSelectGroup(group)
                    onOpenGroupDetail?.invoke(group)
                },
                shape = ExpressiveTokens.CardShape,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = group.icon,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            if (detailInline != null && selectedGroup == group && onOpenGroupDetail != null) {
                detailInline(group)
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
                    modifier = Modifier.fillMaxWidth()
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
    subPage: String,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    isExHobbyEnabled: Boolean,
    cacheState: SettingsCacheUiState,
    privacyState: SettingsPrivacyUiState,
    accountState: SettingsAccountSecurityUiState,
    onPrivacyOptionChange: (String, Int) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onExHobbyEnabledChange: (Boolean) -> Unit,
    onExportLog: () -> Unit,
    onOpenCache: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenShieldList: () -> Unit = {},
    onOpenBlocklist: () -> Unit = {},
    onClearCache: () -> Unit,
    onRefreshAccount: () -> Unit,
    onBackToGroup: () -> Unit
) {
    when {
        group == SettingsGroup.General && subPage == "cache" -> SettingsCacheDetail(
            state = cacheState,
            onClearCache = onClearCache,
            modifier = Modifier.fillMaxSize()
        )
        group == SettingsGroup.General && subPage == "about" -> SettingsAboutDetail(
            modifier = Modifier.fillMaxSize()
        )
        group == SettingsGroup.General -> SettingsGeneralContent(
            cacheState = cacheState,
            onOpenCache = onOpenCache,
            onExportLog = onExportLog,
            onOpenAbout = onOpenAbout,
            modifier = Modifier.fillMaxSize()
        )
        group == SettingsGroup.Privacy -> SettingsPrivacyAndShieldContent(
            privacyState = privacyState,
            onPrivacyOptionChange = onPrivacyOptionChange,
            onOpenShieldList = onOpenShieldList,
            onOpenBlocklist = onOpenBlocklist,
            modifier = Modifier.fillMaxSize()
        )
        group == SettingsGroup.Repoi -> SettingsRepoiContent(
            themeMode = themeMode,
            dynamicColor = dynamicColor,
            isExHobbyEnabled = isExHobbyEnabled,
            onThemeModeChange = onThemeModeChange,
            onDynamicColorChange = onDynamicColorChange,
            onExHobbyEnabledChange = onExHobbyEnabledChange,
            modifier = Modifier.fillMaxSize()
        )
        group == SettingsGroup.Account && subPage == "security" -> SettingsAccountSecurityDetail(
            state = accountState,
            onRetry = onRefreshAccount,
            modifier = Modifier.fillMaxSize()
        )
        group == SettingsGroup.Account -> SettingsAccountContent(
            onOpenProfile = onOpenProfile,
            onOpenSecurity = onOpenSecurity,
            modifier = Modifier.fillMaxSize()
        )
        else -> SettingsPlaceholderContent(
            title = group.title,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun SettingsGeneralContent(
    cacheState: SettingsCacheUiState,
    onOpenCache: () -> Unit,
    onExportLog: () -> Unit,
    onOpenAbout: () -> Unit,
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
        item(key = "cache", contentType = "row") {
            SettingsMenuItem(
                icon = Icons.Filled.CleaningServices,
                title = "清理图片缓存",
                subtitle = if (cacheState.isLoading) "正在统计…" else "已占用 ${formatBytes(cacheState.sizeBytes)}",
                onClick = onOpenCache
            )
        }
        item(key = "export", contentType = "row") {
            SettingsMenuItem(
                icon = Icons.Filled.Share,
                title = "导出应用日志",
                subtitle = "打包近期运行日志并分享便于排查问题",
                onClick = onExportLog
            )
        }
        item(key = "about", contentType = "row") {
            SettingsMenuItem(
                icon = Icons.Filled.Info,
                title = "关于 RePoi",
                subtitle = "版本 ${AppInfo.versionName}",
                onClick = onOpenAbout
            )
        }
    }
}

@Composable
private fun SettingsCacheDetail(
    state: SettingsCacheUiState,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    val appMessages = LocalAppMessages.current
    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let { appMessages.show(it) }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = {
                Text(
                    text = "清理图片缓存",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "确定要清理全部图片缓存吗？清理后图片需要重新加载。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        onClearCache()
                    }
                ) {
                    Text("清理", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "occupancy", contentType = "row") {
            ElevatedCard(
                shape = ExpressiveTokens.CardShape,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CleaningServices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "图片缓存占用",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (state.isLoading) "正在统计…" else formatBytes(state.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        item(key = "clear", contentType = "action") {
            ElevatedCard(
                onClick = { if (!state.isClearing) showConfirm = true },
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
                        text = if (state.isClearing) "清理中…" else "一键清理",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsAboutDetail(modifier: Modifier = Modifier) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "version", contentType = "row") {
            ElevatedCard(
                shape = ExpressiveTokens.CardShape,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = "RePoi",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    supportingContent = {
                        Text(
                            text = "版本 ${AppInfo.versionName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun SettingsRepoiContent(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    isExHobbyEnabled: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onExHobbyEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentMode = themeMode,
            onSelectMode = { onThemeModeChange(it); showThemeDialog = false },
            onDismissRequest = { showThemeDialog = false }
        )
    }
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "theme", contentType = "row") {
            SettingsMenuItem(
                icon = if (isDark) Icons.Filled.NightsStay else Icons.Filled.WbSunny,
                title = "外观与主题风格",
                subtitle = themeMode.title,
                onClick = { showThemeDialog = true }
            )
        }
        if (supportsDynamicColor) {
            item(key = "dynamic", contentType = "row") {
                SettingsSwitchItem(
                    icon = Icons.Filled.Palette,
                    title = "动态壁纸取色 (Dynamic Color)",
                    subtitle = "启用 Material You 风格跟随系统壁纸调色板",
                    checked = dynamicColor,
                    onCheckedChange = onDynamicColorChange
                )
            }
        }
        item(key = "exhobby", contentType = "row") {
            SettingsSwitchItem(
                icon = Icons.Filled.PhotoLibrary,
                title = "ExHobby 扩展图库",
                subtitle = "在手办与相册详情页展示第三方扩展图集（含 R18 扩展内容）",
                checked = isExHobbyEnabled,
                onCheckedChange = onExHobbyEnabledChange
            )
        }
    }
}

@Composable
private fun SettingsPrivacyAndShieldContent(
    privacyState: SettingsPrivacyUiState,
    onPrivacyOptionChange: (String, Int) -> Unit,
    onOpenShieldList: () -> Unit,
    onOpenBlocklist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        com.nobody.repoi.ui.settings.shield.SettingsShieldContent(
            onOpenShieldList = onOpenShieldList,
            onOpenBlocklist = onOpenBlocklist,
            modifier = Modifier.fillMaxWidth().weight(0.35f)
        )
        SettingsPrivacyContent(
            state = privacyState,
            onOptionChange = onPrivacyOptionChange,
            modifier = Modifier.fillMaxWidth().weight(0.65f)
        )
    }
}

@Composable
private fun SettingsPrivacyContent(
    state: SettingsPrivacyUiState,
    onOptionChange: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val visibilityOptions = listOf("所有人可见", "互相关注可见", "仅自己可见")
    val strangerOptions = listOf("可随便私聊我", "只能私聊一次", "不能私聊我")
    val editing = editingKey
    if (editing != null) {
        val isStranger = editing == PrivacySettingKeys.STRANGER_MESSAGE
        val options = if (isStranger) strangerOptions else visibilityOptions
        val current = state.values[editing] ?: 0
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
                                    onOptionChange(editing, index)
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    editingKey = null
                                    onOptionChange(editing, index)
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
    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "header-collect", contentType = "header") {
            Text(
                text = "收藏信息",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        item(key = PrivacySettingKeys.COLLECT_HOBBY, contentType = "row") {
            SettingsMenuItem(
                icon = Icons.Filled.Shield,
                title = "周边条目(手办、动漫、模型、布偶、娃娃)",
                subtitle = visibilityOptions.getOrElse(state.values[PrivacySettingKeys.COLLECT_HOBBY] ?: 0) { visibilityOptions[0] },
                onClick = { editingKey = PrivacySettingKeys.COLLECT_HOBBY }
            )
        }
        item(key = PrivacySettingKeys.COLLECT_ALBUM, contentType = "row") {
            SettingsMenuItem(
                icon = Icons.Filled.Shield,
                title = "收藏内容(相册、图片、文章)",
                subtitle = visibilityOptions.getOrElse(state.values[PrivacySettingKeys.COLLECT_ALBUM] ?: 0) { visibilityOptions[0] },
                onClick = { editingKey = PrivacySettingKeys.COLLECT_ALBUM }
            )
        }
        item(key = PrivacySettingKeys.COLLECT_STATISTICS, contentType = "row") {
            SettingsMenuItem(
                icon = Icons.Filled.Shield,
                title = "收藏统计(历史消费、入手数量、待补款等)",
                subtitle = visibilityOptions.getOrElse(state.values[PrivacySettingKeys.COLLECT_STATISTICS] ?: 0) { visibilityOptions[0] },
                onClick = { editingKey = PrivacySettingKeys.COLLECT_STATISTICS }
            )
        }
        item(key = PrivacySettingKeys.COLLECT_OTHER, contentType = "row") {
            SettingsMenuItem(
                icon = Icons.Filled.Shield,
                title = "其他内容(系列、厂商、角色、人物、作品)",
                subtitle = visibilityOptions.getOrElse(state.values[PrivacySettingKeys.COLLECT_OTHER] ?: 0) { visibilityOptions[0] },
                onClick = { editingKey = PrivacySettingKeys.COLLECT_OTHER }
            )
        }
        item(key = "header-other", contentType = "header") {
            Text(
                text = "其他信息",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        item(key = PrivacySettingKeys.OWN_ACTION, contentType = "row") {
            SettingsMenuItem(
                icon = Icons.Filled.Shield,
                title = "个人主页动态",
                subtitle = visibilityOptions.getOrElse(state.values[PrivacySettingKeys.OWN_ACTION] ?: 0) { visibilityOptions[0] },
                onClick = { editingKey = PrivacySettingKeys.OWN_ACTION }
            )
        }
        item(key = PrivacySettingKeys.PRIVACY_FRIEND, contentType = "row") {
            SettingsMenuItem(
                icon = Icons.Filled.Shield,
                title = "关注、粉丝",
                subtitle = visibilityOptions.getOrElse(state.values[PrivacySettingKeys.PRIVACY_FRIEND] ?: 0) { visibilityOptions[0] },
                onClick = { editingKey = PrivacySettingKeys.PRIVACY_FRIEND }
            )
        }
        item(key = PrivacySettingKeys.PRIVACY_POSTS, contentType = "row") {
            SettingsMenuItem(
                icon = Icons.Filled.Shield,
                title = "讨论板个人主页",
                subtitle = visibilityOptions.getOrElse(state.values[PrivacySettingKeys.PRIVACY_POSTS] ?: 0) { visibilityOptions[0] },
                onClick = { editingKey = PrivacySettingKeys.PRIVACY_POSTS }
            )
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
            SettingsMenuItem(
                icon = Icons.Filled.Shield,
                title = "陌生人私信设置",
                subtitle = strangerOptions.getOrElse(state.values[PrivacySettingKeys.STRANGER_MESSAGE] ?: 0) { strangerOptions[0] },
                onClick = { editingKey = PrivacySettingKeys.STRANGER_MESSAGE }
            )
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
    onOpenProfile: () -> Unit,
    onOpenSecurity: () -> Unit,
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
        item(key = "profile", contentType = "row") {
            SettingsMenuItem(
                icon = Icons.Filled.Person,
                title = "个人信息",
                subtitle = "头像昵称与个人资料",
                onClick = onOpenProfile
            )
        }
        item(key = "security", contentType = "row") {
            SettingsMenuItem(
                icon = Icons.Filled.Lock,
                title = "账号与安全",
                subtitle = "密码手机邮箱微信绑定状态",
                onClick = onOpenSecurity
            )
        }
    }
}

@Composable
private fun SettingsAccountSecurityDetail(
    state: SettingsAccountSecurityUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
                    onClick = onRetry,
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
            items(state.bindings(), key = { it.title }, contentType = { "binding" }) { binding ->
                ElevatedCard(
                    shape = ExpressiveTokens.CardShape,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = binding.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        supportingContent = {
                            Text(
                                text = binding.statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPlaceholderContent(title: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "${title}分组后续票据接入",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(ExpressiveTokens.CardShape)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
    )
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
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
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    )
}

@FormFactorPreviews
@Composable
private fun SettingsContentPreview() {
    PoiShelfTheme {
        SettingsGeneralContent(
            cacheState = SettingsCacheUiState(sizeBytes = 12_582_912L, isLoading = false),
            onOpenCache = {},
            onExportLog = {},
            onOpenAbout = {}
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
