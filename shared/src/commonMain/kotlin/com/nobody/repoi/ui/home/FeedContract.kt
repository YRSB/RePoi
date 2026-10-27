package com.nobody.repoi.ui.home

/**
 * 动态流专区分区契约（自 `HomeFeedViewModel.kt` 提取先行，供 `HomeFilterSheet` 引用）。
 *
 * Wave 6 迁移 `HomeFeedViewModel` 时复用本契约，不得重复声明。
 */
data class FeedCategory(
    val tabKey: String = "all",
    val name: String = "全部",
    val nodeId: Long = 0L,
    val zoneIds: String = "",
    val id: Int = nodeId.toInt()
)
