package com.nobody.poishelf.data.repository

import com.nobody.poishelf.data.remote.api.HpoiApi
import com.nobody.poishelf.data.remote.model.ApiException
import com.nobody.poishelf.data.remote.model.CollectConstants
import com.nobody.poishelf.data.remote.model.CollectCountData
import com.nobody.poishelf.data.remote.model.CollectData
import com.nobody.poishelf.data.remote.model.PictureRelateItem
import com.nobody.poishelf.data.remote.model.WorksCollectData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * 收藏模块聚合数据仓储：
 * 管理条目收藏状态的获取、分类人数统计、备选标签以及收藏的保存与删除。
 * 同时承载收藏页的两条写侧链路——批量取消收藏 (`api/collect/batch/cancel`)
 * 与作品收藏整链 (`api/works/glean/get|add|del`、`api/user/glean/textTag/get`)，
 * 另含图片画廊的关联条目查询 (`api/pic/relate`)。
 * 暴露 [collectUpdates] 响应流，在收藏状态发生增删改时向全应用广播最新状态，
 * 驱动手办详情页等界面爱心状态与计数的原子更新。
 */
class CollectRepository(
    private val api: HpoiApi,
    private val json: Json
) {
    // 广播 (nodeId, CollectData?) 更新事件：CollectData 为 null 或 state=="delete" 时代表取消收藏
    private val _collectUpdates = MutableSharedFlow<Pair<Long, CollectData?>>(extraBufferCapacity = 16)
    val collectUpdates: SharedFlow<Pair<Long, CollectData?>> = _collectUpdates.asSharedFlow()

    /**
     * 获取指定条目的收藏状态信息 (api/collect/state)。
     */
    suspend fun getCollectState(nodeId: Long): Result<CollectData?> = runCatching {
        val response = api.getCollectState(nodeId)
        if (response.isSuccess) {
            response.data?.collect
        } else {
            throw ApiException(response.code, response.msg ?: "获取收藏状态失败")
        }
    }

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
     * 保存/更新条目收藏信息 (api/collect/hobby/upd)。
     */
    suspend fun saveCollect(nodeId: Long, collectData: CollectData): Result<Unit> = runCatching {
        val jsonPayload = json.encodeToString(collectData)
        val response = api.updateCollect(nodeId = nodeId, json = jsonPayload)
        if (response.isSuccess) {
            _collectUpdates.tryEmit(nodeId to collectData)
        } else {
            throw ApiException(response.code, response.msg ?: "保存收藏失败")
        }
    }

    /**
     * 取消条目收藏 (api/collect/hobby/upd，state="delete")。
     */
    suspend fun deleteCollect(nodeId: Long, currentData: CollectData): Result<Unit> = runCatching {
        val deletedData = currentData.copy(state = CollectConstants.STATE_DELETE)
        val jsonPayload = json.encodeToString(deletedData)
        val response = api.updateCollect(nodeId = nodeId, json = jsonPayload)
        if (response.isSuccess) {
            _collectUpdates.tryEmit(nodeId to null)
        } else {
            throw ApiException(response.code, response.msg ?: "取消收藏失败")
        }
    }

    /**
     * 添加通用条目/相册收藏 (api/collect/item/add)。
     */
    suspend fun collectItemAdd(nodeId: Long): Result<Unit> = runCatching {
        val response = api.collectItemAdd(id = nodeId)
        if (response.isSuccess) {
            Unit
        } else {
            throw ApiException(response.code, response.msg ?: "收藏失败")
        }
    }

    /**
     * 取消通用条目/相册收藏 (api/collect/del)。
     */
    suspend fun collectItemDel(nodeId: Long): Result<Unit> = runCatching {
        val response = api.collectItemDel(id = nodeId)
        if (response.isSuccess) {
            Unit
        } else {
            throw ApiException(response.code, response.msg ?: "取消收藏失败")
        }
    }

    /**
     * 批量取消收藏 (api/collect/batch/cancel)。
     *
     * ⚠️ [ids] 必须传**外层收藏节点 id**（如 `UserCollectAlbumItem.id` / `UserCollectHobbyItem.id`），
     * 而**不是** `actualAlbumId` / `actualItemId` 里的真实条目 ID。
     * 仅登录用户本人的收藏中心可用（原版 isSelf 门控）。
     * 空集合直接短路返回成功，避免向服务端提交空的 `[]`。
     */
    suspend fun cancelCollectBatch(ids: List<Long>): Result<Unit> = runCatching {
        if (ids.isEmpty()) return@runCatching Unit
        // 服务端要求 JSON 数组字符串，如 "[1,2,3]"
        val idArr = ids.joinToString(prefix = "[", postfix = "]", separator = ",")
        val response = api.cancelCollectBatch(idArr = idArr)
        if (response.isSuccess) {
            Unit
        } else {
            throw ApiException(response.code, response.msg ?: "批量取消收藏失败")
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
        if (response.isSuccess) {
            Unit
        } else {
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
        if (response.isSuccess) {
            Unit
        } else {
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
        if (response.isSuccess) {
            Unit
        } else {
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
}
