package com.nobody.poishelf.data.remote.model

/**
 * Hpoi API 业务异常类。
 */
class ApiException(
    val code: Int,
    override val message: String
) : Exception("API 业务异常 (code=$code): $message") {
    val isTokenExpired: Boolean
        get() = code == 4001
}
