package com.nobody.repoi.ui.friend

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.repoi.ui.common.LevelPill
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.util.CdnUrlUtils

/**
 * 好友与社交列表界面 (FriendListScreen)。
 * 展示指定用户的关注、粉丝或获赞条目列表，支持下拉刷新、无缝分页与关注即时切换。
 *
 * @param userId 目标用户 ID
 * @param initialType 初始进入的子分类（0=关注, 1=粉丝, 3=获赞）
 * @param onNavigateBack 返回上一级回调
 * @param onNavigateToUser 导航至用户详情回调
 * @param onNavigateToItem 导航至条目详情回调
 * @param viewModel 社交列表 ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendListScreen(
    userId: Long,
    initialType: Int = 0,
    onNavigateBack: () -> Unit = {},
    onNavigateToUser: (Long) -> Unit = {},
    onNavigateToItem: (Long, String?) -> Unit = { _, _ -> },
    viewModel: FriendListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // 页面进入或参数变化时触发初始化
    LaunchedEffect(userId, initialType) {
        viewModel.init(userId, initialType)
    }

    // 处理一次性错误或回滚提示
    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.userMessageShown()
        }
    }

    // 滑动到底部自动加载更多
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalCount = listState.layoutInfo.totalItemsCount
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalCount > 0 && lastVisibleIndex >= totalCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, uiState.items.size) {
        if (shouldLoadMore && uiState.hasMore && !uiState.isLoadingMore && !uiState.isLoading && !uiState.isRefreshing) {
            viewModel.loadMore()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "社交关系",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(PaddingValues(top = innerPadding.calculateTopPadding())),
            contentAlignment = Alignment.TopCenter
        ) {
            // 宽屏平板自适应约束：限制最大宽度为 720dp 并水平居中
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
            ) {
                // 1. 三态分类 TabRow (关注 / 粉丝 / 获赞)
                val selectedTabIndex = when (uiState.currentTab) {
                    1 -> 1
                    3 -> 2
                    else -> 0
                }
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { viewModel.switchTab(0) },
                        text = { Text("关注", fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { viewModel.switchTab(1) },
                        text = { Text("粉丝", fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { viewModel.switchTab(3) },
                        text = { Text("获赞", fontWeight = if (selectedTabIndex == 2) FontWeight.Bold else FontWeight.Normal) }
                    )
                }

                // 2. 下拉刷新与滚动流列表
                PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    when {
                        uiState.isLoading && uiState.items.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(36.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        uiState.errorMessage != null && uiState.items.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = uiState.errorMessage ?: "加载失败",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = { viewModel.refresh() },
                                        shape = ExpressiveTokens.ButtonShape
                                    ) {
                                        Text("重试")
                                    }
                                }
                            }
                        }

                        uiState.items.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val emptyText = when (uiState.currentTab) {
                                    0 -> "暂无关注的用户"
                                    1 -> "暂无粉丝"
                                    3 -> "暂无获赞条目"
                                    else -> "暂无数据"
                                }
                                Text(
                                    text = emptyText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        else -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .consumeWindowInsets(PaddingValues(bottom = innerPadding.calculateBottomPadding())),
                                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp + innerPadding.calculateBottomPadding()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    count = uiState.items.size,
                                    key = { index ->
                                        val item = uiState.items[index]
                                        if (uiState.currentTab == 3) "praise_${item.id}_$index"
                                        else "user_${item.userId}_$index"
                                    },
                                    contentType = { index ->
                                        if (uiState.currentTab == 3) "praise_item" else "user_item"
                                    }
                                ) { index ->
                                    val item = uiState.items[index]
                                    if (uiState.currentTab == 3) {
                                        PraiseItemCard(
                                            item = item,
                                            onItemClick = { targetItemId, itemType -> onNavigateToItem(targetItemId, itemType) },
                                            onUserClick = { targetUserId -> onNavigateToUser(targetUserId) }
                                        )
                                    } else {
                                        UserFollowItemCard(
                                            item = item,
                                            onUserClick = { onNavigateToUser(item.userId) },
                                            onToggleFollow = { viewModel.toggleFollow(item.userId, item.isFollowed) }
                                        )
                                    }
                                }

                                if (uiState.isLoadingMore) {
                                    item(key = "footer_loading", contentType = "loading_footer") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
                                } else if (!uiState.hasMore && uiState.items.isNotEmpty()) {
                                    item(key = "footer_no_more", contentType = "no_more_footer") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "没有更多了",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 关注/粉丝单项用户卡片。
 */
