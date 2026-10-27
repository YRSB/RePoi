package com.nobody.repoi.ui.detail

import kotlinx.serialization.Serializable

/**
 * 手办详情页路由导航参数。
 *
 * Wave 1 契约桩（与老项目 `HobbyDetailViewModel.kt` 同名同字段），Wave 5 转正式契约。
 */
@Serializable
data class DetailNavParam(
    val itemId: Long,
    val itemType: String = "hobby",
    val initialName: String? = null,
    val initialCover: String? = null
)
