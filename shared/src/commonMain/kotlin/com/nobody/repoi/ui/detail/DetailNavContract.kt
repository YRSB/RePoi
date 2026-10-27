package com.nobody.repoi.ui.detail

import kotlinx.serialization.Serializable

/**
 * 手办详情页路由导航参数。
 */
@Serializable
data class DetailNavParam(
    val itemId: Long,
    val itemType: String = "hobby",
    val initialName: String? = null,
    val initialCover: String? = null
)
