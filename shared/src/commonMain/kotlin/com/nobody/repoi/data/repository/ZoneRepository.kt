package com.nobody.repoi.data.repository

import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.POSTS_BOARD_PAGE_SIZE
import com.nobody.repoi.data.remote.model.POSTS_DEFAULT_ORDER
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.data.remote.model.ZoneFocusPayload
import com.nobody.repoi.data.remote.model.ZoneHomeTabsPayload
import com.nobody.repoi.data.remote.model.ZoneInfoPayload
import com.nobody.repoi.data.remote.model.ZoneItemsPayload
import com.nobody.repoi.data.remote.model.ZoneItem
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.remote.model.ZoneTabsSettings
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 专区数据仓储，负责管理专区首页标签、专区分组、关注专区状态及专区设置。
 *
 * @param visitReportScope 访问上报（`zone/home-tabs/visited`）专用作用域。默认独立 IO scope；
 *   测试可注入虚拟调度器作用域，从而确定性地断言「首页未上报访问」而不依赖真实线程时序。
 */
class ZoneRepository(
    private val api: HpoiApi,
    private val viewPreferencesManager: ViewPreferencesManager,
    private val visitReportScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val _focusZonesFlow = MutableStateFlow<List<ZoneItem>>(emptyList())
    val focusZonesFlow: StateFlow<List<ZoneItem>> = _focusZonesFlow.asStateFlow()

    private val _homeZonesFlow = MutableStateFlow<List<ZoneItem>>(emptyList())
    val homeZonesFlow: StateFlow<List<ZoneItem>> = _homeZonesFlow.asStateFlow()

    private val _settingsFlow = MutableStateFlow(ZoneTabsSettings())
    val settingsFlow: StateFlow<ZoneTabsSettings> = _settingsFlow.asStateFlow()

    init {
        // 启动时优先读取本地缓存，实现零延迟立即可见
        repositoryScope.launch {
            val cachedFocusJson = viewPreferencesManager.getCachedFocusZones()
            if (!cachedFocusJson.isNullOrBlank()) {
                runCatching {
                    json.decodeFromString<List<ZoneItem>>(cachedFocusJson)
                }.onSuccess { cachedList ->
                    _focusZonesFlow.value = cachedList
                }
            }

            val cachedSettingsJson = viewPreferencesManager.getCachedZoneSettings()
            if (!cachedSettingsJson.isNullOrBlank()) {
                runCatching {
                    json.decodeFromString<ZoneTabsSettings>(cachedSettingsJson)
                }.onSuccess { cachedSettings ->
                    _settingsFlow.value = cachedSettings
                }
            }
        }
    }

    /**
     * 获取专区首页数据（包含关注专区、首页专区以及所有专区分组）。
     */
    suspend fun getZoneHomeTabs(forceRefresh: Boolean = false): Result<ZoneHomeTabsPayload> = runCatching {
        val response = api.getZoneHomeTabs()
        if (!response.isSuccess) {
            throw ApiException(response.code, response.msg ?: "获取专区数据失败")
        }

        val payload = response.data ?: ZoneHomeTabsPayload()

        payload.focusZones?.let { focusList ->
            _focusZonesFlow.value = focusList
            viewPreferencesManager.setCachedFocusZonesJson(json.encodeToString(focusList))
        }

        payload.homeZones?.let { homeList ->
            _homeZonesFlow.value = homeList
        }

        payload.settings?.let { settings ->
            _settingsFlow.value = settings
            viewPreferencesManager.setCachedZoneSettingsJson(json.encodeToString(settings))
        }

        payload
    }

    /**
     * 获取单个专区的详细资料，并在后台异步上报访问记录（用于最近访问排序）。
     *
     * @param nodeId 专区节点 ID
     * @param reportVisit 是否上报访问记录。专区详情页保持默认 `true`（进入详情即算访问）；
     *   首页只需 `zone/info.tabs` 来装配二级板块，必须传 `false`——否则每次切专区都会污染
     *   「最近访问」排序（首页不是「进入专区」这一用户意图）。
     */
    suspend fun getZoneInfo(nodeId: Long, reportVisit: Boolean = true): Result<ZoneInfoPayload> = runCatching {
        if (reportVisit && nodeId > 0L) {
            visitReportScope.launch {
                runCatching { api.reportZoneVisited(nodeId = nodeId) }
            }
        }

        val response = api.getZoneInfo(nodeId = nodeId)
        if (!response.isSuccess) {
            throw ApiException(response.code, response.msg ?: "获取专区详情失败")
        }
        response.data ?: throw ApiException(response.code, "专区详情为空")
    }

    /**
     * 切换专区关注状态（关注/取消关注）。
     * 成功后自动增删内存中 focusZonesFlow 并持久化到本地 DataStore，使首页和专区列表即时同步。
     */
    suspend fun toggleFocus(
        nodeId: Long,
        zoneName: String? = null,
        zoneIcon: String? = null
    ): Result<ZoneFocusPayload> = runCatching {
        val response = api.toggleZoneFocus(nodeId = nodeId)
        if (!response.isSuccess) {
            throw ApiException(response.code, response.msg ?: "关注状态切换失败")
        }

        val payload = response.data ?: ZoneFocusPayload()
        val currentList = _focusZonesFlow.value.toMutableList()

        if (payload.focus) {
            if (currentList.none { it.nodeId == nodeId }) {
                currentList.add(
                    ZoneItem(
                        nodeId = nodeId,
                        name = zoneName,
                        nameCN = zoneName,
                        icon = zoneIcon
                    )
                )
            }
        } else {
            currentList.removeAll { it.nodeId == nodeId }
        }

        _focusZonesFlow.value = currentList
        viewPreferencesManager.setCachedFocusZonesJson(json.encodeToString(currentList))

        payload
    }

    /**
     * 更新专区首页显示与排序设置。
     */
    suspend fun updateSettings(
        showAll: Boolean,
        sort: String,
        hiddenZoneIds: List<Long>,
        customOrder: List<Long>
    ): Result<Unit> = runCatching {
        val hiddenStr = hiddenZoneIds.joinToString(",")
        val orderStr = customOrder.joinToString(",")

        val response = api.updateZoneSettings(
            showAll = showAll,
            sort = sort,
            hiddenZoneIds = hiddenStr,
            customOrder = orderStr
        )

        if (!response.isSuccess) {
            throw ApiException(response.code, response.msg ?: "保存专区设置失败")
        }

        val updatedSettings = ZoneTabsSettings(
            showAll = showAll,
            sort = sort,
            hiddenZoneIds = hiddenZoneIds,
            customOrder = customOrder,
            recentZoneIds = _settingsFlow.value.recentZoneIds
        )
        _settingsFlow.value = updatedSettings
        viewPreferencesManager.setCachedZoneSettingsJson(json.encodeToString(updatedSettings))
    }


    /**
     * 查询专区讨论贴列表。
     * @param zoneNodeId 专区节点 ID；**null 表示聚合·全部（省略 `zoneNodeId`，服务端返回全站）**。
     *   讨论接口的专区过滤只认单值 `zoneNodeId`（String/Long 均可），`zones` 与逗号串会被
     *   服务端静默忽略并退化为全站，因此**不要**为关注聚合传逗号串；关注态没有多专区通道，
     *   首页已按 `homeBoardsFor(Follow)` 隐藏该板块（见 `docs/research/aggregate-api-verification.md` §4.3）。
     * @param order 排序类型: upd(回复) / add(新贴) / top(推荐)
     * @param page 页码 (1 起始)
     * @param pageSize 每页条数 (默认 20)
     */
    suspend fun getZonePosts(
        zoneNodeId: Long?,
        order: String = POSTS_DEFAULT_ORDER,
        page: Int = 1,
        pageSize: Int = POSTS_BOARD_PAGE_SIZE
    ): Result<List<PostItem>> = runCatching {
        val response = api.searchPosts(
            zoneNodeId = zoneNodeId,
            order = order,
            page = page,
            pageSize = pageSize
        )
        response.list
    }

    /**
     * 查询专区关联实体分类条目列表（厂商/系列/作品/角色/人物）。
     *
     * 作用域由 [ZoneQueryScope] 统一派生，只下发 `zones`：
     * - 单专区 → `zones=<nodeId>`；
     * - 关注聚合 → `zones=<关注专区逗号串>`（实测并集）；
     * - 全部聚合 → 省略 `zones`（等价全站）。
     *
     * **禁止下发 `homeScope`**：实测非空 `homeScope` 会让服务端忽略 `zones`、退化为全站。
     *
     * @param scope 查询作用域（全站 / 关注 / 单专区）
     * @param itemType 条目类型: company(厂商) / series(系列) / works(作品) / charactar(角色) / person(人物)
     * @param page 页码 (1 起始)
     * @param pageSize 每页条数 (默认 20)
     */
    suspend fun getZoneItems(
        scope: ZoneQueryScope,
        itemType: String,
        category: Int = 0,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<ZoneItemsPayload> = runCatching {
        val response = api.getZoneItems(
            itemType = itemType,
            zones = scope.zonesParam(),
            category = category,
            page = page,
            pageSize = pageSize
        )
        if (!response.isSuccess) {
            throw ApiException(response.code, response.msg ?: "获取专区条目失败")
        }
        response.data ?: ZoneItemsPayload()
    }

    companion object {
        /**
         * 按照原版规则拼接专区图标 CDN URL（收敛至 MediaPipeline）。
         */
        fun formatZoneIconUrl(icon: String?): String? =
            MediaPipeline.Default.resolveUrl(icon, MediaCategory.ZONE, ImagePurpose.THUMBNAIL).ifEmpty { null }

        /**
         * 按照原版规则拼接专区封面大图 CDN URL（收敛至 MediaPipeline）。
         */
        fun formatZoneCoverUrl(cover: String?): String? =
            MediaPipeline.Default.resolveUrl(cover, MediaCategory.ZONE, ImagePurpose.CARD_STANDARD).ifEmpty { null }
    }
}
