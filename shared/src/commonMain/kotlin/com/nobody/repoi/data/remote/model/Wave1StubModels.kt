package com.nobody.repoi.data.remote.model

import com.nobody.repoi.util.parseEmoticonUrls
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

// ── Wave 1 契约桩：首页信息流模型 ─────────────────────────────
// 与老项目 `HomeModels.kt` 同名同字段，提前迁移以解耦 util 层。
// Wave 2 迁移完整 `HomeModels.kt` 时删除本文件（调用点零改动）。

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

// ── Wave 1 契约桩：分类树模型 ─────────────────────────────────
// 与老项目 `HobbyModels.kt` 同名同字段。Wave 2 删除本文件。

@Serializable
data class CategoryTreeData(
    val list: List<CategoryNode>? = null
)

@Serializable
data class CategoryNode(
    val id: Int = 0,
    val categoryId: Int = 0,
    val parentId: Int = 0,
    val name: String? = null,
    val order: Int = 0,
    val children: List<CategoryNode>? = null
)

// ── Wave 1 契约桩：评论模型 ───────────────────────────────────
// 与老项目 `CommentModels.kt` 同名同字段。Wave 2 删除本文件。

@Serializable
data class CommentPayload(
    val list: List<CommentItem>? = null
)

@Serializable
data class CommentItem(
    val id: Long = 0L,
    val content: String? = null,
    val status: Int = 0,
    val isMask: Int = 0,
    val addTime: String? = null,
    val addDateString: String? = null,
    val reply: Int = 0,
    val floor: Int = 0,
    val praiseCount: Int = 0,
    val praiseState: Boolean = false,
    val user: CommentUser? = null,
    val replys: List<CommentReplyItem>? = null,
    val emoticon: JsonElement? = null
) {
    /** 纯文本评论内容（脱除 HTML 标签与回车格式化） */
    val contentCleanText: String
        get() {
            if (content.isNullOrBlank()) return ""
            return content
                .replace(Regex("""(?i)<br\s*/?>"""), "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
        }

    val emoticonUrls: List<String>
        get() = parseEmoticonUrls(emoticon)
}

@Serializable
data class CommentUser(
    @SerialName("@id") val atId: String? = null,
    @SerialName("@ref") val atRef: String? = null,
    val id: Long = 0L,
    val userId: Long = 0L,
    val userType: Int = 0,
    val verifyStatus: Boolean = false,
    val nickname: String? = null,
    val header: String? = null,
    val level: Int = 0,
    val sign: String? = null
) {
    val levelText: String
        get() = if (level > 0) "Lv.$level" else ""
}

@Serializable
data class CommentReplyItem(
    val id: Long = 0L,
    val content: String? = null,
    val status: Int = 0,
    val addTime: String? = null,
    val addDateString: String? = null,
    val user: CommentUser? = null,
    /** 被回复的目标用户（直接回复主楼时为空） */
    val toUser: CommentUser? = null,
    /** 服务端渲染后的富文本正文（可空，缺失时用 content） */
    val htmlContent: String? = null,
    val emoticon: JsonElement? = null
) {
    /** 纯文本回复内容（优先服务端富文本，脱除 HTML 标签与回车格式化） */
    val contentCleanText: String
        get() {
            val raw = htmlContent?.ifBlank { null } ?: content
            if (raw.isNullOrBlank()) return ""
            return raw
                .replace(Regex("""(?i)<br\s*/?>"""), "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
        }

    val emoticonUrls: List<String>
        get() = parseEmoticonUrls(emoticon)
}

// ── Wave 1 契约桩：手办条目模型 ───────────────────────────────
// 与老项目 `HobbyModels.kt` 同名同字段。Wave 2 删除本文件。
// 注意：displayRatingText 的 String.format(Locale) 为 JVM-only，此处用纯 Kotlin 实现 "%.2f" 语义。

@Serializable
data class HobbyListData(
    val list: List<HobbyItemSummary>? = null
)

@Serializable
data class HobbyItemSummary(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val itemNodeId: Long = 0L,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val categoryId: Int = 0,
    val categoryName: String? = null,
    val price: String? = null,
    val money: Double? = null,
    val currency: String? = null,
    val tax: Int? = null,
    val moneyString: String? = null,
    val releaseDate: String? = null,
    val addTime: String? = null,
    val companyName: String? = null,
    val hits: Long = 0L,
    val hits7Day: Long = 0L,
    val hitsDay: Long = 0L,
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val typeName: String? = null
) {
    val actualItemId: Long
        get() = if (itemId > 0L) itemId else if (id > 0L) id else itemNodeId

    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: ""

    val displayHitsText: String
        get() {
            val count = if (hits7Day > 0) hits7Day else if (hits > 0) hits else hitsDay
            return when {
                count >= 10000 -> "点击 ${(count / 1000) / 10.0}万"
                count > 0 -> "点击 $count"
                else -> ""
            }
        }

    val displayRatingText: String
        get() = if (ratingCount > 0) {
            formatFixed2(rating / ratingCount)
        } else if (rating in 0.1..5.0) {
            formatFixed2(rating)
        } else {
            ""
        }

    val displayReleaseDateText: String
        get() {
            val raw = releaseDate?.ifBlank { null } ?: addTime?.ifBlank { null } ?: return ""
            val clean = raw.substringBefore(" ").replace("-", "/")
            val parts = clean.split("/")
            return if (parts.size >= 2) {
                val year = parts[0]
                val month = parts[1].trimStart('0').ifEmpty { "0" }
                "$year/$month"
            } else {
                clean
            }
        }

    val displayPriceText: String
        get() = com.nobody.repoi.util.PriceFormatter.format(
            money = money,
            currency = currency,
            tax = tax,
            price = price,
            moneyString = moneyString
        )
}

@Serializable
data class HobbyDetailPayload(
    @SerialName("itemData") val itemData: HobbyDetailData? = null
)

@Serializable
data class HobbyDetailData(
    val id: Long = 0L,
    val nodeId: Long = 0L,
    val itemId: Long = 0L,
    val itemType: String? = null,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val hits: Int = 0,
    val collect: Int = 0,
    val commentCount: Int = 0,
    val detail: String? = null,
    val categoryId: Int? = null,
    val price: String? = null,
    val money: Double? = null,
    val currency: String? = null,
    val tax: Int? = null,
    val moneyString: String? = null,
    val releaseDate: String? = null,
    val maker: String? = null,
    val scale: String? = null,
    val height: String? = null,
    val praiseCount: Int = 0,
    val collectCount: Int = 0
) {
    /** 节点 ID（Hpoi 接口在 itemData 中返回 id 即为 nodeId） */
    val actualNodeId: Long
        get() = if (nodeId > 0L) nodeId else id

    /** 条目 ID（Hpoi 接口中为 itemId） */
    val actualItemId: Long
        get() = if (itemId > 0L) itemId else id

    /** 优先显示中文名，若无则显示日文/外文名 */
    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: ""

    /** 真实计算评分（满分 5.0 分，四舍五入保留一位小数） */
    val ratingScore: Double
        get() = if (ratingCount > 0) {
            val score = rating / ratingCount
            kotlin.math.round(score * 10.0) / 10.0
        } else 0.0

    val ratingCountText: String
        get() = if (ratingCount > 0) "($ratingCount 人评分)" else ""

    val hitsText: String
        get() = if (hits > 0) "浏览 $hits" else ""

    val collectText: String
        get() = if (collect > 0) "收藏 $collect" else ""

    val commentCountText: String
        get() = if (commentCount > 0) "评论 $commentCount" else ""

    /** 统一格式化手办定价文本（例如 "15000日元(含税）"、"价格待定"） */
    val displayPriceText: String
        get() = com.nobody.repoi.util.PriceFormatter.format(
            money = money,
            currency = currency,
            tax = tax,
            price = price,
            moneyString = moneyString
        )

    /** 统一格式化手办发售时间（对齐原版：无发售时间时兜底为 "时间待定"） */
    val displayReleaseDateText: String
        get() {
            val raw = releaseDate?.trim()?.ifBlank { null } ?: return "时间待定"
            val clean = raw.substringBefore(" ").replace("-", "/")
            val parts = clean.split("/")
            return if (parts.size >= 2) {
                val year = parts[0]
                val month = parts[1].trimStart('0').ifEmpty { "0" }
                "$year/$month"
            } else {
                clean
            }
        }

    /** 纯文本详情介绍（去除 HTML 标签，转换换行符） */
    val detailPlainText: String
        get() {
            if (detail.isNullOrBlank()) return ""
            return detail
                .replace(Regex("""(?i)<br\s*/?>"""), "\n")
                .replace(Regex("""(?i)</p>"""), "\n\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
        }
}

/**
 * 纯 Kotlin 实现 `"%.2f"`（Locale.CHINA）语义：保留两位小数，四舍五入。
 * 替代 JVM-only 的 `String.format`。
 */
internal fun formatFixed2(value: Double): String {
    val scaled = kotlin.math.round(value * 100)
    val negative = scaled < 0
    val abs = kotlin.math.abs(scaled)
    val intPart = abs / 100
    val fracPart = (abs % 100).toInt().toString().padStart(2, '0')
    return (if (negative) "-" else "") + "$intPart.$fracPart"
}
