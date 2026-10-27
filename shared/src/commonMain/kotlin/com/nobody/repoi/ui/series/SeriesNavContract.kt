package com.nobody.repoi.ui.series

import kotlinx.serialization.Serializable

/**
 * 系列详情页路由导航参数。
 */
@Serializable
data class SeriesNavParam(
    val seriesId: Long,
    val seriesNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null
)
