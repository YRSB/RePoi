package com.nobody.repoi.data.remote.interceptor

class CommonParamsInterceptor(
    private val tokenProvider: () -> String,
) {
    fun token(): String = runCatching { tokenProvider() }.getOrDefault("")
}
