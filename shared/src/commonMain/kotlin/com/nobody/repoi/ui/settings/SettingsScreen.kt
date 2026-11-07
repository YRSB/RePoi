package com.nobody.repoi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.nobody.repoi.data.local.ThemeManager
import com.nobody.repoi.data.local.ThemeMode
import com.nobody.repoi.data.local.ThemeSeed
import com.nobody.repoi.platform.AppInfo
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.platform.shareLogFile
import com.nobody.repoi.ui.navigation.RePoiNavDisplay
import com.nobody.repoi.ui.profile.ProfileViewModel
import com.nobody.repoi.ui.settings.navigation.SettingsAccountNavKey
import com.nobody.repoi.ui.settings.navigation.SettingsAppearanceNavKey
import com.nobody.repoi.ui.settings.navigation.SettingsContentNavKey
import com.nobody.repoi.ui.settings.navigation.SettingsGroup
import com.nobody.repoi.ui.settings.navigation.SettingsListNavKey
import com.nobody.repoi.ui.settings.navigation.SettingsSubNavSerializersModule
import com.nobody.repoi.ui.settings.panes.SettingsAccountPane
import com.nobody.repoi.ui.settings.panes.SettingsAppearancePane
import com.nobody.repoi.ui.settings.panes.SettingsContentPane
import com.nobody.repoi.ui.settings.panes.SettingsListPane
import com.nobody.repoi.ui.settings.panes.formatBytes
import com.nobody.repoi.ui.settings.shield.SettingsShieldViewModel
import com.nobody.repoi.util.AppLogger
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * 设置中心顶层容器：基于 Navigation 3 原生自适应体系（rememberListDetailSceneStrategy）实现
 * 双端一致的自适应折叠、原生大顶栏与预测性返回。
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenUserInfo: (Long) -> Unit,
    onOpenShieldList: () -> Unit = {},
    onOpenBlocklist: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    themeManager: ThemeManager = koinInject(),
    profileViewModel: ProfileViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    cacheViewModel: SettingsCacheViewModel = koinViewModel(),
    privacyViewModel: SettingsPrivacyViewModel = koinViewModel(),
    accountViewModel: SettingsAccountViewModel = koinViewModel(),
    shieldViewModel: SettingsShieldViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by themeManager.themeModeFlow.collectAsStateWithLifecycle(initialValue = themeManager.getInitialThemeMode())
    val dynamicColor by themeManager.dynamicColorFlow.collectAsStateWithLifecycle(initialValue = themeManager.getInitialDynamicColor())
    val themeSeed by themeManager.themeSeedFlow.collectAsStateWithLifecycle(initialValue = themeManager.getInitialThemeSeed())
    val amoled by themeManager.amoledFlow.collectAsStateWithLifecycle(initialValue = themeManager.getInitialAmoled())
    val paletteStyle by themeManager.paletteStyleFlow.collectAsStateWithLifecycle(initialValue = themeManager.getInitialPaletteStyle())
    val specVersion by themeManager.specVersionFlow.collectAsStateWithLifecycle(initialValue = themeManager.getInitialSpecVersion())
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

    var showLogoutConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showClearCacheConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }

    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val isWide = windowAdaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val directive = remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo)
            .copy(horizontalPartitionSpacerSize = 0.dp)
    }
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)

    val savedStateConfiguration = remember {
        SavedStateConfiguration {
            serializersModule = SettingsSubNavSerializersModule
        }
    }

    val initialKeys: Array<NavKey> = if (isWide) {
        arrayOf(SettingsListNavKey, SettingsAppearanceNavKey)
    } else {
        arrayOf(SettingsListNavKey)
    }
    val backStack = rememberNavBackStack(savedStateConfiguration, *initialKeys)

    LaunchedEffect(isWide) {
        if (isWide && backStack.size == 1 && backStack.firstOrNull() is SettingsListNavKey) {
            backStack.add(SettingsAppearanceNavKey)
        }
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

    LaunchedEffect(privacyState.errorMessage) {
        privacyState.errorMessage?.let {
            appMessages.show(it)
            privacyViewModel.consumeMessage()
        }
    }

    val onSelectGroup: (SettingsGroup) -> Unit = { group ->
        val targetKey = group.navKey
        if (isWide) {
            if (backStack.lastOrNull() != targetKey) {
                if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                }
                backStack.add(targetKey)
            }
        } else {
            if (backStack.lastOrNull() != targetKey) {
                backStack.add(targetKey)
            }
        }
    }

    val localOnBack: () -> Unit = {
        if (isWide) {
            onBack()
        } else {
            if (backStack.size > 1) {
                backStack.removeLastOrNull()
            } else {
                onBack()
            }
        }
    }

    val currentDetailKey = backStack.lastOrNull()
    val selectedGroup = if (isWide) {
        SettingsGroup.fromNavKey(currentDetailKey)
    } else {
        null
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

    val entryProvider = entryProvider<NavKey> {
        entry<SettingsListNavKey>(
            metadata = ListDetailSceneStrategy.listPane(
                detailPlaceholder = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "请从左侧选择设置项",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            )
        ) {
            SettingsListPane(
                isWide = isWide,
                selectedGroup = selectedGroup,
                isLoggedIn = profileState.isLoggedIn,
                profileState = profileState,
                cacheState = cacheState,
                onBack = onBack,
                onSelectGroup = onSelectGroup,
                onOpenUserInfo = onOpenUserInfo,
                onNavigateToLogin = onNavigateToLogin,
                onUpdateCacheLimit = { cacheViewModel.updateMaxSizeBytes(it) },
                onRequestClearCache = { showClearCacheConfirmDialog = true },
                onExportLog = onExportLog,
                onRequestAbout = { showAboutDialog = true },
                onRequestLogout = { showLogoutConfirmDialog = true }
            )
        }

        entry<SettingsAppearanceNavKey>(
            metadata = ListDetailSceneStrategy.detailPane()
        ) {
            SettingsAppearancePane(
                showBackButton = !isWide,
                onBack = localOnBack,
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                themeSeed = themeSeed,
                amoled = amoled,
                paletteStyle = paletteStyle,
                onPaletteStyleChange = onPaletteStyleChange,
                specVersion = specVersion,
                onSpecVersionChange = onSpecVersionChange,
                onThemeModeChange = onThemeModeChange,
                onDynamicColorChange = onDynamicColorChange,
                onThemeSeedChange = onThemeSeedChange,
                onAmoledChange = onAmoledChange
            )
        }

        entry<SettingsContentNavKey>(
            metadata = ListDetailSceneStrategy.detailPane()
        ) {
            SettingsContentPane(
                showBackButton = !isWide,
                onBack = localOnBack,
                isLoggedIn = profileState.isLoggedIn,
                shieldState = shieldState,
                isExHobbyEnabled = isExHobbyEnabled,
                privacyState = privacyState,
                onNsfwChange = { shieldViewModel.setNsfwVisible(it) },
                onExHobbyEnabledChange = { settingsViewModel.setExHobbyEnabled(it) },
                onOpenShieldList = onOpenShieldList,
                onOpenBlocklist = onOpenBlocklist,
                onPrivacyOptionChange = { key, value -> privacyViewModel.setOption(key, value) }
            )
        }

        entry<SettingsAccountNavKey>(
            metadata = ListDetailSceneStrategy.detailPane()
        ) {
            SettingsAccountPane(
                showBackButton = !isWide,
                onBack = localOnBack,
                isLoggedIn = profileState.isLoggedIn,
                accountState = accountState,
                onOpenProfile = {
                    val id = profileState.profile?.id ?: 0L
                    if (id > 0L) {
                        onOpenUserInfo(id)
                    } else if (!profileState.isLoggedIn) {
                        onNavigateToLogin()
                    }
                },
                onRefreshAccount = { accountViewModel.refresh() },
                onRequestLogout = { showLogoutConfirmDialog = true },
                onNavigateToLogin = onNavigateToLogin
            )
        }
    }

    RePoiNavDisplay(
        backStack = backStack,
        onBack = localOnBack,
        sceneStrategies = listOf(listDetailStrategy),
        entryProvider = entryProvider,
        modifier = modifier.fillMaxSize()
    )
}
