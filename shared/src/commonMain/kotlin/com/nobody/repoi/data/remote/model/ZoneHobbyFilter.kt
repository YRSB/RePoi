package com.nobody.repoi.data.remote.model

/** 产品板块默认页大小（协议常量，数据层与 UI 共享唯一来源）。 */
const val HOBBY_BOARD_PAGE_SIZE: Int = 24

/** 产品板块默认排序（`POST api/hobby/query-v2` 的 `order` 值）。 */
const val HOBBY_DEFAULT_ORDER: String = "hits7Day"

/** 产品板块默认分类（`10000` = 全部周边）。 */
const val HOBBY_DEFAULT_CATEGORY_ID: Int = 10000

/**
 * 产品板块的筛选值快照（数据层查询参数，`ItemRepository.queryHobbyBoard` 直接消费）。
 *
 * 归属数据层：仓库签名直接消费本类型，放在 `ui.board` 会造成 data → ui 反向依赖。
 * UI 侧的文案与候选列表（排序档位、年份/月份菜单、分类树）仍在 `ui.board.HobbyBoardOptions`，
 * 本类型只承载「已选值」。
 *
 * 字段语义：
 * - [categoryId] 一级分类；[subCategoryId] 子分类（非空时优先作为请求 `category`）；
 * - [order] 排序键（合法值见 `HobbyBoardOptions.ORDER_KEYS`）；
 * - [releaseYear] / [releaseMonth] `null` = 不限；
 * - [keyword] 关键词，空白时不下发；
 * - [sex] / [specs] / [scale] / [r18] 高级筛选维度。
 */
data class ZoneHobbyFilter(
    val categoryId: Int = HOBBY_DEFAULT_CATEGORY_ID,
    val subCategoryId: Int? = null,
    val order: String = HOBBY_DEFAULT_ORDER,
    val releaseYear: Int? = null,
    val releaseMonth: Int? = null,
    val keyword: String = "",
    val sex: Int? = null,
    val specs: String? = null,
    val scale: String? = null,
    val r18: Int? = null
) {
    /** 高级筛选是否生效（性别 / 属性 / 比例 / 观感任一非空即生效）。 */
    val hasAdvancedFilters: Boolean
        get() = sex != null || !specs.isNullOrBlank() || !scale.isNullOrBlank() || r18 != null
}
