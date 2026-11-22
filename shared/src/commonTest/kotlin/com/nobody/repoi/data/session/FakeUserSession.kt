package com.nobody.repoi.data.session

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.LoginData
import com.nobody.repoi.data.remote.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    var loginResult: Result<LoginData> =
        Result.failure(ApiException(500, "FakeUserSession: 未配置账号登录结果"))

    var tokenLoginResult: Result<UserProfile> =
        Result.failure(ApiException(500, "FakeUserSession: 未配置 Token 登录结果"))

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

    var lastLoginAccount: String = ""
        private set

    var lastLoginPassword: String = ""
        private set

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

    fun setState(state: SessionState) {
        _state.value = state
    }
}
