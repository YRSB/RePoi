package com.nobody.poishelf.ui.item.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.nobody.poishelf.ui.common.AdaptiveChunkedGrid
import com.nobody.poishelf.ui.common.FakeSearchField
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.nobody.poishelf.ui.theme.ExpressiveElevatedCard
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nobody.poishelf.data.local.CategoryItem
import com.nobody.poishelf.data.remote.model.HobbyItemSummary
import com.nobody.poishelf.ui.detail.DetailNavParam
import com.nobody.poishelf.ui.item.list.HobbyListNavParam
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.util.CdnUrlUtils
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemHomeScreen(
    modifier: Modifier = Modifier,
    onNavigateToDetail: (DetailNavParam) -> Unit,
    onNavigateToHobbyList: (HobbyListNavParam) -> Unit,
    onNavigateToSearch: (Int) -> Unit = {},
    viewModel: ItemHomeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val isCompact = !adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomContentPadding = if (isCompact) 96.dp else (16.dp + navBarBottomPadding)
    var restored by remember { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            if (restored) {
                viewModel.saveScrollPosition(index, offset)
            }
        }
    }

    LaunchedEffect(uiState.selectedCategoryId) {
        listState.scrollToItem(0)
    }

    LaunchedEffect(uiState.isLoading) {
        if (!restored && !uiState.isLoading) {
            restored = true
            if (viewModel.savedScrollIndex != 0 || viewModel.savedScrollOffset != 0) {
                listState.scrollToItem(viewModel.savedScrollIndex, viewModel.savedScrollOffset)
            }
        }
    }

    var wasRefreshing by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isRefreshing) {
        if (wasRefreshing && !uiState.isRefreshing && uiState.errorMessage == null) {
            listState.scrollToItem(0)
        }
        wasRefreshing = uiState.isRefreshing
    }

    val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = {
                    scope.launch {
                        listState.scrollToItem(0)
                    }
                    viewModel.refresh()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .consumeWindowInsets(WindowInsets.statusBars),
                    contentPadding = PaddingValues(top = statusBarTopPadding, bottom = bottomContentPadding)
                ) {
                    item(key = "search", contentType = "search") {
                        ItemTopSearchBar(
                            onSearchClick = {
                                onNavigateToSearch(uiState.selectedCategoryId)
                            }
                        )
                    }
                    item(key = "tabs", contentType = "tabs") {
                        ItemCategoryTabBar(
                            categories = uiState.categories,
                            selectedCategoryId = uiState.selectedCategoryId,
                            onCategorySelect = { viewModel.selectCategory(it) }
                        )
                    }
                    item(key = "quick", contentType = "quick") {
                        QuickEntranceSection(
                            selectedCategoryId = uiState.selectedCategoryId,
                            onNavigateToHobbyList = onNavigateToHobbyList
                        )
                    }

                    when {
                        uiState.isLoading && !uiState.isRefreshing -> {
                            item(key = "loading", contentType = "loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(280.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }

                        uiState.errorMessage != null && uiState.preOrderItems.isEmpty() && uiState.releaseItems.isEmpty() -> {
                            item(key = "error", contentType = "error") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = uiState.errorMessage ?: "加载失败",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(onClick = { viewModel.retry() }) {
                                            Text("重新加载")
                                        }
                                    }
                                }
                            }
                        }

                        else -> {
                            // 热门预订区块
                            if (uiState.preOrderItems.isNotEmpty()) {
                                item(key = "preorder", contentType = "preview") {
                                    PreviewSectionGrid(
                                        title = "热门预订",
                                        icon = Icons.Filled.CalendarToday,
                                        iconTint = MaterialTheme.colorScheme.primary,
                                        items = uiState.preOrderItems,
                                        onMoreClick = {
                                            val startStr = ymdPlusMonths(2)
                                            onNavigateToHobbyList(
                                                HobbyListNavParam(
                                                    title = "热门预订",
                                                    order = "hits7Day",
                                                    categoryId = uiState.selectedCategoryId,
                                                    hasShopItem = 1,
                                                    releaseStart = startStr
                                                )
                                            )
                                        },
                                        onItemClick = { item ->
                                            onNavigateToDetail(
                                                DetailNavParam(
                                                    itemId = item.actualItemId,
                                                    initialName = item.displayName,
                                                    initialCover = item.cover
                                                )
                                            )
                                        }
                                    )
                                }
                            }

                            // 热门出荷区块
                            if (uiState.releaseItems.isNotEmpty()) {
                                item(key = "release", contentType = "preview") {
                                    PreviewSectionGrid(
                                        title = "热门出荷",
                                        icon = Icons.Filled.Sell,
                                        iconTint = MaterialTheme.colorScheme.secondary,
                                        items = uiState.releaseItems,
                                        onMoreClick = {
                                            val todayStr = ymdToday()
                                            val startStr = ymdPlusMonths(-3)

                                            onNavigateToHobbyList(
                                                HobbyListNavParam(
                                                    title = "热门出荷",
                                                    order = "hits7Day",
                                                    categoryId = uiState.selectedCategoryId,
                                                    hasShopItem = 1,
                                                    releaseStart = startStr,
                                                    releaseEnd = todayStr
                                                )
                                            )
                                        },
                                        onItemClick = { item ->
                                            onNavigateToDetail(
                                                DetailNavParam(
                                                    itemId = item.actualItemId,
                                                    initialName = item.displayName,
                                                    initialCover = item.cover
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

/**
 * 顶栏搜索栏
 */
@Composable
private fun ItemTopSearchBar(
    onSearchClick: () -> Unit
) {
    FakeSearchField(
        onSearchClick = onSearchClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * 分类 Tab 横滑条
 */
@Composable
private fun ItemCategoryTabBar(
    categories: List<CategoryItem>,
    selectedCategoryId: Int,
    onCategorySelect: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { category ->
            val isSelected = category.id == selectedCategoryId

            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelect(category.id) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Done,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else {
                    null
                },
                label = {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                },
                shape = ExpressiveTokens.ChipShape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun QuickEntranceSection(
    selectedCategoryId: Int,
    onNavigateToHobbyList: (HobbyListNavParam) -> Unit
) {
    val today = todayLocalDate()
    val curYear = today.year
    val curMonth = today.monthNumber
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val isCompact = !adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val cardHeight = if (isCompact) 88.dp else 72.dp
    val iconSize = if (isCompact) 26.dp else 24.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
            QuickEntranceItem(
                icon = Icons.Filled.NewReleases,
                title = "最新入库",
                cardHeight = cardHeight,
                iconSize = iconSize,
                onClick = {
                    onNavigateToHobbyList(
                        HobbyListNavParam(
                            title = "最新入库",
                            order = "add",
                            categoryId = selectedCategoryId
                        )
                    )
                }
            )
            QuickEntranceItem(
                icon = Icons.Filled.CalendarToday,
                title = "发售日历",
                cardHeight = cardHeight,
                iconSize = iconSize,
                onClick = {
                    onNavigateToHobbyList(
                        HobbyListNavParam(
                            title = "发售日历",
                            order = "hits",
                            categoryId = selectedCategoryId,
                            releaseYear = curYear,
                            releaseMonth = curMonth
                        )
                    )
                }
            )
            QuickEntranceItem(
                icon = Icons.Filled.Whatshot,
                title = "每周热门",
                cardHeight = cardHeight,
                iconSize = iconSize,
                onClick = {
                    onNavigateToHobbyList(
                        HobbyListNavParam(
                            title = "每周热门",
                            order = "hits7Day",
                            categoryId = selectedCategoryId
                        )
                    )
                }
            )
            QuickEntranceItem(
                icon = Icons.Filled.MilitaryTech,
                title = "好评TOP",
                cardHeight = cardHeight,
                iconSize = iconSize,
                onClick = {
                    onNavigateToHobbyList(
                        HobbyListNavParam(
                            title = "好评TOP",
                            order = "rating",
                            categoryId = selectedCategoryId
                        )
                    )
                }
            )
        }
}
@Composable
private fun RowScope.QuickEntranceItem(
    icon: ImageVector,
    title: String,
    cardHeight: Dp,
    iconSize: Dp,
    onClick: () -> Unit
) {
    val wide = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    ExpressiveElevatedCard(
        onClick = onClick,
        shape = ExpressiveTokens.MediumCardShape,
        modifier = Modifier
            .weight(1f)
            .height(cardHeight)
    ) {
        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}

/**
 * 预览区块：标题栏 + 3 列网格卡片
 */
@Composable
private fun PreviewSectionGrid(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    items: List<HobbyItemSummary>,
    onMoreClick: () -> Unit,
    onItemClick: (HobbyItemSummary) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        // 区块标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(ExpressiveTokens.SmallCardShape)
                        .background(iconTint.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = iconTint
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 查看更多 >
            TextButton(onClick = onMoreClick) {
                Text(text = "查看更多")
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 自适应网格卡片（按可用宽度动态计算 3/4/6 列）
        AdaptiveChunkedGrid(
            items = items,
            maxRows = 3,
            mediumColumns = 3,
            wideColumns = 9,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) { item ->
            PreviewItemCard(
                item = item,
                onClick = { onItemClick(item) }
            )
        }
    }
}

/**
 * 预览卡片（9:13 黄金手办封面比例）
 */
@Composable
private fun PreviewItemCard(
    item: HobbyItemSummary,
    onClick: () -> Unit
) {
    val coverUrl = CdnUrlUtils.getCoverUrl(item.cover)
    var loadFailed by remember(item.cover) { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 13f),
        shape = ExpressiveTokens.MediumCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (coverUrl.isNotBlank() && !loadFailed) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onError = { loadFailed = true }
                )
            } else if (coverUrl.isBlank()) {
                Text(
                    text = "暂无配图",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.BrokenImage,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/**
 * KMP 日期小工具（`Calendar`/`SimpleDateFormat("yyyy-MM-dd")` 替代，行为对齐原版：
 * 月份按 [DatePeriod] 整月平移，年月日零填充）。
 */
@OptIn(ExperimentalTime::class)
private fun todayLocalDate(timeZone: TimeZone = TimeZone.currentSystemDefault()) =
    Clock.System.todayIn(timeZone)

@OptIn(ExperimentalTime::class)
private fun ymd(date: kotlinx.datetime.LocalDate): String =
    "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"

@OptIn(ExperimentalTime::class)
private fun ymdToday(): String = ymd(todayLocalDate())

@OptIn(ExperimentalTime::class)
private fun ymdPlusMonths(months: Int): String =
    ymd(todayLocalDate().plus(DatePeriod(months = months)))
