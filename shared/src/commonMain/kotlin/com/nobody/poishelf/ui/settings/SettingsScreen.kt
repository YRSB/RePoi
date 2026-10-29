package com.nobody.poishelf.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.runtime.rememberCoroutineScope
import com.nobody.poishelf.platform.AppInfo
import com.nobody.poishelf.platform.LocalAppMessages
import com.nobody.poishelf.platform.shareLogFile
import com.nobody.poishelf.platform.supportsDynamicColor
import com.nobody.poishelf.util.AppLogger
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.material3.Card
import androidx.compose.material3.RadioButton
import androidx.compose.material.icons.filled.Close
import com.nobody.poishelf.data.local.ThemeMode
import com.nobody.poishelf.ui.profile.ProfileViewModel
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.ui.theme.FormFactorPreviews
import com.nobody.poishelf.ui.theme.LocalIsDarkTheme
import com.nobody.poishelf.ui.theme.PoiShelfTheme

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    dynamicColor: Boolean = true,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit = {},
    onOpenDebug: () -> Unit,
    onBack: () -> Unit,
    profileViewModel: ProfileViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel()
) {
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val isExHobbyEnabled by settingsViewModel.isExHobbyEnabled.collectAsStateWithLifecycle()
    val appMessages = LocalAppMessages.current
    val scope = rememberCoroutineScope()

    SettingsContent(
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        isLoggedIn = uiState.isLoggedIn,
        isExHobbyEnabled = isExHobbyEnabled,
        onThemeModeChange = onThemeModeChange,
        onDynamicColorChange = onDynamicColorChange,
        onExHobbyEnabledChange = { settingsViewModel.setExHobbyEnabled(it) },
        onOpenDebug = onOpenDebug,
        onExportLog = {
            scope.launch {
                try {
                    val (fileName, bytes) = AppLogger.exportLogBytes()
                    shareLogFile(fileName, bytes.decodeToString())
                } catch (e: Exception) {
                    AppLogger.w("设置", "日志导出失败：${e.message}")
                    appMessages.show("日志导出失败请重试")
                }
            }
        },
        onLogout = {
            profileViewModel.logout()
            onBack()
        },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    themeMode: ThemeMode,
    dynamicColor: Boolean = true,
    isLoggedIn: Boolean = false,
    isExHobbyEnabled: Boolean = true,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit = {},
    onExHobbyEnabledChange: (Boolean) -> Unit = {},
    onOpenDebug: () -> Unit,
    onExportLog: () -> Unit = {},
    onLogout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val scrollState = rememberScrollState()

    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirmDialog by rememberSaveable { mutableStateOf(false) }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentMode = themeMode,
            onSelectMode = { selectedMode ->
                onThemeModeChange(selectedMode)
                showThemeDialog = false
            },
            onDismissRequest = { showThemeDialog = false }
        )
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
                        onLogout()
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // 顶栏导航
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
                    IconButton(onClick = onBack) {
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
        // 设置内容列表（大屏 680dp 居中约束）
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(PaddingValues(top = innerPadding.calculateTopPadding())),
            contentAlignment = Alignment.TopCenter
        ) {
            val contentModifier = if (maxWidth > 680.dp) Modifier.width(680.dp) else Modifier.fillMaxWidth()
            Column(
                modifier = contentModifier
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .consumeWindowInsets(PaddingValues(bottom = innerPadding.calculateBottomPadding()))
                    .padding(
                        start = 16.dp,
                        top = 12.dp,
                        end = 16.dp,
                        bottom = 24.dp + innerPadding.calculateBottomPadding()
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // 分组 1：外观与个性化
            Text(
                text = "外观与个性化",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp)
            )

            ElevatedCard(
                shape = ExpressiveTokens.CardShape,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = ExpressiveTokens.ElevationDefault),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsMenuItem(
                        icon = if (isDark) Icons.Filled.NightsStay else Icons.Filled.WbSunny,
                        title = "外观与主题风格",
                        subtitle = themeMode.title,
                        onClick = { showThemeDialog = true }
                    )

                    if (supportsDynamicColor) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        SettingsSwitchItem(
                            icon = Icons.Filled.Palette,
                            title = "动态壁纸取色 (Dynamic Color)",
                            subtitle = "启用 Material You 风格跟随系统壁纸调色板",
                            checked = dynamicColor,
                            onCheckedChange = onDynamicColorChange
                        )
                    }
                }
            }

            // 分组 2：内容与第三方扩展
            Text(
                text = "内容与第三方扩展",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp)
            )

            ElevatedCard(
                shape = ExpressiveTokens.CardShape,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = ExpressiveTokens.ElevationDefault),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsSwitchItem(
                        icon = Icons.Filled.PhotoLibrary,
                        title = "ExHobby 扩展图库",
                        subtitle = "在手办与相册详情页展示第三方扩展图集（含 R18 扩展内容）",
                        checked = isExHobbyEnabled,
                        onCheckedChange = onExHobbyEnabledChange
                    )
                }
            }

            // 分组 3：开发者与关于
            Text(
                text = "开发者与关于",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp)
            )

            ElevatedCard(
                shape = ExpressiveTokens.CardShape,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = ExpressiveTokens.ElevationDefault),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsMenuItem(
                        icon = Icons.Filled.BugReport,
                        title = "API 调试控制台",
                        subtitle = "测试 Hpoi 各接口数据与网络层",
                        onClick = onOpenDebug
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsMenuItem(
                        icon = Icons.Filled.Share,
                        title = "导出应用日志",
                        subtitle = "打包近期运行日志并分享便于排查问题",
                        onClick = onExportLog
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsMenuItem(
                        icon = Icons.Filled.Info,
                        title = "关于 PoiShelf",
                        subtitle = "版本 ${AppInfo.versionName} (Material 3 Expressive 版)"
                    )
                }
            }

            // 分组 3：账号操作（仅已登录时显示）
            if (isLoggedIn) {
                Text(
                    text = "账号管理",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp)
                )

                ElevatedCard(
                    onClick = { showLogoutConfirmDialog = true },
                    shape = ExpressiveTokens.CardShape,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = ExpressiveTokens.ElevationDefault),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
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
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.outline
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        ),
        modifier = Modifier
            .fillMaxWidth()
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
        SettingsContent(
            themeMode = ThemeMode.SYSTEM,
            dynamicColor = true,
            onThemeModeChange = {},
            onDynamicColorChange = {},
            onOpenDebug = {},
            onLogout = {},
            onBack = {}
        )
    }
}

