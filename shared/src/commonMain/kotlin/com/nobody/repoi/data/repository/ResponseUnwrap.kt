package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommonResponse
import com.nobody.repoi.data.session.UserSession

/**
 * 通用响应解包与统一异常映射顶层辅助函数（由 `MessageRepository` 私有实现提升）。
 *
 * 所有 23 个仓储共用：成功（`isSuccess || (success && data != null)`）即返回 `data`；
 * 否则遇 4001 通知 [UserSession] 附带当前活跃凭据校验自愈，并抛出 `ApiException`。
 */
internal suspend fun <T> CommonResponse<T>.unwrapData(
    userSession: UserSession,
    fallbackMessage: String,
    triggeredToken: String? = userSession.currentToken,
): T? {
    if (isSuccess || (success && data != null)) {
        return data
    } else {
        if (isTokenExpired) {
            userSession.notifyTokenExpired(triggeredToken)
        }
        throw ApiException(code, msg ?: fallbackMessage)
    }
}

/**
 * 无数据载荷响应的成功断言与统一异常映射顶层辅助函数。
 */
internal suspend fun CommonResponse<*>.unwrapUnit(
    userSession: UserSession,
    fallbackMessage: String,
    triggeredToken: String? = userSession.currentToken,
) {
    if (isSuccess || success) {
        return
    } else {
        if (isTokenExpired) {
            userSession.notifyTokenExpired(triggeredToken)
        }
        throw ApiException(code, msg ?: fallbackMessage)
    }
}
