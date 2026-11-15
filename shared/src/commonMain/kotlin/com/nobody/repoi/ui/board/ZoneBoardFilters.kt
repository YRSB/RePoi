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
 * 已消费字段：[subType]（动态 9 项筛选）、[postsOrder]（讨论 3 档排序，键值见
 * [PostsBoardOptions.ORDERS]）；其余字段为后续板块 ticket 预留，键值定义保持与专区详情现状一致。
 *
 * 讨论板块**没有分类字段**：原版讨论分支不请求分类、`api/posts/search` 也不接受任何分类参数
 * （实测 `itemType`/`category`/`categoryId` 均被忽略）。为避免出现「UI 有入口但服务端忽略」的
 * 假过滤，本类型刻意不提供 `postsCategory` 字段，证据见 [PostsBoardOptions] KDoc。
 */
data class ZoneBoardFilters(
    val subType: String = "recommend",
    val albumOrder: String = "top",
    val albumCategory: Int? = null,
    val postsOrder: String = PostsBoardOptions.DEFAULT_ORDER,
    val hobby: ZoneHobbyFilter = ZoneHobbyFilter(),
    val resaleType: Int = 1,
    val resalePurity: String? = null,
    val resaleKeyword: String = "",
    /** 产品高级筛选 sheet 显隐（B 阶段由 pane 自渲染，发布/举报弹层仍留宿主）。 */
    val showHobbyFilterSheet: Boolean = false,
    /** 产品分类级联 sheet 显隐。 */
    val showHobbyCategoryPickerSheet: Boolean = false
)
