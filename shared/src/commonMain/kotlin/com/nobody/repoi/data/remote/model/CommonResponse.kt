package com.nobody.repoi.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Hpoi API 通用响应结构包装。
 * 格式示例：{ "@id": "1", "success": true, "code": 200, "msg": "操作成功", "data": { ... } }
 */
@Serializable
data class CommonResponse<T>(
    @SerialName("@id") val id: String? = null,
    val success: Boolean = false,
    val code: Int = 0,
    val msg: String? = null,
    val data: T? = null
) {
    /**
     * 业务成功判定：
     * Hpoi 原版服务端规范为 success == true。
     * 大多数接口返回 code = 200 或 0，但回帖讨论等接口（api/discuss/get）成功时返回 code = -1。
     */
    val isSuccess: Boolean
        get() = success && (code == 200 || code == 0 || code == -1)

    val isTokenExpired: Boolean
        get() = code == 4001

    /**
     * 评论空数据判定：
     * 当某条目无评论、无热门神评或分页超出末尾时，Hpoi 服务端返回 code = -9007，msg = "找不到该评论，可能已被删除"。
     */
    val isCommentNotFound: Boolean
        get() = code == -9007 || msg?.contains("找不到该评论") == true
}