/**
 * 主题切换对话框
 */
@Composable
fun ThemeSelectionDialog(
    currentMode: ThemeMode,
    onSelectMode: (ThemeMode) -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ExpressiveTokens.DialogShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = ExpressiveTokens.DialogElevation
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "外观与主题风格",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThemeModeOptionItem(
                        mode = ThemeMode.SYSTEM,
                        title = "跟随系统",
                        subtitle = "自动根据 Android 系统模式切换",
                        isSelected = currentMode == ThemeMode.SYSTEM,
                        icon = Icons.Filled.Smartphone,
                        onClick = { onSelectMode(ThemeMode.SYSTEM) }
                    )
                    ThemeModeOptionItem(
                        mode = ThemeMode.LIGHT,
                        title = "浅色模式",
                        subtitle = "明亮清爽，经典珊瑚红",
                        isSelected = currentMode == ThemeMode.LIGHT,
                        icon = Icons.Filled.WbSunny,
                        onClick = { onSelectMode(ThemeMode.LIGHT) }
                    )
                    ThemeModeOptionItem(
                        mode = ThemeMode.DARK,
                        title = "深色模式",
                        subtitle = "暗色优雅，夜间舒适沉浸",
                        isSelected = currentMode == ThemeMode.DARK,
                        icon = Icons.Filled.NightsStay,
                        onClick = { onSelectMode(ThemeMode.DARK) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeModeOptionItem(
    mode: ThemeMode,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = ExpressiveTokens.MediumCardShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = null
            )
        }
    }
}
