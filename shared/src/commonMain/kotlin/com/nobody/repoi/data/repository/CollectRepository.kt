package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.CollectCountData
import com.nobody.repoi.data.remote.model.CollectData
import com.nobody.repoi.data.remote.model.PictureRelateItem
import com.nobody.repoi.data.remote.model.UserCollectAlbumItem
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.remote.model.UserCollectCommonItem
import com.nobody.repoi.data.remote.model.UserCollectHobbyItem
import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.data.remote.model.WorksCollectData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

data class CreationPageResult<T>(
    val items: List<T> = emptyList(),
    val hasMore: Boolean = false
)

data class CollectCountSnapshot(val total: Long, val revision: Long)

class CollectRepository(
    private val api: HpoiApi,
    private val json: Json,
) {

    private val stateFlows = MutableStateFlow<Map<Long, MutableStateFlow<CollectData?>>>(emptyMap())

    private val _collectMutationGeneration = MutableStateFlow(0L)
    val collectMutationGeneration: StateFlow<Long> = _collectMutationGeneration.asStateFlow()

    private val _countSnapshotRevision = MutableStateFlow(0L)
    private val countSnapshotFlows =
        MutableStateFlow<Map<Long, MutableStateFlow<CollectCountSnapshot?>>>(emptyMap())

    fun observeCollectCountSnapshot(nodeId: Long): StateFlow<CollectCountSnapshot?> =
        mutableCountSnapshotFlowOf(nodeId)

    fun updateCollectCountSnapshot(nodeId: Long, count: Long) {
        var revision = 0L
        _countSnapshotRevision.update { (it + 1).also { next -> revision = next } }
        mutableCountSnapshotFlowOf(nodeId).value = CollectCountSnapshot(count, revision)
    }

    private fun mutableCountSnapshotFlowOf(nodeId: Long): MutableStateFlow<CollectCountSnapshot?> {
        countSnapshotFlows.value[nodeId]?.let { return it }
        countSnapshotFlows.update { current ->
            if (current.containsKey(nodeId)) current
            else current + (nodeId to MutableStateFlow<CollectCountSnapshot?>(null))
        }
        return countSnapshotFlows.value.getValue(nodeId)
    }

    fun observeCollectState(nodeId: Long): StateFlow<CollectData?> = mutableStateFlowOf(nodeId)

    private fun mutableStateFlowOf(nodeId: Long): MutableStateFlow<CollectData?> {

        stateFlows.value[nodeId]?.let { return it }

        stateFlows.update { current ->
            if (current.containsKey(nodeId)) {
                current
            } else {
                current + (nodeId to MutableStateFlow<CollectData?>(null))
            }
        }
        return stateFlows.value.getValue(nodeId)
    }

    private fun advanceMutationGeneration() {
        _collectMutationGeneration.update { it + 1 }
    }

    suspend fun refreshCollectState(nodeId: Long): Result<CollectData?> = runCatching {
        val collectData = api.getCollectState(nodeId)?.collect
        mutableStateFlowOf(nodeId).value = collectData
        collectData
    }

    suspend fun getCollectCount(nodeId: Long): Result<CollectCountData> = runCatching {
        api.getCollectCount(nodeId)?.collect ?: CollectCountData()
    }

    suspend fun getCollectTags(
        page: Int = 1,
        pageSize: Int = 20,
        categoryId: Int = 10000
    ): Result<List<String>> = runCatching {
        api.getCollectTags(page = page, pageSize = pageSize, categoryId = categoryId)?.favTags ?: emptyList()
    }

    suspend fun saveCollect(nodeId: Long, collectData: CollectData): Result<Unit> = runCatching {
        val jsonPayload = json.encodeToString(collectData)
        api.updateCollect(nodeId = nodeId, payloadJson = jsonPayload)
        mutableStateFlowOf(nodeId).value = collectData
        advanceMutationGeneration()
    }

    suspend fun deleteCollect(nodeId: Long, currentData: CollectData): Result<Unit> = runCatching {
        val deletedData = currentData.copy(state = CollectConstants.STATE_DELETE)
        val jsonPayload = json.encodeToString(deletedData)
        api.updateCollect(nodeId = nodeId, payloadJson = jsonPayload)
        mutableStateFlowOf(nodeId).value = null
        advanceMutationGeneration()
    }

    suspend fun collectItemAdd(nodeId: Long): Result<Unit> = runCatching {
        api.collectItemAdd(id = nodeId)
        val previous = mutableStateFlowOf(nodeId).value ?: CollectData()
        mutableStateFlowOf(nodeId).value = previous.copy(state = CollectConstants.STATE_CARE)
        advanceMutationGeneration()
    }

    suspend fun collectItemDel(nodeId: Long): Result<Unit> = runCatching {
        api.collectItemDel(id = nodeId)
        mutableStateFlowOf(nodeId).value = null
        advanceMutationGeneration()
    }

    suspend fun toggleItemCollect(nodeId: Long, isCollected: Boolean): Result<Boolean> {
        val result = if (isCollected) collectItemDel(nodeId) else collectItemAdd(nodeId)
        return result.map { !isCollected }
    }

    suspend fun cancelCollectBatch(ids: List<Long>): Result<Unit> = runCatching {
        if (ids.isEmpty()) return@runCatching Unit

        val idArr = ids.joinToString(prefix = "[", postfix = "]", separator = ",")
        api.cancelCollectBatch(idArr = idArr)
        advanceMutationGeneration()
    }

    private suspend fun <T, R> listWithEnvelope(
        request: suspend () -> T?,
        extract: (T?) -> List<R>?
    ): List<R> = extract(request()) ?: emptyList()

    suspend fun getUserCollectHobbyList(
        userId: Long,
        collectState: String,
        category: Int = CollectConstants.CATEGORY_ALL,
        tag: String? = null,
        order: String = CollectConstants.ORDER_ACTION_DATE,
        sortType: Int = SORT_TYPE_DESC,
        page: Int,
        pageSize: Int = 20
    ): Result<List<UserCollectHobbyItem>> = runCatching {
        val params = buildMap {
            put("userId", userId.toString())
            put("userNodeId", userId.toString())
            if (collectState.isNotEmpty()) {
                put("collectState", collectState)
            }
            put("order", CollectConstants.normalizeHobbyOrder(order, collectState))
            put("category", category.toString())
            put("sortType", sortType.toString())
            put("page", page.toString())
            put("limit", pageSize.toString())
            put("pageSize", pageSize.toString())
            if (!tag.isNullOrBlank()) {
                put("tag", tag)
            }
        }
        listWithEnvelope(
            request = { api.getUserCollectHobby(params) },
            extract = { data -> data?.list }
        )
    }

    suspend fun getUserCollectCommonList(
        userId: Long,
        categoryId: Int,
        type: String,
        order: String = CollectConstants.ORDER_ACTION_DATE,
        page: Int,
        pageSize: Int = 20,
        state: Int? = null,
        isAsc: Boolean? = null,
        tagValue: String? = null
    ): Result<List<UserCollectCommonItem>> = runCatching {
        val resolvedType = type.ifBlank {
            UserCollectCategory.fromKey(categoryId)?.apiType.orEmpty()
        }
        val params = buildMap {
            put("userId", userId.toString())
            put("userNodeId", userId.toString())
            put("categoryId", categoryId.toString())
            put("type", resolvedType)
            put("order", CollectConstants.normalizeCommonOrder(order))
            put("page", page.toString())
            put("limit", pageSize.toString())
            put("pageSize", pageSize.toString())
            if (resolvedType == UserCollectCategory.WORKS.apiType) {
                state?.let { put("state", it.toString()) }
                isAsc?.let { put("isAsc", it.toString()) }
                if (!tagValue.isNullOrBlank()) {
                    put("tagValue", tagValue)
                }
            }
        }
        listWithEnvelope(
            request = { api.getUserCollectCommon(params) },
            extract = { data -> data?.list }
        )
    }

    suspend fun getUserCollectAlbumList(
        userId: Long,
        type: String = "fav",
        order: String = CollectConstants.ORDER_ADD_TIME,
        page: Int,
        pageSize: Int = 20
    ): Result<List<UserCollectAlbumItem>> = runCatching {
        val params = buildMap {
            put("userId", userId.toString())
            put("userNodeId", userId.toString())
            put("type", type)
            put("order", CollectConstants.normalizeMediaOrder(order))
            put("page", page.toString())
            put("limit", pageSize.toString())
            put("pageSize", pageSize.toString())
        }
        listWithEnvelope(
            request = { api.getUserCollectAlbum(params) },
            extract = { data -> data?.list }
        )
    }

    suspend fun getUserCollectPicList(
        userId: Long,
        collectState: String = "collect",
        order: String = CollectConstants.ORDER_ADD_TIME,
        page: Int,
        pageSize: Int = 20
    ): Result<List<UserCollectPicItem>> = runCatching {
        val params = buildMap {
            put("userId", userId.toString())
            put("userNodeId", userId.toString())
            put("collectState", collectState)
            put("order", CollectConstants.normalizeMediaOrder(order))
            put("page", page.toString())
            put("limit", pageSize.toString())
            put("pageSize", pageSize.toString())
        }
        listWithEnvelope(
            request = { api.getUserCollectPic(params) },
            extract = { data -> data?.list }
        )
    }

    suspend fun getUserCreationAlbumList(
        userId: Long,
        page: Int = 1,
        pageSize: Int = 20,
        order: String = CollectConstants.ORDER_ADD_TIME
    ): Result<CreationPageResult<UserCollectAlbumItem>> = runCatching {
        val items = listWithEnvelope(
            request = {
                api.getUserAlbumList(
                    userNodeId = userId,
                    page = page,
                    pageSize = pageSize,
                    type = CREATION_ALBUM_TYPE,
                    order = order
                )
            },
            extract = { data -> data?.list }
        )

        CreationPageResult(items = items, hasMore = items.size >= pageSize)
    }

    suspend fun getUserCreationPicList(
        userId: Long,
        page: Int = 1,
        pageSize: Int = 20,
        order: String = CollectConstants.ORDER_ADD_TIME
    ): Result<CreationPageResult<UserCollectPicItem>> = runCatching {
        val rawList = listWithEnvelope(
            request = {
                api.getUserPicList(
                    userNodeId = userId,
                    page = page,
                    pageSize = pageSize,
                    collectState = CREATION_PIC_STATE,
                    order = order
                )
            },
            extract = { data -> data?.list }
        )

        CreationPageResult(
            items = rawList.filter { it.actualUrl.isNotBlank() },
            hasMore = rawList.size >= pageSize
        )
    }

    suspend fun getWorksCollectTags(
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<String>> = runCatching {
        parseWorksCollectTags(api.getWorksCollectTags(page = page, pageSize = pageSize))
    }

    suspend fun getWorksCollect(worksId: Long): Result<WorksCollectData?> = runCatching {
        parseWorksCollect(api.getWorksCollect(worksId = worksId))
    }

    suspend fun saveWorksCollect(
        worksId: Long,
        state: Int,
        tagStr: String,
        comment: String
    ): Result<Unit> = runCatching {
        api.saveWorksCollect(
            itemType = CollectConstants.WORKS_ITEM_TYPE,
            itemId = worksId,
            tagStr = tagStr,
            comment = comment,
            state = state
        )
        advanceMutationGeneration()
    }

    suspend fun deleteWorksCollect(worksId: Long): Result<Unit> = runCatching {
        api.deleteWorksCollect(
            itemType = CollectConstants.WORKS_ITEM_TYPE,
            itemId = worksId
        )
        advanceMutationGeneration()
    }

    suspend fun getPictureRelate(
        picId: Long = 0L,
        path: String? = null,
    ): Result<List<PictureRelateItem>> = runCatching {
        if (picId <= 0L && path.isNullOrBlank()) return@runCatching emptyList()
        api.getPictureRelate(picId = picId.takeIf { it > 0L }, path = path?.trim()?.ifBlank { null })
            ?.list?.mapNotNull { it.relateItem } ?: emptyList()
    }

    suspend fun submitReport(
        nodeId: Long,
        content: String,
        type: String = CollectConstants.REPORT_TYPE_PICTURE
    ): Result<Unit> = runCatching {
        if (content.isBlank()) {

            throw ApiException(0, "请填写举报原因")
        }
        api.submitReport(
            objId = nodeId,
            userReason = content,
            type = type
        )
    }

    private fun parseWorksCollectTags(data: JsonElement?): List<String> {
        val array = when (data) {
            null -> return emptyList()
            is JsonArray -> data
            is JsonPrimitive -> runCatching { json.parseToJsonElement(data.content) as? JsonArray }.getOrNull() ?: return emptyList()
            is JsonObject -> (data["textTagList"] ?: data["list"]) as? JsonArray ?: return emptyList()
        }
        return array.mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull?.trim()?.ifBlank { null }
                is JsonObject -> {
                    val candidate = element["tagValue"] ?: element["name"] ?: element["tag"]
                    (candidate as? JsonPrimitive)?.contentOrNull?.trim()?.ifBlank { null }
                }
                else -> null
            }
        }
    }

    private fun parseWorksCollect(data: JsonElement?): WorksCollectData? {
        val obj = data as? JsonObject ?: return null
        val target = (obj["glean"] as? JsonObject) ?: obj
        if (target.isEmpty()) return null
        return runCatching { json.decodeFromJsonElement<WorksCollectData>(target) }.getOrNull()
    }

    companion object {

        const val SORT_TYPE_ASC = 1

        const val SORT_TYPE_DESC = 2

        const val CREATION_ALBUM_TYPE = "upload"

        const val CREATION_PIC_STATE = "upload"
    }
}
