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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.local.CategoryItem
import com.nobody.repoi.ui.board.AlbumBoardOptions
import com.nobody.repoi.ui.board.AlbumCategoryOption
import com.nobody.repoi.ui.board.HobbyBoardOptions
import com.nobody.repoi.ui.board.PostsBoardOptions
import com.nobody.repoi.ui.board.ResaleBoardOptions
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardFilters
import com.nobody.repoi.ui.board.session.ZoneBoardIntent
import com.nobody.repoi.ui.resale.components.FilterDropdownChip
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalTime::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ZoneBoardFilterBar(
    board: ZoneBoard,
    filters: ZoneBoardFilters,
    onIntent: (ZoneBoardIntent) -> Unit,
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
                        onClick = { onIntent(ZoneBoardIntent.SelectSubType(key)) },
                        label = { Text(label) }
                    )
                }
            }
        }
        ZoneBoard.Album -> {

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
                        onClick = { onIntent(ZoneBoardIntent.SelectAlbumOrder(key)) },
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
                                    painter = painterResource(PoiIcons.Filled.ArrowDropDown),
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
                                    onIntent(ZoneBoardIntent.SelectAlbumCategory(null))
                                    expanded = false
                                }
                            )
                            albumCategoryOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.name) },
                                    onClick = {
                                        onIntent(ZoneBoardIntent.SelectAlbumCategory(option.id))
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
                        onClick = { onIntent(ZoneBoardIntent.SelectPostsOrder(key)) },
                        label = { Text(label) }
                    )
                }
            }
        }
        ZoneBoard.Resale -> {

            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .horizontalScroll(rowScrollState)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ResaleBoardOptions.TYPES.forEachIndexed { index, (typeKey, label) ->
                        val isSelected = filters.resaleType == typeKey
                        ToggleButton(
                            checked = isSelected,
                            onCheckedChange = { if (!isSelected) onIntent(ZoneBoardIntent.SelectResaleType(typeKey)) },
                            modifier = Modifier.semantics {
                                role = Role.RadioButton
                                this.selected = isSelected
                            },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                ResaleBoardOptions.TYPES.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                        ) {
                            Text(
                                text = label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                ResaleBoardOptions.PURITIES.forEach { (purityKey, label) ->
                    val isSelected = filters.resalePurity == purityKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { onIntent(ZoneBoardIntent.SelectResalePurity(purityKey)) },
                        label = { Text(label) }
                    )
                }
            }
        }
        ZoneBoard.Hobby -> {

            val hobby = filters.hobby
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .horizontalScroll(rowScrollState)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

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
                                    onIntent(ZoneBoardIntent.SelectHobbyOrder(orderKey))
                                    showOrderMenu = false
                                }
                            )
                        }
                    }
                }

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
                    onClick = { onIntent(ZoneBoardIntent.SetHobbyCategoryPickerVisible(true)) }
                )

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
                                    onIntent(ZoneBoardIntent.SelectHobbyYear(year))
                                    showYearMenu = false
                                }
                            )
                        }
                    }
                }

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
                                    onIntent(ZoneBoardIntent.SelectHobbyMonth(month))
                                    showMonthMenu = false
                                }
                            )
                        }
                    }
                }

                if (showHobbyControlActions) {

                    FilterChip(
                        selected = hobby.hasAdvancedFilters,
                        onClick = { onIntent(ZoneBoardIntent.SetHobbyFilterSheetVisible(true)) },
                        label = { Text("高级筛选") },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(PoiIcons.Filled.FilterList),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    IconButton(
                        onClick = { onIntent(ZoneBoardIntent.ToggleHobbyViewMode) }
                    ) {
                        Icon(
                            painter = painterResource(if (hobbyIsGridMode) PoiIcons.AutoMirrored.Filled.List else PoiIcons.Filled.GridView),
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
