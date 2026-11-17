package com.nobody.repoi.ui.home

import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.data.remote.model.ZoneQueryScope

/**
 * 单个「作用域 + 板块」的滚动记忆（仅内存，持久化见 #156）。
 *
 * @param index 首个可见条目下标
 * @param offset 首个可见条目滚动偏移（px）
 * @param filterCollapseFraction 三级筛选层折叠比例（0 = 完全展开，1 = 完全收起）
 */
data class HomeBoardScrollMemory(
    val index: Int = 0,
    val offset: Int = 0,
    val filterCollapseFraction: Float = 0f
)

/**
 * 滚动记忆键：`{scopeKey}|{board.type}`，如 `all|action` / `follow:100,200|action` / `z100|action`。
 *
 * 关注作用域对 id **归一化**（去重 + 升序）后再拼接：关注集合的顺序随 `ZoneTabsSettings.sort`
 * （`recent` / `custom`）变化，若直接沿用原序，同一逻辑作用域会产生多个键，切换后取不到旧记忆
 * 并让宿主的恢复副作用反复触发。`All` / `Single` 保持原样。
 *
 * 注意：本键仅用于内存滚动记忆，**不是** `feed_zone_board` 的落盘形态（落盘一级部分是 `follow`，
 * 不带 id 列表），两者不要互相解码。
 */
fun homeBoardMemoryKey(scope: ZoneQueryScope, board: ZoneBoard): String {
    val scopeKey = if (scope is ZoneQueryScope.Follow) {
        "follow:" + scope.zoneIds.distinct().sorted().joinToString(",")
    } else {
        scope.stateKey()
    }
    return "$scopeKey|${board.type}"
}
