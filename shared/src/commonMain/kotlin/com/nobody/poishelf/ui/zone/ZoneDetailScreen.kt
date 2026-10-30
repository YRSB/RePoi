package com.nobody.poishelf.ui.zone

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import com.nobody.poishelf.platform.isLandscapeOrientation
import com.nobody.poishelf.platform.LocalAppMessages
import com.nobody.poishelf.ui.home.components.ShimmerHost
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
import org.koin.compose.viewmodel.koinViewModel
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.poishelf.data.local.CategoryItem
import com.nobody.poishelf.data.remote.model.ActionItem
import com.nobody.poishelf.data.remote.model.AlbumListItem
import com.nobody.poishelf.data.remote.model.HobbyItemSummary
import com.nobody.poishelf.data.remote.model.PostItem
import com.nobody.poishelf.data.remote.model.ResaleSearchItem
import com.nobody.poishelf.data.remote.model.ZoneEntityItem
import com.nobody.poishelf.ui.album.AlbumNavParam
import com.nobody.poishelf.ui.album.components.AlbumFeedCard
import com.nobody.poishelf.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.poishelf.ui.common.LocalImageViewer
import com.nobody.poishelf.ui.common.rememberSynchronousTopAppBarScrollBehavior
import com.nobody.poishelf.ui.detail.DetailNavParam
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.nobody.poishelf.ui.home.components.ActionCard
import com.nobody.poishelf.ui.home.components.ActionCardSkeleton
import com.nobody.poishelf.ui.home.components.shimmerBackground
import com.nobody.poishelf.ui.home.rememberFeedStaggeredGridColumns
import com.nobody.poishelf.ui.item.list.components.CategoryPickerSheet
import com.nobody.poishelf.ui.item.list.components.HobbyAdvancedFilterParam
import com.nobody.poishelf.ui.item.list.components.HobbyFilterAdaptiveSheet
import com.nobody.poishelf.ui.item.list.components.HobbyGridCard
import com.nobody.poishelf.ui.item.list.components.HobbyGridCardSkeleton
import com.nobody.poishelf.ui.item.list.components.HobbyListCard
import com.nobody.poishelf.ui.item.list.components.HobbyListCardSkeleton
import com.nobody.poishelf.ui.navigation.CharacterNavParam
import com.nobody.poishelf.ui.person.PersonNavParam
import com.nobody.poishelf.ui.series.SeriesNavParam
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.ui.vendor.VendorNavParam
import com.nobody.poishelf.ui.works.WorksNavParam
import com.nobody.poishelf.ui.resale.components.ResaleMoreBottomSheet
import com.nobody.poishelf.ui.resale.components.ResaleReportDialog
import com.nobody.poishelf.ui.zone.components.ZoneEntityCard
import com.nobody.poishelf.ui.zone.components.ZoneEntityCardSkeleton
import com.nobody.poishelf.ui.zone.components.ZonePinnedPostsBar
import com.nobody.poishelf.ui.zone.components.ZonePostCard
import com.nobody.poishelf.ui.zone.components.ZonePostCardSkeleton
import com.nobody.poishelf.ui.zone.components.ZonePublishSheet
import com.nobody.poishelf.ui.zone.components.ZoneResaleCard
import com.nobody.poishelf.ui.zone.components.ZoneResaleCardSkeleton
import com.nobody.poishelf.util.FeedActionDispatcher
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class, kotlin.time.ExperimentalTime::class)
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
    modifier: Modifier = Modifier,
    viewModel: ZoneDetailViewModel = koinViewModel()
) {
    LaunchedEffect(nodeId) {
        viewModel.setNodeId(nodeId, initialName, initialIcon, initialCover)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    // 常驻吸顶层与折叠层高度：实测仅用于位移锚点与折叠上限，内容边距使用固定估高
    // 提供合理的初始默认值，防止首帧 measurement 前的 layout shift
    val defaultPinnedPx = with(density) { (if (uiState.isHobbyTab || uiState.isResaleTab) 96.dp else 48.dp).toPx() }
    val defaultCollapsiblePx = with(density) { (if (uiState.isEntityTab) 0.dp else 48.dp).toPx() }
    var pinnedHeaderHeightPx by remember { mutableFloatStateOf(defaultPinnedPx) }
    var collapsibleHeightPx by remember { mutableFloatStateOf(defaultCollapsiblePx) }
    // 实测常驻栏高度仅用于中层折叠栏位移锚点
    val pinnedHeaderHeightDp = with(density) { pinnedHeaderHeightPx.toDp() }
    // 内容边距固定估高，不回灌实测值
    val pinnedEstimatedDp = if (uiState.isHobbyTab || uiState.isResaleTab) 96.dp else 48.dp
    val collapsibleEstimatedDp = if (uiState.isEntityTab) 0.dp else 48.dp
    val collapsibleMeasuredDp = with(density) { collapsibleHeightPx.toDp() }
    val totalHeaderHeightDp = (if (pinnedHeaderHeightDp > pinnedEstimatedDp) pinnedHeaderHeightDp else pinnedEstimatedDp) +
        if (collapsibleMeasuredDp > collapsibleEstimatedDp) collapsibleMeasuredDp else collapsibleEstimatedDp

    val currentTabTotalItemsCount = when {
        uiState.isAlbumTab -> uiState.albumItems.size
        uiState.isPostsTab -> uiState.postsItems.size
        uiState.isHobbyTab -> uiState.hobbyState.items.size
        uiState.isResaleTab -> uiState.resaleState.items.size
        uiState.isEntityTab -> uiState.currentEntityState.items.size
        else -> uiState.items.size
    }

    val scrollBehavior = rememberSynchronousTopAppBarScrollBehavior(
        initialHeightOffsetLimit = -collapsibleHeightPx,
        canScroll = { currentTabTotalItemsCount > 0 }
    )

    // 折叠行程自动贴合实际高度
    LaunchedEffect(collapsibleHeightPx) {
        scrollBehavior.state.heightOffsetLimit = -collapsibleHeightPx
        if (collapsibleHeightPx == 0f) {
            scrollBehavior.state.heightOffset = 0f
        }
    }

    // 列数自适应
    val feedColumns = rememberFeedStaggeredGridColumns()
    val albumColumns = rememberAlbumStaggeredGridColumns()

    val entityColumns = remember(windowSizeClass, isLandscape) {
        when {
            windowSizeClass.isWidthAtLeastBreakpoint(1200) || (isLandscape && windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)) -> StaggeredGridCells.Fixed(3)
            windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) -> StaggeredGridCells.Fixed(2)
            else -> StaggeredGridCells.Fixed(1)
        }
    }

    val resaleColumns = remember(windowSizeClass, isLandscape) {
        when {
            !isLandscape -> StaggeredGridCells.Fixed(1)
            windowSizeClass.isWidthAtLeastBreakpoint(1200) -> StaggeredGridCells.Fixed(3)
            else -> StaggeredGridCells.Fixed(2)
        }
    }

    val hobbyColumns = remember(uiState.hobbyState.isGridMode) {
        if (uiState.hobbyState.isGridMode) {
            StaggeredGridCells.Adaptive(minSize = 120.dp)
        } else {
            StaggeredGridCells.Adaptive(minSize = 420.dp)
        }
    }

    val gridColumns = when {
        uiState.isAlbumTab -> albumColumns
        uiState.isPostsTab -> feedColumns
        uiState.isHobbyTab -> hobbyColumns
        uiState.isResaleTab -> resaleColumns
        uiState.isEntityTab -> entityColumns
        else -> feedColumns
    }

    // 触底翻页
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalCount = staggeredGridState.layoutInfo.totalItemsCount
            val lastVisibleIndex = staggeredGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalCount > 0 && lastVisibleIndex >= totalCount - 3
        }
    }

    LaunchedEffect(
        shouldLoadMore,
        uiState.items.size,
        uiState.albumItems.size,
        uiState.postsItems.size,
        uiState.hobbyState.items.size,
        uiState.resaleState.items.size,
        uiState.currentEntityState.items.size,
        uiState.selectedTab
    ) {
        if (shouldLoadMore) {
            when {
                uiState.isAlbumTab -> {
                    if (uiState.hasMoreAlbum && !uiState.isAlbumLoadingMore && uiState.albumLoadMoreError == null) {
                        viewModel.loadNextAlbumPage()
                    }
                }
                uiState.isPostsTab -> {
                    if (uiState.hasMorePosts && !uiState.isPostsLoadingMore && uiState.postsLoadMoreError == null) {
                        viewModel.loadNextPostsPage()
                    }
                }
                uiState.isHobbyTab -> {
                    val hobby = uiState.hobbyState
                    if (!hobby.isEndReached && !hobby.isLoadingMore && hobby.loadMoreError == null) {
                        viewModel.loadNextHobbyPage()
                    }
                }
                uiState.isResaleTab -> {
                    val resale = uiState.resaleState
                    if (!resale.isEndReached && !resale.isLoadingMore && resale.loadMoreError == null) {
                        viewModel.loadNextResalePage()
                    }
                }
                uiState.isEntityTab -> {
                    val cur = uiState.currentEntityState
                    if (!cur.isEndReached && !cur.isLoadingMore && cur.loadMoreError == null) {
                        viewModel.loadNextEntityPage(uiState.selectedTab)
                    }
                }
                uiState.selectedTab == "action" -> {
                    if (uiState.hasMore && !uiState.isLoadingMore && uiState.loadMoreError == null) {
                        viewModel.loadNextPage()
                    }
                }
            }
        }
    }

    // 实时记忆当前分类的滚动位置到 ViewModel
    LaunchedEffect(staggeredGridState, uiState.selectedTab) {
        snapshotFlow {
            Pair(staggeredGridState.firstVisibleItemIndex, staggeredGridState.firstVisibleItemScrollOffset)
        }.collect { (index, offset) ->
            if (staggeredGridState.layoutInfo.totalItemsCount > 0) {
                viewModel.saveTabScrollPosition(uiState.selectedTab, index, offset)
            }
        }
    }

    // 记录上一轮的筛选条件，确保从二级页面返回时【绝不】误触发回顶
    var lastSelectedTab by rememberSaveable { mutableStateOf<String?>(null) }
    var lastSelectedSubType by rememberSaveable { mutableStateOf<String?>(null) }
    var lastAlbumOrder by rememberSaveable { mutableStateOf<String?>(null) }
    var lastPostsOrder by rememberSaveable { mutableStateOf<String?>(null) }
    var lastHobbyOrder by rememberSaveable { mutableStateOf<String?>(null) }
    var lastHobbyCategoryId by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastHobbySubCategoryId by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastResaleType by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastResalePurity by rememberSaveable { mutableStateOf<String?>(null) }

    // 主动切换 Tab 时平滑恢复该 Tab 的记忆位置；主动切换过滤条件时滚回顶部并展开
    LaunchedEffect(
        uiState.selectedTab,
        uiState.selectedSubType,
        uiState.albumSelectedOrder,
        uiState.postsSelectedOrder,
        uiState.hobbyState.order,
        uiState.hobbyState.categoryId,
        uiState.hobbyState.subCategoryId,
        uiState.resaleState.type,
        uiState.resaleState.purity
    ) {
        val isInitial = lastSelectedTab == null
        val isTabChanged = lastSelectedTab != null && lastSelectedTab != uiState.selectedTab
        val isFilterChanged = !isInitial && !isTabChanged && (
            lastSelectedSubType != uiState.selectedSubType ||
            lastAlbumOrder != uiState.albumSelectedOrder ||
            lastPostsOrder != uiState.postsSelectedOrder ||
            lastHobbyOrder != uiState.hobbyState.order ||
            lastHobbyCategoryId != uiState.hobbyState.categoryId ||
            lastHobbySubCategoryId != uiState.hobbyState.subCategoryId ||
            lastResaleType != uiState.resaleState.type ||
            lastResalePurity != uiState.resaleState.purity
        )

        if (isTabChanged) {
            val (savedIndex, savedOffset) = viewModel.getTabScrollPosition(uiState.selectedTab)
            staggeredGridState.scrollToItem(savedIndex, savedOffset)
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        } else if (isFilterChanged) {
            viewModel.saveTabScrollPosition(uiState.selectedTab, 0, 0)
            staggeredGridState.scrollToItem(0)
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }

        lastSelectedTab = uiState.selectedTab
        lastSelectedSubType = uiState.selectedSubType
        lastAlbumOrder = uiState.albumSelectedOrder
        lastPostsOrder = uiState.postsSelectedOrder
        lastHobbyOrder = uiState.hobbyState.order
        lastHobbyCategoryId = uiState.hobbyState.categoryId
        lastHobbySubCategoryId = uiState.hobbyState.subCategoryId
        lastResaleType = uiState.resaleState.type
        lastResalePurity = uiState.resaleState.purity
    }

    // 保险兜底：当从二级页面返回恢复或数据就绪时，若处于第 0 项但 ViewModel 中有记忆位置，安全恢复
    var hasRestoredCurrentTabPosition by remember(uiState.selectedTab) { mutableStateOf(false) }

    LaunchedEffect(uiState.selectedTab, currentTabTotalItemsCount > 0) {
        if (!hasRestoredCurrentTabPosition && currentTabTotalItemsCount > 0) {
            hasRestoredCurrentTabPosition = true
            val (savedIndex, savedOffset) = viewModel.getTabScrollPosition(uiState.selectedTab)
            if (savedIndex > 0 || savedOffset > 0) {
                if (staggeredGridState.firstVisibleItemIndex == 0) {
                    staggeredGridState.scrollToItem(savedIndex, savedOffset)
                }
            }
        }
    }

    // 下拉刷新完成且无错误时滚回顶部并展开折叠层
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isAnyRefreshing) {
        if (wasRefreshing && !uiState.isAnyRefreshing && uiState.errorMessage == null && uiState.albumErrorMessage == null && uiState.postsErrorMessage == null) {
            staggeredGridState.scrollToItem(0)
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
        wasRefreshing = uiState.isAnyRefreshing
    }

    val onActionItemClick = remember(
        onNavigateToDetail, onNavigateToAlbum, onNavigateToVendor, onNavigateToCharacter,
        onNavigateToPerson, onNavigateToWorks, onNavigateToSeries, onNavigateToUser
    ) {
        { item: ActionItem ->
            FeedActionDispatcher.dispatch(
                actionItem = item,
                onMessage = { messages.show(it) },
                onNavigateToDetail = onNavigateToDetail,
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToVendor = onNavigateToVendor,
                onNavigateToCharacter = onNavigateToCharacter,
                onNavigateToPerson = onNavigateToPerson,
                onNavigateToWorks = onNavigateToWorks,
                onNavigateToSeries = onNavigateToSeries,
                onNavigateToUser = onNavigateToUser
            )
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
        uiState.selectedTab,
        onNavigateToVendor,
        onNavigateToSeries,
        onNavigateToWorks,
        onNavigateToCharacter,
        onNavigateToPerson
    ) {
        { item: ZoneEntityItem ->
            val targetId = item.targetId
            when (uiState.selectedTab) {
                "company" -> {
                    onNavigateToVendor?.invoke(
                        VendorNavParam(
                            companyId = targetId,
                            companyNodeId = if (item.id > 0L) item.id else 0L,
                            initialName = item.displayName,
                            initialCover = item.cover
                        )
                    )
                }
                "series" -> {
                    onNavigateToSeries?.invoke(
                        SeriesNavParam(
                            seriesId = targetId,
                            initialName = item.displayName,
                            initialCover = item.cover
                        )
                    )
                }
                "works" -> {
                    onNavigateToWorks?.invoke(
                        WorksNavParam(
                            worksId = targetId,
                            initialName = item.displayName,
                            initialCover = item.cover
                        )
                    )
                }
                "charactar" -> {
                    onNavigateToCharacter?.invoke(
                        CharacterNavParam(
                            characterId = targetId,
                            initialName = item.displayName,
                            initialCover = item.cover
                        )
                    )
                }
                "person" -> {
                    onNavigateToPerson?.invoke(
                        PersonNavParam(
                            personId = targetId,
                            initialName = item.displayName,
                            initialCover = item.cover
                        )
                    )
                }
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
        { item: ResaleSearchItem ->
            messages.show("私信功能即将上线")
        }
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                    imageVector = Icons.Filled.Add,
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
            // 1. 底层数据列表与下拉刷新主体（挂载嵌套协同滚动连接）
            PullToRefreshBox(
                isRefreshing = uiState.isAnyRefreshing,
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
                        isRefreshing = uiState.isAnyRefreshing,
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
                    LazyVerticalStaggeredGrid(
                        columns = gridColumns,
                        state = staggeredGridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = totalHeaderHeightDp + 8.dp,
                            bottom = bottomContentPadding
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalItemSpacing = 8.dp
                    ) {
                // 3. 内容流渲染列表
                when {
                    uiState.isAlbumTab -> {
                        // 分享 / 相册列表（骨架按固定类型复用）
                        if (uiState.isAlbumLoading && uiState.albumItems.isEmpty()) {
                            items(
                                8,
                                key = { index -> "zone_album_skeleton_$index" },
                                contentType = { "zone_album_skeleton" }
                            ) { index ->
                                ShimmerHost {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height((180 + (index % 3) * 30).dp)
                                            .clip(ExpressiveTokens.MediumCardShape)
                                            .shimmerBackground()
                                    )
                                }
                            }
                        } else if (uiState.albumErrorMessage != null && uiState.albumItems.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneErrorPlaceholder(
                                    message = uiState.albumErrorMessage ?: "加载分享失败",
                                    onRetry = { viewModel.loadInitialAlbum() }
                                )
                            }
                        } else if (uiState.albumItems.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneEmptyPlaceholder("暂无相关分享")
                            }
                        } else {
                            items(
                                items = uiState.albumItems,
                                key = { item -> "zone_album_${item.targetAlbumId}" },
                                contentType = { "album_feed" }
                            ) { item ->
                                AlbumFeedCard(
                                    item = item,
                                    showTime = true,
                                    onClick = handleAlbumClick
                                )
                            }

                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZonePaginationFooter(
                                    isLoadingMore = uiState.isAlbumLoadingMore,
                                    loadMoreError = uiState.albumLoadMoreError,
                                    isEndReached = !uiState.hasMoreAlbum,
                                    onRetry = { viewModel.loadNextAlbumPage() }
                                )
                            }
                        }
                    }
                    uiState.isPostsTab -> {
                        // 讨论贴列表（骨架按固定类型复用）
                        if (uiState.isPostsLoading && uiState.postsItems.isEmpty() && uiState.pinnedPosts.isEmpty()) {
                            items(
                                5,
                                key = { index -> "zone_post_skeleton_$index" },
                                contentType = { "zone_post_skeleton" }
                            ) {
                                ShimmerHost { ZonePostCardSkeleton() }
                            }
                        } else if (uiState.postsErrorMessage != null && uiState.postsItems.isEmpty() && uiState.pinnedPosts.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneErrorPlaceholder(
                                    message = uiState.postsErrorMessage ?: "加载讨论失败",
                                    onRetry = { viewModel.loadInitialPosts() }
                                )
                            }
                        } else if (uiState.postsItems.isEmpty() && uiState.pinnedPosts.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneEmptyPlaceholder("暂无相关讨论")
                            }
                        } else {
                            if (uiState.pinnedPosts.isNotEmpty()) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    ZonePinnedPostsBar(
                                        pinnedPosts = uiState.pinnedPosts,
                                        onPostClick = handlePostClick
                                    )
                                }
                            }

                            items(
                                items = uiState.postsItems,
                                key = { item -> "zone_post_${item.id}" },
                                contentType = { "zone_post" }
                            ) { post ->
                                ZonePostCard(
                                    post = post,
                                    onPostClick = handlePostClick,
                                    onImageClick = onCardImageClick,
                                    onUserClick = onNavigateToUser
                                )
                            }

                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZonePaginationFooter(
                                    isLoadingMore = uiState.isPostsLoadingMore,
                                    loadMoreError = uiState.postsLoadMoreError,
                                    isEndReached = !uiState.hasMorePosts,
                                    onRetry = { viewModel.loadNextPostsPage() }
                                )
                            }
                        }
                    }
                    uiState.isHobbyTab -> {
                        // 产品 (Hobby) 列表（骨架按固定类型复用）
                        val hobbyState = uiState.hobbyState
                        if (hobbyState.isLoading && hobbyState.items.isEmpty()) {
                            if (hobbyState.isGridMode) {
                                items(
                                    8,
                                    key = { index -> "zone_hobby_grid_skeleton_$index" },
                                    contentType = { "hobby_grid_skeleton" }
                                ) {
                                    ShimmerHost { HobbyGridCardSkeleton() }
                                }
                            } else {
                                items(
                                    6,
                                    key = { index -> "zone_hobby_list_skeleton_$index" },
                                    contentType = { "hobby_list_skeleton" }
                                ) {
                                    ShimmerHost { HobbyListCardSkeleton() }
                                }
                            }
                        } else if (hobbyState.errorMessage != null && hobbyState.items.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneErrorPlaceholder(
                                    message = hobbyState.errorMessage ?: "加载产品失败",
                                    onRetry = { viewModel.loadInitialHobby() }
                                )
                            }
                        } else if (hobbyState.items.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneEmptyPlaceholder("暂无相关产品")
                            }
                        } else {
                            if (hobbyState.isGridMode) {
                                items(
                                    items = hobbyState.items,
                                    key = { item -> "zone_hobby_grid_${item.actualItemId}" },
                                    contentType = { "hobby_grid" }
                                ) { item ->
                                    HobbyGridCard(
                                        item = item,
                                        onClick = { handleHobbyClick(item) }
                                    )
                                }
                            } else {
                                items(
                                    items = hobbyState.items,
                                    key = { item -> "zone_hobby_list_${item.actualItemId}" },
                                    contentType = { "hobby_list" }
                                ) { item ->
                                    HobbyListCard(
                                        item = item,
                                        onClick = { handleHobbyClick(item) }
                                    )
                                }
                            }

                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZonePaginationFooter(
                                    isLoadingMore = hobbyState.isLoadingMore,
                                    loadMoreError = hobbyState.loadMoreError,
                                    isEndReached = hobbyState.isEndReached,
                                    onRetry = { viewModel.loadNextHobbyPage() }
                                )
                            }
                        }
                    }
                    uiState.isResaleTab -> {
                        // 二手 (Resale) 列表（骨架按固定类型复用）
                        val resaleState = uiState.resaleState
                        if (resaleState.isLoading && resaleState.items.isEmpty()) {
                            items(
                                6,
                                key = { index -> "zone_resale_skeleton_$index" },
                                contentType = { "zone_resale_skeleton" }
                            ) {
                                ShimmerHost { ZoneResaleCardSkeleton() }
                            }
                        } else if (resaleState.errorMessage != null && resaleState.items.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneErrorPlaceholder(
                                    message = resaleState.errorMessage ?: "加载二手信息失败",
                                    onRetry = { viewModel.loadInitialResale() }
                                )
                            }
                        } else if (resaleState.items.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneEmptyPlaceholder("暂无相关二手条目")
                            }
                        } else {
                            items(
                                items = resaleState.items,
                                key = { item -> "zone_resale_${item.id}" },
                                contentType = { "zone_resale" }
                            ) { item ->
                                ZoneResaleCard(
                                    item = item,
                                    onClick = { handleResaleClick(item) },
                                    onUserClick = onNavigateToUser,
                                    onContactClick = { handleResaleContactClick(item) },
                                    onMoreClick = { activeMoreResaleItem = item }
                                )
                            }

                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZonePaginationFooter(
                                    isLoadingMore = resaleState.isLoadingMore,
                                    loadMoreError = resaleState.loadMoreError,
                                    isEndReached = resaleState.isEndReached,
                                    onRetry = { viewModel.loadNextResalePage() }
                                )
                            }
                        }
                    }
                    uiState.isEntityTab -> {
                        // 5 个实体分类 (厂商, 系列, 作品, 角色, 人物) 列表（骨架按固定类型复用）
                        val entityState = uiState.currentEntityState
                        if (entityState.isLoading && entityState.items.isEmpty()) {
                            items(
                                6,
                                key = { index -> "zone_entity_skeleton_$index" },
                                contentType = { "zone_entity_skeleton" }
                            ) {
                                ShimmerHost { ZoneEntityCardSkeleton() }
                            }
                        } else if (entityState.errorMessage != null && entityState.items.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneErrorPlaceholder(
                                    message = entityState.errorMessage ?: "加载内容失败",
                                    onRetry = { viewModel.loadInitialEntity(uiState.selectedTab) }
                                )
                            }
                        } else if (entityState.items.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneEmptyPlaceholder("暂无相关条目")
                            }
                        } else {
                            items(
                                items = entityState.items,
                                key = { item -> "zone_entity_${uiState.selectedTab}_${item.targetId}" },
                                contentType = { "zone_entity" }
                            ) { item ->
                                ZoneEntityCard(
                                    item = item,
                                    onClick = { handleEntityClick(item) }
                                )
                            }

                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZonePaginationFooter(
                                    isLoadingMore = entityState.isLoadingMore,
                                    loadMoreError = entityState.loadMoreError,
                                    isEndReached = entityState.isEndReached,
                                    onRetry = { viewModel.loadNextEntityPage(uiState.selectedTab) }
                                )
                            }
                        }
                    }
                    uiState.selectedTab == "action" -> {
                        // 动态流列表（骨架按固定类型复用）
                        if (uiState.isFeedLoading && uiState.items.isEmpty()) {
                            items(
                                6,
                                key = { index -> "zone_feed_skeleton_$index" },
                                contentType = { "feed_skeleton" }
                            ) {
                                ShimmerHost { ActionCardSkeleton() }
                            }
                        } else if (uiState.errorMessage != null && uiState.items.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneErrorPlaceholder(
                                    message = uiState.errorMessage ?: "加载动态失败",
                                    onRetry = { viewModel.refresh() }
                                )
                            }
                        } else if (uiState.items.isEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZoneEmptyPlaceholder("暂无相关动态")
                            }
                        } else {
                            items(
                                items = uiState.items,
                                key = { item -> "zone_feed_${item.id}_${item.actionDate}" },
                                contentType = { "feed_action" }
                            ) { item ->
                                ActionCard(
                                    actionItem = item,
                                    onItemClick = onActionItemClick,
                                    onImageClick = onCardImageClick
                                )
                            }

                            item(span = StaggeredGridItemSpan.FullLine) {
                                ZonePaginationFooter(
                                    isLoadingMore = uiState.isLoadingMore,
                                    loadMoreError = uiState.loadMoreError,
                                    isEndReached = !uiState.hasMore,
                                    onRetry = { viewModel.loadNextPage() }
                                )
                            }
                        }
                    }
                    else -> {
                        // 其他未开放分类占位
                        item(span = StaggeredGridItemSpan.FullLine) {
                            ZoneEmptyPlaceholder("该分类正在建设中...")
                        }
                    }
                }
            }
        }
    }

            // 2. 中层随动折叠筛选栏（吸附于常驻栏正下方，随列表滚动平滑折叠）
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
                when {
                    uiState.isAlbumTab -> {
                        // 分享/相册排序胶囊
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            viewModel.albumOrders.forEach { (key, label) ->
                                val isSelected = uiState.albumSelectedOrder == key
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectAlbumOrder(key) },
                                    label = { Text(label) },
                                    shape = ExpressiveTokens.ChipShape
                                )
                            }
                        }
                    }
                    uiState.isPostsTab -> {
                        // 讨论排序胶囊
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            viewModel.postsOrders.forEach { (key, label) ->
                                val isSelected = uiState.postsSelectedOrder == key
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectPostsOrder(key) },
                                    label = { Text(label) },
                                    shape = ExpressiveTokens.ChipShape
                                )
                            }
                        }
                    }
                    uiState.isHobbyTab -> {
                        val hobbyState = uiState.hobbyState
                        val categoryTree by viewModel.categoryTree.collectAsStateWithLifecycle()
                        val subCategories = remember(hobbyState.categoryId, categoryTree) {
                            viewModel.getSubCategories(hobbyState.categoryId)
                        }

                        // 下拉过滤胶囊行 (排序, 分类, 年份, 月份)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val orderOptions = listOf(
                                "hits7Day" to "7天热度",
                                "hitsDay" to "1天热度",
                                "hits" to "总热度",
                                "add" to "最新入库",
                                "release" to "发售时间",
                                "rating" to "综合评分"
                            )
                            var showOrderMenu by remember { mutableStateOf(false) }
                            Box {
                                val currentOrderLabel = orderOptions.find { it.first == hobbyState.order }?.second ?: "7天热度"
                                FilterDropdownChip(
                                    label = currentOrderLabel,
                                    isSelected = hobbyState.order != "hits7Day",
                                    isExpanded = showOrderMenu,
                                    onClick = { showOrderMenu = true }
                                )
                                DropdownMenu(
                                    expanded = showOrderMenu,
                                    onDismissRequest = { showOrderMenu = false },
                                    shape = ExpressiveTokens.MediumCardShape,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    orderOptions.forEach { (orderKey, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                            onClick = {
                                                viewModel.setHobbyOrder(orderKey)
                                                showOrderMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            val categoryChipLabel = when {
                                hobbyState.subCategoryId != null -> {
                                    subCategories.find { it.id == hobbyState.subCategoryId }?.name ?: "子分类"
                                }
                                hobbyState.categoryId != 10000 -> {
                                    val catMap = mapOf(100 to "手办", 200 to "动漫模型", 500 to "真实模型", 300 to "娃娃人偶", 400 to "毛绒布偶", 900 to "动漫周边")
                                    catMap[hobbyState.categoryId] ?: "分类"
                                }
                                else -> "分类"
                            }
                            val isCategoryActive = hobbyState.categoryId != 10000 || hobbyState.subCategoryId != null
                            FilterDropdownChip(
                                label = categoryChipLabel,
                                isSelected = isCategoryActive,
                                isExpanded = hobbyState.showCategoryPickerSheet,
                                onClick = { viewModel.openHobbyCategoryPicker(true) }
                            )

                            val currentYear = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
                            val yearOptions = listOf(null to "不限年") + ((currentYear + 2 downTo currentYear - 8).map { it to "${it}年" })
                            var showYearMenu by remember { mutableStateOf(false) }
                            Box {
                                val hasYear = (hobbyState.releaseYear ?: 0) > 0
                                FilterDropdownChip(
                                    label = if (hasYear) "${hobbyState.releaseYear}年" else "不限年",
                                    isSelected = hasYear,
                                    isExpanded = showYearMenu,
                                    onClick = { showYearMenu = true }
                                )
                                DropdownMenu(
                                    expanded = showYearMenu,
                                    onDismissRequest = { showYearMenu = false },
                                    shape = ExpressiveTokens.MediumCardShape,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    yearOptions.forEach { (year, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                            onClick = {
                                                viewModel.setHobbyYear(year)
                                                showYearMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            val monthOptions = listOf(null to "不限月") + ((1..12).map { it to "${it}月" })
                            var showMonthMenu by remember { mutableStateOf(false) }
                            Box {
                                val hasMonth = (hobbyState.releaseMonth ?: 0) > 0
                                FilterDropdownChip(
                                    label = if (hasMonth) "${hobbyState.releaseMonth}月" else "不限月",
                                    isSelected = hasMonth,
                                    isExpanded = showMonthMenu,
                                    onClick = { showMonthMenu = true }
                                )
                                DropdownMenu(
                                    expanded = showMonthMenu,
                                    onDismissRequest = { showMonthMenu = false },
                                    shape = ExpressiveTokens.MediumCardShape,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ) {
                                    monthOptions.forEach { (month, label) ->
                                        DropdownMenuItem(
                                            text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                            onClick = {
                                                viewModel.setHobbyMonth(month)
                                                showMonthMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    uiState.isResaleTab -> {
                        val resaleState = uiState.resaleState
                        // 出售/求购 & 成色选择胶囊
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            viewModel.resaleTypes.forEach { (typeKey, label) ->
                                val isSelected = resaleState.type == typeKey
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setResaleType(typeKey) },
                                    label = { Text(label) },
                                    shape = ExpressiveTokens.ChipShape
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            viewModel.resalePurities.forEach { (purityKey, label) ->
                                val isSelected = resaleState.purity == purityKey
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setResalePurity(purityKey) },
                                    label = { Text(label) },
                                    shape = ExpressiveTokens.ChipShape
                                )
                            }
                        }
                    }
                    uiState.selectedTab == "action" -> {
                        // 动态二级子分类胶囊
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            viewModel.subTypes.forEach { (key, label) ->
                                val isSelected = uiState.selectedSubType == key
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectSubType(key) },
                                    label = { Text(label) },
                                    shape = ExpressiveTokens.ChipShape
                                )
                            }
                        }
                    }
                }
            }

            // 3. 顶层常驻吸顶层（一级 Tab 栏 + 搜索控制栏），Z 轴最高，背景遮盖折叠层
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        pinnedHeaderHeightPx = size.height.toFloat()
                    }
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // 一级 Tab 栏（由服务端返回或默认映射驱动）
                val selectedTabIndex = uiState.availableTabs.indexOfFirst { it.first == uiState.selectedTab }
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

                // 常驻搜索与控制栏（仅在有搜索栏的 Tab 中展示）
                when {
                    uiState.isHobbyTab -> {
                        val hobbyState = uiState.hobbyState
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = ExpressiveTokens.PillShape,
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
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    BasicTextField(
                                        value = hobbyState.keyword,
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
                                            if (hobbyState.keyword.isEmpty()) {
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
                                    if (hobbyState.keyword.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                viewModel.updateHobbyKeyword("")
                                                viewModel.submitHobbySearch()
                                            },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
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
                                    imageVector = if (hobbyState.isGridMode) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView,
                                    contentDescription = if (hobbyState.isGridMode) "切换列表模式" else "切换网格模式",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { viewModel.openHobbyFilterSheet(true) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (hobbyState.hasAdvancedFilters) {
                                            Badge()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FilterList,
                                        contentDescription = "高级筛选",
                                        tint = if (hobbyState.hasAdvancedFilters) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    uiState.isResaleTab -> {
                        val resaleState = uiState.resaleState
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = ExpressiveTokens.PillShape,
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
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    BasicTextField(
                                        value = resaleState.keyword,
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
                                            if (resaleState.keyword.isEmpty()) {
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
                                    if (resaleState.keyword.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                viewModel.updateResaleKeyword("")
                                                viewModel.submitResaleSearch()
                                            },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
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
                }
            }
        }
    }

    // 4. 高级筛选底板 (Hobby)
    if (uiState.hobbyState.showFilterSheet) {
        val hobbyState = uiState.hobbyState
        HobbyFilterAdaptiveSheet(
            filterParam = HobbyAdvancedFilterParam(
                sex = hobbyState.sex,
                specs = hobbyState.specs,
                scale = hobbyState.scale,
                r18 = hobbyState.r18
            ),
            onDismiss = { viewModel.openHobbyFilterSheet(false) },
            onApply = { filterParam ->
                viewModel.applyHobbyAdvancedFilters(
                    sex = filterParam.sex,
                    specs = filterParam.specs,
                    scale = filterParam.scale,
                    r18 = filterParam.r18
                )
            },
            onReset = {
                viewModel.resetHobbyAdvancedFilters()
            }
        )
    }

    // 5. 分类级联选择底板 (Hobby)
    if (uiState.hobbyState.showCategoryPickerSheet) {
        CategoryPickerSheet(
            selectedCategoryId = uiState.hobbyState.categoryId,
            selectedSubCategoryId = uiState.hobbyState.subCategoryId,
            getSubCategories = { viewModel.getSubCategories(it) },
            onCategorySelected = { catId, subId ->
                viewModel.selectHobbyCategory(catId, subId)
            },
            onDismiss = { viewModel.openHobbyCategoryPicker(false) }
        )
    }

    // 6. 发布弹窗
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

    // 7. 二手卡片三点更多弹窗 (复用公共组件)
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

    // 8. 二手举报原因弹窗 (复用公共组件)
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

@Composable
private fun FilterDropdownChip(
    label: String,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "DropdownArrowRotation"
    )

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        shape = ExpressiveTokens.ChipShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = null
    )
}

@Composable
private fun ZoneEmptyPlaceholder(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ZoneErrorPlaceholder(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun ZonePaginationFooter(
    isLoadingMore: Boolean,
    loadMoreError: String?,
    isEndReached: Boolean,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoadingMore -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
            loadMoreError != null -> {
                OutlinedButton(onClick = onRetry) {
                    Text("加载更多失败，点击重试")
                }
            }
            isEndReached -> {
                Text(
                    text = "已经到底啦",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
