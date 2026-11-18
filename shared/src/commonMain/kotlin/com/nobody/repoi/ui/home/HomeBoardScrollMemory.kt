package com.nobody.repoi.ui.home

import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.ui.board.session.BoardScrollPosition

/**
 * 单个「作用域 + 板块」的滚动记忆（仅内存，持久化见 #156）。
 *
 * @param index 首个可见条目下标
 * @param offset 首个可见条目滚动偏移（px）
 * @param filterCollapseFraction 三级筛选层折叠比例（0 = 完全展开，1 = 完全收起）
 * @param filterRowScrollX 三级筛选层横向滚动位置（px）：Chip 条滚到一半，切回仍在原处
 */
data class HomeBoardScrollMemory(
    val index: Int = 0,
    val offset: Int = 0,
    val filterCollapseFraction: Float = 0f,
    val filterRowScrollX: Int = 0
)

/** 将深模块 [BoardScrollPosition] 转换为首页滚动记忆。 */
fun BoardScrollPosition.toHomeScrollMemory(): HomeBoardScrollMemory = HomeBoardScrollMemory(
    index = index,
    offset = offset,
    filterCollapseFraction = filterCollapseFraction,
    filterRowScrollX = filterRowScrollX
)

/**
 * 滚动记忆键：`{scope.stateKey()}|{board.type}`，如 `all|action` / `follow:100,200|action` / `z100|action`。
 *
 * 关注作用域的顺序归一（去重 + 升序）由 [ZoneQueryScope.stateKey] 负责：同一逻辑作用域的键必须唯一，
 * 否则 `ZoneTabsSettings.sort`（`recent` / `custom`）改变顺序后旧记忆不可达，宿主的恢复副作用会反复触发。
 *
 * 注意：本键仅用于内存滚动记忆，**不是** `feed_zone_board` 的落盘形态（落盘一级部分是 `follow`，
 * 不带 id 列表），两者不要互相解码。
 */
fun homeBoardMemoryKey(scope: ZoneQueryScope, board: ZoneBoard): String =
    "${scope.stateKey()}|${board.type}"
