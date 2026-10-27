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

// ── 3.1 评论区模型 ───────────────────────────────

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
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
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
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
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

// ── 3.2 子评论详情与回复发表模型 ───────────────────────────────

/** 子评论详情页载荷（api/comment/getreply）：主楼快照 + 子评论分页列表 */
@Serializable
data class CommentReplyPayload(
    val comment: CommentItem? = null,
    val list: List<CommentReplyItem>? = null
)

/**
 * 子评论详情分页数据模型：主楼快照 + 子评论列表。
 */
@Serializable
data class CommentThreadPage(
    val comment: CommentItem? = null,
    val replies: List<CommentReplyItem> = emptyList()
)

/** 回复发表结果载荷（api/comment/add/reply）：新建的子评论对象 */
@Serializable
data class CommentReplyAddPayload(
    val reply: CommentReplyItem? = null
)

@Serializable
data class StickerPackPayload(
    val list: List<StickerPackData>? = null,
    val buyTime: String? = null
)

@Serializable
data class StickerPackData(
    val name: String = "",
    val items: List<StickerPackItem>? = null,
    val applyLocation: Int = -1
)

@Serializable
data class StickerPackItem(
    val imageUrl: String = ""
)

@Serializable
data class StickerBuyTimePayload(
    val buyTime: String? = null
)
