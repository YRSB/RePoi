package com.nobody.repoi.ui.board

/**
 * 产品板块的筛选值快照（从 `ZoneHobbyTabState` 的纯数据部分拆出，供 pane 只读渲染）。
 *
 * 只承载「已选值」，不含选项列表（排序档位、年份范围、分类树等由宿主注入或由 pane 内组件常量提供）；
 * 所有 setter 一律经 [ZoneBoardActions]，pane 不直接改状态。
 */
data class ZoneHobbyFilter(
    val categoryId: Int = 10000,
    val subCategoryId: Int? = null,
    val order: String = "hits7Day",
    val releaseYear: Int? = null,
    val releaseMonth: Int? = null,
    val keyword: String = "",
    val sex: Int? = null,
    val specs: String? = null,
    val scale: String? = null,
    val r18: Int? = null
)

/**
 * 各板块筛选值快照（不可变，pane 只读）。
 *
 * 动态只消费 [subType]（9 项筛选）；相册消费 [albumOrder] / [albumCategory]（#157）；
 * 其余字段为后续板块 ticket 预留，键值定义保持与专区详情现状一致。排序档位/分类候选等
 * **选项列表**不放在这里（见 [AlbumBoardOptions] 与宿主注入），本类型只保存「当前选中值」。
 */
data class ZoneBoardFilters(
    val subType: String = "recommend",
    val albumOrder: String = AlbumBoardOptions.DEFAULT_ORDER,
    val albumCategory: Int? = null,
    val postsOrder: String = "upd",
    val postsCategory: Int? = null,
    val hobby: ZoneHobbyFilter = ZoneHobbyFilter(),
    val resaleType: Int = 1,
    val resalePurity: String? = null,
    val resaleKeyword: String = "",
    /** 产品高级筛选 sheet 显隐（B 阶段由 pane 自渲染，发布/举报弹层仍留宿主）。 */
    val showHobbyFilterSheet: Boolean = false,
    /** 产品分类级联 sheet 显隐。 */
    val showHobbyCategoryPickerSheet: Boolean = false
)
