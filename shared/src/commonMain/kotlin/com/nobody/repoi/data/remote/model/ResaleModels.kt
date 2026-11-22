package com.nobody.repoi.data.remote.model

import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import kotlinx.serialization.Serializable

@Serializable
data class ResaleUserData(
    val userId: Long = 0L,
    val nickname: String? = null,
    val header: String? = null
)

typealias ResaleUserSummary = ResaleUserData

@Serializable
data class ResaleHobbyData(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null
)

typealias ResaleHobbySummary = ResaleHobbyData

@Serializable
data class ResaleSearchItem(
    val id: Long = 0L,
    val price: String? = null,
    val purityStr: String? = null,
    val purity: Int = 0,
    val location: String? = null,
    val detail: String? = null,
    val addDate: String? = null,
    val addDateStr: String? = null,
    val pic: String? = null,
    val type: Int = 1,
    val link: String? = null,
    val item: List<ResaleHobbyData>? = null,
    val user: ResaleUserData? = null,
    val state: Int = 1,
    val zoneIds: List<Long> = emptyList()
) {

    val isSell: Boolean
        get() = type == 1

    val isBuy: Boolean
        get() = type == 2

    val hasExternalLink: Boolean
        get() = !link.isNullOrBlank()

    val cleanDetail: String
        get() {
            val d = detail ?: return ""
            if (!d.contains('<') && !d.contains('&')) return d.trim()
            return d
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
        }

    val hobbyCover: String?
        get() = item?.firstOrNull()?.cover?.takeIf { it.isNotBlank() }

    val hasUserPic: Boolean
        get() = !pic.isNullOrBlank()

    val userPics: List<String>
        get() = pic?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.map { MediaPipeline.Default.resolveUrl(it, MediaCategory.RESALE, ImagePurpose.CARD_STANDARD) }
            ?: emptyList()

    val displayCoverUrl: String?
        get() {
            val hCover = hobbyCover
            if (!hCover.isNullOrBlank()) {
                return MediaPipeline.Default.resolveUrl(hCover, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD)
            }
            return userPics.firstOrNull()
        }

    val displayCover: String?
        get() = hobbyCover ?: pic?.split(",")?.firstOrNull { it.isNotBlank() }

    val targetItemId: Long
        get() = item?.firstOrNull()?.let { if (it.itemId > 0L) it.itemId else it.id } ?: 0L

    val displayPriceText: String
        get() = price?.takeIf { it.isNotBlank() }?.let { "¥$it" } ?: "面议"
}

@Serializable
data class ResaleSearchPayload(
    val list: List<ResaleSearchItem> = emptyList()
)

@Serializable
data class ResaleDetailUser(
    val userId: Long = 0L,
    val nickname: String? = null,
    val header: String? = null,
    val level: Int = 0
)

@Serializable
data class ResaleDetailHobbyItem(
    val itemNodeId: Long = 0L,
    val itemId: Long = 0L,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null
) {
    val targetItemId: Long
        get() = if (itemId > 0L) itemId else itemNodeId

    val displayName: String
        get() = nameCN?.takeIf { it.isNotBlank() } ?: name ?: "关联条目"
}

@Serializable
data class ResaleDetailItem(
    val id: Long = 0L,
    val price: String? = null,
    val purityStr: String? = null,
    val purity: Int = 0,
    val location: String? = null,
    val detail: String? = null,
    val addDate: String? = null,
    val addDateStr: String? = null,
    val link: String? = null,
    val pic: String? = null,
    val type: Int = 1,
    val state: Int = 1,
    val linkItems: List<ResaleDetailHobbyItem> = emptyList(),
    val user: ResaleDetailUser? = null
) {

    val userPics: List<String>
        get() = pic?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.map { MediaPipeline.Default.resolveUrl(it, MediaCategory.RESALE, ImagePurpose.CARD_STANDARD) }
            ?: emptyList()

    val displayPriceText: String
        get() = price?.takeIf { it.isNotBlank() }?.let { "¥$it" } ?: "面议"

    val isSell: Boolean get() = type == 1
    val isBuy: Boolean get() = type == 2

    val hasExternalLink: Boolean get() = !link.isNullOrBlank()

    val firstHobby: ResaleDetailHobbyItem? get() = linkItems.firstOrNull()
}

@Serializable
data class ResaleDetailPayload(
    val resale: ResaleDetailItem? = null
)
