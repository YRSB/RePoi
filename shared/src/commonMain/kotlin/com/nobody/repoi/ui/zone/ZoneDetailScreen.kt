package com.nobody.repoi.ui.zone

import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.repoi.data.local.CategoryItem
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
import com.nobody.repoi.ui.board.boardFilterToken
import com.nobody.repoi.ui.board.components.ZoneBoardFilterBar
import com.nobody.repoi.ui.board.components.ZoneEmptyPlaceholder
import com.nobody.repoi.ui.board.components.ZoneErrorPlaceholder
import com.nobody.repoi.ui.board.itemCount
import com.nobody.repoi.ui.board.rememberResaleStaggeredGridColumns
import com.nobody.repoi.ui.board.session.ZoneBoardIntent
import com.nobody.repoi.ui.board.session.ZoneBoardSession
import com.nobody.repoi.ui.board.session.ZoneBoardSnapshot
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.PoiIcons
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import com.nobody.repoi.ui.common.rememberSynchronousTopAppBarScrollBehavior
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.home.HomeHeaderStackState
import com.nobody.repoi.ui.home.homeBoardMemoryKey
import com.nobody.repoi.ui.home.rememberFeedStaggeredGridColumns
import com.nobody.repoi.ui.home.rememberHomeHeaderStack
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
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

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

    val scope = rememberCoroutineScope()

    val boardItemCount = activeSnapshot.content.itemCount
    val isRefreshing = boardItemCount > 0 && (activeSnapshot.pageView.isLoading || uiState.isInfoLoading)

    val headerStack = rememberHomeHeaderStack(topBarHeight = 0.dp)
    val boardFilterHeights = remember { mutableStateMapOf<ZoneBoard, Float>() }

    val scrollBehavior = rememberSynchronousTopAppBarScrollBehavior(
        initialHeightOffsetLimit = -headerStack.collapsibleHeightPx,
        state = rememberTopAppBarState(
            initialHeightOffsetLimit = -headerStack.collapsibleHeightPx,
            initialHeightOffset = -activeSnapshot.scrollPosition.filterCollapseFraction * headerStack.collapsibleHeightPx
        ),
        canScroll = { boardItemCount > 0 }
    )

    LaunchedEffect(uiState.boards.isEmpty()) {
        if (uiState.boards.isEmpty()) headerStack.clearMeasuredRows()
    }

    LaunchedEffect(uiState.selectedBoard) {
        val board = uiState.selectedBoard
        if (board.isEntity) {
            headerStack.resetCollapsibleHeight()
        } else {
            boardFilterHeights[board]?.let { heightPx ->
                if (heightPx > 0f) headerStack.onCollapsibleMeasured(heightPx.roundToInt())
            }
        }
    }

    val currentFilterHeightPx: Float = if (uiState.selectedBoard.isEntity) {
        0f
    } else {
        boardFilterHeights[uiState.selectedBoard]
            ?: if (headerStack.collapsibleHeightPx > 0f) {
                headerStack.collapsibleHeightPx
            } else {
                with(LocalDensity.current) { 48.dp.toPx() }
            }
    }

    val isConfirmedEmptyOrError =
        (boardItemCount == 0 && !activeSnapshot.pageView.isLoading) || activeSnapshot.pageView.errorMessage != null
    LaunchedEffect(activeSnapshot.board, currentFilterHeightPx, isConfirmedEmptyOrError) {
        val fraction = if (isConfirmedEmptyOrError) {
            0f
        } else {
            activeSnapshot.scrollPosition.filterCollapseFraction
        }
        scrollBehavior.state.heightOffsetLimit = -currentFilterHeightPx
        scrollBehavior.state.contentOffset = 0f
        scrollBehavior.state.heightOffset =
            (-fraction * currentFilterHeightPx).coerceIn(-currentFilterHeightPx, 0f)
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

    val boards = uiState.boards
    val selectedBoardIndex = boards.indexOf(uiState.selectedBoard).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedBoardIndex) {
        boards.size.coerceAtLeast(1)
    }
    val pageGridStates = remember { mutableStateMapOf<ZoneBoard, LazyStaggeredGridState>() }

    val currentBoards by rememberUpdatedState(boards)
    val currentSelectedBoard by rememberUpdatedState(uiState.selectedBoard)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val board = currentBoards.getOrNull(page)
            if (board != null && board != currentSelectedBoard && !pagerState.isScrollInProgress) {
                viewModel.selectBoard(board)
            }
        }
    }

    LaunchedEffect(selectedBoardIndex, boards.size, pagerState.pageCount) {
        if (boards.isNotEmpty() &&
            selectedBoardIndex in 0 until pagerState.pageCount &&
            pagerState.currentPage != selectedBoardIndex
        ) {
            pagerState.scrollToPage(selectedBoardIndex)
        }
    }

    val scrollCurrentBoardToTop: suspend () -> Unit = {
        pageGridStates[uiState.selectedBoard]?.scrollToItem(0)
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
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
                                LoadingIndicator(
                                    modifier = Modifier.size(14.dp)
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
                                LoadingIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
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
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch { scrollCurrentBoardToTop() }
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
                            .padding(top = headerStack.totalHeaderHeight)
                    )
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                ) {
                    if (boards.isEmpty()) {
                        ZoneEmptyPlaceholder(
                            text = "该专区暂无可用板块",
                            onRefresh = { viewModel.refresh() },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(top = headerStack.totalHeaderHeight)
                                .padding(24.dp)
                        )
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            key = { index -> boards.getOrNull(index) ?: index },
                            beyondViewportPageCount = 0,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val board = boards.getOrNull(page) ?: return@HorizontalPager
                            val snapshot = if (board == activeSnapshot.board) activeSnapshot else viewModel.session.snapshotFor(board)
                            ZoneBoardPage(
                                board = board,
                                snapshot = snapshot,
                                session = viewModel.session,
                                uiState = uiState,
                                viewModel = viewModel,
                                headerStack = headerStack,
                                scrollBehavior = scrollBehavior,
                                columns = when {
                                    board.isEntity -> entityColumns
                                    board == ZoneBoard.Resale -> resaleColumns
                                    else -> feedColumns
                                },
                                bottomContentPadding = bottomContentPadding,
                                boardItemActions = boardItemActions,
                                categoryTree = categoryTree,
                                onFilterHeightMeasured = { heightPx ->
                                    boardFilterHeights[board] = heightPx.toFloat()
                                },
                                isCurrentProvider = { uiState.selectedBoard == board },
                                onGridStateReady = { boardKey, state -> pageGridStates[boardKey] = state },
                                focusManager = focusManager
                            )
                        }
                    }
                }
            }

            if (boards.isNotEmpty()) {
                val currentPage = pagerState.currentPage.coerceIn(0, boards.lastIndex)
                PrimaryScrollableTabRow(
                    selectedTabIndex = currentPage,
                    edgePadding = 16.dp,
                    divider = {},
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .onSizeChanged { size -> headerStack.onTabRowMeasured(size.height) }
                ) {
                    boards.forEachIndexed { index, board ->
                        val isSelected = index == currentPage
                        Tab(
                            selected = isSelected,
                            onClick = { viewModel.selectBoard(board) },
                            text = {
                                Text(
                                    text = uiState.boardTitle(board),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (uiState.selectedBoard == ZoneBoard.Hobby) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZoneBoardPage(
    board: ZoneBoard,
    snapshot: ZoneBoardSnapshot,
    session: ZoneBoardSession,
    uiState: ZoneDetailUiState,
    viewModel: ZoneDetailViewModel,
    headerStack: HomeHeaderStackState,
    scrollBehavior: TopAppBarScrollBehavior,
    columns: StaggeredGridCells,
    bottomContentPadding: Dp,
    boardItemActions: ZoneBoardItemActions,
    categoryTree: Map<Int, List<CategoryItem>>,
    onFilterHeightMeasured: (Int) -> Unit,
    isCurrentProvider: () -> Boolean,
    onGridStateReady: (ZoneBoard, LazyStaggeredGridState) -> Unit,
    focusManager: FocusManager
) {
    val gridState = rememberLazyStaggeredGridState()
    LaunchedEffect(gridState, board) { onGridStateReady(board, gridState) }

    val isCurrent = isCurrentProvider()
    val memoryKey = homeBoardMemoryKey(viewModel.zoneScope, board)
    val itemCount = snapshot.content.itemCount
    val errorMessage = snapshot.pageView.errorMessage
    val contentEmpty = itemCount == 0 && errorMessage == null && !snapshot.pageView.isLoading
    val errorEmpty = errorMessage != null && itemCount == 0
    var restoredKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(board, viewModel.zoneScope) {
        viewModel.ensureBoardLoaded(board)
    }

    var collapseFraction by remember(memoryKey) {
        mutableFloatStateOf(snapshot.scrollPosition.filterCollapseFraction)
    }
    LaunchedEffect(itemCount, isCurrent, snapshot.pageView.isLoading) {
        if (isCurrent && itemCount == 0 && !snapshot.pageView.isLoading) collapseFraction = 0f
    }
    var filterRestored by remember(memoryKey) { mutableStateOf(false) }
    LaunchedEffect(memoryKey, isCurrent) {
        if (!isCurrent) {
            filterRestored = false
            return@LaunchedEffect
        }
        val targetFraction = snapshot.scrollPosition.filterCollapseFraction
        collapseFraction = targetFraction
        filterRestored = true
    }

    var filterBarHeightPx by rememberSaveable { mutableStateOf(0) }
    val density = LocalDensity.current
    val ownFilterHeight = if (board.isEntity) {
        0.dp
    } else if (filterBarHeightPx > 0) {
        with(density) { filterBarHeightPx.toDp() }
    } else {
        headerStack.collapsibleHeight
    }
    val topInset = headerStack.pinnedHeight + ownFilterHeight

    val shouldLoadMore by remember(gridState) {
        derivedStateOf {
            val totalCount = gridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalCount > 0 && lastVisibleIndex >= totalCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, isCurrent, snapshot.pageView.isEndReached, snapshot.pageView.isLoadingMore, snapshot.pageView.loadMoreError) {
        if (isCurrent && shouldLoadMore && !snapshot.pageView.isEndReached &&
            !snapshot.pageView.isLoadingMore && snapshot.pageView.loadMoreError == null
        ) {
            session.dispatch(ZoneBoardIntent.LoadMore(board))
        }
    }

    LaunchedEffect(memoryKey, itemCount > 0) {
        if (itemCount <= 0) return@LaunchedEffect
        if (restoredKey == memoryKey) return@LaunchedEffect
        val memory = snapshot.scrollPosition
        val inRange = memory.index in 0 until itemCount
        if (inRange && (memory.index != 0 || memory.offset != 0)) {
            gridState.scrollToItem(memory.index, memory.offset)
        } else {
            if (!inRange) session.dispatch(ZoneBoardIntent.ResetScrollMemory(board))
            gridState.scrollToItem(0)
        }
        restoredKey = memoryKey
    }

    LaunchedEffect(gridState, scrollBehavior.state, memoryKey) {
        snapshotFlow {
            val driverHeight = if (board.isEntity) {
                0f
            } else if (filterBarHeightPx > 0) {
                filterBarHeightPx.toFloat()
            } else {
                headerStack.collapsibleHeightPx
            }
            Triple(
                gridState.firstVisibleItemIndex,
                gridState.firstVisibleItemScrollOffset,
                if (driverHeight > 0f) {
                    (-scrollBehavior.state.heightOffset / driverHeight).coerceIn(0f, 1f)
                } else {
                    0f
                }
            )
        }.collect { (index, offset, filterCollapseFraction) ->
            if (!isCurrentProvider()) return@collect
            if (filterRestored) {
                collapseFraction = filterCollapseFraction
                session.dispatch(ZoneBoardIntent.SaveFilterCollapseFraction(board, filterCollapseFraction))
            }
            if (restoredKey == memoryKey) {
                session.dispatch(ZoneBoardIntent.SaveScrollPosition(board, index, offset))
            }
        }
    }

    val filterToken = boardFilterToken(snapshot.filters, board)
    var lastFilterToken by remember(memoryKey) { mutableStateOf(filterToken) }
    LaunchedEffect(filterToken, memoryKey) {
        if (filterToken == lastFilterToken) return@LaunchedEffect
        lastFilterToken = filterToken
        session.dispatch(ZoneBoardIntent.ResetScrollMemory(board))
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
        gridState.scrollToItem(0)
        restoredKey = memoryKey
    }

    val isRefreshing = snapshot.content.itemCount > 0 && snapshot.pageView.isLoading
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(isRefreshing) {
        if (wasRefreshing && !isRefreshing && errorMessage == null && isCurrentProvider()) {
            gridState.scrollToItem(0)
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
        wasRefreshing = isRefreshing
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            snapshot.pageView.isLoading && itemCount == 0 -> {
                PoiLoadingView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topInset, bottom = bottomContentPadding)
                )
            }
            errorEmpty -> {
                ZoneErrorPlaceholder(
                    message = errorMessage.ifBlank { "数据加载失败" },
                    onRetry = { if (isCurrent) session.dispatch(ZoneBoardIntent.RetryInitial(board)) },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = topInset)
                        .padding(24.dp)
                )
            }
            contentEmpty -> {
                ZoneEmptyPlaceholder(
                    text = board.emptyText,
                    onRefresh = { if (isCurrent) session.dispatch(ZoneBoardIntent.Refresh(board)) },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = topInset)
                        .padding(24.dp)
                )
            }
            else -> {
                ZoneBoardPane(
                    board = board,
                    content = snapshot.content,
                    gridState = gridState,
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        top = topInset + 6.dp,
                        bottom = bottomContentPadding
                    ),
                    onIntent = session::dispatch,
                    itemActions = boardItemActions,
                    columns = columns,
                    showPinnedPosts = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (!board.isEntity) {
            LaunchedEffect(filterBarHeightPx) {
                if (filterBarHeightPx > 0) onFilterHeightMeasured(filterBarHeightPx)
            }
            LaunchedEffect(isCurrent, filterBarHeightPx) {
                if (isCurrent && filterBarHeightPx > 0) {
                    headerStack.onCollapsibleMeasured(filterBarHeightPx)
                }
            }

            val filterRowScroll = remember { ScrollState(0) }
            var filterRowRestored by remember(memoryKey) { mutableStateOf(false) }
            LaunchedEffect(memoryKey, filterRowScroll.maxValue) {
                if (filterRowRestored) return@LaunchedEffect
                val savedX = snapshot.scrollPosition.filterRowScrollX
                if (savedX > 0 && filterRowScroll.maxValue <= 0) return@LaunchedEffect
                if (savedX > 0) filterRowScroll.scrollTo(savedX)
                filterRowRestored = true
            }
            LaunchedEffect(memoryKey, filterRowScroll) {
                snapshotFlow { filterRowScroll.value }.collect { scrollX ->
                    if (filterRowRestored) session.dispatch(ZoneBoardIntent.SaveFilterRowScroll(board, scrollX))
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = headerStack.pinnedHeight)
                    .offset {
                        val heightPx = if (filterBarHeightPx > 0) {
                            filterBarHeightPx
                        } else {
                            headerStack.collapsibleHeightPx.roundToInt()
                        }
                        val offsetY = if (isCurrentProvider() && filterRestored) {
                            scrollBehavior.state.heightOffset
                        } else {
                            -(collapseFraction * heightPx)
                        }
                        IntOffset(0, offsetY.roundToInt())
                    }
                    .onSizeChanged { filterBarHeightPx = it.height }
                    .clipToBounds()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (board == ZoneBoard.Hobby) {
                    val hobbyFilter = snapshot.filters.hobby
                    val isGridMode = (snapshot.content as? ZoneBoardContent.Hobby)?.isGridMode ?: true
                    val hasAdvancedFilters = isHobbyAdvancedFilterActive(
                        sex = hobbyFilter.sex,
                        specs = hobbyFilter.specs,
                        scale = hobbyFilter.scale,
                        r18 = hobbyFilter.r18
                    )
                    ZoneHobbySearchBar(
                        keyword = hobbyFilter.keyword,
                        isGridMode = isGridMode,
                        hasAdvancedFilters = hasAdvancedFilters,
                        onKeywordChange = { viewModel.updateHobbyKeyword(it) },
                        onSearch = {
                            focusManager.clearFocus()
                            viewModel.submitHobbySearch()
                        },
                        onToggleViewMode = { viewModel.toggleHobbyViewMode() },
                        onOpenFilterSheet = { viewModel.openHobbyFilterSheet(true) }
                    )
                } else if (board == ZoneBoard.Resale) {
                    val resaleKeyword = snapshot.filters.resaleKeyword
                    ZoneResaleSearchBar(
                        keyword = resaleKeyword,
                        onKeywordChange = { viewModel.updateResaleKeyword(it) },
                        onSearch = {
                            focusManager.clearFocus()
                            viewModel.submitResaleSearch()
                        }
                    )
                }

                ZoneBoardFilterBar(
                    board = board,
                    filters = snapshot.filters,
                    subTypeOptions = viewModel.subTypes,
                    onIntent = session::dispatch,
                    albumCategoryOptions = uiState.albumCategoryOptions,
                    hobbyCategoryTree = categoryTree,
                    hobbyIsGridMode = (snapshot.content as? ZoneBoardContent.Hobby)?.isGridMode ?: true,
                    showHobbyControlActions = false,
                    rowScrollState = filterRowScroll
                )
            }
        }
    }
}

@Composable
private fun ZoneHobbySearchBar(
    keyword: String,
    isGridMode: Boolean,
    hasAdvancedFilters: Boolean,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onToggleViewMode: () -> Unit,
    onOpenFilterSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
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
                    value = keyword,
                    onValueChange = onKeywordChange,
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    decorationBox = { innerTextField ->
                        if (keyword.isEmpty()) {
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
                if (keyword.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onKeywordChange("")
                            onSearch()
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
            onClick = onToggleViewMode,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(if (isGridMode) PoiIcons.AutoMirrored.Filled.List else PoiIcons.Filled.GridView),
                contentDescription = if (isGridMode) "切换列表模式" else "切换网格模式",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onOpenFilterSheet,
            modifier = Modifier.size(36.dp)
        ) {
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

@Composable
private fun ZoneResaleSearchBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
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
                    value = keyword,
                    onValueChange = onKeywordChange,
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    decorationBox = { innerTextField ->
                        if (keyword.isEmpty()) {
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
                if (keyword.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onKeywordChange("")
                            onSearch()
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
