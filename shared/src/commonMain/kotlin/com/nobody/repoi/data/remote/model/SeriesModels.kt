package com.nobody.repoi.data.remote.model

import com.nobody.repoi.util.DefaultJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

    val subName: String?
        get() = name?.takeIf { it.isNotBlank() && it != displayName }

    val parsedOtherInfo: List<CompanyOtherInfoItem>
        get() {
            val jsonStr = otherInfo ?: return emptyList()
            return try {
                DefaultJson.decodeFromString<List<CompanyOtherInfoItem>>(jsonStr)
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

@Serializable
data class SeriesAlbumPayload(
    val album: List<AlbumData>? = null
)

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
