package com.nobody.repoi.ui.vendor

import kotlinx.serialization.Serializable

/**
 * 厂商详情页路由导航参数。
 */
@Serializable
data class VendorNavParam(
    val companyId: Long,
    val companyNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null
)
