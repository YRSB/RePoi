package com.nobody.repoi.data.session

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.LoginData
import com.nobody.repoi.data.remote.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [UserSession] 的确定性测试替身：无协程时序、无网络、无真实存储。
 *
 * 构造方式：
 * - `FakeUserSession()`：未登录；
 * - `FakeUserSession(token, userId, profile)`：直接进入已认证（token 为空则未登录）；
 * - `FakeUserSession(SessionState.Xxx)`：任意初始状态（如 `Prewarming`）。
 *
 * 动作结果通过 [loginResult] / [tokenLoginResult] / [refreshProfileResult] 预置；
 * 成功时同步更新 [state] 供断言，失败时保持现状。动作调用次数从对应 `xxxCalls` 读取。
 * 需要模拟后台 4001 或登录态迁移时，直接调用 [setState]。
 */
class FakeUserSession(
    initialState: SessionState = SessionState.Unauthenticated,
) : UserSession {

    constructor(
        token: String,
        userId: Long = 0L,
        profile: UserProfile? = null,
    ) : this(
        if (token.isBlank()) {
            SessionState.Unauthenticated
        } else {
            SessionState.Authenticated(userId = userId, token = token, profile = profile)
        }
    )

    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    override val isAuthenticated: Boolean
        get() = currentToken.isNotBlank()

    override val currentUserId: Long
        get() = (_state.value as? SessionState.Authenticated)?.userId ?: 0L

    override val currentToken: String
        get() = (_state.value as? SessionState.Authenticated)?.token.orEmpty()

    /** 账号登录结果；默认失败，提醒用例显式配置。 */
    var loginResult: Result<LoginData> =
        Result.failure(ApiException(500, "FakeUserSession: 未配置账号登录结果"))

    /** Token 登录结果；默认失败，提醒用例显式配置。 */
    var tokenLoginResult: Result<UserProfile> =
        Result.failure(ApiException(500, "FakeUserSession: 未配置 Token 登录结果"))

    /** 档案刷新结果；默认失败，提醒用例显式配置。 */
    var refreshProfileResult: Result<UserProfile> =
        Result.failure(ApiException(500, "FakeUserSession: 未配置档案刷新结果"))

    var loginCalls: Int = 0
        private set

    var tokenLoginCalls: Int = 0
        private set

    var refreshProfileCalls: Int = 0
        private set

    var logoutCalls: Int = 0
        private set

    var tokenExpiredCalls: Int = 0
        private set

    /** 最近一次账号登录的入参（供视图层裁剪/透传断言）。 */
    var lastLoginAccount: String = ""
        private set

    var lastLoginPassword: String = ""
        private set

    /** 最近一次 Token 登录的候选令牌（已由调用链裁剪）。 */
    var lastTokenCandidate: String = ""
        private set

    override suspend fun login(account: String, password: String): Result<LoginData> {
        loginCalls++
        lastLoginAccount = account
        lastLoginPassword = password
        val result = loginResult
        result.onSuccess { data ->
            val token = data.utoken.orEmpty()
            if (token.isNotBlank()) {
                _state.value = SessionState.Authenticated(
                    userId = data.user?.actualUserId ?: 0L,
                    token = token,
                )
            }
        }
        return result
    }

    override suspend fun loginWithToken(rawToken: String): Result<UserProfile> {
        tokenLoginCalls++
        lastTokenCandidate = rawToken
        val result = tokenLoginResult
        result.onSuccess { profile ->
            val token = rawToken.trim()
            if (token.isNotBlank()) {
                _state.value = SessionState.Authenticated(
                    userId = profile.id,
                    token = token,
                    profile = profile,
                )
            }
        }
        return result
    }

    override suspend fun refreshProfile(): Result<UserProfile> {
        refreshProfileCalls++
        val result = refreshProfileResult
        result.onSuccess { profile ->
            val current = _state.value as? SessionState.Authenticated ?: return@onSuccess
            _state.value = current.copy(
                userId = profile.id.takeIf { it > 0L } ?: current.userId,
                profile = profile,
            )
        }
        return result
    }

    override suspend fun logout() {
        logoutCalls++
        _state.value = SessionState.Unauthenticated
    }

    override suspend fun notifyTokenExpired(triggeredToken: String?) {
        tokenExpiredCalls++
        if (triggeredToken != null && currentToken != triggeredToken) {
            return
        }
        _state.value = SessionState.Unauthenticated
    }

    override fun exportActiveToken(): String = currentToken

    /** 直接驱动会话状态（模拟后台 4001、预热完成等）。 */
    fun setState(state: SessionState) {
        _state.value = state
    }
}
