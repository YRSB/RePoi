package com.nobody.repoi.ui.resale

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import com.nobody.repoi.ui.common.PoiPullToRefreshBox
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.repoi.platform.isLandscapeOrientation
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.ui.common.loading.PoiLoadingView
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobody.repoi.data.remote.model.ResaleSearchItem
import com.nobody.repoi.ui.resale.components.FilterDropdownChip
import com.nobody.repoi.ui.resale.components.ResaleMoreBottomSheet
import com.nobody.repoi.ui.resale.components.ResaleRegionPickerSheet
import com.nobody.repoi.ui.resale.components.ResaleReportDialog
import com.nobody.repoi.ui.zone.components.ZoneResaleCard
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResaleMarketScreen(
    hobbyId: Long,
    title: String?,
    onBack: () -> Unit,
    onNavigateToResaleDetail: (Long) -> Unit,
    onNavigateToHobby: (Long) -> Unit,
    onNavigateToUser: (Long) -> Unit,
    onNavigateToChat: (Long, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: ResaleMarketViewModel = koinViewModel()
) {
    val messages = LocalAppMessages.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(hobbyId, title) {
        viewModel.init(hobbyId, title)
    }

    val isLandscape = isLandscapeOrientation()
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val columns = remember(adaptiveInfo.windowSizeClass, isLandscape) {
        when {
            !isLandscape -> StaggeredGridCells.Fixed(1)
            adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) -> StaggeredGridCells.Fixed(3)
            else -> StaggeredGridCells.Fixed(2)
        }
    }

    val gridState = rememberLazyStaggeredGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadNextPage()
        }
    }

    var showRegionPickerSheet by rememberSaveable { mutableStateOf(false) }
    var activeMoreItem by remember { mutableStateOf<ResaleSearchItem?>(null) }
    var reportTargetItem by remember { mutableStateOf<ResaleSearchItem?>(null) }
    var isReporting by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (uiState.hobbyId > 0L && !title.isNullOrBlank()) title else "二手商品",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        if (uiState.hobbyId > 0L) {
                            Text(
                                text = "同款二手",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(PoiIcons.AutoMirrored.Filled.ArrowBack),
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    messages.show("发布二手功能即将上线")
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    painter = painterResource(PoiIcons.Add),
                    contentDescription = "发布二手"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(PaddingValues(top = innerPadding.calculateTopPadding()))
        ) {

            ResaleDropdownFilterBar(
                currentType = uiState.type,
                regionLabel = uiState.regionLabel,
                isRegionActive = uiState.regionNodeId != null,
                isRegionPickerOpen = showRegionPickerSheet,
                currentCategory = uiState.category,
                categoryLabel = uiState.categoryLabel,
                currentPurity = uiState.purity,
                purityLabel = uiState.purityLabel,
                onTypeSelect = { viewModel.setType(it) },
                onOpenRegionPicker = { showRegionPickerSheet = true },
                onCategorySelect = { catId, label -> viewModel.setCategory(catId, label) },
                onPuritySelect = { pId, label -> viewModel.setPurity(pId, label) }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            PoiPullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.loadInitial(isRefresh = true) },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    uiState.isLoading && uiState.items.isEmpty() -> {
                        PoiLoadingView()
                    }
                    uiState.errorMessage != null && uiState.items.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = uiState.errorMessage ?: "加载失败",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Button(onClick = { viewModel.loadInitial() }) {
                                    Text("点击重试")
                                }
                            }
                        }
                    }
                    uiState.items.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无相关二手信息",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    else -> {
                        LazyVerticalStaggeredGrid(
                            columns = columns,
                            state = gridState,
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                top = 16.dp,
                                end = 16.dp,
                                bottom = 88.dp + innerPadding.calculateBottomPadding()
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalItemSpacing = 12.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .consumeWindowInsets(PaddingValues(bottom = innerPadding.calculateBottomPadding()))
                        ) {
                            items(
                                items = uiState.items,
                                key = { item -> "market_resale_${item.id}" },
                                contentType = { "market_resale" }
                            ) { item ->
                                ZoneResaleCard(
                                    item = item,
                                    onClick = {
                                        if (item.isBuy) {
                                            val targetHobbyId = item.targetItemId
                                            if (targetHobbyId > 0L) {
                                                onNavigateToHobby(targetHobbyId)
                                            } else {
                                                messages.show("未关联条目详情")
                                            }
                                        } else {
                                            onNavigateToResaleDetail(item.id)
                                        }
                                    },
                                    onUserClick = { userId ->
                                        onNavigateToUser(userId)
                                    },
                                    onContactClick = {
                                        val targetUserId = item.user?.userId ?: 0L
                                        val nickname = item.user?.nickname ?: "卖家"
                                        if (targetUserId > 0L) {
                                            onNavigateToChat(targetUserId, nickname)
                                        } else {
                                            messages.show("暂无卖家联系方式")
                                        }
                                    },
                                    onMoreClick = {
                                        activeMoreItem = item
                                    }
                                )
                            }

                            if (uiState.isLoadingMore) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LoadingIndicator(
                                            modifier = Modifier.size(28.dp)
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

    if (showRegionPickerSheet) {
        ResaleRegionPickerSheet(
            selectedRegionId = uiState.regionNodeId,
            onRegionSelected = { regId, label ->
                viewModel.setRegion(regId, label)
            },
            onDismiss = { showRegionPickerSheet = false }
        )
    }

    val moreItem = activeMoreItem
    if (moreItem != null) {
        ResaleMoreBottomSheet(
            item = moreItem,
            onDismiss = { activeMoreItem = null },
            onSelectSameResale = { selectedItem ->
                val newHobbyId = selectedItem.targetItemId
                if (newHobbyId > 0L && newHobbyId != hobbyId) {
                    val hName = selectedItem.item?.firstOrNull()?.nameCN ?: selectedItem.item?.firstOrNull()?.name
                    viewModel.init(newHobbyId, hName)
                } else {
                    viewModel.loadInitial(isRefresh = true)
                }
            },
            onSelectReport = { reportTargetItem = it }
        )
    }

    val reportItem = reportTargetItem
    if (reportItem != null) {
        ResaleReportDialog(
            isSubmitting = isReporting,
            onDismiss = { if (!isReporting) reportTargetItem = null },
            onSubmit = { reason ->
                isReporting = true
                viewModel.submitReport(reportItem.id, reason) { success, msg ->
                    isReporting = false
                    messages.show(msg)
                    if (success) {
                        reportTargetItem = null
                    }
                }
            }
        )
    }
}

@Composable
private fun ResaleDropdownFilterBar(
    currentType: Int,
    regionLabel: String,
    isRegionActive: Boolean,
    isRegionPickerOpen: Boolean,
    currentCategory: String?,
    categoryLabel: String,
    currentPurity: String?,
    purityLabel: String,
    onTypeSelect: (Int) -> Unit,
    onOpenRegionPicker: () -> Unit,
    onCategorySelect: (String?, String) -> Unit,
    onPuritySelect: (String?, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var typeMenuOpen by remember { mutableStateOf(false) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var purityMenuOpen by remember { mutableStateOf(false) }

    fun isOptionActive(currentId: String?, optionId: String?): Boolean {
        return (currentId == null && optionId == null) || (currentId != null && currentId == optionId)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box {
            val typeLabel = when (currentType) {
                1 -> "出售"
                2 -> "求购"
                else -> "全部"
            }
            FilterDropdownChip(
                label = typeLabel,
                isSelected = currentType == 1 || currentType == 2,
                isExpanded = typeMenuOpen,
                onClick = { typeMenuOpen = true }
            )
            DropdownMenu(
                expanded = typeMenuOpen,
                onDismissRequest = { typeMenuOpen = false },
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "全部",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (currentType == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onTypeSelect(0)
                        typeMenuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "出售",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (currentType == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onTypeSelect(1)
                        typeMenuOpen = false
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "求购",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (currentType == 2) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onTypeSelect(2)
                        typeMenuOpen = false
                    }
                )
            }
        }

        FilterDropdownChip(
            label = regionLabel,
            isSelected = isRegionActive,
            isExpanded = isRegionPickerOpen,
            onClick = onOpenRegionPicker
        )

        Box {
            val isCatSelected = currentCategory != null
            FilterDropdownChip(
                label = categoryLabel,
                isSelected = isCatSelected,
                isExpanded = categoryMenuOpen,
                onClick = { categoryMenuOpen = true }
            )
            val categories = listOf(
                null to "全部分类",
                "100" to "手办",
                "200" to "动漫模型",
                "500" to "真实模型",
                "300" to "娃娃人偶",
                "400" to "毛绒布偶",
                "900" to "动漫周边"
            )
            DropdownMenu(
                expanded = categoryMenuOpen,
                onDismissRequest = { categoryMenuOpen = false },
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                categories.forEach { (catId, name) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isOptionActive(currentCategory, catId)) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onCategorySelect(catId, if (catId == null) "分类" else name)
                            categoryMenuOpen = false
                        }
                    )
                }
            }
        }

        Box {
            val isPuritySelected = currentPurity != null
            FilterDropdownChip(
                label = purityLabel,
                isSelected = isPuritySelected,
                isExpanded = purityMenuOpen,
                onClick = { purityMenuOpen = true }
            )
            val purities = listOf(
                null to "全部成色",
                "10" to "全新未拆",
                "9" to "仅拆",
                "8" to "无暇",
                "7" to "拆摆",
                "6" to "有瑕疵",
                "5" to "断件少件",
                "4" to "垃圾成色",
                "11" to "转单",
                "12" to "配件/赠品"
            )
            DropdownMenu(
                expanded = purityMenuOpen,
                onDismissRequest = { purityMenuOpen = false },
                shape = MaterialTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                purities.forEach { (purityId, name) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isOptionActive(currentPurity, purityId)) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onPuritySelect(purityId, if (purityId == null) "成色" else name)
                            purityMenuOpen = false
                        }
                    )
                }
            }
        }
    }
}
