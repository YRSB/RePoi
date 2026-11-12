package com.nobody.repoi.ui.settings.panes

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Domain
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nobody.repoi.ui.settings.PrivacySettingKeys
import com.nobody.repoi.ui.settings.SettingsPrivacyUiState
import com.nobody.repoi.ui.settings.components.SettingItem
import com.nobody.repoi.ui.settings.components.SettingItemGroup
import com.nobody.repoi.ui.settings.components.SettingSwitchItem
import com.nobody.repoi.ui.settings.shield.SettingsShieldUiState

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

/**
 * 浏览与内容详情 Pane：承载 R18 过滤、ExHobby 扩展图库、屏蔽与黑名单入口、隐私可见性配置。
 *
 * 遵循 Zhihu-Hyperion 范式：云端设置项在未登录时原生置灰禁用（enabled = false），
 * 对应说明绑定在条目自身 subtitle 与分组 footer 描述中，杜绝弹窗拦截打扰。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContentPane(
    showBackButton: Boolean,
    onBack: () -> Unit,
    isLoggedIn: Boolean,
    shieldState: SettingsShieldUiState,
    isExHobbyEnabled: Boolean,
    privacyState: SettingsPrivacyUiState,
    onNsfwChange: (Boolean) -> Unit,
    onExHobbyEnabledChange: (Boolean) -> Unit,
    onOpenShieldList: () -> Unit,
    onOpenBlocklist: () -> Unit,
    onPrivacyOptionChange: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                shape = MaterialTheme.shapes.large,
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
                                .clip(MaterialTheme.shapes.medium)
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

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "浏览与内容",
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
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "segment-content", contentType = "segment") {
                SettingItemGroup(title = "内容与扩展") {
                    SettingSwitchItem(
                        icon = Icons.Outlined.Visibility,
                        title = "R18 内容",
                        subtitle = if (!isLoggedIn) {
                            "需登录后配置"
                        } else if (shieldState.isLoading) {
                            "正在读取…"
                        } else if (shieldState.nsfwVisible) {
                            "显示"
                        } else {
                            "不显示"
                        },
                        checked = shieldState.nsfwVisible && isLoggedIn,
                        enabled = isLoggedIn && !shieldState.isLoading && !shieldState.isSaving,
                        onCheckedChange = onNsfwChange
                    )
                    SettingSwitchItem(
                        icon = Icons.Outlined.Collections,
                        title = "ExHobby",
                        subtitle = "显示来自ExHobby的图片",
                        checked = isExHobbyEnabled,
                        enabled = true,
                        onCheckedChange = onExHobbyEnabledChange
                    )
                }
            }

            item(key = "segment-shield", contentType = "segment") {
                SettingItemGroup(title = "名单管理") {
                    SettingItem(
                        icon = Icons.Outlined.Block,
                        title = "屏蔽列表",
                        subtitle = if (isLoggedIn) "用户 / 厂商 / 条目" else "需登录后管理",
                        enabled = isLoggedIn && !shieldState.isLoading && !shieldState.isSaving,
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isLoggedIn) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                            )
                        },
                        onClick = if (isLoggedIn) onOpenShieldList else null
                    )
                    SettingItem(
                        icon = Icons.Outlined.PersonOff,
                        title = "黑名单",
                        subtitle = if (isLoggedIn) "被屏蔽的用户将无法向您发送私信" else "需登录后管理",
                        enabled = isLoggedIn && !shieldState.isLoading && !shieldState.isSaving,
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isLoggedIn) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                            )
                        },
                        onClick = if (isLoggedIn) onOpenBlocklist else null
                    )
                }
            }

            item(key = "segment-collect", contentType = "segment") {
                SettingItemGroup(
                    title = "收藏信息可见性",
                ) {
                    if (privacyState.isLoading && isLoggedIn) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    } else {
                        SettingItem(
                            icon = Icons.Outlined.Category,
                            title = "周边条目(手办、动漫、模型、布偶、娃娃)",
                            subtitle = visibilityOptions.getOrElse(
                                privacyState.values[PrivacySettingKeys.COLLECT_HOBBY] ?: 0
                            ) { visibilityOptions[0] },
                            enabled = isLoggedIn && !shieldState.isLoading && !shieldState.isSaving,
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isLoggedIn) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                                )
                            },
                            onClick = if (isLoggedIn) {
                                { editingKey = PrivacySettingKeys.COLLECT_HOBBY }
                            } else null
                        )
                        SettingItem(
                            icon = Icons.Outlined.Bookmarks,
                            title = "收藏内容(相册、图片、文章)",
                            subtitle = visibilityOptions.getOrElse(
                                privacyState.values[PrivacySettingKeys.COLLECT_ALBUM] ?: 0
                            ) { visibilityOptions[0] },
                            enabled = isLoggedIn && !shieldState.isLoading && !shieldState.isSaving,
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isLoggedIn) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                                )
                            },
                            onClick = if (isLoggedIn) {
                                { editingKey = PrivacySettingKeys.COLLECT_ALBUM }
                            } else null
                        )
                        SettingItem(
                            icon = Icons.Outlined.BarChart,
                            title = "收藏统计(历史消费、入手数量、待补款等)",
                            subtitle = visibilityOptions.getOrElse(
                                privacyState.values[PrivacySettingKeys.COLLECT_STATISTICS] ?: 0
                            ) { visibilityOptions[0] },
                            enabled = isLoggedIn && !shieldState.isLoading && !shieldState.isSaving,
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isLoggedIn) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                                )
                            },
                            onClick = if (isLoggedIn) {
                                { editingKey = PrivacySettingKeys.COLLECT_STATISTICS }
                            } else null
                        )
                        SettingItem(
                            icon = Icons.Outlined.Domain,
                            title = "其他内容(系列、厂商、角色、人物、作品)",
                            subtitle = visibilityOptions.getOrElse(
                                privacyState.values[PrivacySettingKeys.COLLECT_OTHER] ?: 0
                            ) { visibilityOptions[0] },
                            enabled = isLoggedIn && !shieldState.isLoading && !shieldState.isSaving,
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isLoggedIn) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                                )
                            },
                            onClick = if (isLoggedIn) {
                                { editingKey = PrivacySettingKeys.COLLECT_OTHER }
                            } else null
                        )
                    }
                }
            }

            item(key = "segment-other", contentType = "segment") {
                if (!privacyState.isLoading || !isLoggedIn) {
                    SettingItemGroup(
                        title = "动态与社交可见性",
                    ) {
                        SettingItem(
                            icon = Icons.Outlined.DynamicFeed,
                            title = "个人主页动态",
                            subtitle = visibilityOptions.getOrElse(
                                privacyState.values[PrivacySettingKeys.OWN_ACTION] ?: 0
                            ) { visibilityOptions[0] },
                            enabled = isLoggedIn && !shieldState.isLoading && !shieldState.isSaving,
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isLoggedIn) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                                )
                            },
                            onClick = if (isLoggedIn) {
                                { editingKey = PrivacySettingKeys.OWN_ACTION }
                            } else null
                        )
                        SettingItem(
                            icon = Icons.Outlined.Groups,
                            title = "关注、粉丝",
                            subtitle = visibilityOptions.getOrElse(
                                privacyState.values[PrivacySettingKeys.PRIVACY_FRIEND] ?: 0
                            ) { visibilityOptions[0] },
                            enabled = isLoggedIn && !shieldState.isLoading && !shieldState.isSaving,
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isLoggedIn) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                                )
                            },
                            onClick = if (isLoggedIn) {
                                { editingKey = PrivacySettingKeys.PRIVACY_FRIEND }
                            } else null
                        )
                        SettingItem(
                            icon = Icons.Outlined.Forum,
                            title = "讨论板个人主页",
                            subtitle = visibilityOptions.getOrElse(
                                privacyState.values[PrivacySettingKeys.PRIVACY_POSTS] ?: 0
                            ) { visibilityOptions[0] },
                            enabled = isLoggedIn && !shieldState.isLoading && !shieldState.isSaving,
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isLoggedIn) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                                )
                            },
                            onClick = if (isLoggedIn) {
                                { editingKey = PrivacySettingKeys.PRIVACY_POSTS }
                            } else null
                        )
                    }
                }
            }

            item(key = PrivacySettingKeys.STRANGER_MESSAGE, contentType = "row") {
                if (!privacyState.isLoading || !isLoggedIn) {
                    SettingItemGroup(
                        title = "私信设置"
                    ) {
                        SettingItem(
                            icon = Icons.AutoMirrored.Outlined.Chat,
                            title = "陌生人私信设置",
                            subtitle = strangerOptions.getOrElse(
                                privacyState.values[PrivacySettingKeys.STRANGER_MESSAGE] ?: 0
                            ) { strangerOptions[0] },
                            enabled = isLoggedIn && !shieldState.isLoading && !shieldState.isSaving,
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isLoggedIn) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
                                )
                            },
                            onClick = if (isLoggedIn) {
                                { editingKey = PrivacySettingKeys.STRANGER_MESSAGE }
                            } else null
                        )
                    }
                }
            }
        }
    }
}
