package com.nobody.poishelf.data.remote.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

@Serializable
data class AlbumListPayload(
    val list: List<AlbumListItem>? = null,
    val page: Int = 1
)

@Serializable
data class AlbumListItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val itemType: String? = null,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val praiseCount: JsonElement? = null,
    val commentCount: Int = 0,
    val addTime: String? = null,
    val userId: Long = 0L,
    val userName: String? = null,
    val userHeader: String? = null,
    val user: HobbyAlbumUser? = null
) {
    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: ""

    val authorName: String?
        get() = user?.nickname?.ifBlank { null } ?: userName?.ifBlank { null }

    val userNickname: String
        get() = authorName ?: "胶友"

    val userAvatar: String?
        get() = user?.header?.ifBlank { null } ?: userHeader?.ifBlank { null }

    val hasAuthor: Boolean
        get() = !authorName.isNullOrBlank() || !userAvatar.isNullOrBlank()

    val targetAlbumId: Long
        get() = if (id > 0L) id else itemId

    val hasCover: Boolean
        get() = !cover.isNullOrBlank() && cover != "none.jpg"
    val praiseCountValue: Long
        get() = when (val p = praiseCount) {
            is JsonPrimitive -> p.longOrNull ?: p.intOrNull?.toLong() ?: p.content.toLongOrNull() ?: 0L
            else -> 0L
        }
}

typealias ZoneTabZone = ZoneItem

@Serializable
data class PraiseTopPayload(
    val list: List<AlbumListItem>? = null
)

enum class AlbumOrder(val apiValue: String, val label: String) {
    Recommend("top", "推荐"),
    Latest("updTime", "最新"),
    Praise("praise", "TOP赞"),
    Care("care", "关注")
}

data class AlbumZoneTab(
    val key: String,
    val name: String,
    val zoneIds: String
) {
    companion object {
        val ALL = AlbumZoneTab(key = "all", name = "全部", zoneIds = "")
        val FOLLOW = AlbumZoneTab(key = "follow", name = "关注", zoneIds = "")
    }
}
