package com.nobody.repoi.ui.collect.subpage

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.remote.model.UserCollectCommonItem
import com.nobody.repoi.ui.collect.SubPageData
import com.nobody.repoi.ui.collect.components.CollectFilterBottomSheet
import com.nobody.repoi.ui.collect.components.CollectOrderTabRow
import com.nobody.repoi.ui.collect.components.EmptyCollectView
import com.nobody.repoi.ui.collect.components.ErrorCollectView
import com.nobody.repoi.ui.collect.components.WorksCollectCard
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import com.nobody.repoi.ui.collect.components.worksDetailItemId
import androidx.compose.foundation.shape.CircleShape
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

internal val WorksCollectStateTabs: List<Pair<String, String>> = listOf(
    "想看" to "1",
    "在看" to "2",
    "看过" to "3"
)

internal fun worksScrollKeyInputs(
    worksState: Int,
    worksOrderAsc: Boolean,
    worksTag: String?,
): Array<out Any?> = arrayOf<Any?>(worksState, worksOrderAsc, worksTag)

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

    val listState = rememberKeyedLazyListState(
        "works_list",
        *worksScrollKeyInputs(worksState, worksOrderAsc, worksTag)
    )

    val shouldLoadMore by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
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

    val sortArrowRotation by animateFloatAsState(
        targetValue = if (worksOrderAsc) 180f else 0f,
        label = "WorksSortArrowRotation"
    )

    Column(modifier = Modifier.fillMaxSize()) {

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
                modifier = Modifier
                    .padding(end = if (isSelf) 4.dp else 12.dp)
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable { onSwitchOrderAsc(!worksOrderAsc) }
                    .semantics { role = Role.Button }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (worksOrderAsc) "升序" else "降序",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    painter = painterResource(PoiIcons.ArrowDownward),
                    contentDescription = if (worksOrderAsc) "当前升序，点击切换为降序" else "当前降序，点击切换为升序",
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = sortArrowRotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelf) {
                IconButton(onClick = onOpenFilter) {
                    Icon(
                        painter = painterResource(PoiIcons.FilterList),
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
                    PoiLoadingView()
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
                                    subPageData.isPaging -> LoadingIndicator(
                                        modifier = Modifier.size(28.dp)
                                    )

                                    subPageData.errorMessage != null -> Surface(
                                        onClick = onLoadNextPage,
                                        shape = CircleShape,
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

    if (showFilterSheet) {
        CollectFilterBottomSheet(
            subCategoryId = null,
            selectedTag = worksTag,
            availableTags = worksAvailableTags,

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
