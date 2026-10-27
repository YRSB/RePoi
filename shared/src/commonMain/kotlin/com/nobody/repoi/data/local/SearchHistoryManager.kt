package com.nobody.repoi.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 搜索历史管理器（最近 20 条关键词，JSON 存单 key）。
 *
 * KMP 版：DataStore 由 Koin 注入（老项目经 `preferencesDataStore` 委托 + Context 获取）。
 */
class SearchHistoryManager(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    val historyFlow: Flow<List<String>> = dataStore.data.map { prefs ->
        val rawJson = prefs[SEARCH_HISTORY_KEY].orEmpty()
        if (rawJson.isBlank()) {
            emptyList()
        } else {
            try {
                json.decodeFromString<List<String>>(rawJson)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun addKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return

        dataStore.edit { prefs ->
            val currentList = try {
                val raw = prefs[SEARCH_HISTORY_KEY].orEmpty()
                if (raw.isBlank()) emptyList() else json.decodeFromString<List<String>>(raw)
            } catch (e: Exception) {
                emptyList()
            }

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
            val currentList = try {
                val raw = prefs[SEARCH_HISTORY_KEY].orEmpty()
                if (raw.isBlank()) emptyList() else json.decodeFromString<List<String>>(raw)
            } catch (e: Exception) {
                emptyList()
            }

            val updatedList = currentList.filterNot { it.equals(trimmed, ignoreCase = true) }
            prefs[SEARCH_HISTORY_KEY] = json.encodeToString(updatedList)
        }
    }

    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.remove(SEARCH_HISTORY_KEY)
        }
    }

    companion object {
        private val SEARCH_HISTORY_KEY = stringPreferencesKey("recent_search_keywords_json")
        private const val MAX_HISTORY_COUNT = 20
    }
}