@Composable
private fun UserFollowItemCard(
    item: FriendListItemModel,
    onUserClick: () -> Unit,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onUserClick,
        shape = ExpressiveTokens.CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = ExpressiveTokens.ElevationDefault
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 用户头像
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(CdnUrlUtils.getHeadUrl(item.header))
                    .crossfade(true)
                    .build(),
                contentDescription = "用户头像",
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 昵称、等级与签名
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.nickname.ifBlank { "Hpoi 用户" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    LevelPill(level = item.level)
                }

                Text(
                    text = item.signature?.ifBlank { null } ?: "这个人很懒，什么都没写~",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 关注状态胶囊按钮
            FollowCapsuleButton(
                isFollowed = item.isFollowed,
                isMutual = item.isMutual,
                onClick = onToggleFollow
            )
        }
    }
}

/**
 * 关注/取关胶囊交互按钮。
 */
@Composable
private fun FollowCapsuleButton(
    isFollowed: Boolean,
    isMutual: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isFollowed) {
        OutlinedButton(
            onClick = onClick,
            shape = ExpressiveTokens.ButtonShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = modifier.heightIn(min = 32.dp)
        ) {
            if (isMutual) {
                Icon(
                    imageVector = Icons.Default.SyncAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = if (isMutual) "互相关注" else "已关注",
                style = MaterialTheme.typography.labelMedium,
                color = if (isMutual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        }
    } else {
        Button(
            onClick = onClick,
            shape = ExpressiveTokens.ButtonShape,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            modifier = modifier.heightIn(min = 32.dp)
        ) {
            Text(
                text = "+ 关注",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 获赞条目卡片。
 */
@Composable
private fun PraiseItemCard(
    item: FriendListItemModel,
    onItemClick: (Long, String?) -> Unit,
    onUserClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val relate = item.relateItem
    val isAlbum = relate?.isAlbum == true
    val targetItemId = if (isAlbum) {
        relate?.actualAlbumId ?: 0L
    } else {
        relate?.actualHobbyId ?: 0L
    }

    ElevatedCard(
        onClick = { if (targetItemId > 0L) onItemClick(targetItemId, if (isAlbum) "album" else relate?.itemType) },
        shape = ExpressiveTokens.CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = ExpressiveTokens.ElevationDefault
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 点赞用户信息行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = item.userId > 0L) {
                        onUserClick(item.userId)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(CdnUrlUtils.getHeadUrl(item.praiseUser?.header ?: item.header))
                        .crossfade(true)
                        .build(),
                    contentDescription = "点赞用户头像",
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = item.nickname.ifBlank { "手办玩家" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.level > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    LevelPill(level = item.level)
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "赞了此手办",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // 被点赞的手办条目内嵌信息
            if (relate != null) {
                Surface(
                    shape = ExpressiveTokens.MediumCardShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val coverUrl = CdnUrlUtils.getCoverUrl(relate.cover ?: relate.path)
                        AsyncImage(
                            model = ImageRequest.Builder(LocalPlatformContext.current)
                                .data(coverUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "条目封面",
                            modifier = Modifier
                                .size(56.dp)
                                .clip(ExpressiveTokens.SmallCardShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val itemName = relate.nameCN?.ifBlank { null } ?: relate.name ?: "未知条目"
                            Text(
                                text = itemName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (relate.collect > 0 || relate.commentCount > 0) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (relate.collect > 0) {
                                        Text(
                                            text = "收藏 ${relate.collect}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    if (relate.commentCount > 0) {
                                        Text(
                                            text = "评论 ${relate.commentCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
