package com.nobody.repoi.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nobody.repoi.data.remote.model.ActionSubType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class ViewPreferencesManager(
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val hobbyListGridRef = AtomicBoolean(true)
    private val collectListGridRef = AtomicBoolean(true)
    private val feedCategoryIdRef = AtomicInt(0)
    private val feedSubTypeRef = AtomicReference(ActionSubType.DEFAULT.apiValue)
    private val feedZoneTabKeyRef = AtomicReference("all")
    private val feedZoneBoardRef = AtomicReference("")
    private val exHobbyEnabledRef = AtomicBoolean(true)
    private val imageCacheMaxBytesRef = AtomicLong(DEFAULT_IMAGE_CACHE_MAX_BYTES)

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

    var feedZoneBoard: String
        get() = feedZoneBoardRef.load()
        private set(value) { feedZoneBoardRef.store(value) }

    var isExHobbyEnabled: Boolean
        get() = exHobbyEnabledRef.load()
        private set(value) { exHobbyEnabledRef.store(value) }

    var imageCacheMaxBytes: Long
        get() = imageCacheMaxBytesRef.load()
        private set(value) { imageCacheMaxBytesRef.store(value) }

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

        ActionSubType.fromPersisted(prefs[KEY_FEED_SUB_TYPE]).apiValue
    }

    val feedZoneTabKeyFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_FEED_ZONE_TAB_KEY] ?: "all"
    }

    val feedZoneBoardFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_FEED_ZONE_BOARD] ?: ""
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

    val imageCacheMaxBytesFlow: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_IMAGE_CACHE_MAX_BYTES] ?: DEFAULT_IMAGE_CACHE_MAX_BYTES
    }

    private val preheatJob: Job = scope.launch {
        runCatching {
            val prefs = dataStore.data.first()
            prefs[KEY_HOBBY_LIST_GRID_MODE]?.let { isHobbyListGridMode = it }
            prefs[KEY_COLLECT_LIST_GRID_MODE]?.let { isCollectListGridMode = it }
            prefs[KEY_FEED_CATEGORY_ID]?.let { feedCategoryId = it }
            prefs[KEY_FEED_SUB_TYPE]?.let { feedSubType = ActionSubType.normalizeKey(it) }
            prefs[KEY_FEED_ZONE_TAB_KEY]?.let { feedZoneTabKey = it }
            prefs[KEY_FEED_ZONE_BOARD]?.let { feedZoneBoard = it }
            prefs[KEY_EXHOBBY_ENABLED]?.let { isExHobbyEnabled = it }
            prefs[KEY_IMAGE_CACHE_MAX_BYTES]?.let { imageCacheMaxBytes = it }
        }
    }

    suspend fun awaitPreheat() {
        preheatJob.join()
    }

    suspend fun setHobbyListGridMode(isGrid: Boolean) {
        awaitPreheat()
        isHobbyListGridMode = isGrid
        dataStore.edit { prefs ->
            prefs[KEY_HOBBY_LIST_GRID_MODE] = isGrid
        }
    }

    suspend fun setCollectListGridMode(isGrid: Boolean) {
        awaitPreheat()
        isCollectListGridMode = isGrid
        dataStore.edit { prefs ->
            prefs[KEY_COLLECT_LIST_GRID_MODE] = isGrid
        }
    }

    suspend fun setCollectHobbyOrder(collectState: String, order: String, sortType: Int) {
        awaitPreheat()
        dataStore.edit { prefs ->
            prefs[keyOfCollectHobbyOrder(collectState)] = order
            prefs[keyOfCollectHobbySortType(collectState)] = sortType
        }
    }

    suspend fun getCollectHobbyOrder(collectState: String): Pair<String, Int>? =
        dataStore.data.first().let { prefs ->
            val order = prefs[keyOfCollectHobbyOrder(collectState)]
            val sortType = prefs[keyOfCollectHobbySortType(collectState)]
            if (order == null || sortType == null) null else Pair(order, sortType)
        }

    fun getInitialFeedCategoryId(): Int = feedCategoryId

    fun getInitialFeedSubType(): String = ActionSubType.fromPersisted(feedSubType).apiValue

    fun getInitialFeedZoneTabKey(): String = feedZoneTabKey

    fun getInitialFeedZoneBoardKey(): String = feedZoneBoard

    suspend fun getFeedPreferences(): Pair<Int, String> {
        awaitPreheat()
        val prefs = dataStore.data.first()
        val catId = prefs[KEY_FEED_CATEGORY_ID] ?: 0

        val sub = ActionSubType.fromPersisted(prefs[KEY_FEED_SUB_TYPE]).apiValue
        feedCategoryId = catId
        feedSubType = sub
        return Pair(catId, sub)
    }

    suspend fun setFeedCategory(categoryId: Int) {
        awaitPreheat()
        feedCategoryId = categoryId
        dataStore.edit { prefs ->
            prefs[KEY_FEED_CATEGORY_ID] = categoryId
        }
    }

    suspend fun setFeedSubType(subType: String) {
        awaitPreheat()
        val normalized = ActionSubType.normalizeKey(subType)
        feedSubType = normalized
        dataStore.edit { prefs ->
            prefs[KEY_FEED_SUB_TYPE] = normalized
        }
    }

    suspend fun setFeedZoneTabKey(tabKey: String) {
        awaitPreheat()
        feedZoneTabKey = tabKey
        dataStore.edit { prefs ->
            prefs[KEY_FEED_ZONE_TAB_KEY] = tabKey
        }
    }

    suspend fun setFeedZoneBoard(boardKey: String) {
        awaitPreheat()
        feedZoneBoard = boardKey
        dataStore.edit { prefs ->
            prefs[KEY_FEED_ZONE_BOARD] = boardKey
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
        awaitPreheat()
        isExHobbyEnabled = enabled
        dataStore.edit { prefs ->
            prefs[KEY_EXHOBBY_ENABLED] = enabled
        }
    }

    suspend fun setImageCacheMaxBytes(bytes: Long) {
        awaitPreheat()
        imageCacheMaxBytes = bytes
        dataStore.edit { prefs ->
            prefs[KEY_IMAGE_CACHE_MAX_BYTES] = bytes
        }
    }

    suspend fun getCachedFocusZones(): String? =
        dataStore.data.first()[KEY_CACHED_FOCUS_ZONES]

    suspend fun getCachedZoneSettings(): String? =
        dataStore.data.first()[KEY_CACHED_ZONE_SETTINGS]

    companion object {
        const val DEFAULT_IMAGE_CACHE_MAX_BYTES: Long = 500L * 1024L * 1024L

        private val KEY_HOBBY_LIST_GRID_MODE = booleanPreferencesKey("hobby_list_is_grid_mode")
        private val KEY_COLLECT_LIST_GRID_MODE = booleanPreferencesKey("collect_list_is_grid_mode")
        private val KEY_FEED_CATEGORY_ID = intPreferencesKey("feed_category_id")
        private val KEY_FEED_SUB_TYPE = stringPreferencesKey("feed_sub_type")
        private val KEY_FEED_ZONE_TAB_KEY = stringPreferencesKey("feed_zone_tab_key")
        private val KEY_FEED_ZONE_BOARD = stringPreferencesKey("feed_zone_board")
        private val KEY_CACHED_FOCUS_ZONES = stringPreferencesKey("cached_focus_zones_json")
        private val KEY_CACHED_ZONE_SETTINGS = stringPreferencesKey("cached_zone_settings_json")
        private val KEY_EXHOBBY_ENABLED = booleanPreferencesKey("is_exhobby_enabled")
        private val KEY_IMAGE_CACHE_MAX_BYTES = longPreferencesKey("image_cache_max_bytes")

        private fun keyOfCollectHobbyOrder(collectState: String) =
            stringPreferencesKey("collect_hobby_order_$collectState")

        private fun keyOfCollectHobbySortType(collectState: String) =
            intPreferencesKey("collect_hobby_sort_type_$collectState")
    }
}
