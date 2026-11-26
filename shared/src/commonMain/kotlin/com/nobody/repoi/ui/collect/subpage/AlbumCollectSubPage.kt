package com.nobody.repoi.ui.collect.subpage

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.nobody.repoi.ui.common.PoiPullToRefreshBox
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
import com.nobody.repoi.data.remote.model.UserCollectAlbumItem
import com.nobody.repoi.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.repoi.ui.collect.AlbumTypeOption
import com.nobody.repoi.ui.collect.SubPageData
import com.nobody.repoi.ui.collect.components.CollectAlbumCard
import com.nobody.repoi.ui.collect.components.CollectAlbumPictureOrderTabs
import com.nobody.repoi.ui.collect.components.CollectOrderTabRow
import com.nobody.repoi.ui.collect.components.EmptyCollectView
import com.nobody.repoi.ui.collect.components.ErrorCollectView
import com.nobody.repoi.ui.collect.components.SelectionBottomBar
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import androidx.compose.foundation.shape.CircleShape

internal const val COLLECT_ALBUM_EMPTY_TEXT = "暂无相册~"

internal const val COLLECT_CREATED_ALBUM_EMPTY_TEXT = "暂无创建的相册~"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AlbumCollectSubPage(
    subPageData: SubPageData<UserCollectAlbumItem>,
    navBarBottomPadding: Dp,
    currentOrder: String,
    albumType: String,
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

    val gridState = rememberKeyedLazyStaggeredGridState("album_staggered", albumType, currentOrder)

    val shouldLoadMore by remember(gridState) {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 4
        }
    }

    LaunchedEffect(shouldLoadMore, subPageData.items.size) {
        if (shouldLoadMore && subPageData.hasMore && !subPageData.isPaging && !subPageData.isLoading && !subPageData.isRefreshing) {
            onLoadNextPage()
        }
    }

    var showCancelConfirm by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            CollectOrderTabRow(
                titles = CollectAlbumPictureOrderTabs,
                selected = currentOrder,
                onSelect = onSwitchOrder
            )

            PoiPullToRefreshBox(
                isRefreshing = subPageData.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    subPageData.isLoading && subPageData.items.isEmpty() -> {
                        PoiLoadingView()
                    }

                    subPageData.errorMessage != null && subPageData.items.isEmpty() -> {
                        if (subPageData.isContentHidden) {

                            EmptyCollectView(title = COLLECT_CONTENT_HIDDEN_TEXT)
                        } else {
                            ErrorCollectView(errorMessage = subPageData.errorMessage, onRetry = onRetry)
                        }
                    }

                    subPageData.items.isEmpty() -> {

                        EmptyCollectView(
                            title = if (albumType == AlbumTypeOption.UPLOAD) {
                                COLLECT_CREATED_ALBUM_EMPTY_TEXT
                            } else {
                                COLLECT_ALBUM_EMPTY_TEXT
                            }
                        )
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

                                        if (isSelectionMode) onToggleSelect(item.id) else onItemClick(item)
                                    },

                                    onLongClick = { onToggleSelect(item.id) },
                                    albumType = albumType
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
                                        LoadingIndicator(modifier = Modifier.size(28.dp))
                                    }
                                }
                            } else if (subPageData.errorMessage != null) {
                                item(span = StaggeredGridItemSpan.FullLine, contentType = "album_feed_footer") {
                                    Surface(
                                        onClick = onLoadNextPage,
                                        shape = CircleShape,
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

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            shape = MaterialTheme.shapes.extraLarge,
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
