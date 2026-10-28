package com.nobody.poishelf.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val SeriesJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * 系列主体响应 (api/item/get-v2?itemId=...&itemType=series)
 */
@Serializable
data class SeriesDetailPayload(
    @SerialName("itemData") val itemData: SeriesData? = null
)

@Serializable
data class SeriesData(
    val id: Long = 0L,
    val nodeId: Long = 0L,
    val itemId: Long = 0L,
    val itemType: String? = "series",
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val detail: String? = null,
    val commentCount: Int = 0,
    val hits: Int = 0,
    val collect: Int = 0,
    val hobbyCount: Int = 0,
    val categoryId: Int? = null,
    val otherInfo: String? = null,
    val website: String? = null,
    val pageState: Int = 0
) {
    val actualNodeId: Long
        get() = if (nodeId > 0L) nodeId else id

    val actualItemId: Long
        get() = if (itemId > 0L) itemId else id

    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: ""

    /** 副标题（若有外文名且与主标题不同） */
    val subName: String?
        get() = name?.takeIf { it.isNotBlank() && it != displayName }

    /** 解析 otherInfo JSON 字符串获取结构化扩展信息 */
    val parsedOtherInfo: List<CompanyOtherInfoItem>
        get() {
            val jsonStr = otherInfo ?: return emptyList()
            return try {
                SeriesJson.decodeFromString<List<CompanyOtherInfoItem>>(jsonStr)
            } catch (_: Exception) {
                emptyList()
            }
        }

    val detailPlainText: String
        get() {
            if (detail.isNullOrBlank()) return ""
            return detail
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
                .replace(Regex("(?i)</p>"), "\n\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
        }
}

/**
 * 系列子系列 / 相关系列响应 (api/series/link/series?seriesId=...&isChild=1|2)
 */
@Serializable
data class SeriesLinkPayload(
    val list: List<SeriesLinkItem>? = null
)

@Serializable
data class SeriesLinkItem(
    val id: Long = 0L,
    val childId: Long = 0L,
    val itemId: Long = 0L,
    val itemType: String? = "series",
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val hobbyCount: Int = 0
) {
    val actualSeriesId: Long
        get() = when {
            childId > 0L -> childId
            itemId > 0L -> itemId
            else -> id
        }

    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: ""
}

/**
 * 系列关联相册响应 (api/series/album?seriesId=...)
 */
@Serializable
data class SeriesAlbumPayload(
    val album: List<AlbumData>? = null
)

/**
 * 系列在售商品推荐响应 (api/goods/tb/more-v2?nodeId=...)
 */
@Serializable
data class SeriesGoodsPayload(
    val list: List<SeriesGoodsItem>? = null
)

@Serializable
data class SeriesGoodsItem(
    val id: Long = 0L,
    val name: String? = null,
    val cover: String? = null,
    val price: String? = null,
    val saleTypeStr: String? = null,
    val link: String? = null
)

/**
 * 系列社区/论坛响应 (api/series/forum?seriesId=...)
 */
@Serializable
data class SeriesForumPayload(
    val forum: SeriesForumInfo? = null,
    val posts: List<SeriesPostItem>? = null
)

@Serializable
data class SeriesForumInfo(
    val id: Long = 0L,
    val name: String? = null,
    val postCount: Int = 0
)

@Serializable
data class SeriesPostItem(
    val id: Long = 0L,
    val title: String? = null,
    val userNickname: String? = null
)
