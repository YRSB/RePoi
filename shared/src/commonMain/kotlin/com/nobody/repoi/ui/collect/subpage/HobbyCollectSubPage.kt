package com.nobody.repoi.ui.collect.subpage

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.nobody.repoi.ui.common.rememberSynchronousTopAppBarScrollBehavior
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import com.nobody.repoi.ui.common.PoiPullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.remote.model.UserCollectHobbyItem
import com.nobody.repoi.ui.collect.CollectStateOption
import com.nobody.repoi.ui.collect.HobbyOrderOption
import com.nobody.repoi.ui.collect.UserCollectUiState
import com.nobody.repoi.ui.collect.components.ErrorCollectView
import com.nobody.repoi.ui.collect.components.SelectionBottomBar
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import com.nobody.repoi.ui.collect.components.renderPagingFooter
import kotlinx.coroutines.launch
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

internal fun hobbyScrollKeyInputs(uiState: UserCollectUiState): Array<out Any?> = arrayOf<Any?>(
    uiState.collectState,
    uiState.order,
    uiState.sortType,
    uiState.subCategoryId,
    uiState.selectedTag,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun HobbyCollectSubPage(
    uiState: UserCollectUiState,
    navBarBottomPadding: Dp,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onSwitchCollectState: (String) -> Unit,
    onSwitchOrder: (String) -> Unit,

    onSwitchSortDirection: (Int) -> Unit = {},
    onOpenFilter: () -> Unit,
    onItemClick: (UserCollectHobbyItem) -> Unit,
    onRetry: () -> Unit,

    onToggleSelect: (Long) -> Unit = {},
    onSelectAll: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onCancelSelectedCollect: () -> Unit = {},
    onDismissBatchError: () -> Unit = {}
) {
    val hobbyData = uiState.hobbyData

    val gridState = rememberKeyedLazyGridState(
        "hobby_grid",
        *hobbyScrollKeyInputs(uiState)
    )

    val isSelectionEnabled = uiState.isSelf
    val isSelectionMode = isSelectionEnabled && uiState.hobbySelectionMode
    val selectedIds = uiState.hobbySelectedIds
    val selectedCount = selectedIds.size

    val density = LocalDensity.current
    var controlBarHeightPx by remember { mutableFloatStateOf(with(density) { 48.dp.toPx() }) }

    val itemsNonEmpty = hobbyData.items.isNotEmpty()

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
    val scope = rememberCoroutineScope()

    val shouldLoadMore by remember(gridState) {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 4
        }
    }

    LaunchedEffect(shouldLoadMore, hobbyData.items.size) {
        if (shouldLoadMore && hobbyData.hasMore && !hobbyData.isPaging && !hobbyData.isLoading && !hobbyData.isRefreshing) {
            onLoadNextPage()
        }
    }

    val filterSignature = listOf<Any?>(
        uiState.collectState,
        uiState.order,
        uiState.sortType,
        uiState.subCategoryId,
        uiState.selectedTag
    )
    var lastFilterSignature by remember { mutableStateOf(filterSignature) }
    LaunchedEffect(filterSignature) {
        if (filterSignature != lastFilterSignature) {
            lastFilterSignature = filterSignature
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
    }

    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(hobbyData.isRefreshing) {
        if (wasRefreshing && !hobbyData.isRefreshing && hobbyData.errorMessage == null) {
            gridState.scrollToItem(0)
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }
        wasRefreshing = hobbyData.isRefreshing
    }

    val listFields = remember(uiState.collectState, uiState.isSelf) {
        hobbyListFields(uiState.collectState, uiState.isSelf)
    }
    val gridBadgeField = remember(uiState.collectState, uiState.isSelf) {
        hobbyGridBadgeField(uiState.collectState, uiState.isSelf)
    }

    val gridColumns = if (uiState.isListView) {
        GridCells.Adaptive(minSize = 420.dp)
    } else {
        GridCells.Adaptive(minSize = 120.dp)
    }

    val hasFilter = uiState.subCategoryId != null || uiState.selectedTag != null

    var showCancelConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()

            .clipToBounds()
            .background(MaterialTheme.colorScheme.background)
    ) {

        PoiPullToRefreshBox(
            isRefreshing = hobbyData.isRefreshing,
            onRefresh = {

                scope.launch {
                    gridState.scrollToItem(0)
                    scrollBehavior.state.heightOffset = 0f
                    scrollBehavior.state.contentOffset = 0f
                }
                onRefresh()
            },
            modifier = Modifier.fillMaxSize(),
            indicatorPaddingTop = controlBarHeightDp + 4.dp,
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
            ) {
                when {
                    hobbyData.isLoading && hobbyData.items.isEmpty() -> {
                        PoiLoadingView(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = controlBarHeightDp)
                        )
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

                    hobbyData.isContentHidden && hobbyData.items.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = controlBarHeightDp),
                            contentAlignment = Alignment.Center
                        ) {
                            HobbyEmptyState(
                                icon = PoiIcons.Filled.VisibilityOff,
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
                                icon = PoiIcons.Filled.Inbox,
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

                                    if (isSelectionMode) 76.dp else 0.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = hobbyData.items,
                                key = { item -> "hobby_${item.actualItemId}_${item.id}" },

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

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .offset { IntOffset(0, scrollBehavior.state.heightOffset.roundToInt()) }

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

                    HobbyStateDropdown(
                        currentState = uiState.collectState,
                        onSwitchCollectState = onSwitchCollectState
                    )

                    Spacer(modifier = Modifier.width(8.dp))

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
                                    painter = painterResource(PoiIcons.FilterList),
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

            if (uiState.batchCancelError != null) {
                BatchErrorBanner(
                    message = uiState.batchCancelError,
                    onDismiss = onDismissBatchError
                )
            }
        }

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
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

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
            modifier = Modifier.semantics {
                stateDescription = "当前：${collectStateLabel(currentState)}"
            },
            label = {
                Text(
                    text = collectStateLabel(currentState),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isFiltered) FontWeight.Bold else FontWeight.Normal
                )
            },
            trailingIcon = {
                Icon(
                    painter = painterResource(PoiIcons.ArrowDropDown),
                    contentDescription = if (expanded) "收起状态菜单" else "展开状态菜单",
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = arrowRotation }
                )
            },
            shape = MaterialTheme.shapes.small,
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
            shape = MaterialTheme.shapes.medium,
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
                    painter = painterResource(if (isAsc) PoiIcons.ArrowUpward else PoiIcons.ArrowDownward),
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
                    painter = painterResource(PoiIcons.ArrowDropDown),
                    contentDescription = if (expanded) "收起排序菜单" else "展开排序菜单",
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = arrowRotation }
                )
            },
            shape = MaterialTheme.shapes.small,
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                selectedLabelColor = MaterialTheme.colorScheme.onSurface
            ),
            border = null
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            options.forEach { (key, label) ->
                val isSelected = key == currentOrder
                DropdownMenuItem(
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                painter = painterResource(if (isAsc) PoiIcons.ArrowUpward else PoiIcons.ArrowDownward),
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

                            val nextSortType = if (isAsc) UserCollectUiState.SORT_DESC else UserCollectUiState.SORT_ASC
                            onSwitchSortDirection(nextSortType)
                        } else {

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

    return if (collectState == CollectStateOption.BUY) {
        base + (HobbyOrderOption.ORDER_TIME to "入手时间")
    } else {
        base
    }
}

private fun hobbyOrderShortLabel(order: String, isSelf: Boolean): String = when (order) {
    HobbyOrderOption.ACTION_DATE -> "收藏时间"
    HobbyOrderOption.RELEASE -> "发售时间"
    HobbyOrderOption.RATING -> "全站评分"
    HobbyOrderOption.MY_RATING -> if (isSelf) "我的评分" else "TA的评分"
    HobbyOrderOption.UPDATE_DATE -> "更新时间"
    HobbyOrderOption.ORDER_TIME -> "入手时间"
    else -> "排序"
}

private fun collectStateLabel(state: String): String = when (state) {
    CollectStateOption.CARE -> "关注"
    CollectStateOption.WANT -> "想买"
    CollectStateOption.PREORDER -> "预定"
    CollectStateOption.BUY -> "已入"
    CollectStateOption.RESELL -> "有过"
    else -> "全部"
}

@Composable
private fun BatchErrorBanner(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.extraSmall,
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
                    painter = painterResource(PoiIcons.Filled.Close),
                    contentDescription = "关闭提示",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
