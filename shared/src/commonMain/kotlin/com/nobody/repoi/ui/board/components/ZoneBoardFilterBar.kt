package com.nobody.repoi.ui.board.components

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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import com.nobody.repoi.ui.board.AlbumBoardOptions
import com.nobody.repoi.ui.board.AlbumCategoryOption
import com.nobody.repoi.ui.board.PostsBoardOptions
import com.nobody.repoi.ui.board.ResaleBoardOptions
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardActions
import com.nobody.repoi.ui.board.ZoneBoardFilters

/**
 * 板块折叠筛选条内容（只渲染行内容，不在内部折叠；折叠位移由宿主的 `scrollBehavior` 驱动）。
 *
 * 已迁移动态（9 项单选 Chip）、分享（6 档排序 + 分类入口，#157）、讨论（3 档排序，#158）与
 * 二手（出售/求购分段 + 成色 Chip，#160）；其余板块由宿主暂时保留原内联分支，后续板块 ticket 在 [when] 内逐个补。
 *
 * 讨论板块**没有分类入口**：原版 2.43 的讨论分支不请求分类、`api/posts/search` 也不接受任何
 * 分类参数（实测 `itemType`/`category`/`categoryId` 均被忽略），故刻意不渲染分类 Chip，
 * 证据见 [PostsBoardOptions] KDoc。
 *
 * @param subTypeOptions 动态筛选选项（键 → 文案）。由宿主传入共享定义 `ActionSubType` 派生列表，
 *   本组件不持有文案/键值，避免两处定义漂移。
 * @param albumCategoryOptions 相册分类候选（单专区作用域才有值；聚合态传空列表即不渲染分类入口，
 *   与原版「仅 `zoneIds` 可解析为单专区时展示分类」一致）。「全部分类」由本组件追加，候选列表不含它。
 * @param postsOrderOptions 讨论排序选项（键 → 文案）。来自共享常量 [PostsBoardOptions.ORDERS]，
 *   首页与专区详情不各写一份。
 */
@Composable
fun ZoneBoardFilterBar(
    board: ZoneBoard,
    filters: ZoneBoardFilters,
    subTypeOptions: List<Pair<String, String>>,
    actions: ZoneBoardActions,
    albumCategoryOptions: List<AlbumCategoryOption> = emptyList(),
    modifier: Modifier = Modifier,
    postsOrderOptions: List<Pair<String, String>> = PostsBoardOptions.ORDERS
) {
    when (board) {
        ZoneBoard.Action -> {
            // 动态二级子分类胶囊
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                subTypeOptions.forEach { (key, label) ->
                    val isSelected = filters.subType == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { actions.onSelectSubType(key) },
                        label = { Text(label) },
                        shape = MaterialTheme.shapes.small
                    )
                }
            }
        }
        ZoneBoard.Album -> {
            // 分享：6 档排序单选 Chip + 分类下拉入口（分类候选仅单专区存在）
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AlbumBoardOptions.ORDERS.forEach { (key, label) ->
                    val isSelected = filters.albumOrder == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { actions.onSelectAlbumOrder(key) },
                        label = { Text(label) },
                        shape = MaterialTheme.shapes.small
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
                            },
                            shape = MaterialTheme.shapes.small
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
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                postsOrderOptions.forEach { (key, label) ->
                    val isSelected = filters.postsOrder == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { actions.onSelectPostsOrder(key) },
                        label = { Text(label) },
                        shape = MaterialTheme.shapes.small
                    )
                }
            }
        }
        ZoneBoard.Resale -> {
            // 二手：出售/求购分段切换 + 成色单选 Chip（选项来自共享定义，首页与详情同一实现）
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
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
                        label = { Text(label) },
                        shape = MaterialTheme.shapes.small
                    )
                }
            }
        }
        else -> Unit
    }
}
