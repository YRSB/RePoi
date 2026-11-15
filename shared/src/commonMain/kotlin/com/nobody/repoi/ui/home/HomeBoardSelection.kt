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

/**
 * 「关注」聚合态不可上线的板块（`homeScope=follow` 待登录态复验 / 无多专区通道）。
 *
 * 依据 `docs/research/aggregate-api-verification.md` §4/§4.2：
 * - 产品（#159）：`zones` 逗号串是交集语义，`homeScope=follow` 匿名/伪造 token 均回落全站，
 *   需登录态复验后才能上线，当前先隐藏；
 * - 讨论（#158）：`posts/search` 无多专区通道，关注捷径必须隐藏。
 *
 * 「关注」态宁可少一个 Tab，也不能展示被服务端降级成全站的假内容。
 */
val FOLLOW_UNAVAILABLE_BOARDS: Set<ZoneBoard> = setOf(ZoneBoard.Hobby)

/**
 * 当前作用域可用的二级板块列表（首页二级 Tab 数据源）。
 *
 * - 聚合「全部」：`HOME_ORDER ∩ HOME_IMPLEMENTED`；
 * - 聚合「关注」：同上再剔除 [FOLLOW_UNAVAILABLE_BOARDS]（不可用即隐藏，不展示「暂不支持」占位）；
 * - 单专区：服务端 `tabs` ∩ `HOME_IMPLEMENTED`（由 ViewModel 异步装配，不用本函数）。
 */
fun homeBoardsFor(scope: ZoneQueryScope): List<ZoneBoard> = when (scope) {
    is ZoneQueryScope.Single -> HOME_BOARD_FALLBACK
    is ZoneQueryScope.Follow -> HOME_AGGREGATE_BOARDS.filter { it !in FOLLOW_UNAVAILABLE_BOARDS }
    ZoneQueryScope.All -> HOME_AGGREGATE_BOARDS
}

/** 单专区板块请求中 / 失败时的兜底板块列表（本阶段仅动态板块）。 */
val HOME_BOARD_FALLBACK: List<ZoneBoard> = listOf(ZoneBoard.Action)

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
 * - 聚合态：客户端固定列表（无需等网络）；
 * - 单专区：先回退 `[ZoneBoard.Action]`，服务端 `zone/info.tabs` 返回后再由 ViewModel 异步替换。
 */
internal fun immediateHomeBoards(scope: ZoneQueryScope): List<ZoneBoard> = homeBoardsFor(scope)

/** 同步兜底板块列表的首板块（切换作用域时 `selectedBoard` 的落点），列表为空时退化为动态板块。 */
internal fun immediateHomeSelectedBoard(scope: ZoneQueryScope): ZoneBoard =
    immediateHomeBoards(scope).firstOrNull() ?: ZoneBoard.Action
