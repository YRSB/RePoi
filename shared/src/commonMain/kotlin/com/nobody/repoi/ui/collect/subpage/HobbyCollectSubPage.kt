package com.nobody.repoi.ui.collect.subpage

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.nobody.repoi.ui.common.rememberSynchronousTopAppBarScrollBehavior
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.remote.model.UserCollectHobbyItem
import com.nobody.repoi.ui.collect.CollectStateOption
import com.nobody.repoi.ui.collect.HobbyOrderOption
import com.nobody.repoi.ui.collect.UserCollectUiState
import com.nobody.repoi.ui.collect.UserCollectViewModel
import com.nobody.repoi.ui.collect.components.ErrorCollectView
import com.nobody.repoi.ui.collect.components.LoadingCollectView
import com.nobody.repoi.ui.collect.components.SelectionBottomBar
import com.nobody.repoi.ui.collect.components.renderPagingFooter
import com.nobody.repoi.ui.theme.ExpressiveTokens
import kotlinx.coroutines.launch

// ── 手办周边子页 ──────────────────────────────────────────

/**
 * 用户收藏「周边」子页（`UserCollectCategory.HOBBY`，key=10000）。
 *
 * 对齐原版 `UserCollectHobbyFragment` + `UserCollectHobbySubPagerFragment`：
 * - 6 档收藏状态胶囊（全部/关注/想买/预定/已入/有过）
 * - 5 档基础排序 + 「按入手时间」（仅已入档），升/降连体二选直接指定升降序
 * - 网格 3 列（宽度 ≥ 600dp 时 4 列）/ 列表恒定 1 列
 * - 列表卡 4 个微标签与网格卡徽章按 (collectState, isSelf) 自适应
 * - 长按进入多选，批量取消收藏需二次确认（仅本人可见）
 *
 * 多选与升降序相关的回调都带默认空实现，便于宿主渐进接线。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun HobbyCollectSubPage(
    uiState: UserCollectUiState,
    navBarBottomPadding: Dp,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onSwitchCollectState: (String) -> Unit,
    onSwitchOrder: (String) -> Unit,
    /** 切换升降序，入参为 [UserCollectUiState.SORT_ASC] 或 [UserCollectUiState.SORT_DESC] */
    onSwitchSortDirection: (Int) -> Unit = {},
    onOpenFilter: () -> Unit,
    onItemClick: (UserCollectHobbyItem) -> Unit,
    onRetry: () -> Unit,
    /** 选中/反选单条，入参必须是**外层收藏节点 id**（[UserCollectHobbyItem.id]） */
    onToggleSelect: (Long) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onCancelSelectedCollect: () -> Unit = {},
    onDismissBatchError: () -> Unit = {}
) {
    val hobbyData = uiState.hobbyData
    // 声明式绑定当前筛选与排序作为 State 标识键：
    // 切换状态、排序或子分类时，Compose 自动以全新的 initialFirstVisibleItemIndex = 0 创建状态，
    // 彻底消除异步网络加载与空列表重构导致的时序竞态问题。
    val gridState = rememberSaveable(
        uiState.collectState,
        uiState.order,
        uiState.sortType,
        uiState.subCategoryId,
        uiState.selectedTag,
        saver = LazyGridState.Saver
    ) {
        LazyGridState()
    }

    // 仅本人拥有写操作入口（对齐原版只在自己视角挂 onCancelCollect）
    val isSelectionEnabled = uiState.isSelf
    val isSelectionMode = isSelectionEnabled && uiState.hobbySelectionMode
    val selectedIds = uiState.hobbySelectedIds
    val selectedCount = selectedIds.size

    val density = LocalDensity.current
    var controlBarHeightPx by remember { mutableFloatStateOf(with(density) { 48.dp.toPx() }) }

    // 有数据即可折叠：只捕获一个 Boolean，避免分页把 items 换成新列表实例时
    // 连带重建 scrollBehavior（重建会替换 nestedScrollConnection，fling 中会丢派发点）。
    val itemsNonEmpty = hobbyData.items.isNotEmpty()

    // 对齐 HomeFeedScreen / FollowScreen 的无缝同步滚动行为：
    // 当非多选且有数据时，1:1 同步驱动控制栏平滑向上收起或即时拉出
    val scrollBehavior = rememberSynchronousTopAppBarScrollBehavior(
        initialHeightOffsetLimit = -controlBarHeightPx,
        canScroll = { !isSelectionMode && itemsNonEmpty }
    )

    LaunchedEffect(controlBarHeightPx, isSelectionMode) {
        if (!isSelectionMode && controlBarHeightPx > 0f) {
            scrollBehavior.state.heightOffsetLimit = -controlBarHeightPx
            scrollBehavior.state.heightOffset =
                scrollBehavior.state.heightOffset.coerceIn(-controlBarHeightPx, 0f)
        } else {
            scrollBehavior.state.heightOffsetLimit = 0f
            scrollBehavior.state.heightOffset = 0f
        }
    }

    val controlBarHeightDp = with(density) { controlBarHeightPx.toDp() }
    val pullRefreshState = rememberPullToRefreshState()
    val scope = rememberCoroutineScope()

    // 以 gridState 为 remember 键：筛选/排序变化时上面的 rememberSaveable 会重建 LazyGridState，
    // 键缺失的 derivedStateOf 会一直盯着已被丢弃的旧实例，导致切过筛选后触底分页永久失效。
    val shouldLoadMore by remember(gridState) {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 4
        }
    }

    // 附带 items.size 键（对齐 HomeFeedScreen:150 / FollowScreen:133）：
    // 宽屏一屏装下时触底判定恒为 true 不再变化，缺这个键就只会翻一页。
    LaunchedEffect(shouldLoadMore, hobbyData.items.size) {
        if (shouldLoadMore && hobbyData.hasMore && !hobbyData.isPaging && !hobbyData.isLoading && !hobbyData.isRefreshing) {
            onLoadNextPage()
        }
    }

    // 监听筛选/排序条件变动：将嵌套折叠控制栏平滑复位展开到顶格
    LaunchedEffect(
        uiState.collectState,
        uiState.order,
        uiState.sortType,
        uiState.subCategoryId,
        uiState.selectedTag
    ) {
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
    }

    // 刷新完成回落时同样复位（覆盖非下拉触发的程序化刷新，如批量取消收藏后的重新拉取），
    // 对齐 HomeFeedScreen:233-241 / FollowScreen:206-214。
    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(hobbyData.isRefreshing) {
        if (wasRefreshing && !hobbyData.isRefreshing && hobbyData.errorMessage == null) {
            gridState.scrollToItem(0)
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
        wasRefreshing = hobbyData.isRefreshing
    }

    // 列表卡微标签 / 网格卡徽章随 (collectState, isSelf) 重算
    val listFields = remember(uiState.collectState, uiState.isSelf) {
        hobbyListFields(uiState.collectState, uiState.isSelf)
    }
    val gridBadgeField = remember(uiState.collectState, uiState.isSelf) {
        hobbyGridBadgeField(uiState.collectState, uiState.isSelf)
    }

    // 列数策略对齐条目页「查看更多」(ui/item/list/HobbyListScreen)：
    // 网格 Adaptive(120.dp)、列表 Adaptive(420.dp)。
    //
    // 此前这里写死 Fixed(1)/Fixed(3)/Fixed(4)，与宽度无关，
    // 导致横屏下列表模式依旧只有 1 列。
    val gridColumns = if (uiState.isListView) {
        GridCells.Adaptive(minSize = 420.dp)
    } else {
        GridCells.Adaptive(minSize = 120.dp)
    }

    // 「全部周边」以 10000 表达而非 null，hasFilter 不能把「全部」误判为已筛选
    val hasFilter = (uiState.subCategoryId != null &&
        uiState.subCategoryId != UserCollectViewModel.DEFAULT_HOBBY_KEY) ||
        uiState.selectedTag != null

    var showCancelConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // ── 页根精确裁剪：本页「折叠控制栏钻进大类 TabRow 下方」的唯一保证 ──────────────
            // 本页宿主是 UserCollectScreen 的 HorizontalPager，页面上边界正好贴在大类 TabRow 下沿。
            // Pager 自带的裁剪来自 foundation 的 scrollableArea → clipScrollableContainer(Horizontal)，
            // 而 HorizontalScrollableClipShape 为了不切掉卡片阴影，在纵向额外外扩
            // MaxSupportedElevation（30dp，见 foundation ClipScrollableContainer.kt:62-80）：
            // 也就是说控制栏上移时，只有超出页顶 30dp 以外的部分才会被裁掉，剩下约 30dp 会画在
            // 宿主 TabRow 之上——这就是"控制栏盖住大类 TabRow"的直接成因。
            // 动态页/关注页没有这个问题：它们的常驻 TabRow 是同一个 Box 里更晚绘制的兄弟节点，
            // 靠 Z 序用不透明 Surface 盖住上移的筛选栏。本页没有这层兄弟，故必须由页根裁剪承担
            // 同等语义——裁到自身精确边界（不外扩），控制栏一越过页顶即消失。
            .clipToBounds()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. 底层：内容网格与下拉刷新容器（铺满整屏，通过 contentPadding 避让顶层控制栏）
        PullToRefreshBox(
            isRefreshing = hobbyData.isRefreshing,
            onRefresh = {
                // 对齐 HomeFeedScreen / FollowScreen：刷新前把列表位置与控制栏折叠态一起复位，
                // 否则刷新结束后会停在半折叠位，顶栏回不来、首项上还压着一截空白。
                scope.launch {
                    gridState.scrollToItem(0)
                    scrollBehavior.state.heightOffset = 0f
                    scrollBehavior.state.contentOffset = 0f
                }
                onRefresh()
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = hobbyData.isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = controlBarHeightDp + 4.dp)
                )
            }
        ) {
            // 嵌套滚动连接只包住可滚内容，且必须挂在 PullToRefreshBox 内部
            //（对齐 HomeFeedScreen:270-274 / FollowScreen:305-309 / AlbumScreen:236-239）：
            // 1. 连接越靠近 LazyVerticalGrid，协同滚动就越先于下拉刷新节点派发，下拉手势不会被
            //    刷新容器抢先消费，顶栏才能与列表 1:1 跟手；
            // 2. 顶层控制栏（含 DropdownMenu 内部的可滚 Column）不再落在连接子树内，
            //    菜单滚动不会误驱动折叠。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            ) {
                when {
                    hobbyData.isLoading && hobbyData.items.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = controlBarHeightDp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingCollectView()
                        }
                    }

                    hobbyData.errorMessage != null && hobbyData.items.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = controlBarHeightDp),
                            contentAlignment = Alignment.Center
                        ) {
                            ErrorCollectView(
                                errorMessage = hobbyData.errorMessage,
                                onRetry = onRetry
                            )
                        }
                    }

                    // 服务端回「用户已隐藏内容」语义：渲染专用空态而非通用错误态
                    // 副标题复用全模块统一常量（原版 strings.xml:1372 text_empty_user_hide）
                    hobbyData.isContentHidden && hobbyData.items.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = controlBarHeightDp),
                            contentAlignment = Alignment.Center
                        ) {
                            HobbyEmptyState(
                                icon = Icons.Filled.VisibilityOff,
                                title = "用户已隐藏内容",
                                subtitle = COLLECT_CONTENT_HIDDEN_TEXT
                            )
                        }
                    }

                    hobbyData.items.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = controlBarHeightDp),
                            contentAlignment = Alignment.Center
                        ) {
                            HobbyEmptyState(
                                icon = Icons.Filled.Inbox,
                                title = "未收藏周边~",
                                subtitle = "可以在详情页将感兴趣的条目加入收藏"
                            )
                        }
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = gridColumns,
                            state = gridState,
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                top = controlBarHeightDp + 8.dp,
                                end = 12.dp,
                                bottom = 16.dp + navBarBottomPadding +
                                    // 多选悬浮条会盖住末尾几行，额外让出高度
                                    if (isSelectionMode) 76.dp else 0.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = hobbyData.items,
                                key = { item -> "hobby_${item.actualItemId}_${item.id}" },
                                // 网格态与列表态卡片结构不同，按态分区避免两者共用复用池
                                contentType = { if (uiState.isListView) "hobby_list" else "hobby_grid" }
                            ) { item ->
                                val isSelected = item.id in selectedIds
                                val onCardClick: () -> Unit = if (isSelectionMode) {
                                    { onToggleSelect(item.id) }
                                } else {
                                    { onItemClick(item) }
                                }

                                if (uiState.isListView) {
                                    HobbyCollectListCard(
                                        item = item,
                                        fields = listFields,
                                        isSelectionEnabled = isSelectionEnabled,
                                        selectionMode = isSelectionMode,
                                        isSelected = isSelected,
                                        onClick = onCardClick,
                                        onLongClick = { onToggleSelect(item.id) }
                                    )
                                } else {
                                    HobbyCollectGridCard(
                                        item = item,
                                        badgeField = gridBadgeField,
                                        isSelectionEnabled = isSelectionEnabled,
                                        selectionMode = isSelectionMode,
                                        isSelected = isSelected,
                                        onClick = onCardClick,
                                        onLongClick = { onToggleSelect(item.id) }
                                    )
                                }
                            }

                            renderPagingFooter(
                                isPaging = hobbyData.isPaging,
                                hasMore = hobbyData.hasMore,
                                errorMessage = hobbyData.errorMessage,
                                onRetryPaging = onLoadNextPage
                            )
                        }
                    }
                }
            }
        }

        // 2. 顶层：悬浮/吸顶控制栏（对齐 HomeFeed/Follow 架构：置顶贴靠，平滑随手势 tuck 进顶栏下方）
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .offset { IntOffset(0, scrollBehavior.state.heightOffset.roundToInt()) }
                // 实测基准取「整个控制栏容器」而不是只量 Surface：
                // 失败横幅（BatchErrorBanner）挂在 Surface 之外，只量 Surface 会漏算横幅高度，
                // 导致折叠上限偏小、折叠后顶部仍露出半截。
                .onSizeChanged { size ->
                    if (size.height > 0) controlBarHeightPx = size.height.toFloat()
                }
                .clipToBounds()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 状态筛选单选下拉胶囊
                    HobbyStateDropdown(
                        currentState = uiState.collectState,
                        onSwitchCollectState = onSwitchCollectState
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 排序维度（带升降序反转箭头 + 展开小三角）+ 高级筛选
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HobbySortControl(
                            currentOrder = uiState.order,
                            currentSortType = uiState.sortType,
                            collectState = uiState.collectState,
                            isSelf = uiState.isSelf,
                            onSwitchOrder = onSwitchOrder,
                            onSwitchSortDirection = onSwitchSortDirection
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        BadgedBox(
                            badge = {
                                if (hasFilter) {
                                    Badge(modifier = Modifier.size(6.dp))
                                }
                            }
                        ) {
                            IconButton(
                                onClick = onOpenFilter,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = "多维筛选",
                                    tint = if (hasFilter) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 批量取消收藏失败提示
            if (uiState.batchCancelError != null) {
                BatchErrorBanner(
                    message = uiState.batchCancelError,
                    onDismiss = onDismissBatchError
                )
            }
        }

        // 3. 多选底部悬浮操作条（仅本人 + 仅多选态）
        if (isSelectionMode) {
            SelectionBottomBar(
                selectedCount = selectedCount,
                totalCount = hobbyData.items.size,
                onSelectAll = onSelectAll,
                onCancelSelection = onClearSelection,
                onCancelCollect = { showCancelConfirm = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // 批量取消收藏二次确认
    if (showCancelConfirm && selectedCount > 0) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text(text = "取消收藏") },
            text = { Text(text = "确定取消这 $selectedCount 项周边收藏吗？取消后不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelConfirm = false
                        onCancelSelectedCollect()
                    },
                    enabled = !uiState.isCancellingBatch
                ) {
                    Text(text = if (uiState.isCancellingBatch) "取消中…" else "取消收藏")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCancelConfirm = false },
                    enabled = !uiState.isCancellingBatch
                ) {
                    Text(text = "再想想")
                }
            },
            shape = ExpressiveTokens.DialogShape
        )
    }
}

