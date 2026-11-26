package com.nobody.repoi.ui.collect.subpage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.nobody.repoi.ui.common.PoiPullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.repoi.ui.collect.SubPageData
import com.nobody.repoi.ui.collect.components.CollectAlbumPictureOrderTabs
import com.nobody.repoi.ui.collect.components.CollectOrderTabRow
import com.nobody.repoi.ui.collect.components.EmptyCollectView
import com.nobody.repoi.ui.collect.components.ErrorCollectView
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import com.nobody.repoi.ui.common.PoiAsyncImage

internal const val COLLECT_PICTURE_EMPTY_TEXT = "暂无图片~"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PictureCollectSubPage(
    subPageData: SubPageData<UserCollectPicItem>,
    navBarBottomPadding: Dp,
    currentOrder: String,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onSwitchOrder: (String) -> Unit,
    onItemClick: (Int) -> Unit,
    onRetry: () -> Unit
) {

    val gridState = rememberKeyedLazyStaggeredGridState("picture_staggered", currentOrder)

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
                    EmptyCollectView(title = COLLECT_PICTURE_EMPTY_TEXT)
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
                        itemsIndexed(
                            items = subPageData.items,
                            key = { _, item -> "pic_${item.id}" },

                            contentType = { _, _ -> "pic_item" }
                        ) { index, item ->
                            UserCollectPicCard(
                                item = item,
                                onClick = { onItemClick(index) }
                            )
                        }

                        item(
                            span = StaggeredGridItemSpan.FullLine,
                            key = "pic_footer",
                            contentType = "pic_footer"
                        ) {
                            when {
                                subPageData.isPaging -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LoadingIndicator(modifier = Modifier.size(28.dp))
                                    }
                                }

                                subPageData.errorMessage != null -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${subPageData.errorMessage}，点击重试",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.clickable(onClick = onLoadNextPage)
                                        )
                                    }
                                }

                                !subPageData.hasMore -> {
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
    }
}

@Composable
internal fun UserCollectPicCard(
    item: UserCollectPicItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    val thumbnailUrl = remember(item.id, item.actualUrl) {
        MediaPipeline.Default.resolvePictureUrl(item.actualUrl, ImagePurpose.CARD_STANDARD)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {

        PoiAsyncImage(
            model = thumbnailUrl,
            contentDescription = item.name?.takeIf { it.isNotBlank() } ?: "收藏图片",
            modifier = Modifier.fillMaxWidth(),

            contentScale = ContentScale.FillBounds,
            adaptiveRatio = true,
            ratioKey = item.actualUrl,
            fallbackRatio = 1.0f
        )
    }
}
