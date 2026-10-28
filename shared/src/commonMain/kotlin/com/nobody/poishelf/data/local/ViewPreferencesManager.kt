package com.nobody.poishelf.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * 界面与展示偏好管理器（KMP 移植）。
 *
 * `SharedPreferences` 同步镜相对 DataStore 单一真相源 + atomics 内存镜像
 * （桌面无 SP；common 无 `@Volatile`。启动默认喂 volatile，collector 随后纠正——与原版首帧语义一致）。
 */
@OptIn(ExperimentalAtomicApi::class)
class ViewPreferencesManager(
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val hobbyListGridRef = AtomicBoolean(true)
    private val collectListGridRef = AtomicBoolean(true)
    private val feedCategoryIdRef = AtomicInt(0)
    private val feedSubTypeRef = AtomicReference("recommend")
    private val feedZoneTabKeyRef = AtomicReference("all")
    private val exHobbyEnabledRef = AtomicBoolean(true)

    var isHobbyListGridMode: Boolean
        get() = hobbyListGridRef.load()
        private set(value) { hobbyListGridRef.store(value) }

    var isCollectListGridMode: Boolean
        get() = collectListGridRef.load()
        private set(value) { collectListGridRef.store(value) }

    var feedCategoryId: Int
        get() = feedCategoryIdRef.load()
        private set(value) { feedCategoryIdRef.store(value) }

    var feedSubType: String
        get() = feedSubTypeRef.load()
        private set(value) { feedSubTypeRef.store(value) }

    var feedZoneTabKey: String
        get() = feedZoneTabKeyRef.load()
        private set(value) { feedZoneTabKeyRef.store(value) }

    var isExHobbyEnabled: Boolean
        get() = exHobbyEnabledRef.load()
        private set(value) { exHobbyEnabledRef.store(value) }

    val isHobbyListGridModeFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_HOBBY_LIST_GRID_MODE] ?: true
    }

    val isCollectListGridModeFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_COLLECT_LIST_GRID_MODE] ?: true
    }

    val feedCategoryIdFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_FEED_CATEGORY_ID] ?: 0
    }

    val feedSubTypeFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_FEED_SUB_TYPE] ?: "recommend"
    }

    val feedZoneTabKeyFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_FEED_ZONE_TAB_KEY] ?: "all"
    }

    val cachedFocusZonesJsonFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_CACHED_FOCUS_ZONES]
    }

    val cachedZoneSettingsJsonFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_CACHED_ZONE_SETTINGS]
    }

    val isExHobbyEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_EXHOBBY_ENABLED] ?: true
    }

    init {
        scope.launch {
            dataStore.data.collect { prefs ->
                isHobbyListGridMode = prefs[KEY_HOBBY_LIST_GRID_MODE] ?: true
                isCollectListGridMode = prefs[KEY_COLLECT_LIST_GRID_MODE] ?: true
                feedCategoryId = prefs[KEY_FEED_CATEGORY_ID] ?: 0
                feedSubType = prefs[KEY_FEED_SUB_TYPE] ?: "recommend"
                feedZoneTabKey = prefs[KEY_FEED_ZONE_TAB_KEY] ?: "all"
                isExHobbyEnabled = prefs[KEY_EXHOBBY_ENABLED] ?: true
            }
        }
    }

    suspend fun setHobbyListGridMode(isGrid: Boolean) {
        isHobbyListGridMode = isGrid
        dataStore.edit { prefs ->
            prefs[KEY_HOBBY_LIST_GRID_MODE] = isGrid
        }
    }

    suspend fun setCollectListGridMode(isGrid: Boolean) {
        isCollectListGridMode = isGrid
        dataStore.edit { prefs ->
            prefs[KEY_COLLECT_LIST_GRID_MODE] = isGrid
        }
    }

    /**
     * 按收藏状态分别记忆周边页的排序维度与升降序。
     *
     * @param collectState 收藏状态档位（""/care/want/preorder/buy/resell）
     * @param order 排序维度（actionDate/release/myRating/updateDate/orderTime）
     * @param sortType 升降序（1 升 / 2 降）
     */
    suspend fun setCollectHobbyOrder(collectState: String, order: String, sortType: Int) {
        dataStore.edit { prefs ->
            prefs[keyOfCollectHobbyOrder(collectState)] = order
            prefs[keyOfCollectHobbySortType(collectState)] = sortType
        }
    }

    /**
     * 读取指定收藏状态档位上次使用的排序维度与升降序；从未设置过时返回 null。
     */
    suspend fun getCollectHobbyOrder(collectState: String): Pair<String, Int>? =
        dataStore.data.first().let { prefs ->
            val order = prefs[keyOfCollectHobbyOrder(collectState)]
            val sortType = prefs[keyOfCollectHobbySortType(collectState)]
            if (order == null || sortType == null) null else Pair(order, sortType)
        }

    /**
     * 同步获取初始分类 ID（启动时无锁零延迟，杜绝首屏跳变）。
     */
    fun getInitialFeedCategoryId(): Int = feedCategoryId

    /**
     * 同步获取初始二级子类型（启动时无锁零延迟，杜绝首屏跳变）。
     */
    fun getInitialFeedSubType(): String = feedSubType

    /**
     * 同步获取初始首页动态选中的专区 TabKey（"all"、"follow" 或 "z{nodeId}"）。
     */
    fun getInitialFeedZoneTabKey(): String = feedZoneTabKey

    suspend fun getFeedPreferences(): Pair<Int, String> {
        val prefs = dataStore.data.first()
        val catId = prefs[KEY_FEED_CATEGORY_ID] ?: 0
        val sub = prefs[KEY_FEED_SUB_TYPE] ?: "recommend"
        feedCategoryId = catId
        feedSubType = sub
        return Pair(catId, sub)
    }

    suspend fun setFeedCategory(categoryId: Int) {
        feedCategoryId = categoryId
        dataStore.edit { prefs ->
            prefs[KEY_FEED_CATEGORY_ID] = categoryId
        }
    }

    suspend fun setFeedSubType(subType: String) {
        feedSubType = subType
        dataStore.edit { prefs ->
            prefs[KEY_FEED_SUB_TYPE] = subType
        }
    }

    suspend fun setFeedZoneTabKey(tabKey: String) {
        feedZoneTabKey = tabKey
        dataStore.edit { prefs ->
            prefs[KEY_FEED_ZONE_TAB_KEY] = tabKey
        }
    }

    fun getCachedFocusZonesJson(): String? = null

    suspend fun setCachedFocusZonesJson(json: String) {
        dataStore.edit { prefs ->
            prefs[KEY_CACHED_FOCUS_ZONES] = json
        }
    }

    fun getCachedZoneSettingsJson(): String? = null

    suspend fun setCachedZoneSettingsJson(json: String) {
        dataStore.edit { prefs ->
            prefs[KEY_CACHED_ZONE_SETTINGS] = json
        }
    }

    suspend fun setExHobbyEnabled(enabled: Boolean) {
        isExHobbyEnabled = enabled
        dataStore.edit { prefs ->
            prefs[KEY_EXHOBBY_ENABLED] = enabled
        }
    }

    suspend fun getCachedFocusZones(): String? =
        dataStore.data.first()[KEY_CACHED_FOCUS_ZONES]

    suspend fun getCachedZoneSettings(): String? =
        dataStore.data.first()[KEY_CACHED_ZONE_SETTINGS]

    companion object {
        private val KEY_HOBBY_LIST_GRID_MODE = booleanPreferencesKey("hobby_list_is_grid_mode")
        private val KEY_COLLECT_LIST_GRID_MODE = booleanPreferencesKey("collect_list_is_grid_mode")
        private val KEY_FEED_CATEGORY_ID = intPreferencesKey("feed_category_id")
        private val KEY_FEED_SUB_TYPE = stringPreferencesKey("feed_sub_type")
        private val KEY_FEED_ZONE_TAB_KEY = stringPreferencesKey("feed_zone_tab_key")
        private val KEY_CACHED_FOCUS_ZONES = stringPreferencesKey("cached_focus_zones_json")
        private val KEY_CACHED_ZONE_SETTINGS = stringPreferencesKey("cached_zone_settings_json")
        private val KEY_EXHOBBY_ENABLED = booleanPreferencesKey("is_exhobby_enabled")

        private fun keyOfCollectHobbyOrder(collectState: String) =
            stringPreferencesKey("collect_hobby_order_$collectState")

        private fun keyOfCollectHobbySortType(collectState: String) =
            intPreferencesKey("collect_hobby_sort_type_$collectState")
    }
}
