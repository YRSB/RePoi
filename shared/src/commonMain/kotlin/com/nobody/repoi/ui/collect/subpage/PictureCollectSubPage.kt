package com.nobody.repoi.ui.collect.subpage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.ui.album.rememberAlbumStaggeredGridColumns
import com.nobody.repoi.ui.collect.SubPageData
import com.nobody.repoi.ui.collect.components.CollectAlbumPictureOrderTabs
import com.nobody.repoi.ui.collect.components.CollectOrderTabRow
import com.nobody.repoi.ui.collect.components.EmptyCollectView
import com.nobody.repoi.ui.collect.components.ErrorCollectView
import com.nobody.repoi.ui.collect.components.LoadingCollectView
import com.nobody.repoi.ui.common.listImageKey
import com.nobody.repoi.ui.common.rememberListImageRequest
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.util.CdnUrlUtils
import com.nobody.repoi.util.ImageAspectRatioCache

// ── 图片子页（key=90000）────────────────────────────────────

/** 原版图片页空态文案 */
internal const val COLLECT_PICTURE_EMPTY_TEXT = "暂无图片~"

/**
 * 收藏页「图片」子页（category key=90000，接口 `api/collect/pic-v2`）。
 *
 * 对标原版 `UserCollectPictureFragment`：
 * - 顶部 3 档二级排序 Tab（发布时间 / 收藏时间 / 点赞量），与相册页共用 `CollectOrderTabRow`
 *   （原版两页共用 `UserCollectSubPagerFragment` 这个排序切换组件）；
 * - **自适应瀑布流**（列宽策略 [rememberAlbumStaggeredGridColumns]，手机竖屏 2 列 / 横屏 3 列）：
 *   原版 `u2.f0(rv, 5, 240.0)` → `e0()` → `u(ctx, 240)` = `ceil(屏宽dp / 240)`
 *   列 + `StaggeredGridLayoutManager` 瀑布流，每项 `WrapContentDraweeView` 按原图比例自适应高度。
 *   早前版本误读为「单列整宽」，导致横屏下一张图就占满整屏、完全无法浏览；
 * - 列宽策略复用项目内所有瀑布流（相册页 / `ui/picture/PictureListScreen`）的同一入口，
 *   骨架屏同步用同一列宽，避免加载前后列数跳变；
 * - 缩略图规格用 `pic/n/`（[CdnUrlUtils.getPicNUrl]），与原版一致；
 * - 列表页不显示图片备注（原版 `UserCollectPictureAdapter.java:97` 显式隐藏 remark）。
 *
 * @param currentOrder 当前排序档（`addTime` / `actionDate` / `praiseCount`）
 * @param onItemClick 点击某张图片，回传其在当前列表中的下标（供宿主打开大图画廊并定位）
 */
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

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部 3 档二级排序 Tab（与相册页共用组件，原版两页同源）
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
                            // itemsIndexed 的 contentType 是 (index, item) 双参签名，
                            // 必须显式写两个参数，不能用单参的 `it` 简写
                            contentType = { _, _ -> "pic_item" }
                        ) { index, item ->
                            UserCollectPicCard(
                                item = item,
                                onClick = { onItemClick(index) }
                            )
                        }

                        // 底部触底分页指示器（跨列整行）
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
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
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

/**
 * 图片卡片：瀑布流单项，按原图比例自适应高度。
 *
 * - 宽高比从 [ImageAspectRatioCache] 取真实原图比例，并夹取到 [MIN_ASPECT_RATIO, MAX_ASPECT_RATIO]
 *   防止极端长图/宽图把单项撑得过高过扁（与 `ui/picture/PictureListScreen.PictureGridCard` 同策略）；
 * - 缩略图用 `pic/n/` 变体（原版 `pe/c.java` 的图片流规格）；
 * - [ContentScale.Crop] + 精确 `aspectRatio` = 完整展示不裁切，同时避免解码前后比例抖动。
 */
@Composable
internal fun UserCollectPicCard(
    item: UserCollectPicItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageUrl = remember(item.id, item.actualUrl) {
        CdnUrlUtils.getPicNUrl(item.actualUrl)
    }
    val ratioKey = listImageKey(item.actualUrl, imageUrl, "n")
    var aspectRatio by remember(ratioKey) {
        mutableFloatStateOf(ImageAspectRatioCache[ratioKey] ?: 1.0f)
    }
    val imageRequest = rememberListImageRequest(item.actualUrl, imageUrl, "n:collect")
    // URL 非空但解码/下载失败时也要给出可见反馈，否则只剩一块灰色占位
    var loadFailed by remember(ratioKey) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ExpressiveTokens.SmallCardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .aspectRatio(aspectRatio.coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = item.name?.takeIf { it.isNotBlank() } ?: "收藏图片",
                // 容器比例 = 原图比例，FillBounds 不会裁掉画面内容
                contentScale = ContentScale.FillBounds,
                onLoading = { loadFailed = false },
                onError = { loadFailed = true },
                onSuccess = { state ->
                    loadFailed = false
                    val size = state.painter.intrinsicSize
                    if (size.width > 0 && size.height > 0) {
                        val ratio = (size.width / size.height)
                            .coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
                        aspectRatio = ratio
                        ImageAspectRatioCache[ratioKey] = ratio
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (imageUrl.isBlank() || loadFailed) {
            Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * 图片卡片宽高比下限（竖图不至于过高）。
 *
 * 与 `ui/picture/PictureListScreen.PictureGridCard` 取同一组常量，保证两处图片流手感一致。
 */
private const val MIN_ASPECT_RATIO = 0.4f

/** 图片卡片宽高比上限（横图不至于过扁） */
private const val MAX_ASPECT_RATIO = 2.5f