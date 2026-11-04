package com.nobody.repoi.ui.settings.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.ui.theme.ExpressiveTokens
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsShieldContent(
    onOpenShieldList: () -> Unit,
    onOpenBlocklist: () -> Unit,
    shieldViewModel: SettingsShieldViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by shieldViewModel.uiState.collectAsStateWithLifecycle()
    val appMessages = LocalAppMessages.current
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            appMessages.show(it)
            shieldViewModel.clearErrorMessage()
        }
    }
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        item(key = "nsfw", contentType = "row") {
            ShieldSwitchCard(
                icon = Icons.Filled.Visibility,
                title = "R18 内容",
                subtitle = if (state.isLoading) "正在读取…" else if (state.nsfwVisible) "显示" else "不显示",
                checked = state.nsfwVisible,
                enabled = !state.isLoading && !state.isSaving,
                index = 0,
                count = 3,
                onCheckedChange = { shieldViewModel.setNsfwVisible(it) }
            )
        }
        item(key = "shieldList", contentType = "row") {
            ShieldMenuCard(
                icon = Icons.Filled.Block,
                title = "屏蔽列表",
                subtitle = "用户 / 厂商 / 条目",
                index = 1,
                count = 3,
                onClick = onOpenShieldList
            )
        }
        item(key = "blocklist", contentType = "row") {
            ShieldMenuCard(
                icon = Icons.Filled.Block,
                title = "黑名单",
                subtitle = "被屏蔽的用户将无法向您发送私信",
                index = 2,
                count = 3,
                onClick = onOpenBlocklist
            )
        }
    }
}

@Composable
private fun ShieldSwitchCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    index: Int,
    count: Int,
    onCheckedChange: (Boolean) -> Unit
) {
    SegmentedListItem(
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        enabled = enabled,
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            if (!enabled) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            else Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                fontSize = 12.sp,
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

@Composable
private fun ShieldMenuCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    index: Int,
    count: Int,
    onClick: () -> Unit
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        leadingContent = {
            Icon(
                imageVector = icon,
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
        supportingContent = {
            Text(
                text = subtitle,
                fontSize = 12.sp,
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

@Composable
fun SettingsShieldListContent(
    onBack: () -> Unit = {},
    listViewModel: SettingsShieldListViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val state by listViewModel.uiState.collectAsStateWithLifecycle()
    val appMessages = LocalAppMessages.current
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            appMessages.show(it)
            listViewModel.clearErrorMessage()
        }
    }
    state.unblockTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { listViewModel.dismissUnblockDialog() },
            title = { Text(text = "取消屏蔽", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "取消屏蔽后，将会查看到对方动态",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { listViewModel.executeUnblock() },
                    enabled = !state.isUnblocking
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { listViewModel.dismissUnblockDialog() }) {
                    Text("取消")
                }
            }
        )
    }
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShieldListTab.entries.forEach { tab ->
                val selected = tab == state.tab
                androidx.compose.material3.FilterChip(
                    selected = selected,
                    onClick = { listViewModel.switchTab(tab) },
                    label = { Text(tab.title) }
                )
            }
        }
        if (state.isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.items.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "暂无屏蔽的数据哟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "tips-${state.tab.name}", contentType = "tips") {
                    Text(
                        text = when (state.tab) {
                            ShieldListTab.User -> "PS:被屏蔽用户的所有动态将不会在首页展示"
                            ShieldListTab.Vendor -> "PS:被屏蔽厂商的所有周边将不会在首页展示"
                            ShieldListTab.Entry -> "PS:被屏蔽条目的所有动态将不会在首页展示"
                        },
                        fontSize = 12.sp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                items(state.items, key = { "${state.tab.name}-${it.id}" }, contentType = { "mask" }) { item ->
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
                                    text = item.name?.ifBlank { null }
                                        ?: item.title?.ifBlank { null }
                                        ?: "ID:${item.id}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            },
                            trailingContent = {
                                TextButton(onClick = { listViewModel.confirmUnblock(item) }) {
                                    Text("取消屏蔽", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.fillMaxWidth().clip(ExpressiveTokens.CardShape)
                        )
                    }
                }
            }
        }
    }
}
