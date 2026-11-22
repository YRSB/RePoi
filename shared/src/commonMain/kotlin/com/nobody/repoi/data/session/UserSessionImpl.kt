package com.nobody.repoi.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.LoginData
import com.nobody.repoi.data.remote.model.UserProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class UserSessionImpl(
    private val api: HpoiApi,
    private val tokenManager: TokenManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UserSession {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<SessionState>(SessionState.Prewarming)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    override val isAuthenticated: Boolean
        get() = currentToken.isNotBlank()

    override val currentUserId: Long
        get() = authenticatedOrNull()?.userId ?: 0L

    override val currentToken: String
        get() = authenticatedOrNull()?.token.orEmpty()

    init {

        scope.launch { runCatching { prewarm() } }
    }

    override suspend fun login(account: String, password: String): Result<LoginData> = runCatching {
        val data = api.login(account = account, password = password)
        val token = data.utoken?.takeIf { it.isNotBlank() }
            ?: throw ApiException(200, "未获取到有效的登录令牌")
        val userId = data.user?.actualUserId ?: 0L
        val userName = data.user?.userName
        mutex.withLock {
            tokenManager.saveToken(token, userName)
            if (userId > 0L) tokenManager.saveCurrentUser(userId, userName)
            _state.value = SessionState.Authenticated(userId = userId, token = token)
        }
        launchProfileRefresh()
        data
    }

    override suspend fun loginWithToken(rawToken: String): Result<UserProfile> = runCatching {
        val candidate = rawToken.trim()
        if (candidate.isBlank()) throw ApiException(400, "请输入登录令牌")
        val profile = try {
            api.getUserSelfWithToken(candidate).user ?: throw ApiException(200, "用户信息为空")
        } catch (e: ApiException) {
            throw mapTokenLoginError(e)
        } catch (e: Exception) {
            throw ApiException(-1, "网络连接异常，请检查后重试")
        }
        val userName = profile.nickname?.ifBlank { null } ?: profile.userName
        mutex.withLock {
            tokenManager.saveToken(candidate, userName)
            runCatching { tokenManager.saveCurrentUser(profile.id, userName) }
            _state.value = SessionState.Authenticated(
                userId = profile.id,
                token = candidate,
                profile = profile,
            )
        }
        profile
    }

    override suspend fun logout() {
        mutex.withLock {
            tokenManager.clearToken()
            _state.value = SessionState.Unauthenticated
        }
    }

    override suspend fun refreshProfile(): Result<UserProfile> = runCatching {
        val snapshot = currentToken
        if (snapshot.isBlank()) throw ApiException(401, "尚未登录")
        val profile = api.getUserSelf().user ?: throw ApiException(200, "用户信息为空")
        applyProfile(snapshot, profile)
        profile
    }

    override suspend fun notifyTokenExpired(triggeredToken: String?) {
        mutex.withLock {
            if (triggeredToken != null && currentToken != triggeredToken) {
                return@withLock
            }

            if (_state.value == SessionState.Unauthenticated && tokenManager.currentToken.isBlank()) {
                return@withLock
            }
            tokenManager.clearToken()
            _state.value = SessionState.Unauthenticated
        }
    }

    override fun exportActiveToken(): String = currentToken

    private suspend fun prewarm() {
        tokenManager.awaitReady()
        val restored = mutex.withLock {

            if (_state.value !is SessionState.Prewarming) return@withLock false
            val token = tokenManager.currentToken
            if (token.isBlank()) {
                _state.value = SessionState.Unauthenticated
                false
            } else {
                _state.value = SessionState.Authenticated(
                    userId = tokenManager.currentUserId,
                    token = token,
                )
                true
            }
        }
        if (restored) refreshProfileSilently()
    }

    private fun launchProfileRefresh() {
        scope.launch { refreshProfileSilently() }
    }

    private suspend fun refreshProfileSilently() {
        runCatching { refreshProfile() }
    }

    private suspend fun applyProfile(token: String, profile: UserProfile) {
        val userName = profile.nickname?.ifBlank { null } ?: profile.userName
        mutex.withLock {
            val current = _state.value
            if (current !is SessionState.Authenticated || current.token != token) return@withLock
            runCatching { tokenManager.saveCurrentUser(profile.id, userName) }
            _state.value = current.copy(
                userId = profile.id.takeIf { it > 0L } ?: current.userId,
                profile = profile,
            )
        }
    }

    private fun authenticatedOrNull(): SessionState.Authenticated? =
        _state.value as? SessionState.Authenticated

    private fun mapTokenLoginError(e: ApiException): ApiException = when {
        e.code == 4001 -> ApiException(e.code, "登录令牌已失效，请重新获取")
        e.code == -1 -> ApiException(e.code, "网络连接异常，请检查后重试")
        else -> e
    }
}

internal fun createUserSession(
    api: HpoiApi,
    dataStore: DataStore<Preferences>,
): UserSession = UserSessionImpl(api, TokenManager(dataStore))
