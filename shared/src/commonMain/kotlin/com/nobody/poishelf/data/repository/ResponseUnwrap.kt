package com.nobody.poishelf.data.repository

import com.nobody.poishelf.data.local.TokenManager
import com.nobody.poishelf.data.remote.model.ApiException
import com.nobody.poishelf.data.remote.model.CommonResponse

/**
 * 通用响应解包与统一异常映射顶层辅助函数（由 `MessageRepository` 私有实现提升）。
 *
 * 所有 23 个仓储共用：成功（`isSuccess || (success && data != null)`）即返回 `data`；
 * 否则遇 4001 清 token 并抛 `ApiException`。
 */
internal suspend fun <T> CommonResponse<T>.unwrapData(tokenManager: TokenManager, fallbackMessage: String): T? {
    if (isSuccess || (success && data != null)) {
        return data
    } else {
        if (isTokenExpired) {
            tokenManager.clearToken()
        }
        throw ApiException(code, msg ?: fallbackMessage)
    }
}

/**
 * 无数据载荷响应的成功断言与统一异常映射顶层辅助函数。
 */
internal suspend fun CommonResponse<*>.unwrapUnit(tokenManager: TokenManager, fallbackMessage: String) {
    if (isSuccess || success) {
        return
    } else {
        if (isTokenExpired) {
            tokenManager.clearToken()
        }
        throw ApiException(code, msg ?: fallbackMessage)
    }
}
