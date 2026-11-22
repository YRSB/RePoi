package com.nobody.repoi.data.local

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchHistoryManagerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private suspend fun manager(): SearchHistoryManager {
        val manager = SearchHistoryManager(TestDataStore.searchHistoryPrefs(), json)
        manager.clearAll()
        return manager
    }

    @Test
    fun addKeyword_keepsNewestFirstAndDeduplicatesIgnoreCase() = runTest {
        val manager = manager()
        manager.addKeyword("初音")
        manager.addKeyword("手办")
        manager.addKeyword("  初音  ")
        manager.addKeyword("ABC")
        manager.addKeyword("abc")

        assertEquals(listOf("abc", "初音", "手办"), manager.historyFlow.first())
    }

    @Test
    fun addKeyword_ignoresBlankInput() = runTest {
        val manager = manager()
        manager.addKeyword("   ")
        manager.addKeyword("")

        assertTrue(manager.historyFlow.first().isEmpty())
    }

    @Test
    fun addKeyword_trimsToTwentyEntries() = runTest {
        val manager = manager()
        (1..21).forEach { manager.addKeyword("k$it") }

        val history = manager.historyFlow.first()
        assertEquals(20, history.size)
        assertEquals("k21", history.first())
        assertEquals("k2", history.last())
    }

    @Test
    fun removeKeyword_removesIgnoreCase() = runTest {
        val manager = manager()
        manager.addKeyword("初音")
        manager.addKeyword("手办")

        manager.removeKeyword(" 初音 ")

        assertEquals(listOf("手办"), manager.historyFlow.first())
    }

    @Test
    fun clearAll_removesEveryEntry() = runTest {
        val manager = manager()
        manager.addKeyword("初音")
        manager.addKeyword("手办")

        manager.clearAll()

        assertTrue(manager.historyFlow.first().isEmpty())
    }
}