// ── 筛选与排序控件 ──────────────────────────────────────

/**
 * 收藏状态单选下拉胶囊。
 * 替代原先在小屏上极易截断溢出的 6 档平铺横滑 Chips，统一为带小三角展开指示的官方 FilterChip。
 */
@Composable
private fun HobbyStateDropdown(
    currentState: String,
    onSwitchCollectState: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "StateArrowRotation"
    )
    val isFiltered = currentState != CollectStateOption.DEFAULT

    Box {
        FilterChip(
            selected = isFiltered,
            onClick = { expanded = true },
            label = {
                Text(
                    text = collectStateLabel(currentState),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isFiltered) FontWeight.Bold else FontWeight.Normal
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) "收起状态菜单" else "展开状态菜单",
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = arrowRotation }
                )
            },
            shape = ExpressiveTokens.ChipShape,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            border = null
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = ExpressiveTokens.MediumCardShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            CollectStateOption.ALL_STATES.forEach { stateKey ->
                val isSelected = currentState == stateKey
                DropdownMenuItem(
                    text = {
                        Text(
                            text = collectStateLabel(stateKey),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        expanded = false
                        onSwitchCollectState(stateKey)
                    }
                )
            }
        }
    }
}

/**
 * 排序控件（做法 B 复合方案）：
 * - 前置图标：方向箭头（ArrowUpward / ArrowDownward），直观表达升序/降序；
 * - 尾部图标：倒三角（ArrowDropDown），指示可点击展开切换维度；
 * - 点击行为：打开下拉菜单选择维度；若在菜单中重复选择当前已选中的排序维度，则自动反转升降序。
 */
