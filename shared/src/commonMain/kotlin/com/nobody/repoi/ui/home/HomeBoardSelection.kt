package com.nobody.repoi.ui.home

import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneQueryScope

/**
 * 聚合态下**没有服务端多专区通道**、必须隐藏的板块（安全闸门）。
 *
 * 讨论板块（issue #158）：`GET api/posts/search` 的专区过滤只认单值 `zoneNodeId`，`zones` 与
 * `zoneNodeId` 逗号串都被服务端**静默忽略并退化为全站**。若在「关注」聚合态展示讨论板块，
 * 用户会看到全站帖子被当成「已关注专区的讨论」——比隐藏更危险，因此关注态必须隐藏该板块。
 *
 * 依据：`docs/research/aggregate-api-verification.md` §3（多专区语义）、§4.2/§4.3、
 * §5「#158 关注聚合无服务端多专区通道，上线时隐藏该板块」。
 *
 * #162 会在此基础上补齐完整能力矩阵并加单测：产品（关注态仅 `homeScope=follow`，需登录态复验
 * 后才能上线，当前先隐藏）、二手（`zones` 逗号串是交集，关注态隐藏）等。本票据只做讨论板块的
 * 最小实现，其余规则保持现状。
 */
internal val FOLLOW_UNAVAILABLE_BOARDS: Set<ZoneBoard> = setOf(ZoneBoard.Posts)

/**
 * 作用域感知的二级板块列表（首页唯一的板块来源，禁止调用点散拼）。
 *
 * - 聚合态（全部 / 关注）= `HOME_ORDER` ∩ [ZoneBoard.HOME_IMPLEMENTED]，再剔除
 *   [FOLLOW_UNAVAILABLE_BOARDS]（仅关注态生效）。聚合态不向服务端要 tabs（一级 `zone/home-tabs`
 *   只下发专区列表），顺序取客户端固定常量；
 * - 单专区 = 请求中/失败时的兜底列表 [HOME_BOARD_FALLBACK]，服务端 `zone/info.tabs` 返回后由
 *   ViewModel 用 `ZoneBoard.tabsFor` 替换（本函数不参与单专区最终结果）。
 *
 * 「不可用即隐藏」是 ADR-0022 的安全约束：宁可少一个 Tab，也不能让用户看到错误作用域的数据。
 */
fun homeBoardsFor(scope: ZoneQueryScope): List<ZoneBoard> = when (scope) {
    is ZoneQueryScope.Single -> HOME_BOARD_FALLBACK
    else -> ZoneBoard.HOME_ORDER.filter { board ->
        board in ZoneBoard.HOME_IMPLEMENTED &&
            !(scope is ZoneQueryScope.Follow && board in FOLLOW_UNAVAILABLE_BOARDS)
    }
}

/**
 * 首页聚合态（全部）的二级板块固定顺序：`HOME_ORDER ∩ HOME_IMPLEMENTED`。
 *
 * 保留该常量作为「全部聚合」的便捷入口（也是 `HomeFeedUiState` 的默认值）；
 * 关注态与其它作用域一律走 [homeBoardsFor]，不要直接引用本常量。
 */
val HOME_AGGREGATE_BOARDS: List<ZoneBoard> = homeBoardsFor(ZoneQueryScope.All)

/** 单专区板块请求中 / 失败时的兜底板块列表（保持最小：只保证动态板块可用）。 */
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
 * - 聚合态：客户端固定列表（无需等网络），关注态已剔除无服务端通道的板块；
 * - 单专区：先回退 [ZoneBoard.Action]，服务端 `zone/info.tabs` 返回后再由 ViewModel 异步替换。
 */
internal fun immediateHomeBoards(scope: ZoneQueryScope): List<ZoneBoard> = homeBoardsFor(scope)

/** 同步兜底板块列表的首板块（切换作用域时 `selectedBoard` 的落点），列表为空时退化为动态板块。 */
internal fun immediateHomeSelectedBoard(scope: ZoneQueryScope): ZoneBoard =
    immediateHomeBoards(scope).firstOrNull() ?: ZoneBoard.Action
