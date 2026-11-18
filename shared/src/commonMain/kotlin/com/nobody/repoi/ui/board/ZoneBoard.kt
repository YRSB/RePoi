package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.ZoneTabItem

/**
 * 首页与专区详情共享的板块定义与能力矩阵（纯逻辑，可单测）。
 *
 * - [type] 与 Hpoi `api/zone/info` 的 `tabs[].type` 一一对应；[Character] 刻意保留服务端历史拼写
 *   `charactar`（真机样本 10 个专区均如此下发，不能写成 `character`）。
 * - [CLIENT_WHITELIST] 为客户端已实现的 10 类板块。`shop_item`（产品决策不做）与 `community`
 *   （RePoi 无实现）永远不进入白名单，见 ADR-0022 决策 2「不可用即隐藏」。
 * - [HOME_ORDER] 为聚合态（全部）的固定展示顺序，同样来自 ADR-0022 决策 2。
 * - [emptyText] 是各板块的空态文案唯一来源：共享面板与首页卡片式空态都读它
 *   （历史实现里两处各写一份，产品板块因此显示过动态流的措辞）。
 * - 首页「作用域 × 板块」的可见性矩阵（关注态隐藏哪些板块等）**只**由
 *   [ZoneBoardCapability] 定义，本枚举不再重复携带各类「已接入 / 聚合可用」集合。
 *
 * 依据：`docs/research/aggregate-api-verification.md` §2.4（真实专区 tabs 覆盖）与 §5。
 */
enum class ZoneBoard(val type: String, val label: String, val emptyText: String) {
    Action("action", "动态", "暂无相关动态"),
    Album("album", "分享", "暂无相关分享"),
    Posts("posts", "讨论", "暂无相关讨论"),
    Hobby("hobby", "产品", "暂无相关产品"),
    Resale("resale_item", "二手", "暂无相关二手条目"),
    Company("company", "厂商", "暂无相关条目"),
    Series("series", "系列", "暂无相关条目"),
    Works("works", "作品", "暂无相关条目"),
    Character("charactar", "角色", "暂无相关条目"),
    Person("person", "人物", "暂无相关条目");

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
         * 1. **严格保留服务端下发顺序**，不按客户端固定顺序重排，也不强制把 [Action] 置顶
         *    （#150 审查 A7：真实样本中 action 恒为第一位，客户端无需也不应自行重排）；
         * 2. 只保留 [available] 内的板块（默认 [CLIENT_WHITELIST]）；
         * 3. [serverTabs] 为空时返回空列表，是否回退固定列表由调用方决定（详情页回退 10 项）。
         */
        fun tabsFor(
            serverTabs: List<ZoneTabItem>,
            available: Set<ZoneBoard> = CLIENT_WHITELIST
        ): List<ZoneBoard> {
            if (serverTabs.isEmpty()) return emptyList()
            return serverTabs
                .mapNotNull { fromType(it.type) }
                .distinct()
                .filter { it in available }
        }
    }
}
