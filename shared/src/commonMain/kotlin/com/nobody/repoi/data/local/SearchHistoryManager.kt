package com.nobody.repoi.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 搜索历史管理器（KMP 移植：构造器改取 `DataStore<Preferences>` + `Json`）。
 */
class SearchHistoryManager(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {
    val historyFlow: Flow<List<String>> = dataStore.data.map { prefs ->
        decode(prefs[SEARCH_HISTORY_KEY].orEmpty())
    }

    suspend fun getHistory(): List<String> =
        dataStore.data.first().let { prefs ->
            decode(prefs[SEARCH_HISTORY_KEY].orEmpty())
        }

    suspend fun addKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return

        dataStore.edit { prefs ->
            val currentList = decode(prefs[SEARCH_HISTORY_KEY].orEmpty())

            val updatedList = mutableListOf(trimmed).apply {
                currentList.forEach { item ->
                    if (!item.equals(trimmed, ignoreCase = true)) {
                        add(item)
                    }
                }
            }.take(MAX_HISTORY_COUNT)

            prefs[SEARCH_HISTORY_KEY] = json.encodeToString(updatedList)
        }
    }

    suspend fun removeKeyword(keyword: String) {
        val trimmed = keyword.trim()
        dataStore.edit { prefs ->
            val currentList = decode(prefs[SEARCH_HISTORY_KEY].orEmpty())

            val updatedList = currentList.filterNot { it.equals(trimmed, ignoreCase = true) }
            prefs[SEARCH_HISTORY_KEY] = json.encodeToString(updatedList)
        }
    }

    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.remove(SEARCH_HISTORY_KEY)
        }
    }

    private fun decode(rawJson: String): List<String> {
        if (rawJson.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(rawJson)
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private val SEARCH_HISTORY_KEY = stringPreferencesKey("recent_search_keywords_json")
        private const val MAX_HISTORY_COUNT = 20
    }
}
