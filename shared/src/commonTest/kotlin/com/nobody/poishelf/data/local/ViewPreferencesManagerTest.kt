package com.nobody.poishelf.data.local

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * 视图偏好管理器测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `ViewPreferencesManagerTest`（逐用例；JUnit4 → kotlin.test，
 * Robolectric → [TestDataStore] 真实 DataStore 隔离目录）。
 */
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
        val manager = manager()
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
