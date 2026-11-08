package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommonResponse
import com.nobody.repoi.data.session.UserSession

/**
 * 通用响应解包与统一异常映射顶层辅助函数（由 MessageRepository 与 SettingsRepository 共用）。
 *
 * 成功（isSuccess || (success && data != null)）即返回 data；
 * 否则遇 4001 通知 [UserSession] 并透传发起请求前捕获的快照凭据 [triggeredToken]，
 * 由会话互斥锁严格校验该在途凭据是否仍为当前活跃令牌，避免在网络往返期间切换会话时误伤新账号。
 */
internal suspend fun <T> CommonResponse<T>.unwrapData(
    userSession: UserSession,
    fallbackMessage: String,
    triggeredToken: String,
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
    triggeredToken: String,
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
