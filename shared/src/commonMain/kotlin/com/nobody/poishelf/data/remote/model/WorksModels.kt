package com.nobody.poishelf.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class WorksDetailPayload(
    val result: WorksDetailResult? = null
)

@Serializable
data class WorksDetailResult(
    val works: WorksData? = null,
    val worksSeries: WorksSeriesRef? = null,
    val worksRatingAvg: Float = 0f
)

@Serializable
data class WorksData(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val itemType: String? = null,
    val categoryId: Int = 0,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val background: String? = null,
    val detail: String? = null,
    val releaseDateString: String? = null,
    val commentCount: Int = 0,
    val hits: Long = 0L,
    val collect: Int = 0
) {
    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name.orEmpty()
    val actualNodeId: Long
        get() = if (id > 0L) id else itemId
    val actualItemId: Long
        get() = if (itemId > 0L) itemId else id
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
data class WorksSeriesRef(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val itemType: String? = null,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null
) {
    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name.orEmpty()
}

@Serializable
data class WorksAlbumPayload(
    val album: List<AlbumData>? = null,
    val list: List<AlbumData>? = null
) {
    val items: List<AlbumData>
        get() = album ?: list ?: emptyList()
}

@Serializable
data class WorksCharacterPayload(
    val worksLinkCharacter: List<WorksCharacterItem>? = null
)

@Serializable
data class WorksCharacterItem(
    val charactarId: Long = 0L,
    val cNameCN: String? = null,
    val cName: String? = null,
    val cCover: String? = null,
    val personId: Long = 0L,
    val pNameCN: String? = null,
    val pName: String? = null,
    val jsCareerType: String? = null
) {
    val displayName: String
        get() = cNameCN?.ifBlank { null } ?: cName.orEmpty()
    val voiceName: String
        get() = pNameCN?.ifBlank { null } ?: pName.orEmpty()
}
