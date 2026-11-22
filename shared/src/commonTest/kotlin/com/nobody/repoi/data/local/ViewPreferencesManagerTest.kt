package com.nobody.repoi.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ViewPreferencesManagerTest {

    private suspend fun manager(): ViewPreferencesManager {
        val manager = ViewPreferencesManager(TestDataStore.viewPrefs())
        manager.isHobbyListGridModeFlow.first()
        return manager
    }

    @Test
    fun listModes_persistAndExposeFlows() = runTest {
        val manager = manager()
        manager.setHobbyListGridMode(false)
        manager.setCollectListGridMode(false)

        assertFalse(manager.isHobbyListGridMode)
        assertFalse(manager.isCollectListGridMode)
        assertFalse(manager.isHobbyListGridModeFlow.first())
        assertFalse(manager.isCollectListGridModeFlow.first())
    }

    @Test
    fun feedFilters_persistToDataStoreAndSharedPreferences() = runTest {

        val store = TestDataStore.inMemory()
        val manager = ViewPreferencesManager(store)
        manager.setFeedCategory(100)

        manager.setFeedSubType("all")

        assertEquals(100, manager.feedCategoryId)
        assertEquals("allInfo", manager.feedSubType)
        assertEquals(100, manager.getInitialFeedCategoryId())
        assertEquals("allInfo", manager.getInitialFeedSubType())
        assertEquals(100, manager.feedCategoryIdFlow.first())
        assertEquals("allInfo", manager.feedSubTypeFlow.first())
        assertEquals(Pair(100, "allInfo"), manager.getFeedPreferences())

        val persisted = store.data.first()
        assertEquals("allInfo", persisted[stringPreferencesKey("feed_sub_type")])
        assertEquals(100, persisted[intPreferencesKey("feed_category_id")])
    }

    @Test
    fun feedSubType_normalizesLegacyAndInvalidValues() = runTest {

        val store = TestDataStore.inMemory()
        val key = stringPreferencesKey("feed_sub_type")
        store.edit { prefs -> prefs[key] = "all" }
        val manager = ViewPreferencesManager(store)

        assertEquals(Pair(0, "allInfo"), manager.getFeedPreferences())
        assertEquals("allInfo", manager.getInitialFeedSubType())
        assertEquals("allInfo", manager.feedSubTypeFlow.first())

        manager.setFeedSubType("zzz_bogus")
        assertEquals("recommend", manager.getInitialFeedSubType())
        assertEquals("recommend", manager.feedSubTypeFlow.first())
        assertEquals("recommend", store.data.first()[key])
    }

    @Test
    fun feedZoneBoard_persistsAndMirrorsSelection() = runTest {
        val manager = manager()

        manager.setFeedZoneBoard("z100|album")

        assertEquals("z100|album", manager.feedZoneBoard)
        assertEquals("z100|album", manager.getInitialFeedZoneBoardKey())
        assertEquals("z100|album", manager.feedZoneBoardFlow.first())
    }

    @Test
    fun feedZoneBoard_preheatsFromDataStoreOnColdStart() = runTest {

        val store = TestDataStore.inMemory()
        val key = stringPreferencesKey("feed_zone_board")
        store.edit { prefs -> prefs[key] = "follow|action" }
        val manager = ViewPreferencesManager(store)

        manager.awaitPreheat()

        assertEquals("follow|action", manager.getInitialFeedZoneBoardKey())
    }

    @Test
    fun exHobbyFlag_persistsAndExposesFlow() = runTest {
        val manager = manager()
        manager.setExHobbyEnabled(false)

        assertFalse(manager.isExHobbyEnabled)
        assertFalse(manager.isExHobbyEnabledFlow.first())
    }

    @Test
    fun collectHobbyOrder_isScopedByCollectState() = runTest {
        val manager = manager()
        manager.setCollectHobbyOrder("care", "actionDate", 2)

        assertEquals(Pair("actionDate", 2), manager.getCollectHobbyOrder("care"))
        assertNull(manager.getCollectHobbyOrder("buy"))
    }
}
