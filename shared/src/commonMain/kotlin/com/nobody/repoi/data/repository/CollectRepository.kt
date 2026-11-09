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
import com.nobody.repoi.data.session.UserSession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

/**
 * 收藏模块聚合数据仓储（收藏数据唯一真相源 SSOT）：
 * 管理条目收藏状态的获取、分类人数统计、备选标签以及收藏的保存与删除。
 * 同时承载收藏页的两条写侧链路——批量取消收藏 (`api/collect/batch/cancel`)
 * 与作品收藏整链 (`api/works/glean/get|add|del`、`api/user/glean/textTag/get`)，
 * 另含图片画廊的关联条目查询 (`api/pic/relate`) 与收藏中心 4 类列表查询
 * (`api/collect/hobby-v2|common-v3|album-v2|pic-v2`)。
 *
 * 条目收藏状态经 [observeCollectState] 以状态流对外暴露：写操作成功后**只**更新内部状态池，
 * 消费端靠 `StateFlow` 观察获得原子更新（状态驱动，不是事件驱动）。
 *
 * 状态池并发模型（ADR-0011）：
 * - 状态池是 `MutableStateFlow<Map<Long, MutableStateFlow<CollectData?>>>` 的**不可变快照**；
 *   commonMain 无 `ConcurrentHashMap`，且读热路径要求零开销，因此不使用 `Mutex`；
 * - 读路径直接取 `stateFlows.value` 快照（无锁）；未命中时用 `update` 的 CAS 语义把新流并入快照，
 *   并发首访同一 `nodeId` 也只落一个实例，从而保证幂等（同 `nodeId` 恒返回同一 `StateFlow`）；
 * - `MutableStateFlow.update` 内部是 CAS 重试，闭包内严禁再嵌套 `update`。
 *
 * 旧接口 [collectUpdates]（`SharedFlow<Pair<Long, CollectData?>>`）已停止发射任何事件，
 * 仅作为过渡残留保留至消费端迁移完成，新代码禁止订阅。
 */
