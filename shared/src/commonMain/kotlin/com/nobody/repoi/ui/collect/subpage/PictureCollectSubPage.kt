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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
 * - 缩略图规格用 `pic/n/`（`ImagePurpose.CARD_STANDARD` 经 `pic/` 家族解析），与原版一致；
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
    // 保存键绑定（统一入口见 rememberKeyedLazyStaggeredGridState）：
    // 切换排序时以全新的 initialFirstVisibleItemIndex = 0 重建状态自动回顶；
    // 排序不变时（Nav3 返回）由 SaveableStateRegistry 按同一键恢复滚动位置。
    val gridState = rememberKeyedLazyStaggeredGridState("picture_staggered", currentOrder)
    // 以 gridState 为 remember 键：排序变化时上面会重建 LazyStaggeredGridState，
    // 缺少该键的 derivedStateOf 会一直盯着已被丢弃的旧实例，导致切过排序后触底分页永久失效。
    val shouldLoadMore by remember(gridState) {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 4
        }
    }

    // 附带 items.size 键（对齐 HobbyCollectSubPage）：宽屏一屏装下时触底判定恒为 true 不再变化，
    // 缺这个键就只会翻一页。
    LaunchedEffect(shouldLoadMore, subPageData.items.size) {
        if (shouldLoadMore && subPageData.hasMore && !subPageData.isPaging && !subPageData.isLoading && !subPageData.isRefreshing) {
            onLoadNextPage()
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
                    PoiLoadingView()
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

/**
 * 图片卡片：瀑布流单项，按原图比例自适应高度。
 *
 * - 高度由 [PoiAsyncImage] 的 `adaptiveRatio` 撑开：首帧读管道纵横比缓存占位，
 *   加载成功后回写并夹取到管道区间 `[0.4f, 2.5f]`，防止极端长图/宽图把单项撑得过高过扁
 *   （与 `ui/picture/PictureListScreen.PictureGridCard` 同策略）；
 * - 缩略图用 `pic/n/` 变体（原版 `pe/c.java` 的图片流规格）；
 * - 容器比例 = 原图比例，配合 `ContentScale.FillBounds` 拉伸铺满（沿用迁移前 `AsyncImage` 的缩放语义），
 *   可避免解码前后比例抖动。
 */
@Composable
internal fun UserCollectPicCard(
    item: UserCollectPicItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // `pic/` 家族无 `sr` 档，缩略档由管道裁决到 `pic/n/`；解析结果交给基元通用重载直通 Coil
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
        // 空路径/缺省图由基元直接落官方缺省图，失败态由基元渲染破损图标
        PoiAsyncImage(
            model = thumbnailUrl,
            contentDescription = item.name?.takeIf { it.isNotBlank() } ?: "收藏图片",
            modifier = Modifier.fillMaxWidth(),
            // 恢复旧 AsyncImage 的拉伸填充语义：铺满瀑布流单项，避免占位比例与真实比例不等时留边
            contentScale = ContentScale.FillBounds,
            adaptiveRatio = true,
            ratioKey = item.actualUrl,
            fallbackRatio = 1.0f
        )
    }
}
