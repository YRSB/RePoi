package com.nobody.repoi.ui.collect.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 相册页 / 图片页的 3 档二级排序 Tab。
 *
 * 对标原版 `UserCollectActivity.java:110 / :139` 传给 `UserCollectSubPagerFragment` 的同一套 titles：
 * `[{发布时间, addTime}, {收藏时间, actionDate}, {点赞量, praiseCount}]`。
 */
internal val CollectAlbumPictureOrderTabs: List<Pair<String, String>> = listOf(
    "发布时间" to "addTime",
    "收藏时间" to "actionDate",
    "点赞量" to "praiseCount"
)

/**
 * 通用 4 页（厂商/系列/角色/人物）的 2 档二级排序 Tab。
 *
 * 对标原版 `UserCollectItemSubPageFragment.java:57-62`：
 * `[{收藏时间, actionDate}, {人气, collect}]`，白名单仅这两个取值。
 */
internal val CollectCommonOrderTabs: List<Pair<String, String>> = listOf(
    "收藏时间" to "actionDate",
    "人气" to "collect"
)

// ── 共享二级排序 ButtonGroup ────────────────────────────────────

/**
 * 收藏二级排序操作栏（基于 M3 Expressive ButtonGroup 与 ToggleButton）。
 *
 * 通过 [titles] 参数化档位数量，三档（[CollectAlbumPictureOrderTabs]）与两档（[CollectCommonOrderTabs]）共用本实现；
 * 采用连结型 ToggleButton 替换传统嵌套 TabRow，避免外层一级分类 TabRow 产生“Tab 套 Tab”认知混淆，
 * 同时高度更紧凑，节约首屏垂直空间。
 *
 * @param titles 档位列表，元素为 `标签 to 排序 key`
 * @param selected 当前选中的排序 key
 * @param onSelect 点击某档时的回调，回传该档的 key
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectOrderTabRow(
    titles: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalAlignment = Alignment.CenterVertically
        ) {
            titles.forEachIndexed { index, (label, key) ->
                val isSelected = key == selected
                ToggleButton(
                    checked = isSelected,
                    onCheckedChange = { if (it) onSelect(key) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            role = Role.RadioButton
                            this.selected = isSelected
                        },
                    shapes = when {
                        titles.size == 1 -> ToggleButtonDefaults.shapesFor(40.dp)
                        index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        index == titles.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
