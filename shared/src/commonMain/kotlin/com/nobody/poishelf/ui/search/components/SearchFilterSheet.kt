package com.nobody.poishelf.ui.search.components

import com.nobody.poishelf.platform.PoiShelfBackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nobody.poishelf.data.local.CategoryItem
import com.nobody.poishelf.data.remote.model.SearchCompanyAggregate
import com.nobody.poishelf.data.remote.model.SearchYearAggregate
import com.nobody.poishelf.ui.search.AlbumSortOption
import com.nobody.poishelf.ui.search.AlbumSubCategory
import com.nobody.poishelf.ui.search.ElementsSubCategory
import com.nobody.poishelf.ui.search.HobbySortOption
import com.nobody.poishelf.ui.search.SearchTab
import com.nobody.poishelf.ui.search.HobbyAllCategory
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import kotlinx.coroutines.delay

/** 侧边抽屉动画时长常量（毫秒） */
private const val SIDE_SHEET_ANIMATION_DURATION_MS = 260

/**
 * 周边筛选状态封装（消除参数泥团）
 */
data class HobbyFilterState(
    val categories: List<CategoryItem> = emptyList(),
    val selectedCategory: Int = HobbyAllCategory.id,
    val companies: List<SearchCompanyAggregate> = emptyList(),
    val selectedCompany: SearchCompanyAggregate? = null,
    val years: List<SearchYearAggregate> = emptyList(),
    val selectedYear: String? = null
)

/**
 * 相册筛选状态封装
 */
data class AlbumFilterState(
    val selectedCategory: AlbumSubCategory = AlbumSubCategory.ALL
)

/**
 * 角色/要素筛选状态封装
 */
data class ElementsFilterState(
    val selectedCategory: ElementsSubCategory = ElementsSubCategory.ALL,
    val attributePairs: List<Pair<Int, String>> = emptyList(),
    val selectedAttribute: Int = 0
)

/**
 * 通用 FilterChip 流式区块组件：
 * 消除多处重复的 Column + Title + FlowRow + FilterChip 模板代码
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> FilterChipSection(
    title: String,
    items: List<T>,
    selectedPredicate: (T) -> Boolean,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    showLeadingCheckmark: Boolean = true
) {
    if (items.isEmpty()) return
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items.forEach { item ->
                val isSelected = selectedPredicate(item)
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(item) },
                    label = { Text(label(item)) },
                    leadingIcon = if (showLeadingCheckmark && isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Done,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null,
                    shape = ExpressiveTokens.ChipShape
                )
            }
        }
    }
}

/**
 * Material Design 3 Modal Side Sheet 组件：
 * 专为平板与横屏大屏设计，从屏幕右侧平滑滑入，具备背景半透明遮罩与返回键手势关闭。
 */
@Composable
fun ModalSideSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isClosing by remember { mutableStateOf(false) }
    var hasEntered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasEntered = true
    }

    val transition = updateTransition(targetState = hasEntered && !isClosing, label = "ModalSideSheetTransition")
    val offsetX by transition.animateFloat(
        transitionSpec = { tween(durationMillis = SIDE_SHEET_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing) },
        label = "SideSheetOffset"
    ) { visible ->
        if (visible) 0f else 1f
    }
    val scrimAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = SIDE_SHEET_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing) },
        label = "SideSheetScrimAlpha"
    ) { visible ->
        if (visible) 0.36f else 0f
    }

    val closeWithAnimation: () -> Unit = remember {
        {
            if (!isClosing) {
                isClosing = true
            }
        }
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            delay(SIDE_SHEET_ANIMATION_DURATION_MS.toLong())
            onDismissRequest()
        }
    }

    PoiShelfBackHandler(onBack = closeWithAnimation)

    val sideSheetNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // 全额消费抽屉内可滚动视图未消费的剩余滚动位移，防止过滚动事件穿透泄漏至底层
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return available
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
    ) {
        // 1. 全屏半透明遮罩 (Scrim)：全屏铺满压暗背景，抽屉圆角外切角处自然透出暗色遮罩，彻底消除白色直角
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .testTag("modal_side_sheet_scrim")
                .semantics(mergeDescendants = true) {
                    contentDescription = "关闭遮罩"
                    onClick {
                        closeWithAnimation()
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { closeWithAnimation() }
                    )
                }
        )

        // 2. 右侧抽屉面板：居右停靠浮于遮罩之上，自身消费空白处点击防止击穿到底层遮罩
        Box(
            modifier = modifier
                .align(Alignment.CenterEnd)
                .width(380.dp)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = size.width * offsetX
                }
                .nestedScroll(sideSheetNestedScrollConnection)
                .pointerInput(Unit) {
                    detectTapGestures {
                        // 消费抽屉空白处的点击，防止事件击穿透传到底层遮罩
                    }
                }
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = ExpressiveTokens.SideSheetShape
                )
        ) {
            Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                content()
            }
        }
    }
}

