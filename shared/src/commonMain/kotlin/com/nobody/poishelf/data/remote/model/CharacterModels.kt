package com.nobody.poishelf.data.remote.model

import com.nobody.poishelf.platform.DateTimeFormats
import kotlinx.serialization.Serializable

/**
 * 角色/要素主体详情响应 (api/charactar/get)
 * ⚠️ "charactar" 为服务端历史拼写
 */
@Serializable
data class CharacterDetailPayload(
    val charactar: CharacterData? = null,
    val character: CharacterData? = null,
    val isAddFav: Boolean = false
) {
    val data: CharacterData?
        get() = (charactar ?: character)?.copy(
            isAddFav = isAddFav
        )
}

/**
 * 角色主体数据结构
 */
@Serializable
data class CharacterData(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val categoryId: Int = 50000,
    val itemType: String? = "charactar",
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val background: String? = null,
    val detail: String? = null,
    val sex: Int = 0,
    val birthday: String? = null,
    val commentCount: Int = 0,
    val collect: Int = 0,
    val hits: Long = 0L,
    val isAddFav: Boolean = false
) {
    val actualNodeId: Long
        get() = if (id > 0L) id else itemId

    val actualItemId: Long
        get() = if (itemId > 0L) itemId else id

    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: ""

    val subName: String?
        get() = name?.takeIf { it.isNotBlank() && it != displayName }

    val sexText: String
        get() = when (sex) {
            1 -> "男"
            2 -> "女"
            else -> "未知"
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
 * 角色登场作品响应 (api/charactar/works/get)
 */
@Serializable
data class CharacterWorksPayload(
    val worksList: List<CharacterWorksItem>? = null,
    val list: List<CharacterWorksItem>? = null
) {
    val items: List<CharacterWorksItem>
        get() = worksList ?: list ?: emptyList()
}

/**
 * 登场作品条目
 */
@Serializable
data class CharacterWorksItem(
    val worksId: Long = 0L,
    val wCover: String? = null,
    val wName: String? = null,
    val wNames: String? = null,
    val wNameCN: String? = null,
    val releaseDate: Long? = null,
    val cNameCN: String? = null,
    val jsCareerType: String? = null,
    val performers: List<CharacterPerformerItem> = emptyList()
) {
    val displayName: String
        get() = wNameCN?.ifBlank { null } ?: wNames?.ifBlank { null } ?: wName ?: "未知作品"

    val characterRoleText: String?
        get() {
            val role = jsCareerType?.trim()?.ifBlank { null }
            val cName = cNameCN?.trim()?.ifBlank { null }
            return when {
                cName != null && role != null -> "$cName ($role)"
                cName != null -> cName
                role != null -> role
                else -> null
            }
        }

    val formattedReleaseDate: String?
        get() {
            val ts = releaseDate ?: return null
            if (ts <= 0L) return null
            return try {
                DateTimeFormats.ymdFromMillis(ts)
            } catch (_: Exception) {
                null
            }
        }

    val validPerformers: List<CharacterPerformerItem>
        get() = performers.filter { it.isValid }
}

/**
 * 作品演职员/配音（CV）条目
 */
@Serializable
data class CharacterPerformerItem(
    val personId: Long = 0L,
    val pCover: String? = null,
    val rwCareerType: String? = null,
    val pName: String? = null,
    val pNames: String? = null,
    val pNameCN: String? = null,
    val rwRemark: String? = null
) {
    val displayName: String
        get() = pNameCN?.ifBlank { null } ?: pNames?.ifBlank { null } ?: pName ?: "未知"

    val careerLabel: String
        get() = rwCareerType?.ifBlank { null } ?: "配音"

    val isValid: Boolean
        get() = personId > 0L && (!pNameCN.isNullOrBlank() || !pNames.isNullOrBlank() || !pName.isNullOrBlank())
}

/**
 * 角色相关相册响应 (api/charactar/album/get)
 */
@Serializable
data class CharacterAlbumPayload(
    val albumList: List<AlbumData>? = null,
    val list: List<AlbumData>? = null
) {
    val items: List<AlbumData>
        get() = albumList ?: list ?: emptyList()
}

/**
 * 推荐要素/相关角色响应 (api/charactar/relevant/get)
 */
@Serializable
data class CharacterRelevantPayload(
    val charactarList: List<CharacterRelevantItem>? = null,
    val list: List<CharacterRelevantItem>? = null
) {
    val items: List<CharacterRelevantItem>
        get() = charactarList ?: list ?: emptyList()
}

/**
 * 推荐要素/相关角色条目
 */
@Serializable
data class CharacterRelevantItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val worksName: String? = null,
    val worksCover: String? = null,
    val worksTime: String? = null,
    val worksCharacter: String? = null
) {
    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: "未知要素"

    val actualItemId: Long
        get() = if (itemId > 0L) itemId else id
}

/**
 * 条目所属分区/属性标签响应 (api/zone/item)
 */
@Serializable
data class ZoneItemPayload(
    val zones: List<ZoneItem>? = null
) {
    val items: List<ZoneItem>
        get() = zones ?: emptyList()
}
