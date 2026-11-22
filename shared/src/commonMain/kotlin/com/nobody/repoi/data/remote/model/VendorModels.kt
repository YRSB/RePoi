package com.nobody.repoi.data.remote.model

import com.nobody.repoi.util.DefaultJson
import com.nobody.repoi.util.PriceFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CompanyDetailPayload(
    val company: CompanyData? = null
)

@Serializable
data class CompanyOtherInfoItem(
    val key: String? = null,
    val value: String? = null
)

@Serializable
data class CompanyData(
    val id: Long = 0L,
    val categoryId: Int = 40000,
    val itemId: Long = 0L,
    val itemType: String? = "company",
    val name: String? = null,
    val nameCN: String? = null,
    val aliases: List<String>? = null,
    val cover: String? = null,
    val detail: String? = null,
    val commentCount: Int = 0,
    val hits: Long = 0L,
    val hitsDay: Long = 0L,
    val hits7Day: Long = 0L,
    val collect: Long = 0L,
    val website: String? = null,
    val hobbyRating: Double = 0.0,
    val hobbyCount: Int = 0,
    val issueHobbyCount: Int = 0,
    val otherInfo: String? = null,
    val state: String? = null
) {

    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: ""

    val subName: String?
        get() = name?.takeIf { it.isNotBlank() && it != displayName }

    val ratingScoreText: String
        get() = if (hobbyRating > 0.0) {
            PriceFormatter.formatFixed(hobbyRating, 1)
        } else "--"

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

    val parsedOtherInfo: List<CompanyOtherInfoItem>
        get() {
            val jsonStr = otherInfo ?: return emptyList()
            return try {
                DefaultJson.decodeFromString<List<CompanyOtherInfoItem>>(jsonStr)
            } catch (_: Exception) {
                emptyList()
            }
        }

    val region: String?
        get() = parsedOtherInfo.firstOrNull { it.key == "地区" }?.value

    val twitterUrl: String?
        get() = parsedOtherInfo.firstOrNull { it.key?.contains("推特", ignoreCase = true) == true || it.key?.contains("twitter", ignoreCase = true) == true }?.value
}

@Serializable
data class CompanyActionPayload(
    val list: List<CompanyActionItem>? = null
)

@Serializable
data class CompanyActionItem(
    val id: Long = 0L,
    val hobbyCategoryId: Int = 0,
    val actionComment: String? = null,
    val actionType: Int = 0,
    val actionMessage: String? = null,
    val actionData: String? = null,
    val actionDateString: String? = null,
    val actionDate: Long = 0L,
    val relateItem: CompanyActionRelateItem? = null,
    val user: CompanyActionUser? = null
) {

    val actionTitle: String
        get() {
            val msg = actionMessage ?: ""
            return when {
                msg.contains("开定") || msg.contains("开订") || msg.contains("预订") || actionData == "preorder" -> "开订时间"
                msg.contains("制作决定") || actionData == "confirm" -> "制作决定"
                msg.contains("出荷延期") || actionData == "delay" -> "出荷延期"
                msg.contains("出荷") || actionData == "release" -> "出荷时间"
                else -> {
                    val afterTag = msg.substringAfterLast("</a>", "").trim()
                    afterTag.ifBlank { "情报更新" }
                }
            }
        }

    fun toActionItem(): ActionItem {
        val cleanComment = (actionMessage ?: actionComment)
            ?.replace(Regex("<[^>]+>"), "")
            ?.replace("&nbsp;", " ")
            ?.replace("&amp;", "&")
            ?.trim()

        val normalizedActionData = actionData?.ifBlank { null } ?: when {
            actionMessage?.contains("制作决定") == true -> "confirm"
            actionMessage?.contains("官图") == true -> "official_pic"
            actionMessage?.contains("预订") == true || actionMessage?.contains("开订") == true || actionMessage?.contains("开定") == true -> "preorder"
            actionMessage?.contains("延期") == true -> "delay"
            actionMessage?.contains("出荷") == true -> "release"
            actionMessage?.contains("再版") == true || actionMessage?.contains("再贩") == true -> "reorder"
            else -> null
        }

        return ActionItem(
            id = id,
            actionDate = if (actionDate > 0L) actionDate.toString() else actionDateString,
            actionType = if (actionType != 0) actionType else 401,
            actionComment = cleanComment,
            actionData = normalizedActionData,
            user = user?.let {
                UserProfileSummary(
                    id = it.id,
                    userId = it.userId,
                    nickname = it.nickname,
                    header = it.header
                )
            },
            relateItem = relateItem?.let {
                RelateItem(
                    id = it.id,
                    itemId = it.itemId,
                    itemType = it.itemType ?: "hobby",
                    name = it.name,
                    nameCN = it.nameCN,
                    cover = it.cover,
                    categoryId = it.category?.categoryId ?: 0,
                    commentCount = it.commentCount,
                    collect = it.collect.toInt()
                )
            }
        )
    }
}

@Serializable
data class CompanyActionRelateItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val itemType: String? = null,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val collect: Long = 0L,
    val commentCount: Int = 0,
    val category: CompanyActionCategory? = null
) {
    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: ""
}

@Serializable
data class CompanyActionCategory(
    val id: Long = 0L,
    val categoryId: Int = 0,
    val name: String? = null,
    val parentId: Long = 0L
)

@Serializable
data class CompanyActionUser(
    val id: Long = 0L,
    val userId: Long = 0L,
    val nickname: String? = null,
    val header: String? = null
)

@Serializable
data class CompanySeriesPayload(
    val seriesList: List<CompanySeriesItem>? = null
)

@Serializable
data class CompanySeriesItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val itemType: String? = "series",
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val hobbyCount: Int = 0,
    val updTime: String? = null
) {
    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: ""
}

@Serializable
data class CompanyTagPayload(
    val list: List<CompanyTagItem>? = null
)

@Serializable
data class CompanyTagItem(
    val id: Long = 0L,
    val name: String? = null,
    val tagName: String? = null,
    val tagText: String? = null,
    val count: Int = 0
) {
    val displayTag: String
        get() = name ?: tagName ?: tagText ?: ""
}

@Serializable
data class CompanyMyVotesData(
    val allTags: List<CompanyTagVoteItem> = emptyList(),
    val userVotes: List<CompanyUserVoteItem> = emptyList()
)

@Serializable
data class CompanyTagVoteItem(
    val id: Long = 0L,
    val name: String = "",
    val allowScoring: Int = 0
) {
    val isScoreTag: Boolean get() = allowScoring == 1
}

@Serializable
data class CompanyUserVoteItem(
    val tagId: Long = 0L,
    val score: Double? = null
)
