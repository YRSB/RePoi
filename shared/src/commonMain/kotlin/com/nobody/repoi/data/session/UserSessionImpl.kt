package com.nobody.repoi.data.session

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

/**
 * [UserSession] 的唯一实现：内聚凭据预热、登录/登出、档案刷新与 4001 自愈。
 *
 * 时序与并发契约：
 * - 构造即启动内部预热协程；恢复出本地有效凭据后立即发射
 *   [SessionState.Authenticated]，网络档案刷新在后台增量进行，绝不阻塞调用方；
 * - 预热期间外部已通过登录 / 登出改变状态时，预热结果直接放弃，不覆盖新状态；
 * - 所有状态迁移经 [mutex] 串行化；网络请求一律在锁外执行，避免长事务阻塞。
 */
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

    override val currentUserName: String
        get() = authenticatedOrNull()?.profile?.displayName ?: tokenManager.currentUserName

    init {
        // 预热在内部 scope 内闭环，外部调用方无需等待即可拿到 Prewarming 态。
        scope.launch { runCatching { prewarm() } }
    }

    override suspend fun login(account: String, password: String): Result<LoginData> = runCatching {
        val response = api.login(account = account, password = password)
        if (response.isSuccess || (response.success && response.data?.utoken?.isNotBlank() == true)) {
            val data = response.data ?: throw ApiException(response.code, "服务器未返回登录数据")
            val token = data.utoken?.takeIf { it.isNotBlank() }
                ?: throw ApiException(response.code, "未获取到有效的登录令牌")
            val userId = data.user?.actualUserId ?: 0L
            val userName = data.user?.userName
            mutex.withLock {
                tokenManager.saveToken(token, userName)
                if (userId > 0L) tokenManager.saveCurrentUser(userId, userName)
                _state.value = SessionState.Authenticated(userId = userId, token = token)
            }
            launchProfileRefresh()
            data
        } else {
            throw ApiException(response.code, response.msg ?: "登录失败")
        }
    }

    override suspend fun loginWithToken(rawToken: String): Result<UserProfile> = runCatching {
        val candidate = rawToken.trim()
        if (candidate.isBlank()) throw ApiException(400, "请输入登录令牌")
        val response = try {
            api.getUserSelfWithToken(candidate)
        } catch (e: Exception) {
            if (e is ApiException) throw mapTokenLoginError(e)
            throw ApiException(-1, "网络连接异常，请检查后重试")
        }
        if (response.isSuccess || (response.success && response.data?.user != null)) {
            val profile = response.data?.user ?: throw ApiException(response.code, "用户信息为空")
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
        } else {
            throw mapTokenLoginError(ApiException(response.code, response.msg ?: "Token 登录失败"))
        }
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
        val response = api.getUserSelf()
        if (response.isSuccess || (response.success && response.data?.user != null)) {
            val profile = response.data?.user ?: throw ApiException(response.code, "用户信息为空")
            applyProfile(snapshot, profile)
            profile
        } else {
            if (response.isTokenExpired) expireIfActive(snapshot)
            throw ApiException(response.code, response.msg ?: "获取用户信息失败")
        }
    }

    override suspend fun notifyTokenExpired() {
        mutex.withLock {
            // 幂等：已处于未认证且存储凭据已空时无需重复清理。
            if (_state.value == SessionState.Unauthenticated && tokenManager.currentToken.isBlank()) {
                return@withLock
            }
            tokenManager.clearToken()
            _state.value = SessionState.Unauthenticated
        }
    }

    override fun exportActiveToken(): String = currentToken

    /** 冷启动预热：凭据就绪即发射状态，档案刷新移交后台。 */
    private suspend fun prewarm() {
        tokenManager.awaitReady()
        val restored = mutex.withLock {
            // 预热期间登录 / 登出已改变状态：放弃预热结果，绝不覆盖新状态。
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

    /** 后台静默刷新：失败/令牌失效均不影响调用方，失效路径内部已自愈。 */
    private suspend fun refreshProfileSilently() {
        runCatching { refreshProfile() }
    }

    /** 把档案写回当前会话；仅当会话令牌未变时生效，避免旧请求覆盖新登录。 */
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

    /** 4001 自愈：仅当失效请求仍属于当前会话时才清理，避免误伤新登录会话。 */
    private suspend fun expireIfActive(token: String) {
        mutex.withLock {
            if (currentToken != token) return@withLock
            tokenManager.clearToken()
            _state.value = SessionState.Unauthenticated
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
