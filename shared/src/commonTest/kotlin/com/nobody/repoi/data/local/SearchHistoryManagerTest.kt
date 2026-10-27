package com.nobody.repoi.data.local

import com.nobody.repoi.test.TestDataStores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchHistoryManagerTest {

    private val stores = TestDataStores()
    private lateinit var manager: SearchHistoryManager

    @BeforeTest
    fun setUp() = runTest {
        manager = SearchHistoryManager(stores.createDataStore("search_history_prefs"), Json)
        manager.clearAll()
    }

    @AfterTest
    fun tearDown() {
        stores.cleanUp()
    }

    @Test
    fun addKeyword_keepsNewestFirstAndDeduplicatesIgnoreCase() = runTest {
        manager.addKeyword("初音")
        manager.addKeyword("手办")
        manager.addKeyword("  初音  ")
        manager.addKeyword("ABC")
        manager.addKeyword("abc")

        assertEquals(listOf("abc", "初音", "手办"), manager.historyFlow.first())
    }

    @Test
    fun addKeyword_ignoresBlankInput() = runTest {
        manager.addKeyword("   ")
        manager.addKeyword("")

        assertTrue(manager.historyFlow.first().isEmpty())
    }

    @Test
    fun addKeyword_trimsToTwentyEntries() = runTest {
        (1..21).forEach { manager.addKeyword("k$it") }

        val history = manager.historyFlow.first()
        assertEquals(20, history.size)
        assertEquals("k21", history.first())
        assertEquals("k2", history.last())
    }

    @Test
    fun removeKeyword_removesIgnoreCase() = runTest {
        manager.addKeyword("初音")
        manager.addKeyword("手办")

        manager.removeKeyword(" 初音 ")

        assertEquals(listOf("手办"), manager.historyFlow.first())
    }

    @Test
    fun clearAll_removesEveryEntry() = runTest {
        manager.addKeyword("初音")
        manager.addKeyword("手办")

        manager.clearAll()

        assertTrue(manager.historyFlow.first().isEmpty())
    }
}
