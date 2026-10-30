package com.nobody.poishelf.ui.collect.subpage

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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobody.poishelf.data.remote.model.UserCollectHobbyItem
import com.nobody.poishelf.ui.collect.CollectStateOption
import com.nobody.poishelf.ui.collect.HobbyOrderOption
import com.nobody.poishelf.ui.collect.UserCollectUiState
import com.nobody.poishelf.ui.collect.UserCollectViewModel
import com.nobody.poishelf.ui.collect.components.ErrorCollectView
import com.nobody.poishelf.ui.collect.components.LoadingCollectView
import com.nobody.poishelf.ui.collect.components.SelectionBottomBar
import com.nobody.poishelf.ui.collect.components.renderPagingFooter
import com.nobody.poishelf.ui.theme.ExpressiveTokens

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
    val gridState = rememberLazyGridState()

    // 仅本人拥有写操作入口（对齐原版只在自己视角挂 onCancelCollect）
    val isSelectionEnabled = uiState.isSelf
    val isSelectionMode = isSelectionEnabled && uiState.hobbySelectionMode
    val selectedIds = uiState.hobbySelectedIds
    val selectedCount = selectedIds.size

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && hobbyData.hasMore && !hobbyData.isPaging && !hobbyData.isLoading && !hobbyData.isRefreshing) {
            onLoadNextPage()
        }
    }

    LaunchedEffect(
        uiState.collectState,
        uiState.order,
        uiState.sortType,
        uiState.subCategoryId,
        uiState.selectedTag
    ) {
        if (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0) {
            gridState.scrollToItem(0)
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 控制栏：6 档状态横滑胶囊 + 排序选择（含升降序）+ 高级筛选
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
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CollectStateOption.ALL_STATES.forEach { stateKey ->
                            val isSelected = uiState.collectState == stateKey
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSwitchCollectState(stateKey) },
                                label = {
                                    Text(
                                        text = collectStateLabel(stateKey),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 排序 + 升降序 + 筛选
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

            // 批量取消收藏失败提示（宿主 snackbar 只消费 errorMessage，本字段就地展示）
            if (uiState.batchCancelError != null) {
                BatchErrorBanner(
                    message = uiState.batchCancelError,
                    onDismiss = onDismissBatchError
                )
            }

            // 下拉刷新与列表内容
            PullToRefreshBox(
                isRefreshing = hobbyData.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    hobbyData.isLoading && hobbyData.items.isEmpty() -> {
                        LoadingCollectView()
                    }

                    hobbyData.errorMessage != null && hobbyData.items.isEmpty() -> {
                        ErrorCollectView(
                            errorMessage = hobbyData.errorMessage,
                            onRetry = onRetry
                        )
                    }

                    // 服务端回「用户已隐藏内容」语义：渲染专用空态而非通用错误态
                    // 副标题复用全模块统一常量（原版 strings.xml:1372 text_empty_user_hide）
                    hobbyData.isContentHidden && hobbyData.items.isEmpty() -> {
                        HobbyEmptyState(
                            icon = Icons.Filled.VisibilityOff,
                            title = "用户已隐藏内容",
                            subtitle = COLLECT_CONTENT_HIDDEN_TEXT
                        )
                    }

                    hobbyData.items.isEmpty() -> {
                        HobbyEmptyState(
                            icon = Icons.Filled.Inbox,
                            title = "未收藏周边~",
                            subtitle = "可以在详情页将感兴趣的条目加入收藏"
                        )
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = gridColumns,
                            state = gridState,
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                top = 8.dp,
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
                                key = { item -> "hobby_${item.actualItemId}_${item.id}" }
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

        // 多选底部悬浮操作条（仅本人 + 仅多选态）
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

// ── 排序控件 ──────────────────────────────────────────────

/**
 * 排序下拉 + 升降序连体二选。
 *
 * 排序值域取自 [HobbyOrderOption]：5 档基础值域
 * `actionDate/release/rating/myRating/updateDate`（原版 `b0.java:124-143`），
 * 「按入手时间 `orderTime`」仅在已入(buy)档追加
 * （原版 `UserCollectHobbySubPagerFragment.java:261-267`）。
 *
 * 文案两套：看自己时 `myRating` 显示「我的评分」，看他人时显示「TA的评分」。
 * 点胶囊打开下拉，点升/降连体按钮直接指定升降序（`sortType` 1 升 / 2 降）。
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

    val options = remember(collectState, isSelf) { buildHobbyOrderOptions(collectState, isSelf) }

    // 胶囊（选排序维度）与升降序连体二选是两个平级可点区域：
    // 连体按钮不能塞进 FilterChip 的 trailingIcon，否则父子两个 clickable 会同时响应。
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            FilterChip(
                selected = true,
                onClick = { expanded = true },
                label = {
                    Text(
                        text = hobbyOrderShortLabel(currentOrder, isSelf),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
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
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (key, label) ->
                    val isSelected = key == currentOrder
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = label,
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
                            onSwitchOrder(key)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val directions = listOf(UserCollectUiState.SORT_ASC, UserCollectUiState.SORT_DESC)
            directions.forEachIndexed { index, sortType ->
                ToggleButton(
                    checked = currentSortType == sortType,
                    onCheckedChange = { if (it) onSwitchSortDirection(sortType) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (sortType == UserCollectUiState.SORT_ASC) "升" else "降",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * 排序下拉候选项（key → 中文文案）。
 *
 * 「按入手时间」仅在已入档出现；「我的评分」在看他人时改称「TA的评分」
 * （原版 `b0.d` / `b0.e` 两张文案表之别）。
 */
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