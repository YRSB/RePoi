package com.nobody.repoi.data.session

import com.nobody.repoi.data.remote.model.LoginData
import com.nobody.repoi.data.remote.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface UserSession {

    val state: StateFlow<SessionState>

    val tokenFlow: Flow<String>
        get() = state
            .map { (it as? SessionState.Authenticated)?.token.orEmpty() }
            .distinctUntilChanged()

    val isAuthenticated: Boolean

    val currentUserId: Long

    val currentToken: String

    suspend fun login(account: String, password: String): Result<LoginData>

    suspend fun loginWithToken(rawToken: String): Result<UserProfile>

    suspend fun logout()

    suspend fun refreshProfile(): Result<UserProfile>

    suspend fun notifyTokenExpired(triggeredToken: String? = null)

    fun exportActiveToken(): String
}
