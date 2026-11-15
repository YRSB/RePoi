package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.ZoneTabItem

/**
 * 首页「作用域 × 板块」能力矩阵（唯一真相，纯逻辑可单测）。
 *
 * 首页二级 Tab 的**是否展示**只由本对象决定：
 * - 聚合·全部（[ZoneQueryScope.All]）：10 类白名单板块全可见，顺序固定为 [ZoneBoard.HOME_ORDER]；
 * - 聚合·关注（[ZoneQueryScope.Follow]）：仅 7 项可见，隐藏 [FOLLOW_UNAVAILABLE_BOARDS]（讨论 / 产品 / 二手）；
 * - 单专区（[ZoneQueryScope.Single]）：服务端 `zone/info.tabs` 下发顺序 ∩ 10 类白名单，保留服务端顺序。
 *
 * 白名单外类型（`shop_item` / `community` / 任意未知 type）在任何作用域下都**永不出现**，
 * 因为它们不在 [ZoneBoard] 枚举内，[ZoneBoard.fromType] 返回 null 后被过滤。
 *
 * 「不可用即隐藏」的安全约束（ADR-0022 决策 6）：宁可少一个 Tab，也不能展示错误作用域的数据。
 *
 * 依据：`docs/research/aggregate-api-verification.md` §3（多专区集合语义）、§4（上线矩阵）、
 * §4.1/§4.2（可上线 / 需隐藏清单）、§4.3（参数建议）、§5「#162」。
 */
object ZoneBoardCapability {

    /**
     * 聚合·全部可见板块：10 项全显示，顺序 = [ZoneBoard.HOME_ORDER]。
     */
    val ALL_BOARDS: List<ZoneBoard> = ZoneBoard.HOME_ORDER

    /**
     * 关注聚合**无服务端多专区通道**、必须隐藏的板块。
     *
     * - [ZoneBoard.Posts]（讨论）：`GET api/posts/search` 只认单值 `zoneNodeId`，`zones` 与
     *   `zoneNodeId` 逗号串都被服务端静默忽略并退化为全站（假「关注」内容）；
     * - [ZoneBoard.Hobby]（产品）：`zones` 逗号串是**交集**语义，关注聚合只能靠 `homeScope=follow`，
     *   而该参数真实过滤效果需登录态复验，未复验前先隐藏；
     * - [ZoneBoard.Resale]（二手）：`zones` 逗号串是**交集**语义（实测手办+机甲最新二手 0 条）。
     */
    val FOLLOW_UNAVAILABLE_BOARDS: Set<ZoneBoard> =
        setOf(ZoneBoard.Posts, ZoneBoard.Hobby, ZoneBoard.Resale)

    /**
     * 聚合·关注可见板块：`ALL_BOARDS - FOLLOW_UNAVAILABLE_BOARDS`，共 7 项。
     *
     * 动态 `zoneIds`、分享 / 实体 `zones` 的逗号串均为**并集**语义，可表达关注聚合。
     */
    val FOLLOW_BOARDS: List<ZoneBoard> =
        ALL_BOARDS.filterNot { it in FOLLOW_UNAVAILABLE_BOARDS }

    /**
     * 单专区 `zone/info.tabs` 尚未返回（请求中 / 失败）时的兜底板块列表。
     *
     * 只保证动态板块可用，不伪造其它板块入口；服务端 tabs 到达后由
     * [homeBoardsFor] 以服务端顺序替换。
     */
    val SINGLE_FALLBACK_BOARDS: List<ZoneBoard> = listOf(ZoneBoard.Action)

    /**
     * 给定作用域下的二级板块列表（顺序即 Tab 顺序，首页唯一入口）。
     *
     * @param scope 查询作用域（全部 / 关注 / 单专区）
     * @param serverTabs 单专区的服务端 `zone/info.tabs`；聚合态忽略该参数
     *   （一级 `zone/home-tabs` 只下发专区列表，没有二级 tabs）
     * @return 可见板块；聚合态为固定顺序，单专区保留服务端顺序并过滤白名单外类型。
     *
     * 单专区的 `null` 与空列表语义不同：
     * - `null` = 尚未拿到服务端结果（请求中 / 失败）→ 回退 [SINGLE_FALLBACK_BOARDS]；
     * - 非 null 的空列表 = 服务端明确没有可用板块 → 返回空列表（宿主隐藏 TabRow 并展示空态）。
     */
    fun homeBoardsFor(
        scope: ZoneQueryScope,
        serverTabs: List<ZoneTabItem>? = null
    ): List<ZoneBoard> = when (scope) {
        ZoneQueryScope.All -> ALL_BOARDS
        is ZoneQueryScope.Follow -> FOLLOW_BOARDS
        is ZoneQueryScope.Single -> serverTabs?.let { ZoneBoard.tabsFor(it) } ?: SINGLE_FALLBACK_BOARDS
    }

    /**
     * 该板块在当前作用域下是否允许出现在首页二级 Tab。
     *
     * 用于调用点做安全闸门（例如「板块不在列表内就绝不发请求」）。单专区只判断是否属于
     * 客户端白名单——具体该专区是否下发该板块由服务端 `zone/info.tabs` 决定。
     */
    fun isAvailableOnHome(scope: ZoneQueryScope, board: ZoneBoard): Boolean = when (scope) {
        ZoneQueryScope.All -> board in ALL_BOARDS
        is ZoneQueryScope.Follow -> board in FOLLOW_BOARDS
        is ZoneQueryScope.Single -> board in ZoneBoard.CLIENT_WHITELIST
    }
}