class CollectRepository(
    private val api: HpoiApi,
    private val json: Json,
    private val userSession: UserSession,
) {
    // 旧事件广播残留：已停止发射，等待消费端迁移后物理删除，新代码禁止订阅
    @Deprecated("收藏状态改由 observeCollectState 状态流承载，该广播已停止发射")
    private val _collectUpdates = MutableSharedFlow<Pair<Long, CollectData?>>(extraBufferCapacity = 16)

    @Deprecated("收藏状态改由 observeCollectState 状态流承载，该广播已停止发射")
    val collectUpdates: SharedFlow<Pair<Long, CollectData?>> = _collectUpdates.asSharedFlow()

    /** 条目状态池：nodeId → 该条目的收藏状态流（不可变 map 快照，写入走 CAS） */
    private val stateFlows = MutableStateFlow<Map<Long, MutableStateFlow<CollectData?>>>(emptyMap())

    /** 画廊写操作计数：单调递增，供列表页恢复时判定是否需要静默刷新 */
    private val _galleryMutationTicks = MutableStateFlow(0L)
    val galleryMutationTicks: StateFlow<Long> = _galleryMutationTicks.asStateFlow()

    /**
     * 观察指定条目的收藏状态流；同 [nodeId] 恒返回同一实例（幂等），未拉取前值为 `null`。
     */
    fun observeCollectState(nodeId: Long): StateFlow<CollectData?> {
        // 快路径：已存在的流无锁直接返回
        stateFlows.value[nodeId]?.let { return it }
        // 慢路径：CAS 合并；getOrPut 必须与写入处于同一闭包，避免并发首访产生双实例
        stateFlows.update { current ->
            if (current.containsKey(nodeId)) {
                current
            } else {
                current + (nodeId to MutableStateFlow<CollectData?>(null))
            }
        }
        return stateFlows.value.getValue(nodeId)
    }

    /**
     * 标记画廊发生过收藏写操作；计数只增不减，可随时重读、无订阅者也不丢。
     */
    fun markGalleryMutated() {
        _galleryMutationTicks.update { it + 1 }
    }

    /**
     * 拉取指定条目的收藏状态并原子写入状态池 (api/collect/state)。
     * 成功才写池；失败不改写池内已有值，仅透传异常。
     */
    suspend fun refreshCollectState(nodeId: Long): Result<CollectData?> = runCatching {
        val response = api.getCollectState(nodeId)
        if (response.isSuccess) {
            val collectData = response.data?.collect
            observeCollectState(nodeId).value = collectData
            collectData
        } else {
            throw ApiException(response.code, response.msg ?: "获取收藏状态失败")
        }
    }

    /**
     * 获取指定条目的收藏状态信息 (api/collect/state)。
     * 语义等同 [refreshCollectState]：网络成功即写入状态池，失败保持池内原值。
     */
    suspend fun getCollectState(nodeId: Long): Result<CollectData?> = refreshCollectState(nodeId)

    /**
     * 获取指定条目的 5 种分类统计人数 (api/hobby/collectCount)。
     */
    suspend fun getCollectCount(nodeId: Long): Result<CollectCountData> = runCatching {
        val response = api.getCollectCount(nodeId)
        if (response.isSuccess) {
            response.data?.collect ?: CollectCountData()
        } else {
            throw ApiException(response.code, response.msg ?: "获取收藏统计失败")
        }
    }

    /**
     * 获取热门候选推荐标签池 (api/collect/tag)。
     */
    suspend fun getCollectTags(
        page: Int = 1,
        pageSize: Int = 20,
        categoryId: Int = 10000
    ): Result<List<String>> = runCatching {
        val response = api.getCollectTags(page = page, pageSize = pageSize, categoryId = categoryId)
        if (response.isSuccess) {
            response.data?.favTags ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取推荐标签失败")
        }
    }

    /**
     * 保存/更新条目收藏信息 (api/collect/hobby/upd)；成功后状态池 := [collectData]。
     */
    suspend fun saveCollect(nodeId: Long, collectData: CollectData): Result<Unit> = runCatching {
        val jsonPayload = json.encodeToString(collectData)
        val response = api.updateCollect(nodeId = nodeId, payloadJson = jsonPayload)
        if (response.isSuccess) {
            observeCollectState(nodeId).value = collectData
        } else {
            throw ApiException(response.code, response.msg ?: "保存收藏失败")
        }
    }

    /**
     * 取消条目收藏 (api/collect/hobby/upd，state="delete")；成功后状态池 := null。
     */
    suspend fun deleteCollect(nodeId: Long, currentData: CollectData): Result<Unit> = runCatching {
        val deletedData = currentData.copy(state = CollectConstants.STATE_DELETE)
        val jsonPayload = json.encodeToString(deletedData)
        val response = api.updateCollect(nodeId = nodeId, payloadJson = jsonPayload)
        if (response.isSuccess) {
            observeCollectState(nodeId).value = null
        } else {
            throw ApiException(response.code, response.msg ?: "取消收藏失败")
        }
    }

    /**
     * 添加通用条目/相册收藏 (api/collect/item/add)。
     *
     * 服务端不返回收藏详情，成功后状态池 := `(旧值 ?: CollectData()).copy(state = "care")`。
     * ⚠️ 这里的 `care` 仅作为「已收藏」**占位值**（`CollectData.isCollected` 据此为真），
     * 不代表手办心愿档；其余字段沿用旧值，避免覆盖用户已填的评分/入手信息。
     */
    suspend fun collectItemAdd(nodeId: Long): Result<Unit> = runCatching {
        val response = api.collectItemAdd(id = nodeId)
        if (!response.isSuccess) {
            throw ApiException(response.code, response.msg ?: "收藏失败")
        }
        val previous = observeCollectState(nodeId).value ?: CollectData()
        observeCollectState(nodeId).value = previous.copy(state = CollectConstants.STATE_CARE)
    }

    /**
     * 取消通用条目/相册收藏 (api/collect/del)；成功后状态池 := null。
     */
    suspend fun collectItemDel(nodeId: Long): Result<Unit> = runCatching {
        val response = api.collectItemDel(id = nodeId)
        if (!response.isSuccess) {
            throw ApiException(response.code, response.msg ?: "取消收藏失败")
        }
        observeCollectState(nodeId).value = null
    }

    /**
     * 批量取消收藏 (api/collect/batch/cancel)。
     *
     * ⚠️ [ids] 必须传**外层收藏节点 id**（如 `UserCollectAlbumItem.id` / `UserCollectHobbyItem.id`），
     * 而**不是** `actualAlbumId` / `actualItemId` 里的真实条目 ID。
     * 仅登录用户本人的收藏中心可用（原版 isSelf 门控）。
     * 空集合直接短路返回成功，避免向服务端提交空的 `[]`。
     *
     * 批量接口缺少「外层 id → nodeId」映射，故不逐条改写状态池，列表页靠恢复刷新对齐。
     */
    suspend fun cancelCollectBatch(ids: List<Long>): Result<Unit> = runCatching {
        if (ids.isEmpty()) return@runCatching Unit
        // 服务端要求 JSON 数组字符串，如 "[1,2,3]"
        val idArr = ids.joinToString(prefix = "[", postfix = "]", separator = ",")
        val response = api.cancelCollectBatch(idArr = idArr)
        if (!response.isSuccess) {
            throw ApiException(response.code, response.msg ?: "批量取消收藏失败")
        }
    }

    /**
     * 查询用户全量手办收藏列表 (api/collect/hobby-v2, GET)。
     *
     * @param category 周边细分类，「全部」传 [CollectConstants.CATEGORY_ALL]（10000，原版不用 null）
     * @param sortType 升降序：1 升序 / 2 降序
     * @param order 排序 key，白名单 actionDate/release/rating/myRating/updateDate；
     *   `orderTime`（入手时间）语义上仅 `collectState == "buy"` 时可用，其余状态自动回落 actionDate
     */
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
        val snapshotToken = userSession.currentToken
        val response = api.getUserCollectHobby(params)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
            }
            throw ApiException(response.code, response.msg ?: "获取收藏列表失败")
        }
    }

    /**
     * 查询用户通用实体收藏列表 (厂商/系列/作品/角色/人物) (api/collect/common-v3, GET)。
     *
     * @param type 分类查询的唯一载体，取值见 [UserCollectCategory.apiType]
     *   （works/company/series/charactar/person）。
     *   ⚠️ 原版从不传 `categoryId`，`type` 才是分类维度；此处为兼容服务端双条件支持仍保留 categoryId。
     * @param order 排序 key，白名单仅 `actionDate` / `collect`（原版 `UserCollectItemSubPageFragment.java:55-64`），
     *   越界值统一回落 `actionDate`
     * @param state 作品三态：1 想看 / 2 在看 / 3 看过；null 时不下发
     * @param isAsc 作品页收藏时间升降序；null 时不下发
     * @param tagValue 作品页标签筛选；null 或空串时不下发
     *
     * ⚠️ [state] / [isAsc] / [tagValue] 是作品页**独立于 order 的正交维度**，
     * 且仅在 `type == "works"` 时下发，厂商/系列/角色/人物 4 页的请求参数保持原样。
     */
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
        val snapshotToken = userSession.currentToken
        val response = api.getUserCollectCommon(params)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
            }
            throw ApiException(response.code, response.msg ?: "获取通用实体收藏列表失败")
        }
    }

    /**
     * 查询用户相册收藏列表 (api/collect/album-v2, GET)。
     *
     * @param type 相册归属，默认 fav（我收藏的）
     * @param order 排序 key，白名单 addTime（发布时间）/ actionDate（收藏时间）/ praiseCount（点赞量）
     */
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
        val snapshotToken = userSession.currentToken
        val response = api.getUserCollectAlbum(params)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
            }
            throw ApiException(response.code, response.msg ?: "获取相册收藏列表失败")
        }
    }

    /**
     * 查询用户图片收藏列表 (api/collect/pic-v2, GET)。
     *
     * @param collectState 收藏状态，默认 collect（图片页原版硬编码）
     * @param order 排序 key，白名单 addTime（发布时间）/ actionDate（收藏时间）/ praiseCount（点赞量），
     *   与相册页共用同一套排序组件
     */
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
        val snapshotToken = userSession.currentToken
        val response = api.getUserCollectPic(params)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
            }
            throw ApiException(response.code, response.msg ?: "获取图片收藏列表失败")
        }
    }

    /**
     * 获取作品收藏候选标签池 (api/user/glean/textTag/get)。
     *
     * ⚠️ 与手办标签池 `api/collect/tag` **不是同一套**，作品页筛选必须用本接口。
     * 数组元素兼容纯字符串与 `{ "tagValue": "..." }` 两种形态，统一归一化为字符串。
     */
    suspend fun getWorksCollectTags(
        page: Int = 1,
        pageSize: Int = 20
    ): Result<List<String>> = runCatching {
        val response = api.getWorksCollectTags(page = page, pageSize = pageSize)
        if (response.isSuccess) {
            parseWorksCollectTags(response.data)
        } else {
            throw ApiException(response.code, response.msg ?: "获取作品收藏标签失败")
        }
    }

    /**
     * 查询作品收藏详情 (api/works/glean/get/{worksId})。
     *
     * 兼容两种响应形态：`data.glean` 包裹 与 `data` 铺平，均归一化为 [WorksCollectData]；
     * 服务端未收藏时返回 null。
     */
    suspend fun getWorksCollect(worksId: Long): Result<WorksCollectData?> = runCatching {
        val response = api.getWorksCollect(worksId = worksId)
        if (response.isSuccess) {
            parseWorksCollect(response.data)
        } else {
            throw ApiException(response.code, response.msg ?: "获取作品收藏信息失败")
        }
    }

    /**
     * 保存作品收藏 (api/works/glean/add)。
     *
     * @param state 收藏状态：1 想看 / 2 在看 / 3 看过
     * @param tagStr 标签串，英文逗号分隔（形如 `"热血,校园"`）
     */
    suspend fun saveWorksCollect(
        worksId: Long,
        state: Int,
        tagStr: String,
        comment: String
    ): Result<Unit> = runCatching {
        val response = api.saveWorksCollect(
            itemType = CollectConstants.WORKS_ITEM_TYPE,
            itemId = worksId,
            tagStr = tagStr,
            comment = comment,
            state = state
        )
        if (!response.isSuccess) {
            throw ApiException(response.code, response.msg ?: "保存作品收藏失败")
        }
    }

    /**
     * 取消作品收藏 (api/works/glean/del)。
     */
    suspend fun deleteWorksCollect(worksId: Long): Result<Unit> = runCatching {
        val response = api.deleteWorksCollect(
            itemType = CollectConstants.WORKS_ITEM_TYPE,
            itemId = worksId
        )
        if (!response.isSuccess) {
            throw ApiException(response.code, response.msg ?: "取消作品收藏失败")
        }
    }

    /**
     * 获取图片关联条目 (api/pic/relate)。
     *
     * 覆盖相册(album)、手办(hobby)、文章(article) 三类，逐项取 `relateItem`。
     */
    suspend fun getPictureRelate(picId: Long): Result<List<PictureRelateItem>> = runCatching {
        val response = api.getPictureRelate(picId = picId)
        if (response.isSuccess) {
            response.data?.list?.mapNotNull { it.relateItem } ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取图片关联条目失败")
        }
    }

    /**
     * 提交举报 (api/web-report/add)。
     *
     * ⚠️ **服务端字段名不是 `nodeId` / `content`**：原版两个独立调用点
     * （`f3.java:55` 图片举报、`ResaleReportActivity.java:125` 二手举报）一致提交
     * `type` / `userReason` / `objId`。本方法的形参名沿用调用方习惯，
     * 但映射关系如下：
     *
     * | 形参 | 服务端字段 |
     * |---|---|
     * | [nodeId] | `objId` |
     * | [content] | `userReason` |
     * | [type] | `type` |
     *
     * @param nodeId 被举报对象 ID，图片页传 picId（原版取当前大图对象的 `id`）
     * @param content 用户填写的举报理由；原版在理由为空白时**直接不发请求**只弹提示，此处同样短路
     * @param type 举报对象类型，图片传 [CollectConstants.REPORT_TYPE_PICTURE]
     */
    suspend fun submitReport(
        nodeId: Long,
        content: String,
        type: String = CollectConstants.REPORT_TYPE_PICTURE
    ): Result<Unit> = runCatching {
        if (content.isBlank()) {
            // 对齐原版 f3$a.a() 的空理由守卫：不提交，并让调用方拿到明确失败原因
            throw ApiException(0, "请填写举报原因")
        }
        val response = api.submitReport(
            objId = nodeId,
            userReason = content,
            type = type
        )
        if (!response.isSuccess) {
            throw ApiException(response.code, response.msg ?: "举报提交失败")
        }
    }

    /**
     * 归一化作品标签池：兼容 `["热血", ...]` 与 `[{"tagValue":"热血"}, ...]` 两种数组元素形态。
     */
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

    /**
     * 归一化作品收藏详情：优先取 `glean` 包裹对象，否则按铺平对象解析。
     */
    private fun parseWorksCollect(data: JsonElement?): WorksCollectData? {
        val obj = data as? JsonObject ?: return null
        val target = (obj["glean"] as? JsonObject) ?: obj
        if (target.isEmpty()) return null
        return runCatching { json.decodeFromJsonElement<WorksCollectData>(target) }.getOrNull()
    }

    companion object {
        /** 周边列表排序：升序 */
        const val SORT_TYPE_ASC = 1

        /** 周边列表排序：降序（原版默认值） */
        const val SORT_TYPE_DESC = 2
    }
}
