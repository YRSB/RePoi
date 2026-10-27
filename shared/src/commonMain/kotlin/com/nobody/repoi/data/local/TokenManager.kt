package com.nobody.repoi.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 管理登录令牌 `utoken` 与用户信息的本地持久化。
 *
 * KMP 版：DataStore 由 Koin 注入（老项目经 `preferencesDataStore` 委托 + Context 获取）。
 */
open class TokenManager(
    private val dataStore: DataStore<Preferences>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    open var currentToken: String = ""

    @Volatile
    var currentUserId: Long = 0L
        private set

    @Volatile
    var currentUserName: String = ""
        private set

    val tokenFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[TOKEN_KEY] ?: ""
    }

    init {
        runCatching {
            scope.launch {
                dataStore.data.collect { prefs ->
                    prefs[TOKEN_KEY]?.let { currentToken = it }
                    prefs[USER_ID_KEY]?.let { currentUserId = it }
                    prefs[USER_NAME_KEY]?.let { currentUserName = it }
                }
            }
        }
    }

    suspend fun saveToken(token: String, userName: String? = null) {
        currentToken = token
        currentUserId = 0L
        if (userName != null) {
            currentUserName = userName
        }
        dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
            prefs.remove(USER_ID_KEY)
            if (userName != null) {
                prefs[USER_NAME_KEY] = userName
            }
        }
    }

    suspend fun saveCurrentUser(userId: Long, userName: String? = null) {
        currentUserId = userId
        if (userName != null) {
            currentUserName = userName
        }
        dataStore.edit { prefs ->
            prefs[USER_ID_KEY] = userId
            if (userName != null) {
                prefs[USER_NAME_KEY] = userName
            }
        }
    }

    suspend fun clearToken() {
        currentToken = ""
        currentUserId = 0L
        currentUserName = ""
        dataStore.edit { prefs ->
            prefs.remove(TOKEN_KEY)
            prefs.remove(USER_ID_KEY)
            prefs.remove(USER_NAME_KEY)
        }
    }

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("utoken")
        private val USER_ID_KEY = longPreferencesKey("user_id")
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
    }
}
