package com.nobody.repoi.ui.board

object AlbumBoardOptions {

    const val DEFAULT_ORDER: String = "top"

    const val PAGE_SIZE: Int = 24

    val ORDERS: List<Pair<String, String>> = listOf(
        "top" to "推荐",
        "add" to "最新创建",
        "update" to "最近更新",
        "picCount" to "图片数量",
        "hits" to "总热度",
        "hitsDay" to "一天热度"
    )

    val ORDER_KEYS: Set<String> = ORDERS.map { it.first }.toSet()
}

data class AlbumCategoryOption(
    val id: Int,
    val name: String
)
