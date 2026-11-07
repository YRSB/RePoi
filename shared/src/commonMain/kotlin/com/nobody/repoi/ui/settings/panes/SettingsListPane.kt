package com.nobody.repoi.ui.settings.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobody.repoi.platform.AppInfo
import com.nobody.repoi.ui.common.UserAvatar
import com.nobody.repoi.ui.profile.ProfileUiState
import com.nobody.repoi.ui.settings.SettingsCacheUiState
import com.nobody.repoi.ui.settings.components.SettingItem
import com.nobody.repoi.ui.settings.components.SettingItemGroup
import com.nobody.repoi.ui.settings.components.SettingSliderItem
import com.nobody.repoi.ui.settings.navigation.SettingsGroup
import kotlin.math.roundToInt

internal fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "未知"
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = bytes / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}

internal val CacheLimitValues: List<Long> = listOf(
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

internal fun formatCacheLimit(bytes: Long): String = when (bytes) {
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

internal fun cacheLimitToIndex(bytes: Long): Int {
    val idx = CacheLimitValues.indexOf(bytes)
    return if (idx >= 0) idx else 3
}

/**
 * 设置中心一级主列表 Pane：承载用户卡片、三大偏好分组入口与系统通用配置项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsListPane(
    isWide: Boolean,
    selectedGroup: SettingsGroup?,
    isLoggedIn: Boolean,
    profileState: ProfileUiState,
    cacheState: SettingsCacheUiState,
    onBack: () -> Unit,
    onSelectGroup: (SettingsGroup) -> Unit,
    onOpenUserInfo: (Long) -> Unit,
    onNavigateToLogin: () -> Unit,
    onUpdateCacheLimit: (Long) -> Unit,
    onRequestClearCache: () -> Unit,
    onExportLog: () -> Unit,
    onRequestAbout: () -> Unit,
    onRequestLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = "系统设置",
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
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            }
        ) { innerPadding ->
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "account-header", contentType = "header") {
                    if (isLoggedIn) {
                        Surface(
                            onClick = {
                                val uid = profileState.profile?.id ?: 0L
                                if (uid > 0L) onOpenUserInfo(uid)
                            },
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(
                                    avatarUrl = profileState.profile?.avatar,
                                    size = 56.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = profileState.profile?.nickname?.ifBlank { null }
                                            ?: profileState.profile?.userName
                                            ?: "Hpoi 用户",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "点击查看个人主页",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Login,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Text(
                                    text = "登录 Hpoi 账号",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "登录后同步手账、管理隐私可见性与账号安全",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FilledTonalButton(
                                    onClick = onNavigateToLogin,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Login,
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize)
                                    )
                                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                    Text("立即登录")
                                }
                            }
                        }
                    }
                }

                item(key = "segment-preferences", contentType = "segment") {
                    SettingItemGroup(title = "偏好设置") {
                        SettingsGroup.entries.forEach { group ->
                            val selected = selectedGroup == group
                            val subtitleText = if (group == SettingsGroup.Account && !isLoggedIn) {
                                "未登录"
                            } else {
                                group.description
                            }
                            SettingItem(
                                title = group.title,
                                subtitle = subtitleText,
                                icon = group.icon,
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                titleColor = if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                iconTint = if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
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
                        val isExceeded = cacheState.sizeBytes > 0 &&
                            currentLimitBytes != Long.MAX_VALUE &&
                            cacheState.sizeBytes > currentLimitBytes

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
                            subtitle = if (cacheState.isLoading) {
                                "正在统计…"
                            } else if (cacheState.isClearing) {
                                "正在清理…"
                            } else {
                                "已占用 ${formatBytes(cacheState.sizeBytes)}"
                            },
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
