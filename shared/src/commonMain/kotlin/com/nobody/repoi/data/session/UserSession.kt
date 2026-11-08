package com.nobody.repoi.data.session

import com.nobody.repoi.data.remote.model.LoginData
import com.nobody.repoi.data.remote.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * 用户会话深模块契约：单一职责的鉴权接缝。
 *
 * 对外只暴露一条响应式状态流 [state] 与登录/登出/刷新/失效意图，
 * 屏蔽底层凭据存储的异步冷启动与持久化细节：
 * - 冷启动凭据恢复后立即进入 [SessionState.Authenticated]，网络档案刷新在后台增量进行；
 * - HTTP 4001 令牌失效由 [notifyTokenExpired] 原子自愈，杜绝幽灵半登录态；
 * - [exportActiveToken] 是凭据的唯一出口（ADR-0005）。
 */
interface UserSession {

    /** 会话状态流；初始为 [SessionState.Prewarming]，预热完成后流转为未认证或已认证。 */
    val state: StateFlow<SessionState>

    /** 活跃登录令牌流；未认证时发射空字符串，已去重。 */
    val tokenFlow: Flow<String>
        get() = state
            .map { (it as? SessionState.Authenticated)?.token.orEmpty() }
            .distinctUntilChanged()

    /** 是否处于已认证态（凭据可用）。 */
    val isAuthenticated: Boolean

    /** 当前用户 ID；未认证或凭据刚恢复且档案未刷新时为 0。 */
    val currentUserId: Long

    /** 当前活跃登录令牌；未认证时为空串。 */
    val currentToken: String

    /**
     * 账号密码登录。成功后持久化凭据并切换为已认证，失败不改变现有会话。
     */
    suspend fun login(account: String, password: String): Result<LoginData>

    /**
     * Token 登录（ADR-0005）：候选令牌验权成功后持久化凭据与档案并切换为已认证；
     * 失败保留旧会话、不落盘，并按失败类型返回友好错误。
     */
    suspend fun loginWithToken(rawToken: String): Result<UserProfile>

    /** 登出：原子清空存储凭据与内存状态，发射 [SessionState.Unauthenticated]。 */
    suspend fun logout()

    /**
     * 刷新当前用户档案。未认证直接失败；4001 触发令牌失效自愈，其他错误保留会话。
     */
    suspend fun refreshProfile(): Result<UserProfile>

    /**
     * 令牌失效自愈：原子清空存储凭据、内存令牌与缓存档案；幂等。
     *
     * @param triggeredToken 触发 4001 的在途请求所携带的凭据。
     * 若传入且与当前活跃凭据不匹配（说明期间用户已切换会话），则忽略该过期通知，避免误伤新会话。
     */
    suspend fun notifyTokenExpired(triggeredToken: String? = null)

    /** 导出当前活跃令牌；未认证返回空串。 */
    fun exportActiveToken(): String
}
