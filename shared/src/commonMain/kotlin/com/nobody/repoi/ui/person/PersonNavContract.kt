package com.nobody.repoi.ui.person

import kotlinx.serialization.Serializable

/**
 * 人物详情页路由导航参数。
 */
@Serializable
data class PersonNavParam(
    val personId: Long,
    val personNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null
)
