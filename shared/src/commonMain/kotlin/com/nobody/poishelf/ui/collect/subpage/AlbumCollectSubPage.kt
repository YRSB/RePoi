package com.nobody.poishelf.ui.collect.subpage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobody.poishelf.data.remote.model.UserCollectAlbumItem
import com.nobody.poishelf.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.poishelf.ui.collect.SubPageData
import com.nobody.poishelf.ui.collect.components.CollectAlbumCard
import com.nobody.poishelf.ui.collect.components.CollectAlbumPictureOrderTabs
import com.nobody.poishelf.ui.collect.components.CollectOrderTabRow
import com.nobody.poishelf.ui.collect.components.EmptyCollectView
import com.nobody.poishelf.ui.collect.components.ErrorCollectView
import com.nobody.poishelf.ui.collect.components.LoadingCollectView
import com.nobody.poishelf.ui.collect.components.SelectionBottomBar
import com.nobody.poishelf.ui.theme.ExpressiveTokens

// ── 相册子页（key=70000）────────────────────────────────────

/** 原版相册页空态文案 `R.string.text_empty_collect_album` */
internal const val COLLECT_ALBUM_EMPTY_TEXT = "暂无相册~"

/**
 * 收藏页「相册」子页（category key=70000，接口 `api/collect/album-v2`）。
 *
 * 对标原版 `UserCollectSubPagerFragment` + `UserCollectAlbumFragment`：
 * - 顶部 3 档二级排序 Tab（发布时间 / 收藏时间 / 点赞量），等价于原版 `MagicIndicator`；
 * - 长按卡片（封面/标题/头像任一处）进入多选，底部 `SelectionBottomBar`（全选/取消/取消收藏N）；
 * - 批量取消收藏提交**外层收藏节点 id**（`UserCollectAlbumItem.id`），提交前二次确认。
 *
 * @param currentOrder 当前排序档（`addTime` / `actionDate` / `praiseCount`）
 * @param isSelectionMode 是否处于多选模式
 * @param selectedIds 已选中的**外层收藏节点 id**集合
 * @param isSelf 是否浏览自己的收藏（批量取消收藏仅本人可用）
 * @param isCancellingBatch 批量取消收藏请求进行中（用于确认按钮 loading 与防重复提交）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumCollectSubPage(
    subPageData: SubPageData<UserCollectAlbumItem>,
    navBarBottomPadding: Dp,
    currentOrder: String,
    isSelectionMode: Boolean,
    selectedIds: Set<Long>,
    isSelf: Boolean,
    isCancellingBatch: Boolean,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onSwitchOrder: (String) -> Unit,
    onItemClick: (UserCollectAlbumItem) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onCancelSelection: () -> Unit,
    onCancelCollect: () -> Unit,
    onRetry: () -> Unit
) {
    val gridState = rememberLazyStaggeredGridState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && subPageData.hasMore && !subPageData.isPaging && !subPageData.isLoading && !subPageData.isRefreshing) {
            onLoadNextPage()
        }
    }

    // 排序切换后重置到第 1 页（ViewModel 侧已重置数据），并把列表滚回顶部
    LaunchedEffect(currentOrder) {
        if (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0) {
            gridState.scrollToItem(0)
        }
    }

    // 批量取消收藏二次确认（仅本人可见，对齐原版 isSelf 门控）
    var showCancelConfirm by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部 3 档二级排序 Tab（等价原版 UserCollectSubPagerFragment）
            CollectOrderTabRow(
                titles = CollectAlbumPictureOrderTabs,
                selected = currentOrder,
                onSelect = onSwitchOrder
            )

            PullToRefreshBox(
                isRefreshing = subPageData.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    subPageData.isLoading && subPageData.items.isEmpty() -> {
                        LoadingCollectView()
                    }

                    subPageData.errorMessage != null && subPageData.items.isEmpty() -> {
                        if (subPageData.isContentHidden) {
                            // 「用户已隐藏内容」独立空态（与通用 4 页共用同一文案常量）
                            EmptyCollectView(title = COLLECT_CONTENT_HIDDEN_TEXT)
                        } else {
                            ErrorCollectView(errorMessage = subPageData.errorMessage, onRetry = onRetry)
                        }
                    }

                    subPageData.items.isEmpty() -> {
                        EmptyCollectView(title = COLLECT_ALBUM_EMPTY_TEXT)
                    }

                    else -> {
                        LazyVerticalStaggeredGrid(
                            columns = rememberAlbumStaggeredGridColumns(),
                            state = gridState,
                            contentPadding = PaddingValues(
                                start = 8.dp,
                                top = 8.dp,
                                end = 8.dp,
                                bottom = 16.dp + navBarBottomPadding
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalItemSpacing = 8.dp,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = subPageData.items,
                                key = { item -> "album_${item.actualAlbumId}_${item.id}" },
                                contentType = { "album_feed" }
                            ) { item ->
                                CollectAlbumCard(
                                    item = item,
                                    currentOrder = currentOrder,
                                    isSelected = item.id in selectedIds,
                                    isSelectionMode = isSelectionMode,
                                    onClick = {
                                        // 多选模式下点击切换选中，否则进入相册详情
                                        if (isSelectionMode) onToggleSelect(item.id) else onItemClick(item)
                                    },
                                    // 长按任意子区域（封面/标题/头像）进入多选
                                    onLongClick = { onToggleSelect(item.id) }
                                )
                            }

                            if (subPageData.isPaging) {
                                item(span = StaggeredGridItemSpan.FullLine, contentType = "album_feed_footer") {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    }
                                }
                            } else if (subPageData.errorMessage != null) {
                                item(span = StaggeredGridItemSpan.FullLine, contentType = "album_feed_footer") {
                                    Surface(
                                        onClick = onLoadNextPage,
                                        shape = ExpressiveTokens.ButtonShape,
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp, horizontal = 16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${subPageData.errorMessage}，点击重试",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            } else if (!subPageData.hasMore) {
                                item(span = StaggeredGridItemSpan.FullLine, contentType = "album_feed_footer") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "已展示全部收藏",
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

        // 多选模式底部悬浮操作条（全选 / 取消 / 取消收藏N）
        if (isSelectionMode) {
            SelectionBottomBar(
                selectedCount = selectedIds.size,
                totalCount = subPageData.items.size,
                onSelectAll = onSelectAll,
                onCancelSelection = onCancelSelection,
                onCancelCollect = { showCancelConfirm = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // 批量取消收藏二次确认（仅本人可见）
    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            shape = ExpressiveTokens.DialogShape,
            title = { Text("取消收藏确认") },
            text = {
                Text(
                    if (isSelf) {
                        "取消收藏后，将所选 ${selectedIds.size} 个条目都移除您的收藏中心"
                    } else {
                        "仅可管理自己的收藏"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = isSelf && !isCancellingBatch && selectedIds.isNotEmpty(),
                    onClick = {
                        showCancelConfirm = false
                        onCancelCollect()
                    }
                ) {
                    Text(if (isCancellingBatch) "取消中…" else "取消收藏")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) {
                    Text("再想想")
                }
            }
        )
    }
}

/**
 * 收藏相册瀑布流骨架相关实现已下线：收藏页统一改用 [LoadingCollectView]（居中加载指示器）。
 */