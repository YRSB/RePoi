package com.nobody.repoi.ui.board.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardActions
import com.nobody.repoi.ui.board.ZoneBoardFilters

/**
 * 板块折叠筛选条内容（只渲染行内容，不在内部折叠；折叠位移由宿主的 `scrollBehavior` 驱动）。
 *
 * A 阶段只迁移动态板块的 9 项单选 `FilterChip` 行（从 `ZoneDetailScreen` 内联分支抽出）；
 * 其余板块由宿主暂时保留原内联分支，B 阶段在 [when] 内逐个补。
 *
 * @param subTypeOptions 动态筛选选项（键 → 文案）。A 阶段由宿主传入 `ZoneDetailViewModel.subTypes`；
 *   #153 合入后改传共享定义，本组件不持有文案/键值，避免两处定义漂移。
 */
@Composable
fun ZoneBoardFilterBar(
    board: ZoneBoard,
    filters: ZoneBoardFilters,
    subTypeOptions: List<Pair<String, String>>,
    actions: ZoneBoardActions,
    modifier: Modifier = Modifier
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
        else -> Unit
    }
}
