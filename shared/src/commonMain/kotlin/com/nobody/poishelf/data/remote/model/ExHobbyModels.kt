package com.nobody.poishelf.data.remote.model

import kotlinx.serialization.Serializable

/**
 * ExHobby 扩展图库图片项数据模型。
 *
 * @param path 相对图片路径 (如 "2023/05/abc.jpg")
 */
@Serializable
data class ExHobbyImageItem(
    val path: String
)
