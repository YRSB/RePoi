package com.nobody.poishelf.data.remote.interceptor

import com.nobody.poishelf.data.local.TokenManager

/**
 * 公共参数拦截器桩（OkHttp 时代的 Koin 绑定保留位）。
 *
 * OkHttp 已由 Ktor 替代：发包合并逻辑在
 * `data.remote.plugin.CommonParamsPlugin`（HttpSend）。
 * 本类保留单例绑定形态（networkModule 显式 tokenProvider 具名绑定），供
 * 旧单测语义对照；实际 Header/Param 合并常量见 [CommonParams]。
 */
class CommonParamsInterceptor(
    private val tokenProvider: () -> String,
) {
    constructor(tokenManager: TokenManager) : this({ tokenManager.currentToken })

    fun token(): String = runCatching { tokenProvider() }.getOrDefault("")
}
