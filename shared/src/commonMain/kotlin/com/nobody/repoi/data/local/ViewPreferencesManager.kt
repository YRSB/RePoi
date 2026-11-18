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

    /** 首页二级板块偏好键，值格式 `{tabKey}|{type}`（如 `all|action`、`z100|album`）；未设置时为空串。 */
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
        // 读取侧归一：历史落盘值 all 映射为 allInfo，非法值回落默认项
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

    /**
     * 冷启动预热协程：从 DataStore 一次性恢复内存镜像。
     *
     * 不使用持续 collect，避免与本地写入产生持续的异步时序竞态。
     * 所有「会改写镜像」的读写入口都必须先 [awaitPreheat]，否则预热协程可能在用户写入之后才落定，
     * 把刚写入的内存镜像覆盖回旧快照（真实竞态：Android host 测试曾偶发 `allInfo` 覆盖 `recommend`）。
     */
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

    /**
     * 等待冷启动预热落定：保证后续对内存镜像的读写不会被预热协程的旧快照覆盖。
     *
     * 公开为有界等待点（没有轮询 / 真实延时）：除生产读写入口外，测试也用它替代
     * 「真实时间等待预热完成」的写法，避免 CI 在慢机上偶发失败（#150 审查 B3 / D3）。
     */
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

    /**
     * 按收藏状态分别记忆周边页的排序维度与升降序。
     *
     * @param collectState 收藏状态档位（""/care/want/preorder/buy/resell）
     * @param order 排序维度（actionDate/release/myRating/updateDate/orderTime）
     * @param sortType 升降序（1 升 / 2 降）
     */
    suspend fun setCollectHobbyOrder(collectState: String, order: String, sortType: Int) {
        awaitPreheat()
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
     * 读取收藏中心已消费的收藏变更代数游标（从未写入过时为 0）。
     *
     * 直读 DataStore 而不走预热镜像：ViewModel 构造期即可确定性恢复，不依赖预热协程时序。
     */
    suspend fun getConsumedCollectMutationGeneration(): Long =
        dataStore.data.first()[KEY_CONSUMED_COLLECT_MUTATION_GENERATION] ?: 0L

    /** 持久化收藏中心已消费的代数游标，抵抗配置变更与进程重建后的重复刷新。 */
    suspend fun setConsumedCollectMutationGeneration(generation: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_CONSUMED_COLLECT_MUTATION_GENERATION] = generation
        }
    }

    /**
     * 同步获取初始分类 ID（启动时无锁零延迟，杜绝首屏跳变）。
     */
    fun getInitialFeedCategoryId(): Int = feedCategoryId

    /**
     * 同步获取初始二级子类型（启动时无锁零延迟，杜绝首屏跳变）。
     *
     * 读取侧统一归一：旧值 `all` → `allInfo`，非法/空值 → 默认项（`recommend`）。
     */
    fun getInitialFeedSubType(): String = ActionSubType.fromPersisted(feedSubType).apiValue

    /**
     * 同步获取初始首页动态选中的专区 TabKey（"all"、"follow" 或 "z{nodeId}"）。
     */
    fun getInitialFeedZoneTabKey(): String = feedZoneTabKey

    /**
     * 同步获取初始首页二级板块键（值格式 `{tabKey}|{type}`；从未选择过时返回空串）。
     *
     * 前缀校验与「不可用回落首板块」规则由 `ui/home/HomeSelectionRestore.kt` 的纯函数负责，
     * 偏好层只做透传，避免恢复语义散落在数据层。
     */
    fun getInitialFeedZoneBoardKey(): String = feedZoneBoard

    suspend fun getFeedPreferences(): Pair<Int, String> {
        awaitPreheat()
        val prefs = dataStore.data.first()
        val catId = prefs[KEY_FEED_CATEGORY_ID] ?: 0
        // 读取侧归一：历史落盘值 all 映射为 allInfo，非法值回落默认项
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

    /**
     * 写入二级子类型偏好。
     *
     * 写入侧归一：先把任意来源的值转成协议键值（旧键 `all` → `allInfo`，非法值 → `recommend`），
     * 保证落盘不再出现服务端不识别的 `all`。
     */
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

    /**
     * 写入首页二级板块偏好键（值格式 `{tabKey}|{type}`）。
     *
     * 调用方保证键的业务合法性（前缀为当前一级 tabKey、type 为可用板块），偏好层不做形状校验。
     */
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
        private val KEY_CONSUMED_COLLECT_MUTATION_GENERATION =
            longPreferencesKey("consumed_collect_mutation_generation")

        private fun keyOfCollectHobbyOrder(collectState: String) =
            stringPreferencesKey("collect_hobby_order_$collectState")

        private fun keyOfCollectHobbySortType(collectState: String) =
            intPreferencesKey("collect_hobby_sort_type_$collectState")
    }
}
