package com.nobody.repoi.ui.person

import kotlinx.serialization.Serializable

/**
 * 人物详情页路由导航参数。
 *
 * Wave 1 契约桩（与老项目 `PersonDetailViewModel.kt` 同名同字段），Wave 5 转正式契约。
 */
@Serializable
data class PersonNavParam(
    val personId: Long,
    val personNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null
)
