package com.nobody.repoi.data.remote.model

/**
 * Hpoi API 业务异常类。
 */
open class ApiException(
    val code: Int,
    override val message: String
) : Exception("API 业务异常 (code=$code): $message") {
    val isTokenExpired: Boolean
        get() = code == 4001
}

/**
 * 评论未命中异常：服务端以 -9007 或「找不到该评论」消息表示目标评论不存在。
 * 由客户端网络管道统一抛出，仓储可据此干净地映射为空列表。
 */
class CommentNotFoundException(code: Int, message: String) : ApiException(code, message)
