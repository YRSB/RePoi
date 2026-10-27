package com.nobody.repoi.ui.series

import kotlinx.serialization.Serializable

/**
 * 系列详情页路由导航参数。
 *
 * Wave 1 契约桩（与老项目 `SeriesDetailViewModel.kt` 同名同字段），Wave 5 转正式契约。
 */
@Serializable
data class SeriesNavParam(
    val seriesId: Long,
    val seriesNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null
)
