package com.nobody.poishelf.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import com.nobody.poishelf.util.PriceFormatter
import com.nobody.poishelf.util.parseEmoticonUrls

// ── 3. 图片与相册模型 ───────────────────────────────

@Serializable
data class PicListPayload(
    val list: List<PicItem>? = null
)

@Serializable
data class PicItem(
    val id: Long = 0L,
    val rank: Int = 0,
    val subType: String? = null,
    val pictureInfo: PicPictureInfo? = null,
    val path: String? = null,
    val smallPath: String? = null,
    val rawPath: String? = null
) {
    /** 统一提取图片相对路径 */
    val imagePath: String?
        get() = pictureInfo?.path ?: path ?: smallPath ?: rawPath

    /** 计算图片的纵横宽高比 (宽/高) */
    val aspectRatio: Float?
        get() = pictureInfo?.let { info ->
            val w = info.width ?: 0
            val h = info.height ?: 0
            if (w > 0 && h > 0) w.toFloat() / h else null
        }
}

@Serializable
data class PicPictureInfo(
    val id: Long = 0L,
    val path: String? = null,
    val itemId: Long? = null,
    val itemType: String? = null,
    val categoryId: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val state: String? = null,
    val remark: String? = null,
    val praiseCount: Int? = null,
    val collect: Int? = null
)

@Serializable
data class HobbyAlbumPayload(
    val list: List<HobbyAlbumItem>? = null
)

@Serializable
data class HobbyAlbumItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val itemType: String? = null,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val picCount: Int = 0,
    val hits: Int = 0,
    val collect: Int = 0,
    val commentCount: Int = 0,
    val user: HobbyAlbumUser? = null,
    val praiseCount: Long? = null
) {
    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: ""

    val userNickname: String
        get() = user?.nickname ?: "胶友"
}

@Serializable
data class HobbyAlbumUser(
    val id: Long = 0L,
    val userId: Long = 0L,
    val nickname: String? = null,
    val header: String? = null,
    val level: Int = 0,
    val sign: String? = null,
    val hp: Int = 0,
    val atk: Int = 0,
    val def: Int = 0
)

/**
 * 条目/相册点赞状态响应载荷 (/api/item/likeInfo)
 */
@Serializable
data class ItemLikeInfoPayload(
    val praiseState: Boolean = false
)

@Serializable
data class AlbumDetailPayload(
    val album: AlbumData? = null
)

@Serializable
data class AlbumData(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val itemType: String? = null,
    val name: String? = null,
    val nameCN: String? = null,
    val detail: String? = null,
    val cover: String? = null,
    val categoryId: Int = 0,
    val picCount: Int = 0,
    val collect: Long? = null,
    val hits: Long = 0L,
    val praiseCount: Long? = null,
    val commentCount: Int = 0,
    val sortType: String? = null,
    val owner: String? = null,
    val r18: Int = 0,
    val user: HobbyAlbumUser? = null,
    val userId: Long = 0L,
    val userName: String? = null
) {
    /** 实际相册 ID：优先取自身的 id，若无则降级为 itemId */
    val actualAlbumId: Long
        get() = if (id > 0L) id else itemId

    /** 作者用户 ID：优先取 user?.id（>0），其次 user?.userId（>0），降级为 userId */
    val authorUserId: Long
        get() = user?.id?.takeIf { it > 0L } ?: user?.userId?.takeIf { it > 0L } ?: userId

    /** 优先显示中文名，若无则显示原名 */
    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: ""

    val userNickname: String
        get() = user?.nickname?.ifBlank { null } ?: userName ?: "胶友"

    /** 纯文本相册描述（去除 HTML 标签，转换换行符） */
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
data class RelatedAlbumPayload(
    val list: List<RelatedAlbumItem>? = null
)

@Serializable
data class RelatedAlbumItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val itemType: String? = null,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val detail: String? = null,
    val picCount: Int = 0,
    val collect: Long? = null,
    val hits: Long = 0L,
    val praiseCount: Long? = null,
    val commentCount: Int = 0,
    val user: HobbyAlbumUser? = null
) {
    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: ""

    val userNickname: String
        get() = user?.nickname?.ifBlank { null } ?: "胶友"
}

@Serializable
data class HobbyGroupPayload(
    val groups: List<HobbyGroupItem>? = null
)

@Serializable
data class HobbyGroupItem(
    val typeName: String? = null,
    val items: List<HobbyItemSummary>? = null
)

@Serializable
data class HobbyPushListPayload(
    val hobbys: List<HobbyItemSummary>? = null
)

@Serializable
data class RelatedHobbyPayload(
    val hobbys: List<RelatedHobbyItem>? = null
)

@Serializable
data class RelatedHobbyItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val itemType: String? = null,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val hits: Long = 0L,
    val collect: JsonElement? = null,
    val commentCount: Int = 0,
    val rating: Long? = null,
    val money: Double? = null,
    val currency: String? = null
) {
    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: ""

    val collectCount: Long
        get() = when (val c = collect) {
            is JsonPrimitive -> c.content.toLongOrNull() ?: 0L
            else -> 0L
        }

    val priceText: String
        get() = if (money != null && money > 0) {
            val currencyLabel = when (currency?.lowercase()) {
                "yen" -> "円"
                "rmb", "cny" -> "元"
                else -> currency ?: ""
            }
            "${money.toLong()} $currencyLabel"
        } else ""
}

@Serializable
data class ItemTagsPayload(
    val tags: List<ItemTagEntry>? = null
)

@Serializable
data class ItemTagEntry(
    val count: Int = 0,
    val tag: ItemTag? = null
) {
    val tagText: String
        get() = tag?.tagValue ?: ""
}

@Serializable
data class ItemTag(
    val id: Long = 0L,
    val tagValue: String? = null
)
