package com.nobody.repoi.ui.board

import com.nobody.repoi.data.local.CategoryItem

/**
 * 产品（手办）板块的排序档位、年份/月份候选与分类标签：首页与专区详情**唯一**共享定义（issue #159）。
 *
 * 键值来自 `docs/home-hierarchy.md` §3.4「7 天热度/1 天热度/总热度/最新入库/发售时间/综合评分」，
 * 与专区详情历史实现完全一致（`POST api/hobby/query-v2`）；文案与 `HobbyListScreen` 既有实现一致。
 *
 * 本对象只承载**协议常量与纯函数**：不持有状态、不读写偏好、不发请求。
 * 禁止在首页/详情/独立列表页各写一份（与 [AlbumBoardOptions] 同属「共享常量收敛」）。
 */
object HobbyBoardOptions {
    /** 默认排序（与专区详情、独立产品列表历史默认值一致）。 */
    const val DEFAULT_ORDER: String = "hits7Day"

    /** 默认分类（`10000` = 全部周边）。 */
    const val DEFAULT_CATEGORY_ID: Int = 10000

    /** 产品板块页大小（专区详情与首页共用；请求参数 `pageSize`）。 */
    const val PAGE_SIZE: Int = 24

    /** 6 档排序：`key` = 协议 `order` 值，`value` = 界面文案；顺序即渲染顺序。 */
    val ORDERS: List<Pair<String, String>> = listOf(
        "hits7Day" to "7天热度",
        "hitsDay" to "1天热度",
        "hits" to "总热度",
        "add" to "最新入库",
        "release" to "发售时间",
        "rating" to "综合评分"
    )

    /** 合法排序键集合（宿主切换入口的边界校验）。 */
    val ORDER_KEYS: Set<String> = ORDERS.map { it.first }.toSet()

    /** 月份候选：`null` = 不限月；顺序即菜单顺序。 */
    val MONTH_OPTIONS: List<Pair<Int?, String>> =
        listOf(null to "不限月") + (1..12).map { month -> month to "${month}月" }

    /**
     * 年份候选：`null` = 不限年，其后为「今年 +2 … 今年 -8」倒序。
     *
     * @param currentYear 当前公历年（由调用方按平台时区取，便于纯逻辑测试）
     */
    fun yearOptions(currentYear: Int): List<Pair<Int?, String>> =
        listOf(null to "不限年") +
            ((currentYear + 2 downTo currentYear - 8).map { year -> year to "${year}年" })

    /** 一级分类候选项（与 `CategoryPickerSheet` 左栏一致，仅用于悬浮 Chip 文案回退）。 */
    val PRIMARY_CATEGORIES: List<Pair<Int, String>> = listOf(
        DEFAULT_CATEGORY_ID to "全部周边",
        100 to "手办",
        200 to "动漫模型",
        500 to "真实模型",
        300 to "娃娃人偶",
        400 to "毛绒布偶",
        900 to "动漫周边"
    )

    /**
     * 分类悬浮 Chip 文案：
     * - 已选子分类 → 子分类名（分类树缺失时回退「子分类」）；
     * - 已选一级分类 → [PRIMARY_CATEGORIES] 文案；
     * - 否则 → 「分类」。
     */
    fun categoryLabel(
        categoryId: Int,
        subCategoryId: Int?,
        categoryTree: Map<Int, List<CategoryItem>>
    ): String {
        if (subCategoryId != null) {
            val subName = categoryTree[categoryId]
                ?.firstOrNull { it.id == subCategoryId }
                ?.name
                ?.takeIf { it.isNotBlank() }
            return subName ?: "子分类"
        }
        if (categoryId != DEFAULT_CATEGORY_ID) {
            return PRIMARY_CATEGORIES.firstOrNull { it.first == categoryId }?.second ?: "分类"
        }
        return "分类"
    }

    /** 高级筛选是否生效（性别 / 属性 / 比例 / 观感任一非空即生效）。 */
    fun hasAdvancedFilters(
        sex: Int?,
        specs: String?,
        scale: String?,
        r18: Int?
    ): Boolean = sex != null || !specs.isNullOrBlank() || !scale.isNullOrBlank() || r18 != null
}
