package com.nobody.repoi.ui.album

import kotlinx.serialization.Serializable

/**
 * 相册详情路由导航参数。
 *
 * Wave 1 契约桩（与老项目 `AlbumDetailViewModel.kt` 同名同字段），Wave 5 转正式契约。
 */
@Serializable
data class AlbumNavParam(
    val albumId: Long,
    val initialTitle: String? = null,
    val initialCover: String? = null
)
