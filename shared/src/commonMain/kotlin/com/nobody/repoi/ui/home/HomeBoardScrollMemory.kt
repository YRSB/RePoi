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
 * 滚动记忆键：`{scope.stateKey()}|{board.type}`。
 *
 * 形如 `all|action` / `follow:100,200|action` / `z100|action`，与 #156 的持久化键
 * `feed_zone_board = {tabKey}|{type}` 同形对齐（`stateKey()` 的 `all` / `follow:…` / `z{nodeId}`
 * 正好是一级 tabKey 的规范化形式），避免出现两套编码。
 */
fun homeBoardMemoryKey(scope: ZoneQueryScope, board: ZoneBoard): String =
    "${scope.stateKey()}|${board.type}"
