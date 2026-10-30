package com.nobody.poishelf.ui.forum

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.poishelf.platform.isLandscapeOrientation
import com.nobody.poishelf.platform.LocalAppMessages
import com.nobody.poishelf.data.remote.model.PostItem
import com.nobody.poishelf.ui.common.LocalImageViewer
import com.nobody.poishelf.ui.common.UserAvatar
import com.nobody.poishelf.ui.forum.components.ForumEmptyPlaceholder
import com.nobody.poishelf.ui.forum.components.ForumErrorPlaceholder
import com.nobody.poishelf.ui.forum.components.ForumHeaderCard
import com.nobody.poishelf.ui.forum.components.ForumPaginationFooter
import com.nobody.poishelf.ui.home.components.ShimmerHost
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.ui.zone.components.ZonePinnedPostsBar
import com.nobody.poishelf.ui.zone.components.ZonePostCard
import com.nobody.poishelf.ui.zone.components.ZonePostCardSkeleton
import kotlinx.coroutines.launch

/**
 * 讨论板主页（对标原版 ForumHomeActivity）。
 * 动态 Tabs + 排序胶囊 + 站内搜索 + 帖子分页流，宽屏升级为双列。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumHomeScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPost: (itemId: Long, nodeId: Long?, title: String?) -> Unit,
    onNavigateToUser: (userId: Long) -> Unit,
    onNavigateToSelf: () -> Unit,
    onNavigateToMessage: () -> Unit,
    onNavigateToPlates: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForumHomeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val imageViewer = LocalImageViewer.current

    // 从订阅页返回时刷新动态 Tabs（跳过首次进入）
    var resumeCount by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        resumeCount++
        if (resumeCount > 1) viewModel.loadTabs()
    }

    val tabs = uiState.tabs
    val selectedIndex = remember(tabs, uiState.selectedTabId) {
        tabs.indexOfFirst { it.id == uiState.selectedTabId }.coerceAtLeast(0)
    }
    val tabsKey = remember(tabs) { tabs.joinToString(",") { it.id.toString() } }
    val pagerState = key(tabsKey) {
        rememberPagerState(
            initialPage = selectedIndex.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
        ) {
            tabs.size.coerceAtLeast(1)
        }
    }
    LaunchedEffect(pagerState, tabs) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            tabs.getOrNull(page)?.let { viewModel.selectTab(it.id) }
        }
    }
    LaunchedEffect(selectedIndex, tabs.size) {
        if (tabs.isNotEmpty() && pagerState.currentPage != selectedIndex) {
            pagerState.scrollToPage(selectedIndex)
        }
    }

    // 各 Tab 网格滚动进度常驻，避免切页回顶
    val gridStates = remember { mutableMapOf<Long, LazyStaggeredGridState>() }

    // 新帖排序下强制展示发布时间（对标原版时间分支）
    val displayPosts = remember(uiState.posts, uiState.selectedOrder) {
        if (uiState.selectedOrder == ForumOrders.NEW) {
            uiState.posts.map { if (it.isReplyTime) it.copy(updTime = it.addTime) else it }
        } else {
            uiState.posts
        }
    }

    val handlePostClick = remember(onNavigateToPost) {
        { item: PostItem ->
            val targetItemId = if (item.itemId > 0L) item.itemId else item.id
            val targetNodeId = if (item.id > 0L) item.id else null
            if (targetItemId > 0L) {
                onNavigateToPost(targetItemId, targetNodeId, item.title.ifBlank { null })
            } else {
                messages.show("帖子详情功能即将上线")
            }
        }
    }
    val onCardImageClick = remember(imageViewer) {
        { rawUrls: List<String>, initialIndex: Int ->
            imageViewer.show(rawUrls, initialIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "讨论板",
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
                    IconButton(onClick = onNavigateToSelf) {
                        UserAvatar(
                            avatarUrl = uiState.userAvatar,
                            size = 30.dp,
                            contentDescription = "我的主页"
                        )
                    }
                    IconButton(onClick = onNavigateToMessage) {
                        Icon(
                            imageVector = Icons.Outlined.Email,
                            contentDescription = "消息中心"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    messages.show("发布帖子功能即将上线")
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "发布帖子"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ForumSearchRow(
                searchInput = uiState.searchInput,
                selectedItemType = uiState.selectedItemType,
                onInputChange = viewModel::updateSearchInput,
                onSubmit = {
                    viewModel.submitSearch()
                    focusManager.clearFocus()
                },
                onClear = viewModel::clearSearch,
                onTypeSelect = viewModel::selectItemType
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.isTabsLoading && tabs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "版块加载中…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (tabs.isNotEmpty()) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                        modifier = Modifier.weight(1f),
                        containerColor = Color.Transparent,
                        edgePadding = 8.dp
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedIndex == index,
                                onClick = {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                                text = {
                                    Text(
                                        text = tab.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                }
                IconButton(onClick = onNavigateToPlates) {
                    Icon(
                        imageVector = Icons.Outlined.GridView,
                        contentDescription = "板块选择"
                    )
                }
            }

            if (uiState.tabsErrorMessage != null && uiState.isLoggedIn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.tabsErrorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::retryTabs) {
                        Text("重试")
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
            ) {
                val orders = ForumOrders.all
                orders.forEachIndexed { index, (order, label) ->
                    ToggleButton(
                        checked = uiState.selectedOrder == order,
                        onCheckedChange = { if (it) viewModel.selectOrder(order) },
                        modifier = Modifier.semantics { role = Role.RadioButton },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            orders.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                    ) {
                        Text(label)
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.Top
            ) { page ->
                val tab = tabs.getOrNull(page)
                if (tab == null) {
                    ForumPostsSkeletonPage()
                } else {
                    val gridState = remember(tab.id) {
                        gridStates.getOrPut(tab.id) { LazyStaggeredGridState() }
                    }
                    ForumPostsPage(
                        tab = tab,
                        uiState = uiState,
                        displayPosts = displayPosts,
                        gridState = gridState,
                        onRefresh = viewModel::refresh,
                        onLoadMore = viewModel::loadMore,
                        onRetry = viewModel::retryPosts,
                        onPostClick = handlePostClick,
                        onImageClick = onCardImageClick,
                        onUserClick = onNavigateToUser,
                        onHeaderClick = {
                            messages.show("版块详情功能即将上线")
                        }
                    )
                }
            }
        }
    }
}

/**
 * 讨论板自适应列数：横屏且宽度不小于 Medium 时双列，其余单列。
 */
