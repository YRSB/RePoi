package com.nobody.repoi.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path.Companion.toPath

object TestDataStore {

    fun create(fileName: String, scope: CoroutineScope? = null): DataStore<Preferences> {
        val dir = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "repoi-test-datastore-${Random.nextLong()}").toString()
        val produceFile = { dir.toPath().resolve(fileName) }
        return if (scope == null) {
            PreferenceDataStoreFactory.createWithPath(produceFile = produceFile)
        } else {
            PreferenceDataStoreFactory.createWithPath(produceFile = produceFile, scope = scope)
        }
    }

    fun userPrefs(scope: CoroutineScope? = null): DataStore<Preferences> =
        create(DataStoreFiles.USER_PREFS, scope)

    fun themePrefs(scope: CoroutineScope? = null): DataStore<Preferences> =
        create(DataStoreFiles.THEME_PREFS, scope)

    fun searchHistoryPrefs(scope: CoroutineScope? = null): DataStore<Preferences> =
        create(DataStoreFiles.SEARCH_HISTORY_PREFS, scope)

    fun viewPrefs(scope: CoroutineScope? = null): DataStore<Preferences> =
        create(DataStoreFiles.VIEW_PREFS, scope)

    fun coverFreshnessPrefs(scope: CoroutineScope? = null): DataStore<Preferences> =
        create(DataStoreFiles.COVER_FRESHNESS, scope)

    fun inMemory(): DataStore<Preferences> = InMemoryPreferencesDataStore()
}

suspend fun DataStore<Preferences>.awaitPendingWrites() {
    edit { }
}

private class InMemoryPreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)
    private val guard = Mutex()

    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        guard.withLock {
            val updated = transform(state.value)
            state.value = updated
            updated
        }
}

internal fun tempPathOf(name: String): String =
    (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "repoi-test-$name").toString().also {
        FileSystem.SYSTEM.createDirectories(it.toPath())
    }
