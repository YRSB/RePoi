package com.nobody.repoi.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * 管理登录令牌 `utoken` 与用户信息的本地持久化。
 *
 * KMP 移植：构造器改取 `DataStore<Preferences>`（平台模块按同名
 * `user_prefs.preferences_pb` 提供），替代 `Context.preferencesDataStore`。
 * 内存镜像经 `kotlin.concurrent.atomics` 实现（common 无 `@Volatile`）。
 */
@OptIn(ExperimentalAtomicApi::class)
open class TokenManager(
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val writeVersion = AtomicLong(0L)
    private val tokenRef = AtomicReference("")
    private val userIdRef = AtomicLong(0L)
    private val userNameRef = AtomicReference("")

    open var currentToken: String
        get() = tokenRef.load()
        set(value) { tokenRef.store(value) }

    var currentUserId: Long
        get() = userIdRef.load()
        private set(value) { userIdRef.store(value) }

    var currentUserName: String
        get() = userNameRef.load()
        private set(value) { userNameRef.store(value) }

    val tokenFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[TOKEN_KEY] ?: ""
    }

    val userIdFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[USER_ID_KEY] ?: 0L
    }

    init {
        runCatching {
            scope.launch {
                dataStore.data.collect { latest ->
                    val incoming = latest[VERSION_KEY] ?: 0L
                    if (incoming >= writeVersion.load()) {
                        currentToken = latest[TOKEN_KEY] ?: ""
                        currentUserId = latest[USER_ID_KEY] ?: 0L
                        currentUserName = latest[USER_NAME_KEY] ?: ""
                    }
                }
            }
        }
    }

    suspend fun saveToken(token: String, userName: String? = null) {
        val version = writeVersion.incrementAndGet()
        currentToken = token
        currentUserId = 0L
        if (userName != null) {
            currentUserName = userName
        }
        dataStore.edit { prefs ->
            prefs[VERSION_KEY] = version
            prefs[TOKEN_KEY] = token
            prefs.remove(USER_ID_KEY)
            if (userName != null) {
                prefs[USER_NAME_KEY] = userName
            }
        }
    }

    suspend fun saveCurrentUser(userId: Long, userName: String? = null) {
        val version = writeVersion.incrementAndGet()
        currentUserId = userId
        if (userName != null) {
            currentUserName = userName
        }
        dataStore.edit { prefs ->
            prefs[VERSION_KEY] = version
            prefs[USER_ID_KEY] = userId
            if (userName != null) {
                prefs[USER_NAME_KEY] = userName
            }
        }
    }

    // M7: open 化以便 commonTest 替身统计 4001 过期清 token 调用（行为无变化）。
    open suspend fun clearToken() {
        val version = writeVersion.incrementAndGet()
        currentToken = ""
        currentUserId = 0L
        currentUserName = ""
        dataStore.edit { prefs ->
            prefs[VERSION_KEY] = version
            prefs.remove(TOKEN_KEY)
            prefs.remove(USER_ID_KEY)
            prefs.remove(USER_NAME_KEY)
        }
    }

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("utoken")
        private val USER_ID_KEY = longPreferencesKey("user_id")
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val VERSION_KEY = longPreferencesKey("write_version")
    }
}
