package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.ActionListData
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.JsonRefResolver
import com.nobody.repoi.util.DateTimeFormats
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * 关注动态分页结果（含翻页游标）。
 */
data class FollowPage(
    val items: List<ActionItem> = emptyList(),
    val lastId: Long = 0L,
    val hasMore: Boolean = true
)

/**
 * 关注条目/用户数据仓储（关注屏数据来源）。
 */
class FollowRepository constructor(
    private val apiService: HpoiApi,
    private val json: Json
) {
    /**
     * 拉取关注动态分页（条目动态/用户动态共用，翻页靠 lastId）。
     */
    suspend fun getFollowFeed(
        type: String = "item",
        actionType: Int? = null,
        subType: String? = null,
        lastId: Long = 0L,
        pageSize: Int = 20
    ): Result<FollowPage> = runCatching {
        val response = apiService.getFollowDynamics(
            // 原 App 条目动态恒带 page=1，用户动态不带该字段
            page = if (type == "item") 1 else null,
            lastId = lastId,
            pageSize = pageSize,
            actionType = actionType,
            subType = subType,
            type = type,
            // 原 App 条目动态恒带 isHome=0，用户动态不带该字段
            isHome = if (type == "item") 0 else null
        )
        if (!response.isSuccess) throw ApiException(response.code, response.msg ?: "数据加载失败")
        val list = decodeList(response.data)
        FollowPage(
            items = list,
            lastId = parseFollowCursor(list.lastOrNull()?.actionDate),
            hasMore = list.isNotEmpty()
        )
    }

    /**
     * 解码列表载荷（先还原服务端 `@id`/`@ref` 引用去重，再转 DTO）。
     */
    internal fun decodeList(data: JsonObject?): List<ActionItem> {
        if (data == null) return emptyList()
        val resolved = JsonRefResolver.resolveRefs(data)
        return json.decodeFromJsonElement<ActionListData>(resolved).list ?: emptyList()
    }
}

/**
 * 解析翻页游标（兼容数字时间戳与日期字符串两种格式）。
 */
internal fun parseFollowCursor(dateStr: String?): Long {
    if (dateStr.isNullOrBlank()) return 0L
    dateStr.toLongOrNull()?.let { return it }
    // KMP：DateTimeFormats.parseDateTimeToEpochMillis 覆盖全部三种服务端格式
    //（数字段分割解析，与 SimpleDateFormat 严格模式等价）。
    return DateTimeFormats.parseDateTimeToEpochMillis(dateStr) ?: 0L
}
