package com.nobody.repoi.data.repository

import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.data.remote.model.ZoneFocusPayload
import com.nobody.repoi.data.remote.model.ZoneHomeTabsPayload
import com.nobody.repoi.data.remote.model.ZoneInfoPayload
import com.nobody.repoi.data.remote.model.ZoneItemsPayload
import com.nobody.repoi.data.remote.model.ZoneItem
import com.nobody.repoi.data.remote.model.ZoneTabsSettings
import com.nobody.repoi.util.CdnUrlUtils
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
 */
class ZoneRepository(
    private val api: HpoiApi,
    private val viewPreferencesManager: ViewPreferencesManager
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
     */
    suspend fun getZoneInfo(nodeId: Long): Result<ZoneInfoPayload> = runCatching {
        if (nodeId > 0L) {
            repositoryScope.launch {
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
     * @param zoneNodeId 专区节点 ID
     * @param order 排序类型: upd(回复) / add(新贴) / top(推荐)
     * @param page 页码 (1 起始)
     * @param pageSize 每页条数 (默认 20)
     */
    suspend fun getZonePosts(
        zoneNodeId: Long,
        order: String = "upd",
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<PostItem>> = runCatching {
        val response = api.searchPosts(
            zoneNodeId = zoneNodeId,
            order = order,
            page = page,
            pageSize = pageSize
        )
        if (!response.isSuccess) {
            throw ApiException(response.code, response.msg ?: "获取讨论列表失败")
        }
        response.data?.list ?: emptyList()
    }

    /**
     * 查询专区关联实体分类条目列表（厂商/系列/作品/角色/人物）。
     * @param nodeId 专区节点 ID
     * @param itemType 条目类型: company(厂商) / series(系列) / works(作品) / charactar(角色) / person(人物)
     * @param page 页码 (1 起始)
     * @param pageSize 每页条数 (默认 20)
     */
    suspend fun getZoneItems(
        nodeId: Long,
        itemType: String,
        category: Int = 0,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<ZoneItemsPayload> = runCatching {
        val response = api.getZoneItems(
            nodeId = nodeId,
            itemType = itemType,
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
         * 按照原版规则拼接专区图标 CDN URL（收敛至 CdnUrlUtils）。
         */
        fun formatZoneIconUrl(icon: String?): String? = CdnUrlUtils.getZoneIconUrl(icon)

        /**
         * 按照原版规则拼接专区封面大图 CDN URL（收敛至 CdnUrlUtils）。
         */
        fun formatZoneCoverUrl(cover: String?): String? = CdnUrlUtils.getZoneCoverUrl(cover)
    }
}
