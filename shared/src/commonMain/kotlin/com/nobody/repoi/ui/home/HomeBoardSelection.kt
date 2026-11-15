package com.nobody.repoi.ui.home

import com.nobody.repoi.data.remote.model.ZoneTabItem
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardCapability
import com.nobody.repoi.data.remote.model.ZoneQueryScope

/**
 * 首页二级板块的**薄封装**：所有「作用域 × 板块」事实来自 [ZoneBoardCapability]，
 * 本文件不重复定义任何矩阵规则（关注态隐藏清单、固定顺序、白名单交集都只有一处）。
 *
 * 保留本文件的函数名是为调用点语义清晰（`ui.home` 层只关心「首页作用域 → Tab 列表」），
 * 内部一律委托矩阵对象；新增规则请改 [ZoneBoardCapability]，不要在这里加分支。
 */

/**
 * 作用域感知的二级板块列表（首页唯一的板块来源，禁止调用点散拼）。
 *
 * - 聚合态（全部 / 关注）= 客户端固定顺序（详情见 [ZoneBoardCapability.homeBoardsFor]）；
 * - 单专区：传 `null`（请求中 / 失败）回退 [HOME_BOARD_FALLBACK]；传服务端 `zone/info.tabs`
 *   则按服务端顺序 ∩ 白名单。
 */
fun homeBoardsFor(
    scope: ZoneQueryScope,
    serverTabs: List<ZoneTabItem>? = null
): List<ZoneBoard> = ZoneBoardCapability.homeBoardsFor(scope, serverTabs)

/**
 * 首页聚合态（全部）的二级板块固定顺序便捷入口。
 *
 * 与 [ZoneBoardCapability.ALL_BOARDS] 是同一份列表（`HomeFeedUiState.boards` 默认值使用）；
 * 关注态与单专区一律走 [homeBoardsFor]。
 */
val HOME_AGGREGATE_BOARDS: List<ZoneBoard> = ZoneBoardCapability.ALL_BOARDS

/** 单专区板块请求中 / 失败时的兜底板块列表（保持最小：只保证动态板块可用）。 */
val HOME_BOARD_FALLBACK: List<ZoneBoard> = ZoneBoardCapability.SINGLE_FALLBACK_BOARDS

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
 * - 聚合态：矩阵固定列表（无需等网络，关注态已剔除无服务端通道的板块）；
 * - 单专区：先回退 [ZoneBoard.Action]，服务端 `zone/info.tabs` 返回后再由 ViewModel 异步替换。
 */
internal fun immediateHomeBoards(scope: ZoneQueryScope): List<ZoneBoard> = homeBoardsFor(scope)

/** 同步兜底板块列表的首板块（切换作用域时 `selectedBoard` 的落点），列表为空时退化为动态板块。 */
internal fun immediateHomeSelectedBoard(scope: ZoneQueryScope): ZoneBoard =
    immediateHomeBoards(scope).firstOrNull() ?: ZoneBoard.Action