@Composable
private fun rememberForumGridColumns(): StaggeredGridCells {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val isLandscape = isLandscapeOrientation()
    return remember(windowSizeClass, isLandscape) {
        if (isLandscape && windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)) {
            StaggeredGridCells.Fixed(2)
        } else {
            StaggeredGridCells.Fixed(1)
        }
    }
}

/**
 * 讨论板一体式搜索条（对标原版 search_layout）。
 * 单胶囊容器内横排类型下拉、竖分隔线、搜索图标与关键词输入。
 */
@Composable
private fun ForumSearchRow(
    searchInput: String,
    selectedItemType: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onTypeSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }
    val typeName = remember(selectedItemType) {
        ForumSearchTypes.all.firstOrNull { it.first == selectedItemType }?.second ?: "帖子"
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = ExpressiveTokens.PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(start = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .clip(ExpressiveTokens.PillShape)
                        .clickable { typeMenuExpanded = true }
                        .padding(start = 12.dp, end = 2.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = typeName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "切换搜索类型",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false }
                ) {
                    ForumSearchTypes.all.forEach { (type, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                onTypeSelect(type)
                                typeMenuExpanded = false
                            },
                            leadingIcon = if (type == selectedItemType) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            VerticalDivider(
                modifier = Modifier
                    .height(20.dp)
                    .padding(horizontal = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "搜索",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            BasicTextField(
                value = searchInput,
                onValueChange = onInputChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchInput.isEmpty()) {
                            Text(
                                text = "请输入关键词",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier.weight(1f)
            )
            if (searchInput.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "清除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 单个 Tab 的帖子分页流。
 */
@Composable
private fun ForumPostsPage(
    tab: ForumTab,
    uiState: ForumHomeUiState,
    displayPosts: List<PostItem>,
    gridState: LazyStaggeredGridState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onPostClick: (PostItem) -> Unit,
    onImageClick: (List<String>, Int) -> Unit,
    onUserClick: (Long) -> Unit,
    onHeaderClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = tab.id == uiState.selectedTabId

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalCount = gridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalCount > 0 && lastVisibleIndex >= totalCount - 3
        }
    }
    LaunchedEffect(
        shouldLoadMore,
        isSelected,
        displayPosts.size,
        uiState.hasMore,
        uiState.isLoadingMore,
        uiState.loadMoreError
    ) {
        if (isSelected && shouldLoadMore && displayPosts.isNotEmpty() &&
            uiState.hasMore && !uiState.isLoadingMore && uiState.loadMoreError == null
        ) {
            onLoadMore()
        }
    }

    if (!isSelected) {
        ForumPostsSkeletonPage(modifier = modifier)
        return
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        LazyVerticalStaggeredGrid(
            columns = rememberForumGridColumns(),
            state = gridState,
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = 88.dp
            ),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (uiState.isPostsLoading && displayPosts.isEmpty() && uiState.pinnedPosts.isEmpty()) {
                items(
                    5,
                    key = { index -> "forum_post_skeleton_$index" },
                    contentType = { "forum_post_skeleton" }
                ) {
                    ShimmerHost { ZonePostCardSkeleton() }
                }
            } else if (uiState.postsErrorMessage != null && displayPosts.isEmpty() && uiState.pinnedPosts.isEmpty()) {
                item(
                    span = StaggeredGridItemSpan.FullLine,
                    key = "forum_posts_error",
                    contentType = "forum_posts_error"
                ) {
                    ForumErrorPlaceholder(
                        message = uiState.postsErrorMessage ?: "加载帖子失败",
                        onRetry = onRetry
                    )
                }
            } else if (displayPosts.isEmpty() && uiState.pinnedPosts.isEmpty()) {
                item(
                    span = StaggeredGridItemSpan.FullLine,
                    key = "forum_posts_empty",
                    contentType = "forum_posts_empty"
                ) {
                    ForumEmptyPlaceholder("暂无帖子，您可以发布帖子~")
                }
            } else {
                val header = uiState.forumHeader
                if (header != null) {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                        key = "forum_header_${header.id}",
                        contentType = "forum_header"
                    ) {
                        ForumHeaderCard(
                            header = header,
                            onHeaderClick = onHeaderClick
                        )
                    }
                }

                if (uiState.pinnedPosts.isNotEmpty()) {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                        key = "forum_pinned_bar",
                        contentType = "forum_pinned"
                    ) {
                        ZonePinnedPostsBar(
                            pinnedPosts = uiState.pinnedPosts,
                            onPostClick = onPostClick
                        )
                    }
                }

                items(
                    items = displayPosts,
                    key = { item -> "forum_post_${item.id}" },
                    contentType = { "forum_post" }
                ) { post ->
                    ZonePostCard(
                        post = post,
                        onPostClick = onPostClick,
                        onImageClick = onImageClick,
                        onUserClick = onUserClick
                    )
                }

                item(
                    span = StaggeredGridItemSpan.FullLine,
                    key = "forum_posts_footer",
                    contentType = "forum_posts_footer"
                ) {
                    ForumPaginationFooter(
                        isLoadingMore = uiState.isLoadingMore,
                        loadMoreError = uiState.loadMoreError,
                        isEndReached = !uiState.hasMore,
                        onRetry = onLoadMore
                    )
                }
            }
        }
    }
}

/**
 * 非当前页 / Tab 未就绪时的骨架占位。
 */
@Composable
private fun ForumPostsSkeletonPage(modifier: Modifier = Modifier) {
    LazyVerticalStaggeredGrid(
        columns = rememberForumGridColumns(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp),
        verticalItemSpacing = 12.dp,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        items(
            5,
            key = { index -> "forum_post_skeleton_$index" },
            contentType = { "forum_post_skeleton" }
        ) {
            ShimmerHost { ZonePostCardSkeleton() }
        }
    }
}
