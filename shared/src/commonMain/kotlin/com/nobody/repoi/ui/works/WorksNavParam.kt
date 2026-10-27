package com.nobody.repoi.ui.works

import kotlinx.serialization.Serializable

/**
 * 作品详情页路由导航参数。
 *
 * Wave 1 契约桩（与老项目 `WorksDetailViewModel.kt` 同名同字段），Wave 5 转正式契约。
 */
@Serializable
data class WorksNavParam(
    val worksId: Long,
    val initialName: String? = null,
    val initialCover: String? = null
)
