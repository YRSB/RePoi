package com.nobody.repoi.data.repository

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.TokenManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * commonTest TokenManager 替身：替代 Mockito `mock(TokenManager)`。
 *
 * 不复用真实存储：构造时传入 `prewarmOnCreate = false`，不触发任何 DataStore 读取；
 * 全部读写走内存镜像，并覆写 `awaitReady` / `saveToken` / `saveCurrentUser` / `clearToken`
 * 以便同步观测与计数（`clearTokenCalls` 等价 `verify(tokenManager).clearToken()`）。
 *
 * @param token 预置登录令牌，空串即未登录
 * @param userId 预置用户 ID
 * @param userName 预置用户名（评论乐观回显等场景）
 */
internal class FakeTokenManager(
    token: String = "",
    userId: Long = 0L,
    userName: String = "",
) : TokenManager(TestDataStore.userPrefs(), prewarmOnCreate = false) {

    private val tokenState = MutableStateFlow(token)

    override val tokenFlow: Flow<String> = tokenState

    override var currentToken: String
        get() = tokenState.value
        set(value) { tokenState.value = value }

    override var currentUserId: Long = userId

    override var currentUserName: String = userName

    var clearTokenCalls: Int = 0
        private set

    var awaitReadyCalls: Int = 0
        private set

    var saveTokenCalls: Int = 0
        private set

    var saveCurrentUserCalls: Int = 0
        private set

    override suspend fun awaitReady() {
        awaitReadyCalls++
    }

    override suspend fun saveToken(token: String, userName: String?) {
        saveTokenCalls++
        currentToken = token
        currentUserId = 0L
        if (userName != null) currentUserName = userName
    }

    override suspend fun saveCurrentUser(userId: Long, userName: String?) {
        saveCurrentUserCalls++
        currentUserId = userId
        if (userName != null) currentUserName = userName
    }

    override suspend fun clearToken() {
        clearTokenCalls++
        currentToken = ""
        currentUserId = 0L
        currentUserName = ""
    }
}
