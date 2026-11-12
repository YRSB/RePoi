package com.nobody.repoi.ui.settings.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.ui.common.EmptyState
import com.nobody.repoi.ui.common.plainTextClipEntry
import com.nobody.repoi.ui.settings.SettingsAccountSecurityUiState
import com.nobody.repoi.ui.settings.bindings
import com.nobody.repoi.ui.settings.components.SettingItem
import com.nobody.repoi.ui.settings.components.SettingItemGroup
import kotlinx.coroutines.launch

private fun accountBindingIcon(title: String): ImageVector = when (title) {
    "登录密码" -> Icons.Outlined.Lock
    "手机" -> Icons.Outlined.Smartphone
    "邮箱" -> Icons.Outlined.Email
    "微信" -> Icons.Outlined.Link
    else -> Icons.Outlined.AccountCircle
}

/**
 * 账号与安全详情 Pane：承载个人信息入口、账号安全与第三方绑定状态、Token 导出与退出登录。
 *
 * 遵循 Zhihu-Hyperion 的 EmptyState 范式：未登录时展示居中 EmptyState 占位卡片与「立即登录」按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAccountPane(
    showBackButton: Boolean,
    onBack: () -> Unit,
    isLoggedIn: Boolean,
    accountState: SettingsAccountSecurityUiState,
    activeToken: String,
    onOpenProfile: () -> Unit,
    onRefreshAccount: () -> Unit,
    onOpenExportDialog: () -> Unit,
    onDismissExportDialog: () -> Unit,
    onToggleExportTokenVisibility: () -> Unit,
    onRequestLogout: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "账号与安全",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    ) { innerPadding ->
        if (!isLoggedIn) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Outlined.Lock,
                    title = "登录后管理账号与安全",
                    description = "查看已绑定的手机、邮箱与微信",
                    actionLabel = "立即登录",
                    actionIcon = Icons.AutoMirrored.Outlined.Login,
                    onAction = onNavigateToLogin
                )
            }
        } else {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
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
                        if (accountState.isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        } else if (accountState.loadFailed) {
                            SettingItem(
                                icon = Icons.Outlined.ErrorOutline,
                                title = "账号状态加载失败",
                                subtitle = "点击重试",
                                titleColor = MaterialTheme.colorScheme.error,
                                onClick = onRefreshAccount
                            )
                        } else {
                            val bindings = accountState.bindings()
                            bindings.forEach { binding ->
                                SettingItem(
                                    icon = accountBindingIcon(binding.title),
                                    title = binding.title,
                                    subtitle = binding.statusText
                                )
                            }
                            SettingItem(
                                icon = Icons.Outlined.Key,
                                title = "导出登录 Token",
                                subtitle = "复制当前会话令牌到其他设备",
                                trailingContent = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                },
                                onClick = onOpenExportDialog
                            )
                        }
                    }
                }

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

    if (accountState.showExportDialog) {
        TokenExportDialog(
            token = activeToken,
            revealed = accountState.exportTokenRevealed,
            onToggleVisibility = onToggleExportTokenVisibility,
            onDismiss = onDismissExportDialog
        )
    }
}

/**
 * Token 导出安全弹窗：警示横幅 + 脱敏只读令牌 + 显隐切换 + 一键复制。
 *
 * 复制走跨平台剪贴板并经全局消息条反馈，不在弹窗内保留明文状态。
 */
@Composable
private fun TokenExportDialog(
    token: String,
    revealed: Boolean,
    onToggleVisibility: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboard.current
    val messages = LocalAppMessages.current
    val scope = rememberCoroutineScope()
    val displayToken = if (revealed) {
        token.ifBlank { "暂无可用令牌，请重新登录" }
    } else {
        maskToken(token)
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        },
        title = { Text("导出登录 Token") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            MaterialTheme.shapes.medium
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "令牌等同于账号密码，请勿截图转发或泄露给他人，仅粘贴到你信任的 RePoi 客户端。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
                OutlinedTextField(
                    value = displayToken,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    label = { Text("当前会话令牌") },
                    trailingIcon = {
                        IconButton(onClick = onToggleVisibility) {
                            Icon(
                                imageVector = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (revealed) "隐藏令牌" else "显示令牌"
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    shape = MaterialTheme.shapes.large
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
                FilledTonalButton(
                    enabled = token.isNotBlank(),
                    onClick = {
                        scope.launch {
                            runCatching { clipboard.setClipEntry(plainTextClipEntry(token)) }
                            messages.show("登录 Token 已复制")
                            onDismiss()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("复制到剪贴板")
                }
            }
        }
    )
}

/**
 * 令牌脱敏展示：保留首尾各 4 位原文，中间以 `••••••••` 遮蔽；过短令牌整体遮蔽。
 */
internal fun maskToken(token: String): String {
    if (token.isBlank()) return "暂无可用令牌，请重新登录"
    if (token.length <= 8) return "••••••••"
    return "${token.take(4)}••••••••${token.takeLast(4)}"
}
