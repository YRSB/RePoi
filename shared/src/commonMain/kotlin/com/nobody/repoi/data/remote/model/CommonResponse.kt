package com.nobody.repoi.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommonResponse<T>(
    @SerialName("@id") val id: String? = null,
    val success: Boolean = false,
    val code: Int = 0,
    val msg: String? = null,
    val data: T? = null
) {

    val isSuccess: Boolean
        get() = success && (code == 200 || code == 0 || code == -1)

    val isTokenExpired: Boolean
        get() = code == 4001

    val isCommentNotFound: Boolean
        get() = code == -9007 || msg?.contains("找不到该评论") == true
}
