package com.nobody.repoi.ui.settings.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource

private fun accountBindingIcon(title: String): DrawableResource = when (title) {
    "登录密码" -> PoiIcons.Outlined.Lock
    "手机" -> PoiIcons.Outlined.Smartphone
    "邮箱" -> PoiIcons.Outlined.Email
    "微信" -> PoiIcons.Outlined.Link
    else -> PoiIcons.Outlined.AccountCircle
}

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
                                painter = painterResource(PoiIcons.AutoMirrored.Filled.ArrowBack),
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
                    icon = PoiIcons.Outlined.Lock,
                    title = "登录后管理账号与安全",
                    description = "查看已绑定的手机、邮箱与微信",
                    actionLabel = "立即登录",
                    actionIcon = PoiIcons.AutoMirrored.Outlined.Login,
                    onAction = onNavigateToLogin
                )
            }
        } else {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "profile", contentType = "row") {
                    SettingItemGroup(title = "基本信息") {
                        SettingItem(
                            icon = PoiIcons.Outlined.Person,
                            title = "个人信息",
                            subtitle = "头像昵称与个人主页",
                            trailingContent = {
                                Icon(
                                    painter = painterResource(PoiIcons.AutoMirrored.Filled.ArrowForwardIos),
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
                                icon = PoiIcons.Outlined.ErrorOutline,
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
                                icon = PoiIcons.Outlined.Key,
                                title = "导出登录 Token",
                                subtitle = "复制当前会话令牌到其他设备",
                                trailingContent = {
                                    Icon(
                                        painter = painterResource(PoiIcons.AutoMirrored.Filled.ArrowForwardIos),
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
                            icon = PoiIcons.AutoMirrored.Outlined.Logout,
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
                painter = painterResource(PoiIcons.Outlined.Key),
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
                                painter = painterResource(if (revealed) PoiIcons.Filled.VisibilityOff else PoiIcons.Filled.Visibility),
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
                        painter = painterResource(PoiIcons.Filled.ContentCopy),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("复制到剪贴板")
                }
            }
        }
    )
}

internal fun maskToken(token: String): String {
    if (token.isBlank()) return "暂无可用令牌，请重新登录"
    if (token.length <= 8) return "••••••••"
    return "${token.take(4)}••••••••${token.takeLast(4)}"
}