@Composable
private fun HobbySortControl(
    currentOrder: String,
    currentSortType: Int,
    collectState: String,
    isSelf: Boolean,
    onSwitchOrder: (String) -> Unit,
    onSwitchSortDirection: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "SortArrowRotation"
    )

    val options = remember(collectState, isSelf) { buildHobbyOrderOptions(collectState, isSelf) }
    val isAsc = currentSortType == UserCollectUiState.SORT_ASC

    Box {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            leadingIcon = {
                Icon(
                    imageVector = if (isAsc) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = if (isAsc) "升序" else "降序",
                    modifier = Modifier.size(16.dp)
                )
            },
            label = {
                Text(
                    text = hobbyOrderShortLabel(currentOrder, isSelf),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) "收起排序菜单" else "展开排序菜单",
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = arrowRotation }
                )
            },
            shape = ExpressiveTokens.ChipShape,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                selectedLabelColor = MaterialTheme.colorScheme.onSurface
            ),
            border = null
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = ExpressiveTokens.MediumCardShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            options.forEach { (key, label) ->
                val isSelected = key == currentOrder
                DropdownMenuItem(
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = if (isAsc) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    text = {
                        Text(
                            text = if (isSelected) "$label (${if (isAsc) "升序" else "降序"})" else label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    },
                    onClick = {
                        expanded = false
                        if (isSelected) {
                            // 重复点击已选项：反转升降序
                            val nextSortType = if (isAsc) UserCollectUiState.SORT_DESC else UserCollectUiState.SORT_ASC
                            onSwitchSortDirection(nextSortType)
                        } else {
                            // 切换到新维度
                            onSwitchOrder(key)
                        }
                    }
                )
            }
        }
    }
}

