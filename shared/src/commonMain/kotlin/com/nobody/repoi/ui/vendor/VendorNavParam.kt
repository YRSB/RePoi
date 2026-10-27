package com.nobody.repoi.ui.vendor

import kotlinx.serialization.Serializable

/**
 * 厂商详情页路由导航参数。
 *
 * Wave 1 契约桩（与老项目 `VendorDetailViewModel.kt` 同名同字段），Wave 5 转正式契约。
 */
@Serializable
data class VendorNavParam(
    val companyId: Long,
    val companyNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null
)
