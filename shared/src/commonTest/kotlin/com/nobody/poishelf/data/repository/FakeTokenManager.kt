package com.nobody.poishelf.data.repository

import com.nobody.poishelf.data.local.TestDataStore
import com.nobody.poishelf.data.local.TokenManager

/**
 * commonTest TokenManager 替身：替代 Mockito `mock(TokenManager)`。
 *
 * shared `TokenManager` 为 `open class` + `open var currentToken` + `open suspend fun clearToken`，
 * 此处以子类覆写提供固定 token（`"utoken-test"` 或空串测未登录短路），
 * 并统计 `clearToken()` 调用次数（替代 `verify(tokenManager).clearToken()`）。
 */
class FakeTokenManager(token: String) : TokenManager(TestDataStore.userPrefs()) {
    override var currentToken: String = token

    var clearTokenCalls: Int = 0
        private set

    override suspend fun clearToken() {
        clearTokenCalls++
        currentToken = ""
    }
}
