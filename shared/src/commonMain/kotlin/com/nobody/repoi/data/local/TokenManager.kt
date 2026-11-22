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
