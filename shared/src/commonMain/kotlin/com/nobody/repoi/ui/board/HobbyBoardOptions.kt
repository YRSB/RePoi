package com.nobody.repoi.ui.board

import com.nobody.repoi.data.local.CategoryItem
import com.nobody.repoi.data.remote.model.HOBBY_BOARD_PAGE_SIZE
import com.nobody.repoi.data.remote.model.HOBBY_DEFAULT_CATEGORY_ID
import com.nobody.repoi.data.remote.model.HOBBY_DEFAULT_ORDER

object HobbyBoardOptions {

    const val DEFAULT_ORDER: String = HOBBY_DEFAULT_ORDER

    const val DEFAULT_CATEGORY_ID: Int = HOBBY_DEFAULT_CATEGORY_ID

    const val PAGE_SIZE: Int = HOBBY_BOARD_PAGE_SIZE

    val ORDERS: List<Pair<String, String>> = listOf(
        "hits7Day" to "7天热度",
        "hitsDay" to "1天热度",
        "hits" to "总热度",
        "add" to "最新入库",
        "release" to "发售时间",
        "rating" to "综合评分"
    )

    val ORDER_KEYS: Set<String> = ORDERS.map { it.first }.toSet()

    val MONTH_OPTIONS: List<Pair<Int?, String>> =
        listOf(null to "不限月") + (1..12).map { month -> month to "${month}月" }

    fun yearOptions(currentYear: Int): List<Pair<Int?, String>> =
        listOf(null to "不限年") +
            ((currentYear + 2 downTo currentYear - 8).map { year -> year to "${year}年" })

    val PRIMARY_CATEGORIES: List<Pair<Int, String>> = listOf(
        DEFAULT_CATEGORY_ID to "全部周边",
        100 to "手办",
        200 to "动漫模型",
        500 to "真实模型",
        300 to "娃娃人偶",
        400 to "毛绒布偶",
        900 to "动漫周边"
    )

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
}