private fun buildHobbyOrderOptions(collectState: String, isSelf: Boolean): List<Pair<String, String>> {
    val base = HobbyOrderOption.BASE_ORDERS.map { key ->
        key to when (key) {
            HobbyOrderOption.ACTION_DATE -> "收藏时间"
            HobbyOrderOption.RELEASE -> "发售时间"
            HobbyOrderOption.RATING -> "全站评分"
            HobbyOrderOption.MY_RATING -> if (isSelf) "我的评分" else "TA的评分"
            HobbyOrderOption.UPDATE_DATE -> "更新时间"
            else -> "收藏时间"
        }
    }
    // 入手时间仅「已入」档合法（原版其余档位会主动回落收藏时间）
    return if (collectState == CollectStateOption.BUY) {
        base + (HobbyOrderOption.ORDER_TIME to "入手时间")
    } else {
        base
    }
}

/**
 * 排序胶囊上的档位文案。
 *
 * 对齐原版 `UserCollectHobbySubPagerFragment.T()`：顶栏直接显示完整档位名
 * （收藏时间 / 发售时间 / 全站评分 / 我的评分·TA的评分 / 更新时间），不做两字截断。
 */
private fun hobbyOrderShortLabel(order: String, isSelf: Boolean): String = when (order) {
    HobbyOrderOption.ACTION_DATE -> "收藏时间"
    HobbyOrderOption.RELEASE -> "发售时间"
    HobbyOrderOption.RATING -> "全站评分"
    HobbyOrderOption.MY_RATING -> if (isSelf) "我的评分" else "TA的评分"
    HobbyOrderOption.UPDATE_DATE -> "更新时间"
    HobbyOrderOption.ORDER_TIME -> "入手时间"
    else -> "排序"
}

/** 6 档收藏状态胶囊文案（对齐原版 `b0.c`）。 */
private fun collectStateLabel(state: String): String = when (state) {
    CollectStateOption.CARE -> "关注"
    CollectStateOption.WANT -> "想买"
    CollectStateOption.PREORDER -> "预定"
    CollectStateOption.BUY -> "已入"
    CollectStateOption.RESELL -> "有过"
    else -> "全部"
}

/**
 * 批量取消收藏失败提示条。
 *
 * 宿主 snackbar 只消费 `uiState.errorMessage`，`batchCancelError` 由本页就地展示。
 */
@Composable
private fun BatchErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = ExpressiveTokens.ExtraSmallShape,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭提示",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}