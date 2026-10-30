package com.nobody.repoi.data.remote.model

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
import com.nobody.repoi.util.PriceFormatter
import com.nobody.repoi.util.parseEmoticonUrls

// ── 1. 首页信息流 (Action Feed) 模型 ─────────────────────────

@Serializable
data class ActionListData(
    val list: List<ActionItem>? = null
)

@Serializable
data class ActionItem(
    val id: Long = 0L,
    val actionDate: String? = null,
    val actionType: Int = 0,
    val actionComment: String? = null,
    val actionData: String? = null,
    val user: UserProfileSummary? = null,
    val relateItem: RelateItem? = null,
    val relateData: List<RelateData>? = null
)

@Serializable
data class UserProfileSummary(
    val id: Long = 0L,
    val userId: Long = 0L,
    val level: Int = 0,
    val header: String? = null,
    val nickname: String? = null,
    val userName: String? = null,
    val sign: String? = null
) {
    val actualUserId: Long
        get() = if (id > 0L) id else userId

    val displayName: String
        get() = nickname?.ifBlank { null } ?: userName ?: "Hpoi 用户"
}

@Serializable
data class RelateItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val itemType: String? = null,
    val name: String? = null,
    val nameCN: String? = null,
    val path: String? = null,
    val cover: String? = null,
    val categoryId: Int = 0,
    val commentCount: Int = 0,
    val collect: Int = 0
) {
    /** 判定是否为相册类型（通过 itemType 或 categoryId 分类区间 60000..69999） */
    val isAlbum: Boolean
        get() = itemType.equals("album", ignoreCase = true) || categoryId in 60000..69999

    /** 相册真实 ID（优先取实体 id，缺失时回退 itemId） */
    val actualAlbumId: Long
        get() = if (id > 0L) id else itemId

    /** 手办真实条目 ID（优先取 itemId，缺失时回退 id） */
    val actualHobbyId: Long
        get() = if (itemId > 0L) itemId else id

    /** 统一解析后的目标 ID */
    val actualId: Long
        get() = if (isAlbum) actualAlbumId else actualHobbyId
}

@Serializable
data class RelateData(
    val content: String? = null,
    val path: String? = null,
    val price: String? = null,
    val detail: String? = null,
    val purityStr: String? = null,
    val emoticon: JsonElement? = null
) {
    val emoticonText: String?
        get() {
            val element = emoticon ?: return null
            if (element is JsonPrimitive) {
                return element.content.trim().ifBlank { null }
            }
            if (element is JsonArray) {
                return if (element.isNotEmpty()) "[表情]" else null
            }
            return null
        }

    val emoticonUrls: List<String>
        get() = parseEmoticonUrls(emoticon)
}
