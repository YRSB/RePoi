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

const val SHIELDED_COMMENT_TEXT = "(已屏蔽)"

internal fun stripReplyPrefix(raw: String?, toNickname: String? = null): String {
    if (raw.isNullOrBlank()) return ""
    var result = raw.trim()

    result = result.replace(
        Regex("(?i)^\\s*(?:&lt;|<)span(?:&gt;|>)\\s*回复\\s*(?:&lt;|<)a\\b[^>]*?(?:&gt;|>).*?(?:&lt;|<)/a(?:&gt;|>)\\s*(?:&lt;|<)/span(?:&gt;|>)\\s*[:：]?\\s*"),
        ""
    )
    result = result.replace(
        Regex("(?i)^\\s*(?:&lt;|<)span(?:&gt;|>)\\s*回复\\s*(?:&lt;|<)a\\b[^>]*?(?:&gt;|>).*?(?:&lt;|<)/a(?:&gt;|>)\\s*[:：]?\\s*(?:&lt;|<)/span(?:&gt;|>)\\s*"),
        ""
    )

    result = result.replace(
        Regex("(?i)^\\s*(?:&lt;|<)a\\b[^>]*?(?:&gt;|>)\\s*回复.*?(?:&lt;|<)/a(?:&gt;|>)\\s*[:：]?\\s*"),
        ""
    )

    result = result.replace(
        Regex("(?i)^\\s*回复\\s*(?:&lt;|<)a\\b[^>]*?(?:&gt;|>).*?(?:&lt;|<)/a(?:&gt;|>)\\s*[:：]?\\s*"),
        ""
    )

    if (!toNickname.isNullOrBlank()) {
        val cleanName = toNickname.trim().removePrefix("@").removeSuffix(":").removeSuffix("：").trim()
        if (cleanName.isNotBlank()) {
            result = result.replace(
                Regex("^\\s*回复\\s*@?${Regex.escape(cleanName)}\\s*[:：]\\s*"),
                ""
            )
        }
    }
    return result.trim()
}

internal fun cleanHtmlToPlainText(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw
        .replace(Regex("(?i)&lt;br\\s*/?&gt;|<br\\s*/?>"), "\n")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("(?i)</?[a-z][a-z0-9]*(?:\\s+[^>]*)?>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .trim()
}

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

    val contentCleanText: String
        get() = cleanHtmlToPlainText(content)

    val emoticonUrls: List<String>
        get() = parseEmoticonUrls(emoticon)

    val isShielded: Boolean
        get() = contentCleanText == SHIELDED_COMMENT_TEXT || isMask == 1
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

    val toUser: CommentUser? = null,

    val htmlContent: String? = null,
    val emoticon: JsonElement? = null
) {

    val contentCleanText: String
        get() {
            val raw = content?.ifBlank { null } ?: htmlContent
            val stripped = stripReplyPrefix(raw, toUser?.nickname)
            return cleanHtmlToPlainText(stripped)
        }

    val emoticonUrls: List<String>
        get() = parseEmoticonUrls(emoticon)

    val isShielded: Boolean
        get() = contentCleanText == SHIELDED_COMMENT_TEXT
}

@Serializable
data class CommentReplyPayload(
    val comment: CommentItem? = null,
    val list: List<CommentReplyItem>? = null
)

@Serializable
data class CommentThreadPage(
    val comment: CommentItem? = null,
    val replies: List<CommentReplyItem> = emptyList()
)

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
