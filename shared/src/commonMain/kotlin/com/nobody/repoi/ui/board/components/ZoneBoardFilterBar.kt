package com.nobody.repoi.ui.board.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.local.CategoryItem
import com.nobody.repoi.ui.board.AlbumBoardOptions
import com.nobody.repoi.ui.board.AlbumCategoryOption
import com.nobody.repoi.ui.board.HobbyBoardOptions
import com.nobody.repoi.ui.board.PostsBoardOptions
import com.nobody.repoi.ui.board.ResaleBoardOptions
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardActions
import com.nobody.repoi.ui.board.ZoneBoardFilters
import com.nobody.repoi.ui.resale.components.FilterDropdownChip
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * 板块折叠筛选条内容（只渲染行内容，不在内部折叠；折叠位移由宿主的 `scrollBehavior` 驱动）。
 *
 * 已迁移动态（9 项单选 Chip）、分享（6 档排序 + 分类入口，#157）、讨论（3 档排序，#158）、
 * 二手（出售/求购分段 + 成色 Chip，#160）与产品（6 档排序 + 分类级联 + 年份 + 月份 + 高级筛选入口，
 * #159）；实体 5 类本身没有筛选项，渲染空内容（不是「待迁移」）。
 *
 * 讨论板块**没有分类入口**：原版 2.43 的讨论分支不请求分类、`api/posts/search` 也不接受任何
 * 分类参数（实测 `itemType`/`category`/`categoryId` 均被忽略），故刻意不渲染分类 Chip，
 * 证据见 [PostsBoardOptions] KDoc。
 *
 * 产品行内的两个弹层（[com.nobody.repoi.ui.item.list.components.HobbyFilterAdaptiveSheet] 与
 * [com.nobody.repoi.ui.item.list.components.CategoryPickerSheet]）由宿主经 `ZoneBoardHobbySheets`
 * 在内容四态之外渲染，本组件只负责「打开」入口与悬浮 Chip 的选中态。
 *
 * @param subTypeOptions 动态筛选选项（键 → 文案）。仅 [ZoneBoard.Action] 分支渲染；其余板块不消费，
 *   故默认空列表。由宿主传入共享定义 `ActionSubType` 派生列表，本组件不持有文案/键值，避免两处定义漂移。
 * @param albumCategoryOptions 相册分类候选（单专区作用域才有值；聚合态传空列表即不渲染分类入口，
 *   与原版「仅 `zoneIds` 可解析为单专区时展示分类」一致）。「全部分类」由本组件追加，候选列表不含它。
 * @param hobbyCategoryTree 产品分类树（`parentId -> 子分类`），用于子分类名称回显与级联弹层。
 * @param hobbyIsGridMode 产品当前是否网格视图（仅决定视图切换按钮图标）。
 * @param showHobbyControlActions 是否渲染产品「高级筛选」入口与网格/列表切换：
 *   首页为 true（自有筛选行）；专区详情为 false（顶栏常驻搜索控制栏已提供两个入口）。
 * @param postsOrderOptions 讨论排序选项（键 → 文案）。来自共享常量 [PostsBoardOptions.ORDERS]，
 *   首页与专区详情不各写一份。
 * @param rowScrollState 横向滚动状态（Chip 条滚动位置）。宿主可传入按板块持久的状态，
 *   实现「切走再切回，筛选条仍停在原处」；默认 `rememberScrollState()` 仅随页存活。
 */
