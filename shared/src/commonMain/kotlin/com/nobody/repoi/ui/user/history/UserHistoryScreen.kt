package com.nobody.repoi.ui.user.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.nobody.repoi.ui.common.PoiPullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.remote.model.UserHistoryItem
import com.nobody.repoi.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.repoi.ui.common.ExpressiveFloatingToolbar
import com.nobody.repoi.ui.common.PoiAsyncImage
import com.nobody.repoi.ui.common.UserAvatar
import com.nobody.repoi.ui.common.card.PoiCaptionGridCard
import com.nobody.repoi.ui.common.card.PoiPosterCard
import kotlinx.coroutines.launch
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserHistoryScreen(
    onBackClick: () -> Unit,
    onNavigateToHobbyDetail: (Long) -> Unit,
    onNavigateToAlbumDetail: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserHistoryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val categories = remember { UserHistoryCategory.entries.toTypedArray() }
    val pagerState = rememberPagerState(
        initialPage = uiState.selectedCategory.ordinal,
        pageCount = { categories.size }
    )

    val selectedIdsSnapshot = remember { mutableStateOf(emptySet<Long>()) }
    selectedIdsSnapshot.value = uiState.selectedIds
    val isSelectedProvider: (Long) -> Boolean = remember {
        { id -> selectedIdsSnapshot.value.contains(id) }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { pageIndex ->
            val targetCategory = categories.getOrNull(pageIndex) ?: UserHistoryCategory.HOBBY
            viewModel.selectCategory(targetCategory)
        }
    }

    LaunchedEffect(uiState.userMessage) {
        val msg = uiState.userMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeUserMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isManageMode) "已选择 ${uiState.selectedIds.size} 项" else "浏览历史",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isManageMode) {
                            viewModel.toggleManageMode(false)
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            painter = painterResource(PoiIcons.AutoMirrored.Filled.ArrowBack),
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    val currentItems = uiState.currentTabState.items
                    if (currentItems.isNotEmpty()) {
                        TextButton(onClick = { viewModel.toggleManageMode() }) {
                            Text(
                                text = if (uiState.isManageMode) "完成" else "管理",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(PaddingValues(top = innerPadding.calculateTopPadding()))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    categories.forEachIndexed { index, category ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = {
                                Text(
                                    text = category.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { pageIndex ->
                    val category = categories[pageIndex]
                    val tabState = if (category == UserHistoryCategory.HOBBY) {
                        uiState.hobbyState
                    } else {
                        uiState.albumState
                    }

                    HistoryTabContent(
                        category = category,
                        tabState = tabState,
                        isManageMode = uiState.isManageMode,
                        isSelectedProvider = isSelectedProvider,
                        onRefresh = { viewModel.refresh(category) },
                        onLoadMore = { viewModel.loadMore(category) },
                        onToggleSelect = { id -> viewModel.toggleSelectItem(id) },
                        onNavigateToHobby = onNavigateToHobbyDetail,
                        onNavigateToAlbum = onNavigateToAlbumDetail,
                        onCoverLoadFailed = { rowId, detailId, category ->
                            viewModel.onCoverLoadFailed(rowId, detailId, category)
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.isManageMode,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ExpressiveFloatingToolbar(modifier = Modifier.widthIn(max = 560.dp)) {
                        TextButton(onClick = { viewModel.toggleSelectAll() }) {
                            Text(if (uiState.isAllSelected) "取消全选" else "全选")
                        }

                        TextButton(onClick = { viewModel.toggleManageMode(false) }) {
                            Text("取消")
                        }

                        Button(
                            onClick = { showDeleteConfirmDialog = true },
                            enabled = uiState.selectedIds.isNotEmpty() && !uiState.isDeleting,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            if (uiState.isDeleting) {
                                LoadingIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("删除(${uiState.selectedIds.size})")
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("删除浏览记录") },
            text = { Text("确定要删除选中的 ${uiState.selectedIds.size} 条浏览历史记录吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteSelected()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun HistoryTabContent(
    category: UserHistoryCategory,
    tabState: HistoryTabState,
    isManageMode: Boolean,
    isSelectedProvider: (Long) -> Boolean,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleSelect: (Long) -> Unit,
    onNavigateToHobby: (Long) -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onCoverLoadFailed: (rowId: Long, detailId: Long, category: UserHistoryCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    PoiPullToRefreshBox(
        isRefreshing = tabState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        when {
            tabState.isLoading && tabState.items.isEmpty() -> {
                PoiLoadingView()
            }

            !tabState.isLoading && tabState.items.isEmpty() -> {
                HistoryEmptyView(category = category)
            }

            category == UserHistoryCategory.HOBBY -> {
                HobbyHistoryGridContent(
                    tabState = tabState,
                    isManageMode = isManageMode,
                    isSelectedProvider = isSelectedProvider,
                    onLoadMore = onLoadMore,
                    onToggleSelect = onToggleSelect,
                    onNavigateToHobby = onNavigateToHobby,
                    onCoverLoadFailed = onCoverLoadFailed
                )
            }

            else -> {
                AlbumHistoryStaggeredGridContent(
                    tabState = tabState,
                    isManageMode = isManageMode,
                    isSelectedProvider = isSelectedProvider,
                    onLoadMore = onLoadMore,
                    onToggleSelect = onToggleSelect,
                    onNavigateToAlbum = onNavigateToAlbum,
                    onCoverLoadFailed = onCoverLoadFailed
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HobbyHistoryGridContent(
    tabState: HistoryTabState,
    isManageMode: Boolean,
    isSelectedProvider: (Long) -> Boolean,
    onLoadMore: () -> Unit,
    onToggleSelect: (Long) -> Unit,
    onNavigateToHobby: (Long) -> Unit,
    onCoverLoadFailed: (rowId: Long, detailId: Long, category: UserHistoryCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            totalItemsCount > 0 && lastVisibleIndex >= totalItemsCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            onLoadMore()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val columns = remember(screenWidth) {
            when {
                screenWidth >= 840.dp -> 6
                screenWidth >= 600.dp -> 4
                else -> maxOf(3, (screenWidth / 105.dp).toInt())
            }
        }

        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomPadding = (if (isManageMode) 96.dp else 24.dp) + navBarBottom

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(WindowInsets.navigationBars),
            contentPadding = PaddingValues(
                start = 10.dp,
                end = 10.dp,
                top = 6.dp,
                bottom = bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabState.groups.forEach { group ->
                stickyHeader(key = "header_hobby_${group.dayStart}", contentType = "date_header") {
                    HistoryDateHeader(title = group.title)
                }

                val chunkedRows = group.items.chunked(columns)
                items(
                    items = chunkedRows,
                    key = { row -> "row_hobby_${row.first().id}" },
                    contentType = { "hobby_row" }
                ) { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { item ->
                            Box(modifier = Modifier.weight(1f)) {
                                HobbyHistoryGridCard(
                                    item = item,
                                    isManageMode = isManageMode,
                                    isSelectedProvider = { isSelectedProvider(item.id) },
                                    onClick = {
                                        if (isManageMode) {
                                            onToggleSelect(item.id)
                                        } else {
                                            onNavigateToHobby(item.actualDetailId)
                                        }
                                    },
                                    onLongClick = { onToggleSelect(item.id) },
                                    onCoverLoadFailed = {
                                        onCoverLoadFailed(item.id, item.actualDetailId, UserHistoryCategory.HOBBY)
                                    }
                                )
                            }
                        }

                        val emptySlots = columns - rowItems.size
                        if (emptySlots > 0) {
                            repeat(emptySlots) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (tabState.isLoadingMore) {
                item(key = "footer_hobby_loading_more", contentType = "loading_footer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumHistoryStaggeredGridContent(
    tabState: HistoryTabState,
    isManageMode: Boolean,
    isSelectedProvider: (Long) -> Boolean,
    onLoadMore: () -> Unit,
    onToggleSelect: (Long) -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onCoverLoadFailed: (rowId: Long, detailId: Long, category: UserHistoryCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val staggeredGridState = rememberLazyStaggeredGridState()

    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = staggeredGridState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            totalItemsCount > 0 && lastVisibleIndex >= totalItemsCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            onLoadMore()
        }
    }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = (if (isManageMode) 96.dp else 24.dp) + navBarBottom

    LazyVerticalStaggeredGrid(
        columns = rememberAlbumStaggeredGridColumns(),
        state = staggeredGridState,
        contentPadding = PaddingValues(
            start = 8.dp,
            end = 8.dp,
            top = 6.dp,
            bottom = bottomPadding
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        modifier = modifier
            .fillMaxSize()
            .consumeWindowInsets(WindowInsets.navigationBars)
    ) {
        tabState.groups.forEach { group ->
            item(
                span = StaggeredGridItemSpan.FullLine,
                key = "header_album_${group.dayStart}",
                contentType = "date_header"
            ) {
                HistoryDateHeader(title = group.title)
            }

            items(
                items = group.items,
                key = { it.id },
                contentType = { "album_item" }
            ) { item ->
                AlbumHistoryWaterfallCard(
                    item = item,
                    isManageMode = isManageMode,
                    isSelectedProvider = { isSelectedProvider(item.id) },
                    onClick = {
                        if (isManageMode) {
                            onToggleSelect(item.id)
                        } else {
                            onNavigateToAlbum(item.actualDetailId)
                        }
                    },
                    onLongClick = { onToggleSelect(item.id) },
                    onCoverLoadFailed = {
                        onCoverLoadFailed(item.id, item.actualDetailId, UserHistoryCategory.ALBUM)
                    }
                )
            }
        }

        if (tabState.isLoadingMore) {
            item(span = StaggeredGridItemSpan.FullLine, key = "footer_album_loading_more", contentType = "loading_footer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryDateHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(top = 10.dp, bottom = 4.dp, start = 2.dp, end = 2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HobbyHistoryGridCard(
    item: UserHistoryItem,
    isManageMode: Boolean,
    isSelectedProvider: () -> Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCoverLoadFailed: () -> Unit,
    modifier: Modifier = Modifier
) {

    val isSelected = isSelectedProvider()

    PoiPosterCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        aspectRatio = 9f / 13f,
        selectionOverlay = if (isManageMode) {
            {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (isSelected) 0.45f else 0.25f))
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .then(
                            if (isSelected) {
                                Modifier.background(MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .border(1.5.dp, Color.White, CircleShape)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            painter = painterResource(PoiIcons.Filled.Check),
                            contentDescription = "已选择",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else null,
        cover = {
            PoiAsyncImage(
                path = item.actualCover,
                category = MediaCategory.HOBBY,
                purpose = ImagePurpose.CARD_STANDARD,
                contentDescription = item.actualTitle,
                modifier = Modifier.fillMaxSize(),

                onError = onCoverLoadFailed
            )
        }
    )
}

@Composable
private fun AlbumHistoryWaterfallCard(
    item: UserHistoryItem,
    isManageMode: Boolean,
    isSelectedProvider: () -> Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCoverLoadFailed: () -> Unit,
    modifier: Modifier = Modifier
) {

    val isSelected = isSelectedProvider()

    PoiCaptionGridCard(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        cover = {

            PoiAsyncImage(
                path = item.actualCover,
                category = MediaCategory.ALBUM,
                purpose = ImagePurpose.ADAPTIVE_GRID,
                contentDescription = item.actualTitle,
                modifier = Modifier.fillMaxWidth(),
                adaptiveRatio = true,
                fallbackRatio = 1.0f,

                onError = onCoverLoadFailed
            )
        },
        caption = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                val displayTitle = item.actualTitle.ifBlank { "相册图集" }
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))

                val author = item.parsedUser
                val authorName = author?.authorNickname?.ifBlank { null }
                val authorAvatar = author?.authorAvatar?.ifBlank { null }

                if (authorName != null || authorAvatar != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UserAvatar(
                            avatarUrl = authorAvatar,
                            size = 18.dp,
                            contentDescription = authorName
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = authorName ?: "胶友",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        overlay = if (isManageMode) {
            {

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = if (isSelected) 0.45f else 0.25f))
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .then(
                            if (isSelected) {
                                Modifier.background(MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier
                                    .background(Color.Black.copy(alpha = 0.35f))
                                    .border(1.5.dp, Color.White, CircleShape)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            painter = painterResource(PoiIcons.Filled.Check),
                            contentDescription = "已选择",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else null
    )
}

@Composable
private fun HistoryEmptyView(
    category: UserHistoryCategory,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(PoiIcons.Filled.History),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "暂无${category.label}浏览记录",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "在手办和相册详情页浏览时，足迹将自动漫游记录于此",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}
