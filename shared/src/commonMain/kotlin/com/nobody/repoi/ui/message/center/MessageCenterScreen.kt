package com.nobody.repoi.ui.message.center

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nobody.repoi.data.remote.model.MessageContactItem
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.nobody.repoi.ui.common.AdaptiveChunkedGrid
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.util.CdnUrlUtils
import org.koin.compose.viewmodel.koinViewModel

/**
 * 消息中心主界面：汇聚 6 大通知分类入口与私信联系人列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageCenterScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToNotice: (type: String, typeNumber: Int, title: String) -> Unit = { _, _, _ -> },
    onNavigateToChat: (targetUserId: Long, targetUserName: String, targetUserHeader: String?) -> Unit = { _, _, _ -> },
    onNavigateToBlock: () -> Unit = {},
    viewModel: MessageCenterViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var contactToDelete by remember { mutableStateOf<MessageContactItem?>(null) }
    val listState = rememberLazyListState()

    // 触底分页：屏级派生滚动位置，空列表、已到底、错误态不触发
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalCount = listState.layoutInfo.totalItemsCount
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalCount > 0 && lastVisibleIndex >= totalCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, uiState.contacts.size) {
        if (shouldLoadMore && uiState.contacts.isNotEmpty() && uiState.hasMore &&
            !uiState.isLoadingMore && !uiState.isLoading && !uiState.isRefreshing &&
            uiState.errorMessage == null
        ) {
            viewModel.loadMoreContacts()
        }
    }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { err ->
            snackbarHostState.showSnackbar(err)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "消息中心",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.markAllRead() }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DoneAll,
                            contentDescription = "全部已读",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = onNavigateToBlock
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "黑名单",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.loadData(refresh = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(PaddingValues(top = innerPadding.calculateTopPadding()))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 24.dp + innerPadding.calculateBottomPadding())
            ) {
                // ── 顶部 6 大分类卡片网格 ──────────────────────────────────────────
                item(key = "notification_categories_grid", contentType = "categories_grid") {
                    NotificationCategoriesSection(
                        unreadCounts = uiState.unreadCounts,
                        onCategoryClick = onNavigateToNotice
                    )
                }

                item(key = "divider_before_contacts", contentType = "divider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                // ── 私信列表标头 ────────────────────────────────────────────────
                item(key = "contacts_header", contentType = "contacts_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "私信对话",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.contacts.isNotEmpty()) {
                            Text(
                                text = "${uiState.contacts.size} 个会话",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ── 私信会话流 ──────────────────────────────────────────────────
                if (uiState.isLoading && uiState.contacts.isEmpty()) {
                    item(key = "loading_indicator", contentType = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        }
                    }
                } else if (uiState.contacts.isEmpty()) {
                    item(key = "empty_contacts", contentType = "empty") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Forum,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "暂无私信消息",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = uiState.contacts,
                        key = { it.id },
                        contentType = { "contact" }
                    ) { contact ->
                        ContactItemCard(
                            item = contact,
                            onClick = {
                                onNavigateToChat(contact.otherId, contact.otherName, contact.otherHeader)
                            },
                            onToggleTop = { viewModel.toggleContactTop(contact) },
                            onDelete = { contactToDelete = contact }
                        )
                    }

                    // 分页加载脚：被动展示，不在此触发加载（加载由屏级触底判断驱动）
                    if (uiState.isLoadingMore) {
                        item(key = "contacts_footer_loading", contentType = "loading_footer") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // 删除私信会话确认弹窗
    contactToDelete?.let { contact ->
        AlertDialog(
            onDismissRequest = { contactToDelete = null },
            title = { Text("删除会话") },
            text = { Text("确定要删除与“${contact.otherName.ifBlank { "该用户" }}”的会话记录吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteContact(contact)
                        contactToDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { contactToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 顶部 6 大通知分类入口卡片区域。
 */
@Composable
private fun NotificationCategoriesSection(
    unreadCounts: com.nobody.repoi.data.remote.model.PushCountCategory,
    onCategoryClick: (type: String, typeNumber: Int, title: String) -> Unit
) {
    val categories = listOf(
        CategoryEntry(
            title = "系统通知",
            type = "system",
            typeNumber = 1,
            icon = Icons.Filled.Notifications,
            tint = Color(0xFF1E88E5),
            badgeCount = unreadCounts.system
        ),
        CategoryEntry(
            title = "回复我的",
            type = "reply",
            typeNumber = 2,
            icon = Icons.Filled.Forum,
            tint = Color(0xFFFB8C00),
            badgeCount = unreadCounts.reply
        ),
        CategoryEntry(
            title = "收到的赞",
            type = "praise",
            typeNumber = 3,
            icon = Icons.Filled.Favorite,
            tint = Color(0xFFE91E63),
            badgeCount = unreadCounts.praise
        ),
        CategoryEntry(
            title = "特别推荐",
            type = "recommend",
            typeNumber = 4,
            icon = Icons.Filled.Campaign,
            tint = Color(0xFF8E24AA),
            badgeCount = unreadCounts.recommend
        ),
        CategoryEntry(
            title = "关注动态",
            type = "follow",
            typeNumber = 5,
            icon = Icons.Filled.People,
            tint = Color(0xFF43A047),
            badgeCount = unreadCounts.actualFollowCount
        ),
        CategoryEntry(
            title = "周边提醒",
            type = "commodity",
            typeNumber = 6,
            icon = Icons.Filled.ShoppingBag,
            tint = Color(0xFF00ACC1),
            badgeCount = unreadCounts.commodity
        )
    )

    AdaptiveChunkedGrid(
        items = categories,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalSpacing = 10.dp,
        verticalSpacing = 10.dp
    ) { entry ->
        CategoryGridItem(
            entry = entry,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onCategoryClick(entry.type, entry.typeNumber, entry.title) }
        )
    }
}

private data class CategoryEntry(
    val title: String,
    val type: String,
    val typeNumber: Int,
    val icon: ImageVector,
    val tint: Color,
    val badgeCount: Int
)

@Composable
private fun CategoryGridItem(
    entry: CategoryEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(ExpressiveTokens.CardShape)
            .clickable(onClick = onClick),
        shape = ExpressiveTokens.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            BadgedBox(
                badge = {
                    if (entry.badgeCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) {
                            Text(
                                text = if (entry.badgeCount > 99) "99+" else entry.badgeCount.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(entry.tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = entry.title,
                        modifier = Modifier.size(24.dp),
                        tint = entry.tint
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = entry.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 单条私信联系人卡片项。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactItemCard(
    item: MessageContactItem,
    onClick: () -> Unit,
    onToggleTop: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        color = if (item.isTop) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            BadgedBox(
                badge = {
                    if (item.unreadCount > 0) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) {
                            Text(
                                text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            ) {
                AsyncImage(
                    model = CdnUrlUtils.getCoverSmallUrl(item.otherHeader ?: ""),
                    contentDescription = item.otherName,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // 文本区域
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        if (item.isTop) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = "置顶",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = item.otherName.ifBlank { "用户 ${item.otherId}" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (item.unreadCount > 0) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    item.lastTime?.let { timeStr ->
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.lastContent.orEmpty().ifBlank { "[消息]" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.unreadCount > 0) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 选项菜单
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多操作",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (item.isTop) "取消置顶" else "置顶会话") },
                        onClick = {
                            showMenu = false
                            onToggleTop()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "删除会话",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
