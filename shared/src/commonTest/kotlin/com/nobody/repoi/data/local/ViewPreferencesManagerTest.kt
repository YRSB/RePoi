package com.nobody.repoi.data.local

import com.nobody.repoi.test.TestDataStores
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ViewPreferencesManagerTest {

    private val stores = TestDataStores()
    private lateinit var manager: ViewPreferencesManager

    @BeforeTest
    fun setUp() = runTest {
        manager = ViewPreferencesManager(stores.createDataStore("view_prefs"))
        manager.isHobbyListGridModeFlow.first()
    }

    @AfterTest
    fun tearDown() {
        stores.cleanUp()
    }

    @Test
    fun listModes_persistAndExposeFlows() = runTest {
        manager.setHobbyListGridMode(false)
        manager.setCollectListGridMode(false)

        assertFalse(manager.isHobbyListGridMode)
        assertFalse(manager.isCollectListGridMode)
        assertFalse(manager.isHobbyListGridModeFlow.first())
        assertFalse(manager.isCollectListGridModeFlow.first())
    }

    @Test
    fun feedFilters_persistToDataStore() = runTest {
        manager.setFeedCategory(100)
        manager.setFeedSubType("all")

        assertEquals(100, manager.feedCategoryId)
        assertEquals("all", manager.feedSubType)
        assertEquals(100, manager.getInitialFeedCategoryId())
        assertEquals("all", manager.getInitialFeedSubType())
        assertEquals(100, manager.feedCategoryIdFlow.first())
        assertEquals("all", manager.feedSubTypeFlow.first())
        assertEquals(Pair(100, "all"), manager.getFeedPreferences())
    }

    @Test
    fun exHobbyFlag_persistsAndExposesFlow() = runTest {
        manager.setExHobbyEnabled(false)

        assertFalse(manager.isExHobbyEnabled)
        assertFalse(manager.isExHobbyEnabledFlow.first())
    }

    @Test
    fun collectHobbyOrder_isScopedByCollectState() = runTest {
        manager.setCollectHobbyOrder("care", "actionDate", 2)

        assertEquals(Pair("actionDate", 2), manager.getCollectHobbyOrder("care"))
        assertNull(manager.getCollectHobbyOrder("buy"))
    }
}