/**
 * 自适应搜索筛选与排序面板：
 * 宽屏/平板使用 Modal Side Sheet（右侧抽屉），窄屏/手机使用 Modal Bottom Sheet（底部抽屉）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchFilterAdaptiveSheet(
    isWideScreen: Boolean,
    selectedTab: SearchTab,
    order: String,
    onSortChange: (String) -> Unit,
    hobbyState: HobbyFilterState,
    onHobbyCategorySelect: (Int) -> Unit,
    onCompanySelect: (SearchCompanyAggregate?) -> Unit,
    onYearSelect: (String?) -> Unit,
    albumState: AlbumFilterState,
    onAlbumCategorySelect: (AlbumSubCategory) -> Unit,
    elementsState: ElementsFilterState,
    onElementsCategorySelect: (ElementsSubCategory) -> Unit,
    onAttributeSelect: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    if (isWideScreen) {
        ModalSideSheet(onDismissRequest = onDismiss) {
            SearchFilterSheetContent(
                selectedTab = selectedTab,
                order = order,
                onSortChange = onSortChange,
                hobbyState = hobbyState,
                onHobbyCategorySelect = onHobbyCategorySelect,
                onCompanySelect = onCompanySelect,
                onYearSelect = onYearSelect,
                albumState = albumState,
                onAlbumCategorySelect = onAlbumCategorySelect,
                elementsState = elementsState,
                onElementsCategorySelect = onElementsCategorySelect,
                onAttributeSelect = onAttributeSelect,
                onReset = onReset,
                onDismiss = onDismiss
            )
        }
    } else {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            SearchFilterSheetContent(
                selectedTab = selectedTab,
                order = order,
                onSortChange = onSortChange,
                hobbyState = hobbyState,
                onHobbyCategorySelect = onHobbyCategorySelect,
                onCompanySelect = onCompanySelect,
                onYearSelect = onYearSelect,
                albumState = albumState,
                onAlbumCategorySelect = onAlbumCategorySelect,
                elementsState = elementsState,
                onElementsCategorySelect = onElementsCategorySelect,
                onAttributeSelect = onAttributeSelect,
                onReset = onReset,
                onDismiss = onDismiss
            )
        }
    }
}

/**
 * 筛选与排序统一内容呈现组件（高内聚单 when 分支呈现）
 */
@Composable
fun SearchFilterSheetContent(
    selectedTab: SearchTab,
    order: String,
    onSortChange: (String) -> Unit,
    hobbyState: HobbyFilterState,
    onHobbyCategorySelect: (Int) -> Unit,
    onCompanySelect: (SearchCompanyAggregate?) -> Unit,
    onYearSelect: (String?) -> Unit,
    albumState: AlbumFilterState,
    onAlbumCategorySelect: (AlbumSubCategory) -> Unit,
    elementsState: ElementsFilterState,
    onElementsCategorySelect: (ElementsSubCategory) -> Unit,
    onAttributeSelect: (Int) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. 顶部标题栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "筛选与排序",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. 中间可滚动区域：每个 Tab 内聚定义专属筛选维度
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when (selectedTab) {
                SearchTab.HOBBY -> {
                    // 1. 排序方式
                    FilterChipSection(
                        title = "排序方式",
                        items = HobbySortOption.entries,
                        selectedPredicate = { order == it.key },
                        onSelect = { onSortChange(it.key) },
                        label = { it.label }
                    )

                    // 2. 周边分类
                    FilterChipSection(
                        title = "周边分类",
                        items = hobbyState.categories,
                        selectedPredicate = { hobbyState.selectedCategory == it.id },
                        onSelect = { onHobbyCategorySelect(it.id) },
                        label = { it.name }
                    )

                    // 3. 制作厂商
                    val companyItems = listOf<SearchCompanyAggregate?>(null) + hobbyState.companies
                    FilterChipSection(
                        title = "制作厂商",
                        items = companyItems,
                        selectedPredicate = {
                            if (it == null) hobbyState.selectedCompany == null
                            else hobbyState.selectedCompany?.nodeId == it.nodeId
                        },
                        onSelect = { onCompanySelect(it) },
                        label = { it?.let { c -> "${c.itemName} (${c.docCount})" } ?: "全部厂商" }
                    )

                    // 4. 发售年份
                    val yearItems = listOf<SearchYearAggregate?>(null) + hobbyState.years
                    FilterChipSection(
                        title = "发售年份",
                        items = yearItems,
                        selectedPredicate = {
                            if (it == null) hobbyState.selectedYear == null
                            else hobbyState.selectedYear == it.keyAsString
                        },
                        onSelect = { onYearSelect(it?.keyAsString) },
                        label = { it?.let { y -> "${y.keyAsString} (${y.docCount})" } ?: "全部年份" }
                    )
                }

                SearchTab.ALBUM -> {
                    // 1. 排序方式
                    FilterChipSection(
                        title = "排序方式",
                        items = AlbumSortOption.entries,
                        selectedPredicate = { order == it.key },
                        onSelect = { onSortChange(it.key) },
                        label = { it.label }
                    )

                    // 2. 相册分类
                    FilterChipSection(
                        title = "相册分类",
                        items = AlbumSubCategory.entries,
                        selectedPredicate = { albumState.selectedCategory == it },
                        onSelect = { onAlbumCategorySelect(it) },
                        label = { it.label }
                    )
                }

                SearchTab.WORKS_ELEMENT -> {
                    // 1. 要素分类
                    FilterChipSection(
                        title = "要素分类",
                        items = ElementsSubCategory.entries,
                        selectedPredicate = { elementsState.selectedCategory == it },
                        onSelect = { onElementsCategorySelect(it) },
                        label = { it.label }
                    )

                    // 2. 属性筛选
                    val attrItems = listOf(0 to "全部属性") + elementsState.attributePairs
                    FilterChipSection(
                        title = "属性筛选",
                        items = attrItems,
                        selectedPredicate = { elementsState.selectedAttribute == it.first },
                        onSelect = { onAttributeSelect(it.first) },
                        label = { it.second }
                    )
                }

                else -> {}
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        FilterSheetActions(
            onReset = onReset,
            onDismiss = onDismiss
        )
    }
}

/**
 * 筛选抽屉共用底部固定操作栏（重置 + 完成）
 */
@Composable
fun FilterSheetActions(
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(
            onClick = onReset,
            shape = ExpressiveTokens.ButtonShape,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            Text("重置", fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onDismiss,
            shape = ExpressiveTokens.ButtonShape,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
        ) {
            Text("完成", fontWeight = FontWeight.Bold)
        }
    }
}


