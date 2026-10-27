package com.nobody.repoi.ui.navigation

import kotlinx.serialization.Serializable

/**
 * 角色/要素详情页路由导航参数（原版 `type="charactar"` 对应的详情实体）。
 *
 * Wave 1 契约桩（与老项目 `NavRoutes.kt` 同名同字段），Wave 5 转正式契约。
 */
@Serializable
data class CharacterNavParam(
    val characterId: Long,
    val characterNodeId: Long = 0L,
    val initialName: String? = null,
    val initialCover: String? = null
)
