package com.nobody.repoi.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 界面与展示偏好管理器，用于持久化列表视图布局（网格卡片/详细列表）以及首页动态筛选偏好等。
 *
 * KMP 版变更（相对老项目）：
 * - DataStore 由 Koin 注入；
 * - 砍掉 SharedPreferences 同步缓存层（新包名=全新安装，无历史数据可迁）：
 *   同步读改为内存字段（初值=默认值），DataStore 为唯一持久化。
 */
class ViewPreferencesManager(
    private val dataStore: DataStore<Preferences>
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var isHobbyListGridMode: Boolean = true
        private set

    @Volatile
    var isCollectListGridMode: Boolean = true
        private set

    @Volatile
    var feedCategoryId: Int = 0
        private set

    @Volatile
    var feedSubType: String = "recommend"
        private set

    @Volatile
    var feedZoneTabKey: String = "all"
        private set

    @Volatile
    var isExHobbyEnabled: Boolean = true
        private set

    @Volatile
    private var cachedFocusZonesJson: String? = null

    @Volatile
    private var cachedZoneSettingsJson: String? = null

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
        // 初始加载：只读一次历史数据回填内存（持续 collect 可能用旧快照覆盖内存新值）。
        scope.launch {
            val prefs = dataStore.data.first()
            isHobbyListGridMode = prefs[KEY_HOBBY_LIST_GRID_MODE] ?: true
            isCollectListGridMode = prefs[KEY_COLLECT_LIST_GRID_MODE] ?: true
            feedCategoryId = prefs[KEY_FEED_CATEGORY_ID] ?: 0
            feedSubType = prefs[KEY_FEED_SUB_TYPE] ?: "recommend"
            feedZoneTabKey = prefs[KEY_FEED_ZONE_TAB_KEY] ?: "all"
            isExHobbyEnabled = prefs[KEY_EXHOBBY_ENABLED] ?: true
            cachedFocusZonesJson = prefs[KEY_CACHED_FOCUS_ZONES]
            cachedZoneSettingsJson = prefs[KEY_CACHED_ZONE_SETTINGS]
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
     * 对标原版 Hpoi `UserCollectHobbySubPagerFragment` 的 `sortObject` / `orderObject`
     * （分别落 Prefs 的 `collect_sort` 与 `collect_order`），切换收藏状态时各自恢复上次选择。
     *
     * @param collectState 收藏状态档位（""/care/want/preorder/buy/resell）
     * @param order 排序维度（actionDate/release/rating/myRating/updateDate/orderTime）
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
        val catId = dataStore.data.first()[KEY_FEED_CATEGORY_ID] ?: 0
        val sub = dataStore.data.first()[KEY_FEED_SUB_TYPE] ?: "recommend"
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

    fun getCachedFocusZonesJson(): String? = cachedFocusZonesJson

    suspend fun setCachedFocusZonesJson(json: String) {
        cachedFocusZonesJson = json
        dataStore.edit { prefs ->
            prefs[KEY_CACHED_FOCUS_ZONES] = json
        }
    }

    fun getCachedZoneSettingsJson(): String? = cachedZoneSettingsJson

    suspend fun setCachedZoneSettingsJson(json: String) {
        cachedZoneSettingsJson = json
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
