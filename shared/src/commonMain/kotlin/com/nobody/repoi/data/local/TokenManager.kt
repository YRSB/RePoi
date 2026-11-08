package com.nobody.repoi.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * 管理登录令牌 `utoken` 与用户信息的本地持久化。
 *
 * KMP 移植：构造器改取 `DataStore<Preferences>`（平台模块按同名
 * `user_prefs.preferences_pb` 提供），替代 `Context.preferencesDataStore`。
 * 内存镜像经 `kotlin.concurrent.atomics` 实现（common 无 `@Volatile`）。
 *
 * 可见性：本适配器为 [com.nobody.repoi.data.session.UserSessionImpl] 的内部实现细节，
 * 仅由 [com.nobody.repoi.data.session.createUserSession] 组合根构造，
 * 不注册为可独立注入的单例，也不再作为公开接缝（ADR-0005 单一鉴权出口）。
 * `prewarmOnCreate` 仅供测试替身关闭真实 DataStore 预读。
 */
@OptIn(ExperimentalAtomicApi::class)
internal open class TokenManager(
    private val dataStore: DataStore<Preferences>,
    prewarmOnCreate: Boolean = true,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val tokenRef = AtomicReference("")
    private val userIdRef = AtomicLong(0L)
    private val userNameRef = AtomicReference("")
    private val localWriteRef = AtomicBoolean(false)

    open var currentToken: String
        get() = tokenRef.load()
        set(value) { tokenRef.store(value) }

    open var currentUserId: Long
        get() = userIdRef.load()
        set(value) { userIdRef.store(value) }

    open var currentUserName: String
        get() = userNameRef.load()
        set(value) { userNameRef.store(value) }

    open val tokenFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[TOKEN_KEY] ?: ""
    }

    val userIdFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[USER_ID_KEY] ?: 0L
    }

    private val prewarmJob: Job? = if (prewarmOnCreate) {
        scope.launch {
            runCatching {
                // 冷启动预热：从 DataStore 一次性恢复内存镜像。
                // 不使用持续 collect，避免与本地写入产生异步时序竞态或覆写测试替身。
                val prefs = dataStore.data.first()
                if (localWriteRef.load()) return@runCatching
                if (tokenRef.load().isEmpty()) prefs[TOKEN_KEY]?.let { tokenRef.store(it) }
                if (userIdRef.load() == 0L) prefs[USER_ID_KEY]?.let { userIdRef.store(it) }
                if (userNameRef.load().isEmpty()) prefs[USER_NAME_KEY]?.let { userNameRef.store(it) }
            }
        }
    } else {
        null
    }

    open suspend fun awaitReady() {
        prewarmJob?.join()
    }

    open suspend fun saveToken(token: String, userName: String? = null) {
        localWriteRef.store(true)
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

    open suspend fun saveCurrentUser(userId: Long, userName: String? = null) {
        localWriteRef.store(true)
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

    // M7: open 化以便 commonTest 替身统计 4001 过期清 token 调用（行为无变化）。
    open suspend fun clearToken() {
        localWriteRef.store(true)
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
