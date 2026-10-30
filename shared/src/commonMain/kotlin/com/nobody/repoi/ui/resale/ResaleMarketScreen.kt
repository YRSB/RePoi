package com.nobody.repoi.ui.resale

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.repoi.platform.isLandscapeOrientation
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.ui.home.components.ShimmerHost
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
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.ui.zone.components.ZoneResaleCard
import com.nobody.repoi.ui.zone.components.ZoneResaleCardSkeleton

/**
 * 同款二手与全站二手市场页（复用专区已有的二手设计体系与规范组件）。
 *
 * 功能契约：
 * 1. 顶栏：返回键、标题居中展示“二手商品”（若指定手办则展示手办标题并带有“同款二手”副标）；
 * 2. 筛选栏：直接位于顶栏下方，横向滑动排列【出售/求购 ▾】、【区域 ▾】、【分类 ▾】、【成色 ▾】四个标准 FilterDropdownChip；
 *    - 类型下拉：支持【全部 / 出售 / 求购】；
 *    - 区域抽屉：参考条目页分类底板，采用双栏级联选择器（支持全国34个省级区划及下辖城市区县）；
 *    - 分类下拉：补齐原版全量周边分类（手办、动漫模型、真实模型、娃娃人偶、毛绒布偶、动漫周边）；
 *    - 成色下拉：补齐原版全量成色（全新未拆、仅拆、无暇、拆摆、有瑕疵、断件少件、垃圾成色、转单、配件/赠品）；
 * 3. 内容流：复用专区打磨成熟的 ZoneResaleCard 资产，支持封面大图查看、私信与三点更多菜单；
 * 4. 悬浮按钮：右下角常驻“+”发布二手 FAB（形状与配色 100% 对齐专区 primaryContainer 与圆角矩形令牌）；
 * 5. 自适应网格：竖屏强制 1 列（对齐平板与手机）、普通横屏平板 2 列、超宽屏（≥1200dp）3 列。
 */
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

    // 自适应列数（竖屏强制 1 列、普通横屏平板 2 列、超宽屏 3 列）
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
    val pullRefreshState = rememberPullToRefreshState()

    // 触底自动分页加载
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

    // 抽屉与弹窗状态
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                    imageVector = Icons.Default.Add,
                    contentDescription = "发布二手"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── 1. 四级下拉筛选工具栏（出售/求购 ▾、区域 ▾、分类 ▾、成色 ▾）──
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

            // ── 2. 二手卡片列表流 ──
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.loadInitial(isRefresh = true) },
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    uiState.isLoading && uiState.items.isEmpty() -> {
                        LazyVerticalStaggeredGrid(
                            columns = columns,
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalItemSpacing = 12.dp,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(6) {
                                ShimmerHost { ZoneResaleCardSkeleton() }
                            }
                        }
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
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalItemSpacing = 12.dp,
                            modifier = Modifier.fillMaxSize()
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

                            // 底部触底分页指示器
                            if (uiState.isLoadingMore) {
                                item(span = StaggeredGridItemSpan.FullLine) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp
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

    // ── 3. 交易区域双栏级联选择抽屉 ──
    if (showRegionPickerSheet) {
        ResaleRegionPickerSheet(
            selectedRegionId = uiState.regionNodeId,
            onRegionSelected = { regId, label ->
                viewModel.setRegion(regId, label)
            },
            onDismiss = { showRegionPickerSheet = false }
        )
    }

    // ── 4. 卡片三点底部操作菜单 ──
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

    // ── 5. 二手举报对话框 ──
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

/**
 * 具有展开指示箭头的四级下拉筛选工具栏（出售/求购 ▾、区域 ▾、分类 ▾、成色 ▾）。
 * 采用项目统一的 FilterDropdownChip 组件与横向滚动布局。
 */
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
        // 1. 类型下拉（全部 / 出售 / 求购）
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
                shape = ExpressiveTokens.MediumCardShape,
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

        // 2. 区域筛选（点击调起抽屉底板）
        FilterDropdownChip(
            label = regionLabel,
            isSelected = isRegionActive,
            isExpanded = isRegionPickerOpen,
            onClick = onOpenRegionPicker
        )

        // 3. 分类下拉（对齐原版全量周边分类体系）
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
                shape = ExpressiveTokens.MediumCardShape,
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

        // 4. 成色下拉（对齐原版全量成色规范体系）
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
                shape = ExpressiveTokens.MediumCardShape,
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
