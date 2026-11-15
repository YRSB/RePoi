package com.nobody.repoi.data.local

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
        // 旧键 all 写入时即归一为 allInfo，落盘不再出现 all
        manager.setFeedSubType("all")

        assertEquals(100, manager.feedCategoryId)
        assertEquals("allInfo", manager.feedSubType)
        assertEquals(100, manager.getInitialFeedCategoryId())
        assertEquals("allInfo", manager.getInitialFeedSubType())
        assertEquals(100, manager.feedCategoryIdFlow.first())
        assertEquals("allInfo", manager.feedSubTypeFlow.first())
        assertEquals(Pair(100, "allInfo"), manager.getFeedPreferences())
    }

    @Test
    fun feedSubType_normalizesLegacyAndInvalidValues() = runTest {
        // 直接向 DataStore 写入老版本可能留下的旧值 all，模拟老用户升级
        val store = TestDataStore.inMemory()
        val key = stringPreferencesKey("feed_sub_type")
        store.edit { prefs -> prefs[key] = "all" }
        val manager = ViewPreferencesManager(store)

        // 读侧：旧值 all 归一为 allInfo，不会把服务端不识别的键发出去
        // （先走 getFeedPreferences 同步内存镜像，避免与构造时的异步预热抢时序）
        assertEquals(Pair(0, "allInfo"), manager.getFeedPreferences())
        assertEquals("allInfo", manager.getInitialFeedSubType())
        assertEquals("allInfo", manager.feedSubTypeFlow.first())

        // 写侧：非法值回落默认项 recommend，且落盘值同步归一
        manager.setFeedSubType("zzz_bogus")
        assertEquals("recommend", manager.getInitialFeedSubType())
        assertEquals("recommend", manager.feedSubTypeFlow.first())
        assertEquals("recommend", store.data.first()[key])
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
