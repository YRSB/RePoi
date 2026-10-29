package com.nobody.poishelf.ui.message.notice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nobody.poishelf.data.remote.model.PushMessageItem
import com.nobody.poishelf.ui.navigation.MessageNoticeNavParam
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.util.CdnUrlUtils
import org.koin.compose.viewmodel.koinViewModel

/**
 * 分类通知列表页面，支持单项已读、全部已读、多选管理删除与实体跳转联动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageNoticeScreen(
    param: MessageNoticeNavParam,
    onNavigateBack: () -> Unit = {},
    onNavigateToHobby: (Long) -> Unit = {},
    onNavigateToCharacter: (Long) -> Unit = {},
    onNavigateToWorks: (Long) -> Unit = {},
    onNavigateToVendor: (Long) -> Unit = {},
    onNavigateToPerson: (Long) -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToUser: (Long) -> Unit = {},
    onNavigateToCommentDetail: (commentId: Long, relateItemId: Long) -> Unit = { _, _ -> },
    viewModel: MessageNoticeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // 选择态下沉：屏级持有选择集合快照，叶子经 provider 按需读取，避免全列表重组
    val selectedIdsSnapshot = remember { mutableStateOf(emptySet<Long>()) }
    selectedIdsSnapshot.value = uiState.selectedIds

    // 触底分页：屏级派生滚动位置，空列表、已到底、错误态不触发
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalCount = listState.layoutInfo.totalItemsCount
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalCount > 0 && lastVisibleIndex >= totalCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, uiState.messages.size) {
        if (shouldLoadMore && uiState.hasMore && !uiState.isLoadingMore &&
            !uiState.isLoading && !uiState.isRefreshing && uiState.errorMessage == null
        ) {
            viewModel.loadMore()
        }
    }

    LaunchedEffect(param) {
        viewModel.loadInitial(param)
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
                        text = if (uiState.isManagerMode) "已选择 ${uiState.selectedCount} 项" else uiState.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (uiState.isManagerMode) {
                        IconButton(onClick = { viewModel.toggleManagerMode() }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "退出管理")
                        }
                    } else {
                        IconButton(onClick = onNavigateBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (uiState.isManagerMode) {
                        TextButton(onClick = { viewModel.toggleSelectAll() }) {
                            Text(if (uiState.isAllSelected) "取消全选" else "全选")
                        }
                        TextButton(onClick = { viewModel.invertSelection() }) {
                            Text("反选")
                        }
                    } else {
                        IconButton(onClick = { viewModel.markAllRead() }) {
                            Icon(
                                imageVector = Icons.Filled.DoneAll,
                                contentDescription = "全部已读",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (uiState.messages.isNotEmpty()) {
                            IconButton(onClick = { viewModel.toggleManagerMode() }) {
                                Icon(
                                    imageVector = Icons.Filled.Checklist,
                                    contentDescription = "批量管理",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.isManagerMode,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已选 ${uiState.selectedCount} / ${uiState.messages.size} 项",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { viewModel.batchDeleteSelected() },
                            enabled = uiState.selectedCount > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("删除所选")
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(PaddingValues(top = innerPadding.calculateTopPadding()))
        ) {
            if (uiState.isLoading && uiState.messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            } else if (uiState.messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "暂无通知消息",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = if (uiState.isManagerMode) (80.dp + innerPadding.calculateBottomPadding()) else (24.dp + innerPadding.calculateBottomPadding()))
                ) {
                    items(
                        items = uiState.messages,
                        key = { it.id },
                        contentType = { "notice_item" }
                    ) { message ->
                        NoticeItemRow(
                            item = message,
                            isManagerMode = uiState.isManagerMode,
                            isSelectedProvider = { selectedIdsSnapshot.value.contains(message.id) },
                            onSelectToggle = { viewModel.toggleSelectId(message.id) },
                            onClick = {
                                viewModel.markItemRead(message)
                                handleNoticeNavigation(
                                    item = message,
                                    onNavigateToHobby = onNavigateToHobby,
                                    onNavigateToCharacter = onNavigateToCharacter,
                                    onNavigateToWorks = onNavigateToWorks,
                                    onNavigateToVendor = onNavigateToVendor,
                                    onNavigateToPerson = onNavigateToPerson,
                                    onNavigateToAlbum = onNavigateToAlbum,
                                    onNavigateToUser = onNavigateToUser,
                                    onNavigateToCommentDetail = onNavigateToCommentDetail
                                )
                            },
                            onDelete = { viewModel.deleteItem(message) }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 0.6.dp
                        )
                    }

                    // 分页加载脚：被动展示，不在此触发加载（加载由屏级触底判断驱动）
                    if (uiState.isLoadingMore) {
                        item(key = "footer_notice_loading_more", contentType = "loading_footer") {
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
}

/**
 * 单条分类通知行组件。
 */
@Composable
private fun NoticeItemRow(
    item: PushMessageItem,
    isManagerMode: Boolean,
    isSelectedProvider: () -> Boolean,
    onSelectToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    // 选择态在叶子内部读取，选中变化时仅本行重组
    val isSelected = isSelectedProvider()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isManagerMode) onSelectToggle() else onClick()
            },
        color = if (item.isUnread) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (isManagerMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // 发送者头像或系统图标
            val avatarUrl = item.dataMap?.header
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = CdnUrlUtils.getCoverSmallUrl(avatarUrl),
                    contentDescription = item.dataMap.nickname,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.dataMap?.nickname ?: item.title.orEmpty().ifBlank { "系统消息" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (item.isUnread) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    item.date?.let { dateStr ->
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 正文描述
                val mainContent = item.content ?: item.dataMap?.content ?: item.title.orEmpty()
                if (mainContent.isNotBlank()) {
                    Text(
                        text = mainContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 关联回复/评论内容卡片预览
                val quote = item.dataMap?.comment ?: item.dataMap?.reply
                if (!quote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        shape = ExpressiveTokens.CardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = quote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 未读小红点
            if (item.isUnread) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                )
            }
        }
    }
}

/**
 * 处理各类通知条目点击后的目标实体导航跳转。
 */
private fun handleNoticeNavigation(
    item: PushMessageItem,
    onNavigateToHobby: (Long) -> Unit,
    onNavigateToCharacter: (Long) -> Unit,
    onNavigateToWorks: (Long) -> Unit,
    onNavigateToVendor: (Long) -> Unit,
    onNavigateToPerson: (Long) -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToUser: (Long) -> Unit,
    onNavigateToCommentDetail: (Long, Long) -> Unit
) {
    val dataMap = item.dataMap ?: return
    val relateItemId = dataMap.relateItemId ?: dataMap.nodeId ?: 0L
    val relateType = dataMap.relateItemType ?: dataMap.itemType ?: ""

    // 若属于评论或楼中楼回复通知，优先跳往评论详情
    val commentId = dataMap.commentId ?: 0L
    if (commentId > 0L) {
        onNavigateToCommentDetail(commentId, relateItemId)
        return
    }

    if (relateItemId > 0L) {
        when (relateType) {
            "hobby" -> onNavigateToHobby(relateItemId)
            "character", "charactar" -> onNavigateToCharacter(relateItemId)
            "works" -> onNavigateToWorks(relateItemId)
            "company", "vendor" -> onNavigateToVendor(relateItemId)
            "person" -> onNavigateToPerson(relateItemId)
            "album" -> onNavigateToAlbum(relateItemId)
            else -> onNavigateToHobby(relateItemId)
        }
        return
    }

    dataMap.userId?.let { uid ->
        if (uid > 0L) onNavigateToUser(uid)
    }
}
