package com.nobody.repoi.ui.zone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.data.remote.model.ResaleSearchItem
import com.nobody.repoi.data.remote.model.ZoneEntityItem
import com.nobody.repoi.data.remote.model.isHobbyAdvancedFilterActive
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.platform.isLandscapeOrientation
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.board.ZoneBoardHobbySheets
import com.nobody.repoi.ui.board.ZoneBoardItemActions
import com.nobody.repoi.ui.board.ZoneBoardPane
import com.nobody.repoi.ui.board.components.ZoneBoardFilterBar
import com.nobody.repoi.ui.board.rememberResaleStaggeredGridColumns
import com.nobody.repoi.ui.board.session.ZoneBoardIntent
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import com.nobody.repoi.ui.common.rememberSynchronousTopAppBarScrollBehavior
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.home.rememberFeedStaggeredGridColumns
import com.nobody.repoi.ui.navigation.CharacterNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.resale.components.ResaleMoreBottomSheet
import com.nobody.repoi.ui.resale.components.ResaleReportDialog
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam
import com.nobody.repoi.ui.zone.components.ZonePublishSheet
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneDetailScreen(
    nodeId: Long,
    initialName: String? = null,
    initialIcon: String? = null,
    initialCover: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: ((DetailNavParam) -> Unit)? = null,
    onNavigateToAlbum: ((AlbumNavParam) -> Unit)? = null,
    onNavigateToPerson: ((PersonNavParam) -> Unit)? = null,
    onNavigateToWorks: ((WorksNavParam) -> Unit)? = null,
    onNavigateToVendor: ((VendorNavParam) -> Unit)? = null,
    onNavigateToCharacter: ((CharacterNavParam) -> Unit)? = null,
    onNavigateToSeries: ((SeriesNavParam) -> Unit)? = null,
    onNavigateToUser: ((Long) -> Unit)? = null,
    onNavigateToPost: ((Long, Long?, String?) -> Unit)? = null,
    onNavigateToResaleDetail: ((Long) -> Unit)? = null,
    onNavigateToResaleMarket: ((Long, String?) -> Unit)? = null,
    onActionClick: ((ActionItem) -> Unit)? = null,

    onUrlClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ZoneDetailViewModel = koinViewModel()
) {
    LaunchedEffect(nodeId) {
        viewModel.setNodeId(nodeId, initialName, initialIcon, initialCover)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeSnapshot by viewModel.activeSnapshot.collectAsStateWithLifecycle()
    val categoryTree by viewModel.categoryTree.collectAsStateWithLifecycle()
    val staggeredGridState = rememberLazyStaggeredGridState()
    val pullRefreshState = rememberPullToRefreshState()
    val messages = LocalAppMessages.current
    val focusManager = LocalFocusManager.current
    val imageViewer = LocalImageViewer.current

    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val windowSizeClass = adaptiveInfo.windowSizeClass
    val isLandscape = isLandscapeOrientation()
    val isWideScreen = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomContentPadding = if (!isWideScreen) 16.dp else (16.dp + navBarBottomPadding)

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val isHobbyOrResale = activeSnapshot.board == ZoneBoard.Hobby || activeSnapshot.board == ZoneBoard.Resale
    val defaultPinnedPx = with(density) { (if (isHobbyOrResale) 96.dp else 48.dp).toPx() }
    val defaultCollapsiblePx = with(density) { (if (activeSnapshot.board.isEntity) 0.dp else 48.dp).toPx() }
    var pinnedHeaderHeightPx by remember { mutableFloatStateOf(defaultPinnedPx) }
    var collapsibleHeightPx by remember { mutableFloatStateOf(defaultCollapsiblePx) }
    val pinnedHeaderHeightDp = with(density) { pinnedHeaderHeightPx.toDp() }
    val pinnedEstimatedDp = if (isHobbyOrResale) 96.dp else 48.dp
    val collapsibleEstimatedDp = if (activeSnapshot.board.isEntity) 0.dp else 48.dp
    val collapsibleMeasuredDp = with(density) { collapsibleHeightPx.toDp() }
    val totalHeaderHeightDp = (if (pinnedHeaderHeightDp > pinnedEstimatedDp) pinnedHeaderHeightDp else pinnedEstimatedDp) +
        if (collapsibleMeasuredDp > collapsibleEstimatedDp) collapsibleMeasuredDp else collapsibleEstimatedDp

    val currentTabTotalItemsCount = when (val c = activeSnapshot.content) {
        is ZoneBoardContent.Action -> c.items.size
        is ZoneBoardContent.Album -> c.items.size
        is ZoneBoardContent.Posts -> c.items.size + c.pinnedPosts.size
        is ZoneBoardContent.Hobby -> c.items.size
        is ZoneBoardContent.Resale -> c.items.size
        is ZoneBoardContent.Entity -> c.items.size
    }

    val scrollBehavior = rememberSynchronousTopAppBarScrollBehavior(
        initialHeightOffsetLimit = -collapsibleHeightPx,
        canScroll = { currentTabTotalItemsCount > 0 }
    )

    LaunchedEffect(collapsibleHeightPx) {
        scrollBehavior.state.heightOffsetLimit = -collapsibleHeightPx
        if (collapsibleHeightPx == 0f) {
            scrollBehavior.state.heightOffset = 0f
        }
    }

    val feedColumns = rememberFeedStaggeredGridColumns()
    val entityColumns = remember(windowSizeClass, isLandscape) {
        when {
            windowSizeClass.isWidthAtLeastBreakpoint(1200) || (isLandscape && windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)) -> StaggeredGridCells.Fixed(3)
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) -> StaggeredGridCells.Fixed(2)
            else -> StaggeredGridCells.Fixed(1)
        }
    }
    val resaleColumns = rememberResaleStaggeredGridColumns()
    val gridColumns = when {
        activeSnapshot.board.isEntity -> entityColumns
        activeSnapshot.board == ZoneBoard.Resale -> resaleColumns
        else -> feedColumns
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalCount = staggeredGridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = staggeredGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalCount > 0 && lastVisibleIndex >= totalCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore, currentTabTotalItemsCount, activeSnapshot.board) {
        if (shouldLoadMore) {
            val page = activeSnapshot.pageView
            if (!page.isEndReached && !page.isLoadingMore && page.loadMoreError == null && !page.isLoading) {
                viewModel.session.dispatch(ZoneBoardIntent.LoadMore())
            }
        }
    }

    LaunchedEffect(staggeredGridState, activeSnapshot.board) {
        snapshotFlow {
            Pair(staggeredGridState.firstVisibleItemIndex, staggeredGridState.firstVisibleItemScrollOffset)
        }.collect { (index, offset) ->
            if (staggeredGridState.layoutInfo.totalItemsCount > 0) {
                viewModel.session.dispatch(ZoneBoardIntent.SaveScrollPosition(activeSnapshot.board, index, offset))
            }
        }
    }

    var lastSelectedBoard by rememberSaveable { mutableStateOf<ZoneBoard?>(null) }
    LaunchedEffect(activeSnapshot.board) {
        val isInitial = lastSelectedBoard == null
        val isBoardChanged = !isInitial && lastSelectedBoard != activeSnapshot.board

        if (isBoardChanged) {
            val saved = activeSnapshot.scrollPosition
            staggeredGridState.scrollToItem(saved.index, saved.offset)
            scrollBehavior.state.heightOffset = -saved.filterCollapseFraction * collapsibleHeightPx
            scrollBehavior.state.contentOffset = 0f
        }
        lastSelectedBoard = activeSnapshot.board
    }

    var hasRestoredCurrentTabPosition by remember(activeSnapshot.board) { mutableStateOf(false) }
    LaunchedEffect(activeSnapshot.board, currentTabTotalItemsCount > 0) {
        if (!hasRestoredCurrentTabPosition && currentTabTotalItemsCount > 0) {
            hasRestoredCurrentTabPosition = true
            val saved = activeSnapshot.scrollPosition
            if (saved.index > 0 || saved.offset > 0) {
                if (staggeredGridState.firstVisibleItemIndex == 0) {
                    staggeredGridState.scrollToItem(saved.index, saved.offset)
                }
            }
        }
    }

    val onCardImageClick = remember(imageViewer) {
        { rawUrls: List<String>, initialIndex: Int ->
            imageViewer.show(rawUrls, initialIndex)
        }
    }

    val handleAlbumClick = remember(onNavigateToAlbum) {
        { item: AlbumListItem ->
            if (item.targetAlbumId > 0) {
                onNavigateToAlbum?.invoke(
                    AlbumNavParam(
                        albumId = item.targetAlbumId,
                        initialTitle = item.displayName.ifBlank { null },
                        initialCover = item.cover
                    )
                )
            }
        }
    }

    val handlePostClick = remember(onNavigateToPost) {
        { item: PostItem ->
            val targetItemId = if (item.itemId > 0L) item.itemId else item.id
            val targetNodeId = if (item.id > 0L) item.id else null
            if (onNavigateToPost != null && targetItemId > 0L) {
                onNavigateToPost(targetItemId, targetNodeId, item.title.ifBlank { null })
            } else {
                messages.show("帖子详情功能即将上线")
            }
        }
    }

    val handleHobbyClick = remember(onNavigateToDetail) {
        { item: HobbyItemSummary ->
            val actualId = item.actualItemId
            if (actualId > 0L && onNavigateToDetail != null) {
                onNavigateToDetail(
                    DetailNavParam(
                        itemId = actualId,
                        initialName = item.displayName,
                        initialCover = item.cover
                    )
                )
            }
        }
    }

    val handleEntityClick = remember(
        onNavigateToVendor,
        onNavigateToSeries,
        onNavigateToWorks,
        onNavigateToCharacter,
        onNavigateToPerson
    ) {
        { board: ZoneBoard, item: ZoneEntityItem ->
            val targetId = item.targetId
            when (board) {
                ZoneBoard.Company -> {
                    onNavigateToVendor?.invoke(
                        VendorNavParam(
                            companyId = targetId,
                            companyNodeId = if (item.id > 0L) item.id else 0L,
                            initialName = item.displayName,
                            initialCover = item.cover
                        )
                    )
                }
                ZoneBoard.Series -> {
                    onNavigateToSeries?.invoke(
                        SeriesNavParam(
                            seriesId = targetId,
                            initialName = item.displayName,
                            initialCover = item.cover
                        )
                    )
                }
                ZoneBoard.Works -> {
                    onNavigateToWorks?.invoke(
                        WorksNavParam(
                            worksId = targetId,
                            initialName = item.displayName,
                            initialCover = item.cover
                        )
                    )
                }
                ZoneBoard.Character -> {
                    onNavigateToCharacter?.invoke(
                        CharacterNavParam(
                            characterId = targetId,
                            initialName = item.displayName,
                            initialCover = item.cover
                        )
                    )
                }
                ZoneBoard.Person -> {
                    onNavigateToPerson?.invoke(
                        PersonNavParam(
                            personId = targetId,
                            initialName = item.displayName,
                            initialCover = item.cover
                        )
                    )
                }
                else -> Unit
            }
        }
    }

    var activeMoreResaleItem by remember { mutableStateOf<ResaleSearchItem?>(null) }
    var reportResaleTargetItem by remember { mutableStateOf<ResaleSearchItem?>(null) }

    val handleResaleClick = remember(onNavigateToDetail, onNavigateToResaleDetail) {
        { item: ResaleSearchItem ->
            if (item.isBuy) {
                val targetHobbyId = item.targetItemId
                if (targetHobbyId > 0L && onNavigateToDetail != null) {
                    onNavigateToDetail(DetailNavParam(itemId = targetHobbyId))
                } else {
                    messages.show("未关联条目详情")
                }
            } else {
                if (onNavigateToResaleDetail != null) {
                    onNavigateToResaleDetail(item.id)
                } else {
                    messages.show("二手出售详情即将上线")
                }
            }
        }
    }

    val handleResaleContactClick = remember {
        { _: ResaleSearchItem ->
            messages.show("私信功能即将上线")
        }
    }

    val boardItemActions = remember(
        onActionClick,
        onCardImageClick,
        handlePostClick,
        handleAlbumClick,
        handleResaleClick,
        handleResaleContactClick,
        onNavigateToUser,
        handleEntityClick,
        handleHobbyClick,
        onUrlClick
    ) {
        ZoneBoardItemActions(
            onActionClick = onActionClick,
            onActionImageClick = onCardImageClick,
            onPostClick = handlePostClick,
            onPostImageClick = onCardImageClick,
            onPostUserClick = onNavigateToUser,
            onAlbumClick = handleAlbumClick,
            onResaleClick = handleResaleClick,
            onResaleContactClick = handleResaleContactClick,
            onResaleUserClick = onNavigateToUser,
            onResaleMoreClick = { item -> activeMoreResaleItem = item },
            onHobbyClick = handleHobbyClick,
            onEntityClick = { board, item -> handleEntityClick(board, item) },
            onUrlClick = onUrlClick
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.zoneInfo?.displayName ?: initialName ?: "专区详情",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${uiState.followCount} 人已关注",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(PoiIcons.AutoMirrored.Filled.ArrowBack),
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    val isFocusToggling = uiState.isFocusToggling
                    val focusState = uiState.focusState

                    if (focusState) {
                        OutlinedButton(
                            onClick = { viewModel.toggleFocus() },
                            enabled = !isFocusToggling,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .padding(end = 8.dp)
                        ) {
                            if (isFocusToggling) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "已关注",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = { viewModel.toggleFocus() },
                            enabled = !isFocusToggling,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .padding(end = 8.dp)
                        ) {
                            if (isFocusToggling) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = "+ 关注",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showPublishSheet(true) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    painter = painterResource(PoiIcons.Filled.Add),
                    contentDescription = "发布内容到专区"
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isRefreshing = (activeSnapshot.pageView.isLoading && currentTabTotalItemsCount > 0) || uiState.isInfoLoading

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        staggeredGridState.scrollToItem(0)
                        scrollBehavior.state.heightOffset = 0f
                        scrollBehavior.state.contentOffset = 0f
                    }
                    viewModel.refresh()
                },
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullRefreshState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = totalHeaderHeightDp)
                    )
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                ) {
                    val isCurrentTabLoading = activeSnapshot.pageView.isLoading && currentTabTotalItemsCount == 0

                    if (isCurrentTabLoading) {
                        PoiLoadingView(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    top = totalHeaderHeightDp + 8.dp,
                                    bottom = bottomContentPadding
                                )
                        )
                    } else {
                        ZoneBoardPane(
                            board = activeSnapshot.board,
                            content = activeSnapshot.content,
                            gridState = staggeredGridState,
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                end = 8.dp,
                                top = totalHeaderHeightDp + 8.dp,
                                bottom = bottomContentPadding
                            ),
                            onIntent = viewModel.session::dispatch,
                            itemActions = boardItemActions,
                            columns = gridColumns,
                            showPinnedPosts = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = pinnedHeaderHeightDp)
                    .offset { IntOffset(x = 0, y = scrollBehavior.state.heightOffset.roundToInt()) }
                    .onSizeChanged { size ->
                        collapsibleHeightPx = size.height.toFloat()
                    }
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                ZoneBoardFilterBar(
                    board = activeSnapshot.board,
                    filters = activeSnapshot.filters,
                    subTypeOptions = viewModel.subTypes,
                    onIntent = viewModel.session::dispatch,
                    albumCategoryOptions = uiState.albumCategoryOptions,
                    hobbyCategoryTree = categoryTree,
                    hobbyIsGridMode = (activeSnapshot.content as? ZoneBoardContent.Hobby)?.isGridMode ?: true,
                    showHobbyControlActions = false
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        pinnedHeaderHeightPx = size.height.toFloat()
                    }
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                val selectedTabIndex = uiState.availableTabs.indexOfFirst { it.first == activeSnapshot.board.type }
                    .coerceAtLeast(0)
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    uiState.availableTabs.forEachIndexed { index, (tabType, tabTitle) ->
                        val isSelected = index == selectedTabIndex
                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.selectTab(tabType) },
                            text = {
                                Text(
                                    text = tabTitle,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                when (activeSnapshot.board) {
                    ZoneBoard.Hobby -> {
                        val hobbyFilter = activeSnapshot.filters.hobby
                        val isGridMode = (activeSnapshot.content as? ZoneBoardContent.Hobby)?.isGridMode ?: true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(PoiIcons.Filled.Search),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    BasicTextField(
                                        value = hobbyFilter.keyword,
                                        onValueChange = { viewModel.updateHobbyKeyword(it) },
                                        singleLine = true,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                focusManager.clearFocus()
                                                viewModel.submitHobbySearch()
                                            }
                                        ),
                                        decorationBox = { innerTextField ->
                                            if (hobbyFilter.keyword.isEmpty()) {
                                                Text(
                                                    text = "在专区产品中搜索",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                            innerTextField()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (hobbyFilter.keyword.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                viewModel.updateHobbyKeyword("")
                                                viewModel.submitHobbySearch()
                                            },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(PoiIcons.Filled.Close),
                                                contentDescription = "清空搜索",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            IconButton(
                                onClick = { viewModel.toggleHobbyViewMode() },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(if (isGridMode) PoiIcons.AutoMirrored.Filled.List else PoiIcons.Filled.GridView),
                                    contentDescription = if (isGridMode) "切换列表模式" else "切换网格模式",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { viewModel.openHobbyFilterSheet(true) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                val hasAdvancedFilters = isHobbyAdvancedFilterActive(
                                    sex = hobbyFilter.sex,
                                    specs = hobbyFilter.specs,
                                    scale = hobbyFilter.scale,
                                    r18 = hobbyFilter.r18
                                )
                                BadgedBox(
                                    badge = {
                                        if (hasAdvancedFilters) {
                                            Badge()
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(PoiIcons.Filled.FilterList),
                                        contentDescription = "高级筛选",
                                        tint = if (hasAdvancedFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    ZoneBoard.Resale -> {
                        val resaleKeyword = activeSnapshot.filters.resaleKeyword
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(PoiIcons.Filled.Search),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    BasicTextField(
                                        value = resaleKeyword,
                                        onValueChange = { viewModel.updateResaleKeyword(it) },
                                        singleLine = true,
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(
                                            onSearch = {
                                                focusManager.clearFocus()
                                                viewModel.submitResaleSearch()
                                            }
                                        ),
                                        decorationBox = { innerTextField ->
                                            if (resaleKeyword.isEmpty()) {
                                                Text(
                                                    text = "在二手交易中搜索",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                )
                                            }
                                            innerTextField()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (resaleKeyword.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                viewModel.updateResaleKeyword("")
                                                viewModel.submitResaleSearch()
                                            },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(PoiIcons.Filled.Close),
                                                contentDescription = "清空搜索",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    if (activeSnapshot.board == ZoneBoard.Hobby) {
        ZoneBoardHobbySheets(
            filters = activeSnapshot.filters,
            hobbyCategoryTree = categoryTree,
            onIntent = viewModel.session::dispatch
        )
    }

    if (uiState.showPublishSheet) {
        ZonePublishSheet(
            onDismiss = { viewModel.showPublishSheet(false) },
            onPublishPostClick = {
                messages.show("发布讨论贴功能即将上线")
            },
            onPublishAlbumClick = {
                messages.show("发布相册功能即将上线")
            },
            onPublishResaleClick = {
                messages.show("发布二手条目功能即将上线")
            }
        )
    }

    val moreResaleItem = activeMoreResaleItem
    if (moreResaleItem != null) {
        ResaleMoreBottomSheet(
            item = moreResaleItem,
            onDismiss = { activeMoreResaleItem = null },
            onSelectSameResale = { selectedItem ->
                val targetHobbyId = selectedItem.targetItemId
                val figureName = selectedItem.item?.firstOrNull()?.nameCN ?: selectedItem.item?.firstOrNull()?.name
                if (targetHobbyId > 0L) {
                    onNavigateToResaleMarket?.invoke(targetHobbyId, figureName)
                } else {
                    messages.show("未关联手办详情")
                }
            },
            onSelectReport = { reportResaleTargetItem = it }
        )
    }

    val reportItem = reportResaleTargetItem
    if (reportItem != null) {
        var isReporting by remember { mutableStateOf(false) }
        ResaleReportDialog(
            isSubmitting = isReporting,
            onDismiss = { if (!isReporting) reportResaleTargetItem = null },
            onSubmit = { reason ->
                isReporting = true
                viewModel.submitResaleReport(reportItem.id, reason) { success, msg ->
                    isReporting = false
                    messages.show(msg)
                    if (success) {
                        reportResaleTargetItem = null
                    }
                }
            }
        )
    }
}
