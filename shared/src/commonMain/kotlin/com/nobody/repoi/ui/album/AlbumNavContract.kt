package com.nobody.repoi.ui.album

import kotlinx.serialization.Serializable

/**
 * 相册详情路由导航参数。
 */
@Serializable
data class AlbumNavParam(
    val albumId: Long,
    val initialTitle: String? = null,
    val initialCover: String? = null
)
