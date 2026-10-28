package com.nobody.poishelf.ui.collect.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

// ── 共享二级排序 Tab ────────────────────────────────────────

/**
 * 收藏二级排序 Tab 行（等价于原版 `UserCollectSubPagerFragment` 的排序切换组件）。
 *
 * 通过 [titles] 参数化档位数量，三档（[CollectAlbumPictureOrderTabs]）与两档（[CollectCommonOrderTabs]）共用本实现；
 * 样式与宿主层「8 大实体大类」TabRow 保持一致。
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
        SecondaryScrollableTabRow(
            selectedTabIndex = titles.indexOfFirst { it.second == selected }.coerceAtLeast(0),
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
        ) {
            titles.forEach { (label, key) ->
                val isSelected = key == selected
                Tab(
                    selected = isSelected,
                    onClick = { onSelect(key) },
                    text = {
                        Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                )
            }
        }
    }
}