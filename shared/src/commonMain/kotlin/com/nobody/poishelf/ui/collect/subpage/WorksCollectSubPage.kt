package com.nobody.poishelf.ui.collect.subpage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobody.poishelf.data.remote.model.UserCollectCommonItem
import com.nobody.poishelf.ui.collect.SubPageData
import com.nobody.poishelf.ui.collect.components.CollectFilterBottomSheet
import com.nobody.poishelf.ui.collect.components.CollectOrderTabRow
import com.nobody.poishelf.ui.collect.components.EmptyCollectView
import com.nobody.poishelf.ui.collect.components.ErrorCollectView
import com.nobody.poishelf.ui.collect.components.LoadingCollectView
import com.nobody.poishelf.ui.collect.components.WorksCollectCard
import com.nobody.poishelf.ui.collect.components.worksDetailItemId
import com.nobody.poishelf.ui.theme.ExpressiveTokens

// ── 作品子页（从通用 4 页拆出，独立实现） ──────────────────

/**
 * 作品页顶部 3 档收藏状态 Tab。
 *
 * 对标原版 `UserCollectWorksSubFragment.java:55`：
 * `[{想看,1}, {在看,2}, {看过,3}]`。**这里 Tab 的语义是「收藏状态」而不是排序维度**，
 * 请求参数对应 `state`，因此与通用 4 页的排序 Tab 完全不同构。
 *
 * key 用字符串承载以便复用 [CollectOrderTabRow]，取回时用 `toIntOrNull()` 转回 Int。
 */
internal val WorksCollectStateTabs: List<Pair<String, String>> = listOf(
    "想看" to "1",
    "在看" to "2",
    "看过" to "3"
)

/**
 * 作品收藏子页（原版 `UserCollectWorksSubFragment` + `UserCollectWorksFragment`）。
 *
 * 与通用 4 页的三点根本差异（见差异报告 §3.8）：
 * 1. 顶部 Tab 是**收藏状态**（想看/在看/看过）而非排序维度；
 * 2. 排序控件是升/降连体二选（单收藏时间维度），**没有第二档排序**；
 * 3. **有**标签筛选（仅本人可见），且**没有**网格/列表切换（固定线性列表）。
 *
 * 标签池来自 `api/user/glean/textTag/get`，与周边页的 `api/collect/tag` **不是同一个池**。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorksCollectSubPage(
    subPageData: SubPageData<UserCollectCommonItem>,
    worksState: Int,
    worksOrderAsc: Boolean,
    worksTag: String?,
    worksAvailableTags: List<String>,
    isSelf: Boolean,
    showFilterSheet: Boolean,
    navBarBottomPadding: Dp,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onSwitchState: (Int) -> Unit,
    onSwitchOrderAsc: (Boolean) -> Unit,
    onOpenFilter: () -> Unit,
    onSelectTag: (String?) -> Unit,
    onResetTag: () -> Unit,
    onDismissFilter: () -> Unit,
    onItemClick: (UserCollectCommonItem) -> Unit,
    onRetry: () -> Unit
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
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

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部：3 档状态 Tab + 升/降连体二选 + 标签筛选按钮（仅本人可见）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CollectOrderTabRow(
                titles = WorksCollectStateTabs,
                selected = worksState.toString(),
                onSelect = { key -> key.toIntOrNull()?.let(onSwitchState) },
                modifier = Modifier.weight(1f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val directions = listOf(true, false)
                directions.forEachIndexed { index, asc ->
                    ToggleButton(
                        checked = worksOrderAsc == asc,
                        onCheckedChange = { if (it) onSwitchOrderAsc(asc) },
                        modifier = Modifier.semantics { role = Role.RadioButton },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (asc) "升" else "降",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 原版 `UserCollectWorksSubFragment.java:246-255`：非本人隐藏筛选入口
            if (isSelf) {
                IconButton(onClick = onOpenFilter) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "标签筛选",
                        tint = if (worksTag.isNullOrBlank()) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = subPageData.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                subPageData.isLoading && subPageData.items.isEmpty() -> {
                    LoadingCollectView()
                }

                subPageData.errorMessage != null && subPageData.items.isEmpty() && !subPageData.isContentHidden -> {
                    ErrorCollectView(errorMessage = subPageData.errorMessage, onRetry = onRetry)
                }

                subPageData.isContentHidden && subPageData.items.isEmpty() -> {
                    EmptyCollectView(title = COLLECT_CONTENT_HIDDEN_TEXT)
                }

                subPageData.items.isEmpty() -> {
                    EmptyCollectView(title = COLLECT_EMPTY_TEXT)
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 8.dp,
                            end = 12.dp,
                            bottom = 16.dp + navBarBottomPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = subPageData.items,
                            key = { item -> "works_${worksDetailItemId(item)}_${item.id}" }
                        ) { item ->
                            WorksCollectCard(
                                item = item,
                                // 真实实体 id 不可确定时禁止点击，避免带着收藏记录 id 跳错详情
                                onClick = { if (worksDetailItemId(item) > 0L) onItemClick(item) }
                            )
                        }
                        item(key = "works_paging_footer") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when {
                                    subPageData.isPaging -> CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    subPageData.errorMessage != null -> Surface(
                                        onClick = onLoadNextPage,
                                        shape = ExpressiveTokens.ButtonShape,
                                        color = MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Text(
                                            text = "${subPageData.errorMessage}，点击重试",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }

                                    !subPageData.hasMore -> Text(
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

    // 作品页标签筛选抽屉：复用周边页抽屉，但传空 subCategories 隐藏「细分类型」区块
    if (showFilterSheet) {
        CollectFilterBottomSheet(
            subCategoryId = null,
            selectedTag = worksTag,
            availableTags = worksAvailableTags,
            // 抽屉为「暂存-确定」语义：只有与当前生效值不同才提交，
            // 否则用户原样点「确定」会被 VM 的反选逻辑误清空
            onConfirm = { _, tag -> if (tag != worksTag) onSelectTag(tag) },
            onReset = onResetTag,
            onDismiss = onDismissFilter,
            title = "作品收藏标签",
            subCategories = emptyList(),
            tagSectionTitle = "我的作品收藏标签",
            emptyTagHint = "无标签（可在作品收藏编辑页添加自定义标签）"
        )
    }
}