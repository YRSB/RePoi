package com.nobody.repoi.data.remote.model

open class ApiException(
    val code: Int,
    override val message: String
) : Exception("API 业务异常 (code=$code): $message") {
    val isTokenExpired: Boolean
        get() = code == 4001
}

class CommentNotFoundException(code: Int, message: String) : ApiException(code, message)
