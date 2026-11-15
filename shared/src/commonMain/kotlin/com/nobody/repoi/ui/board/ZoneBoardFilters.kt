package com.nobody.repoi.ui.board

import com.nobody.repoi.ui.item.list.components.HobbyAdvancedFilterParam

/**
 * 产品板块的筛选值快照（从 `ZoneHobbyTabState` 的纯数据部分拆出，供 pane 只读渲染）。
 *
 * 只承载「已选值」，不含选项列表（排序档位、年份范围、分类树等由宿主注入或由 pane 内组件常量提供）；
 * 所有 setter 一律经 [ZoneBoardActions]，pane 不直接改状态。
 */
data class ZoneHobbyFilter(
    val categoryId: Int = HobbyBoardOptions.DEFAULT_CATEGORY_ID,
    val subCategoryId: Int? = null,
    val order: String = HobbyBoardOptions.DEFAULT_ORDER,
    val releaseYear: Int? = null,
    val releaseMonth: Int? = null,
    val keyword: String = "",
    val sex: Int? = null,
    val specs: String? = null,
    val scale: String? = null,
    val r18: Int? = null
) {
    /** 高级筛选是否生效（与专区详情历史 `hasAdvancedFilters` 判定一致）。 */
    val hasAdvancedFilters: Boolean
        get() = HobbyBoardOptions.hasAdvancedFilters(sex = sex, specs = specs, scale = scale, r18 = r18)
}

/** 高级筛选弹层入参投影（弹层只关心 4 个高级维度，其余筛选值留在 [ZoneHobbyFilter]）。 */
fun ZoneHobbyFilter.toAdvancedFilterParam(): HobbyAdvancedFilterParam = HobbyAdvancedFilterParam(
    sex = sex,
    specs = specs,
    scale = scale,
    r18 = r18
)

/** 用弹层返回值覆盖 4 个高级维度，保留同一快照里的排序 / 年份 / 月份 / 分类 / 关键词。 */
fun HobbyAdvancedFilterParam.applyTo(base: ZoneHobbyFilter): ZoneHobbyFilter = base.copy(
    sex = sex,
    specs = specs,
    scale = scale,
    r18 = r18
)

/**
 * 各板块筛选值快照（不可变，pane 只读）。
 *
 * 已消费字段：[subType]（动态 9 项筛选）、[albumOrder] / [albumCategory]（分享 6 档排序与分类，#157）、
 * [postsOrder]（讨论 3 档排序，键值见 [PostsBoardOptions.ORDERS]）、[hobby] 与两个产品弹层显隐（#159）；
 * 其余字段为后续板块 ticket 预留，键值定义保持与专区详情现状一致。排序档位/分类候选等**选项列表**
 * 不放在这里（见 [AlbumBoardOptions] / [HobbyBoardOptions] 与宿主注入），本类型只保存「当前选中值」。
 *
 * 讨论板块**没有分类字段**：原版讨论分支不请求分类、`api/posts/search` 也不接受任何分类参数
 * （实测 `itemType`/`category`/`categoryId` 均被忽略）。为避免出现「UI 有入口但服务端忽略」的
 * 假过滤，本类型刻意不提供 `postsCategory` 字段，证据见 [PostsBoardOptions] KDoc。
 */
data class ZoneBoardFilters(
    val subType: String = "recommend",
    val albumOrder: String = AlbumBoardOptions.DEFAULT_ORDER,
    val albumCategory: Int? = null,
    val postsOrder: String = PostsBoardOptions.DEFAULT_ORDER,
    val hobby: ZoneHobbyFilter = ZoneHobbyFilter(),
    val resaleType: Int = 1,
    val resalePurity: String? = null,
    val resaleKeyword: String = "",
    /** 产品高级筛选 sheet 显隐（由 pane 自渲染，发布/举报弹层仍留宿主）。 */
    val showHobbyFilterSheet: Boolean = false,
    /** 产品分类级联 sheet 显隐。 */
    val showHobbyCategoryPickerSheet: Boolean = false
)
