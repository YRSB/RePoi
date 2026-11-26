package com.nobody.repoi.data.repository

import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.api.HpoiApi
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class ZoneRepository(
    private val api: HpoiApi,
    private val viewPreferencesManager: ViewPreferencesManager,
    private val visitReportScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val focusCacheMutex = Mutex()
    private val settingsCacheMutex = Mutex()
    private val focusUpdated = AtomicBoolean(false)
    private val settingsUpdated = AtomicBoolean(false)

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
        repositoryScope.launch {
            val cachedFocusJson = viewPreferencesManager.getCachedFocusZones()
            if (!cachedFocusJson.isNullOrBlank()) {
                runCatching {
                    json.decodeFromString<List<ZoneItem>>(cachedFocusJson)
                }.onSuccess { cachedList ->
                    focusCacheMutex.withLock {
                        if (!focusUpdated.load()) {
                            _focusZonesFlow.value = cachedList
                        }
                    }
                }
            }

            val cachedSettingsJson = viewPreferencesManager.getCachedZoneSettings()
            if (!cachedSettingsJson.isNullOrBlank()) {
                runCatching {
                    json.decodeFromString<ZoneTabsSettings>(cachedSettingsJson)
                }.onSuccess { cachedSettings ->
                    settingsCacheMutex.withLock {
                        if (!settingsUpdated.load()) {
                            _settingsFlow.value = cachedSettings
                        }
                    }
                }
            }
        }
    }

    suspend fun getZoneHomeTabs(forceRefresh: Boolean = false): Result<ZoneHomeTabsPayload> = runCatching {
        val payload = api.getZoneHomeTabs()

        payload.focusZones?.let { focusList ->
            focusCacheMutex.withLock {
                focusUpdated.store(true)
                _focusZonesFlow.value = focusList
            }
            viewPreferencesManager.setCachedFocusZonesJson(json.encodeToString(focusList))
        }

        payload.homeZones?.let { homeList ->
            _homeZonesFlow.value = homeList
        }

        payload.settings?.let { settings ->
            settingsCacheMutex.withLock {
                settingsUpdated.store(true)
                _settingsFlow.value = settings
            }
            viewPreferencesManager.setCachedZoneSettingsJson(json.encodeToString(settings))
        }

        payload
    }

    suspend fun getZoneInfo(nodeId: Long, reportVisit: Boolean = true): Result<ZoneInfoPayload> = runCatching {
        if (reportVisit && nodeId > 0L) {
            visitReportScope.launch {
                runCatching { api.reportZoneVisited(nodeId = nodeId) }
            }
        }

        api.getZoneInfo(nodeId = nodeId)
    }

    suspend fun toggleFocus(
        nodeId: Long,
        zoneName: String? = null,
        zoneIcon: String? = null
    ): Result<ZoneFocusPayload> = runCatching {
        val payload = api.toggleZoneFocus(nodeId = nodeId)
        val updatedList = focusCacheMutex.withLock {
            focusUpdated.store(true)
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
            currentList
        }
        viewPreferencesManager.setCachedFocusZonesJson(json.encodeToString(updatedList))

        payload
    }

    suspend fun updateSettings(
        showAll: Boolean,
        sort: String,
        hiddenZoneIds: List<Long>,
        customOrder: List<Long>
    ): Result<Unit> = runCatching {
        val hiddenStr = hiddenZoneIds.joinToString(",")
        val orderStr = customOrder.joinToString(",")

        api.updateZoneSettings(
            showAll = showAll,
            sort = sort,
            hiddenZoneIds = hiddenStr,
            customOrder = orderStr
        )

        val updatedSettings = settingsCacheMutex.withLock {
            settingsUpdated.store(true)
            val nextSettings = ZoneTabsSettings(
                showAll = showAll,
                sort = sort,
                hiddenZoneIds = hiddenZoneIds,
                customOrder = customOrder,
                recentZoneIds = _settingsFlow.value.recentZoneIds
            )
            _settingsFlow.value = nextSettings
            nextSettings
        }
        viewPreferencesManager.setCachedZoneSettingsJson(json.encodeToString(updatedSettings))
    }

    suspend fun getZonePosts(
        zoneNodeId: Long?,
        order: String = POSTS_DEFAULT_ORDER,
        page: Int = 1,
        pageSize: Int = POSTS_BOARD_PAGE_SIZE
    ): Result<List<PostItem>> = runCatching {
        api.searchPosts(
            zoneNodeId = zoneNodeId,
            order = order,
            page = page,
            pageSize = pageSize
        ).list
    }

    suspend fun getZoneItems(
        scope: ZoneQueryScope,
        itemType: String,
        category: Int = 0,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<ZoneItemsPayload> = runCatching {
        api.getZoneItems(
            itemType = itemType,
            zones = scope.zonesParam(),
            category = category,
            page = page,
            pageSize = pageSize
        )
    }

    companion object {

        fun formatZoneIconUrl(icon: String?): String? =
            MediaPipeline.Default.resolveUrl(icon, MediaCategory.ZONE, ImagePurpose.THUMBNAIL).ifEmpty { null }

        fun formatZoneCoverUrl(cover: String?): String? =
            MediaPipeline.Default.resolveUrl(cover, MediaCategory.ZONE, ImagePurpose.CARD_STANDARD).ifEmpty { null }
    }
}
