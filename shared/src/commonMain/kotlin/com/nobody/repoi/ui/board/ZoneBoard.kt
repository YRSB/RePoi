package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.ZoneTabItem

/**
 * 首页与专区详情共享的板块定义与能力矩阵（纯逻辑，可单测）。
 *
 * - [type] 与 Hpoi `api/zone/info` 的 `tabs[].type` 一一对应；[Character] 刻意保留服务端历史拼写
 *   `charactar`（真机样本 10 个专区均如此下发，不能写成 `character`）。
 * - [CLIENT_WHITELIST] 为客户端已实现的 10 类板块。`shop_item`（产品决策不做）与 `community`
 *   （RePoi 无实现）永远不进入白名单，见 ADR-0022 决策 2「不可用即隐藏」。
 * - [HOME_ORDER] 为聚合态（全部/关注）的固定展示顺序，同样来自 ADR-0022 决策 2。
 *
 * 依据：`docs/research/aggregate-api-verification.md` §2.4（真实专区 tabs 覆盖）与 §5。
 */
enum class ZoneBoard(val type: String, val label: String) {
    Action("action", "动态"),
    Album("album", "分享"),
    Posts("posts", "讨论"),
    Hobby("hobby", "产品"),
    Resale("resale_item", "二手"),
    Company("company", "厂商"),
    Series("series", "系列"),
    Works("works", "作品"),
    Character("charactar", "角色"),
    Person("person", "人物");

    /** 是否为实体类板块（厂商/系列/作品/角色/人物），它们共用 `zone/items` 接口与参数。 */
    val isEntity: Boolean
        get() = this in ENTITY

    /**
     * Tab 文案：优先使用服务端下发的 `title`，为空时回退枚举文案。
     *
     * 产品板块强制「产品」与专区详情现状一致（历史 `parseTabs` 对 hobby 恒用固定文案）。
     */
    fun tabTitle(serverTitle: String?): String = when (this) {
        Hobby -> label
        else -> serverTitle?.takeIf { it.isNotBlank() } ?: label
    }

    companion object {
        /** 实体 5 类板块。 */
        val ENTITY: Set<ZoneBoard> = setOf(Company, Series, Works, Character, Person)

        /** 聚合态固定 10 项展示顺序（原版 11 类去掉商品；ADR-0022 决策 2）。 */
        val HOME_ORDER: List<ZoneBoard> =
            listOf(Action, Album, Posts, Hobby, Resale, Company, Series, Works, Character, Person)

        /** 客户端已实现白名单，与 [HOME_ORDER] 等价的 10 类。 */
        val CLIENT_WHITELIST: Set<ZoneBoard> = HOME_ORDER.toSet()

        /**
         * 首页已接入的板块集合（#155 动态、#157 分享、#158 讨论）。
         *
         * 与 [CLIENT_WHITELIST]（专区详情已实现白名单）区分：专区详情 10 类均已实现，首页按票逐步接入，
         * 首页二级 Tab 只展示本集合内的板块（聚合态 = [HOME_ORDER] ∩ 本集合，再按作用域过滤；单专区 =
         * 服务端 tabs ∩ 本集合），未接入的板块不出现在 Tab 中（issue #155 验收标准）。
         *
         * 扩展方式：#159–#161 各自把自己的板块加进来（一行改动）；聚合态的「无服务端通道板块」
         * （如关注态的讨论/二手）由 `ui/home/HomeBoardSelection.kt` 的 `homeBoardsFor(scope)` 隐藏，
         * 不要把作用域规则塞进本枚举（#162 再做完整能力矩阵）。
         */
        val HOME_IMPLEMENTED: Set<ZoneBoard> = setOf(Action, Album, Posts)

        /**
         * 服务端 `type` → 板块。
         *
         * `share` 是 `album` 的历史别名（专区详情现状 `album|share` 同属分享分支），统一映射到
         * [Album]；白名单外的 `shop_item` / `community` 及任何未知 type 返回 null（调用方丢弃）。
         */
        fun fromType(type: String): ZoneBoard? = when (type) {
            "share" -> Album
            else -> entries.firstOrNull { it.type == type }
        }

        /**
         * 单专区二级 Tab 的能力矩阵：服务端下发顺序 ∩ [available]，未知/未实现板块丢弃。
         *
         * 规则（与专区详情现状 `parseTabs` 行为对齐）：
         * 1. 保留服务端下发顺序，不按客户端固定顺序重排；
         * 2. 只保留 [available] 内的板块（默认 [CLIENT_WHITELIST]）；
         * 3. [Action] 保证存在且位于首位——服务端真实样本中 action 恒为第一，此处仅做防御性置顶；
         * 4. [serverTabs] 为空时返回空列表，是否回退固定列表由调用方决定（详情页回退 10 项）。
         */
        fun tabsFor(
            serverTabs: List<ZoneTabItem>,
            available: Set<ZoneBoard> = CLIENT_WHITELIST
        ): List<ZoneBoard> {
            if (serverTabs.isEmpty()) return emptyList()
            val ordered = serverTabs
                .mapNotNull { fromType(it.type) }
                .distinct()
                .filter { it in available }
            if (Action !in available) return ordered
            return listOf(Action) + ordered.filter { it != Action }
        }
    }
}
