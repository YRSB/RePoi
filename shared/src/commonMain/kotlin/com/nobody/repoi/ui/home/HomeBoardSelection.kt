package com.nobody.repoi.ui.home

import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneQueryScope

/**
 * 首页聚合态（全部 / 关注）的二级板块固定顺序：`HOME_ORDER ∩ HOME_IMPLEMENTED`。
 *
 * 聚合态不向服务端要 tabs（一级 `zone/home-tabs` 只下发专区列表），顺序取客户端固定常量；
 * 未接入的板块在此被过滤掉，保证「未接入即隐藏」（issue #155 验收标准）。
 * 后续 #157–#161 往 [ZoneBoard.HOME_IMPLEMENTED] 里加板块时，本列表自动跟随，无需再改这里。
 */
val HOME_AGGREGATE_BOARDS: List<ZoneBoard> =
    ZoneBoard.HOME_ORDER.filter { it in ZoneBoard.HOME_IMPLEMENTED }

/** 单专区板块请求中 / 失败时的兜底板块列表（本阶段仅动态板块）。 */
val HOME_BOARD_FALLBACK: List<ZoneBoard> = listOf(ZoneBoard.Action)

/**
 * 「关注」聚合态不可用的板块集合（能力矩阵差异，issue #160 建立机制）。
 *
 * 关注聚合需要「多专区并集」语义；以下板块的服务端参数只能表达单一作用域，或对多值按**交集**
 * 处理，强行下发会静默得到空列表/错误内容，因此在关注态**直接隐藏**而不是渲染「暂不支持」占位
 * （`docs/home-hierarchy.md` §3.6、`docs/research/aggregate-api-verification.md` §5）：
 * - [ZoneBoard.Resale]：`zones` 逗号串实测为交集语义（多专区 0 条），无法表达关注并集。
 *
 * 后续 #158（讨论，`zoneNodeId` 单值）等板块同样加入本集合，扩展即一行。
 */
val FOLLOW_UNAVAILABLE_BOARDS: Set<ZoneBoard> = setOf(ZoneBoard.Resale)

/**
 * 指定作用域下的聚合态首页板块列表（纯函数，可单测）。
 *
 * - [ZoneQueryScope.All] → [HOME_AGGREGATE_BOARDS]（全部可用板块）；
 * - [ZoneQueryScope.Follow] → 去掉 [FOLLOW_UNAVAILABLE_BOARDS]（关注不可用板块隐藏）；
 * - [ZoneQueryScope.Single] → 由服务端 `zone/info.tabs` 决定，不适用本函数（返回兜底 [HOME_BOARD_FALLBACK]）。
 *
 * 注意：关注集合为空时本函数仍返回列表，但调用方必须短路请求（空 `zones` 会被服务端当全站）。
 */
fun homeBoardsFor(scope: ZoneQueryScope): List<ZoneBoard> = when (scope) {
    is ZoneQueryScope.Single -> HOME_BOARD_FALLBACK
    is ZoneQueryScope.Follow -> HOME_AGGREGATE_BOARDS.filter { it !in FOLLOW_UNAVAILABLE_BOARDS }
    ZoneQueryScope.All -> HOME_AGGREGATE_BOARDS
}

/**
 * 由一级分类派生查询作用域（禁止调用点散拼 zone 参数）。
 *
 * - `all` → [ZoneQueryScope.All]
 * - `follow` → [ZoneQueryScope.Follow]，`zoneIds` 逗号串拆成 id 列表；空关注标记 `-1` 与非法值一律丢弃，
 *   得到空集合后由上层短路（不发起「降级成全站」的假关注请求）
 * - `z{nodeId}` → [ZoneQueryScope.Single]；首帧分类尚未回填 `nodeId` 时从 tabKey 兜底解析
 */
internal fun FeedCategory.toZoneQueryScope(): ZoneQueryScope = when (tabKey) {
    ZONE_TAB_KEY_ALL -> ZoneQueryScope.All
    ZONE_TAB_KEY_FOLLOW -> ZoneQueryScope.Follow(
        zoneIds.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it > 0L }
    )

    else -> {
        val zoneNodeId = nodeId.takeIf { it > 0L }
            ?: tabKey.removePrefix("z").toLongOrNull()?.takeIf { it > 0L }
        if (zoneNodeId != null) ZoneQueryScope.Single(zoneNodeId) else ZoneQueryScope.All
    }
}

/**
 * 切换作用域时可**同步**给出的板块列表：
 * - 聚合态：客户端固定列表（全部 / 关注各不相同，关注态隐藏能力矩阵不可用板块）；
 * - 单专区：先回退 `[ZoneBoard.Action]`，服务端 `zone/info.tabs` 返回后再由 ViewModel 异步替换。
 */
internal fun immediateHomeBoards(scope: ZoneQueryScope): List<ZoneBoard> = homeBoardsFor(scope)

/** 同步兜底板块列表的首板块（切换作用域时 `selectedBoard` 的落点），列表为空时退化为动态板块。 */
internal fun immediateHomeSelectedBoard(scope: ZoneQueryScope): ZoneBoard =
    immediateHomeBoards(scope).firstOrNull() ?: ZoneBoard.Action