@OptIn(ExperimentalTime::class)
@Composable
fun ZoneBoardFilterBar(
    board: ZoneBoard,
    filters: ZoneBoardFilters,
    actions: ZoneBoardActions,
    subTypeOptions: List<Pair<String, String>> = emptyList(),
    albumCategoryOptions: List<AlbumCategoryOption> = emptyList(),
    hobbyCategoryTree: Map<Int, List<CategoryItem>> = emptyMap(),
    hobbyIsGridMode: Boolean = true,
    showHobbyControlActions: Boolean = true,
    modifier: Modifier = Modifier,
    postsOrderOptions: List<Pair<String, String>> = PostsBoardOptions.ORDERS,
    rowScrollState: ScrollState = rememberScrollState()
) {
    when (board) {
        ZoneBoard.Action -> {
            // 动态二级子分类胶囊
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .horizontalScroll(rowScrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                subTypeOptions.forEach { (key, label) ->
                    val isSelected = filters.subType == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { actions.onSelectSubType(key) },
                        label = { Text(label) }
                    )
                }
            }
        }
        ZoneBoard.Album -> {
            // 分享：6 档排序单选 Chip + 分类下拉入口（分类候选仅单专区存在）
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .horizontalScroll(rowScrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AlbumBoardOptions.ORDERS.forEach { (key, label) ->
                    val isSelected = filters.albumOrder == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { actions.onSelectAlbumOrder(key) },
                        label = { Text(label) }
                    )
                }
                if (albumCategoryOptions.isNotEmpty()) {
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        val selectedCategory = albumCategoryOptions.firstOrNull { it.id == filters.albumCategory }
                        FilterChip(
                            selected = selectedCategory != null,
                            onClick = { expanded = true },
                            label = { Text(selectedCategory?.name ?: "全部分类") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("全部分类") },
                                onClick = {
                                    actions.onSelectAlbumCategory(null)
                                    expanded = false
                                }
                            )
                            albumCategoryOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.name) },
                                    onClick = {
                                        actions.onSelectAlbumCategory(option.id)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        ZoneBoard.Posts -> {
            // 讨论排序胶囊（回复 / 新贴 / 推荐），无分类入口（原版无入口、接口无参数）
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .horizontalScroll(rowScrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                postsOrderOptions.forEach { (key, label) ->
                    val isSelected = filters.postsOrder == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { actions.onSelectPostsOrder(key) },
                        label = { Text(label) }
                    )
                }
            }
        }
        ZoneBoard.Resale -> {
            // 二手：出售/求购分段切换 + 成色单选 Chip（选项来自共享定义，首页与详情同一实现）
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .horizontalScroll(rowScrollState)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SingleChoiceSegmentedButtonRow {
                    ResaleBoardOptions.TYPES.forEachIndexed { index, (typeKey, label) ->
                        SegmentedButton(
                            selected = filters.resaleType == typeKey,
                            onClick = { actions.onSelectResaleType(typeKey) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ResaleBoardOptions.TYPES.size
                            )
                        ) {
                            Text(label)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                ResaleBoardOptions.PURITIES.forEach { (purityKey, label) ->
                    val isSelected = filters.resalePurity == purityKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { actions.onSelectResalePurity(purityKey) },
                        label = { Text(label) }
                    )
                }
            }
        }
        ZoneBoard.Hobby -> {
            // 产品：排序 / 分类级联 / 年份 / 月份（+ 可选高级筛选与视图切换），与专区详情历史布局一致
            val hobby = filters.hobby
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .horizontalScroll(rowScrollState)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. 6 档排序下拉
                Box {
                    var showOrderMenu by remember { mutableStateOf(false) }
                    val currentOrderLabel = HobbyBoardOptions.ORDERS
                        .firstOrNull { it.first == hobby.order }
                        ?.second
                        ?: HobbyBoardOptions.ORDERS.first().second
                    FilterDropdownChip(
                        label = currentOrderLabel,
                        isSelected = hobby.order != HobbyBoardOptions.DEFAULT_ORDER,
                        isExpanded = showOrderMenu,
                        onClick = { showOrderMenu = true }
                    )
                    DropdownMenu(
                        expanded = showOrderMenu,
                        onDismissRequest = { showOrderMenu = false },
                        shape = MaterialTheme.shapes.medium,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        HobbyBoardOptions.ORDERS.forEach { (orderKey, label) ->
                            DropdownMenuItem(
                                text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    actions.onHobbyOrder(orderKey)
                                    showOrderMenu = false
                                }
                            )
                        }
                    }
                }

                // 2. 分类级联入口（分类树缺失时仍可打开弹层，由宿主注入候选）
                val isCategoryActive = hobby.categoryId != HobbyBoardOptions.DEFAULT_CATEGORY_ID ||
                    hobby.subCategoryId != null
                FilterDropdownChip(
                    label = HobbyBoardOptions.categoryLabel(
                        categoryId = hobby.categoryId,
                        subCategoryId = hobby.subCategoryId,
                        categoryTree = hobbyCategoryTree
                    ),
                    isSelected = isCategoryActive,
                    isExpanded = filters.showHobbyCategoryPickerSheet,
                    onClick = { actions.onHobbyCategoryPickerVisible(true) }
                )

                // 3. 年份下拉（候选按当前年份一次性派生，避免每次重组读系统时钟并重建列表）
                val currentYear = remember {
                    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
                }
                val yearOptions = remember(currentYear) { HobbyBoardOptions.yearOptions(currentYear) }
                Box {
                    var showYearMenu by remember { mutableStateOf(false) }
                    val hasYear = (hobby.releaseYear ?: 0) > 0
                    FilterDropdownChip(
                        label = if (hasYear) "${hobby.releaseYear}年" else "不限年",
                        isSelected = hasYear,
                        isExpanded = showYearMenu,
                        onClick = { showYearMenu = true }
                    )
                    DropdownMenu(
                        expanded = showYearMenu,
                        onDismissRequest = { showYearMenu = false },
                        shape = MaterialTheme.shapes.medium,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        yearOptions.forEach { (year, label) ->
                            DropdownMenuItem(
                                text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    actions.onHobbyYear(year)
                                    showYearMenu = false
                                }
                            )
                        }
                    }
                }

                // 4. 月份下拉
                Box {
                    var showMonthMenu by remember { mutableStateOf(false) }
                    val hasMonth = (hobby.releaseMonth ?: 0) > 0
                    FilterDropdownChip(
                        label = if (hasMonth) "${hobby.releaseMonth}月" else "不限月",
                        isSelected = hasMonth,
                        isExpanded = showMonthMenu,
                        onClick = { showMonthMenu = true }
                    )
                    DropdownMenu(
                        expanded = showMonthMenu,
                        onDismissRequest = { showMonthMenu = false },
                        shape = MaterialTheme.shapes.medium,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        HobbyBoardOptions.MONTH_OPTIONS.forEach { (month, label) ->
                            DropdownMenuItem(
                                text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                                onClick = {
                                    actions.onHobbyMonth(month)
                                    showMonthMenu = false
                                }
                            )
                        }
                    }
                }

                if (showHobbyControlActions) {
                    // 5. 高级筛选入口（弹层由 pane 自渲染，选中态表示 4 个高级维度任一已生效）
                    FilterChip(
                        selected = hobby.hasAdvancedFilters,
                        onClick = { actions.onHobbyFilterSheetVisible(true) },
                        label = { Text("高级筛选") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    // 6. 网格 / 列表切换
                    IconButton(
                        onClick = { actions.onHobbyViewMode() }
                    ) {
                        Icon(
                            imageVector = if (hobbyIsGridMode) {
                                Icons.AutoMirrored.Filled.List
                            } else {
                                Icons.Filled.GridView
                            },
                            contentDescription = if (hobbyIsGridMode) "切换列表模式" else "切换网格模式",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        else -> Unit
    }
}
