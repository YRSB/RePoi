package com.nobody.repoi.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.nobody.repoi.platform.DateTimeFormats

@Serializable
data class UnreadPushCountPayload(
    val map: UnreadPushCountMap? = null
)

@Serializable
data class UnreadPushCountMap(
    val push: PushCountCategory? = null
)

@Serializable
data class PushCountCategory(
    val system: Int = 0,
    val reply: Int = 0,
    val praise: Int = 0,
    val recommend: Int = 0,
    @SerialName("follownews") val followNews: Int = 0,
    val follow: Int = 0,
    val commodity: Int = 0,
    @SerialName("posts_reply") val postsReply: Int = 0
) {

    val actualFollowCount: Int
        get() = if (followNews > 0) followNews else follow

    val totalCount: Int
        get() = system + reply + praise + recommend + actualFollowCount + commodity
}

@Serializable
data class MessageContactPayload(
    val contact: List<MessageContactItem> = emptyList()
)

@Serializable
data class MessageContactItem(
    val id: Long = 0L,
    val otherId: Long = 0L,
    val otherName: String = "",
    val otherHeader: String? = null,
    val lastTime: String? = null,
    val lastContent: String? = null,
    val unreadCount: Int = 0,
    val topTime: String? = null,
    val type: Int = 0
) {

    val isTop: Boolean
        get() = !topTime.isNullOrBlank()
}

@Serializable
data class PushMessagePayload(
    val pushMessage: List<PushMessageItem> = emptyList()
)

@Serializable
data class PushMessageItem(
    val id: Long = 0L,
    val type: Int = 0,
    val state: Int = 0,
    val title: String? = null,
    val date: String? = null,
    val content: String? = null,
    val params: String? = null,
    val dataMap: PushMessageDataMap? = null
) {
    val isUnread: Boolean
        get() = state == 0
}

@Serializable
data class PushMessageDataMap(
    val header: String? = null,
    val nickname: String? = null,
    val itemType: String? = null,
    val relateItemId: Long? = null,
    val relateItemType: String? = null,
    val relateNodeId: Long? = null,
    val nodeId: Long? = null,
    val commentId: Long? = null,
    val comment: String? = null,
    val reply: String? = null,
    val link: String? = null,
    val orderId: String? = null,
    val userId: Long? = null,
    val title: String? = null,
    val content: String? = null,
    val couponsId: String? = null,
    val systemType: Int? = null
)

@Serializable
data class ChatMessagePayload(
    val direction: Int = 0,
    val strangerMessageSetting: Int = 1,
    val canSendState: Int = 1,
    val messages: List<ChatMessageItem> = emptyList()
)

@Serializable
data class ChatMessageItem(
    val messageId: Long = 0L,
    val id: Long = 0L,
    val fromId: Long = 0L,
    val toId: Long = 0L,
    val content: String = "",
    @Serializable(with = MessageLenientDateTimeToEpochSerializer::class)
    val date: Long = 0L,
    val type: Int = 1,
    val emoticon: String? = null,
    val dataMap: ChatMessageDataMap? = null
) {

    val actualMessageId: Long
        get() = if (messageId > 0L) messageId else id

    val isPicture: Boolean
        get() = type == 8
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object MessageLenientDateTimeToEpochSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("MessageLenientDateTimeToEpoch", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }

    override fun deserialize(decoder: Decoder): Long {
        val jsonDecoder = decoder as? JsonDecoder ?: return runCatching { decoder.decodeLong() }.getOrDefault(0L)
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return 0L
        val content = (element as? JsonPrimitive)?.contentOrNull?.trim() ?: return 0L
        if (content.isEmpty() || content.equals("null", ignoreCase = true)) return 0L

        content.toLongOrNull()?.let { num ->
            return when {
                num > 100_000_000_000L -> num
                num > 1_000_000_000L -> num * 1000L
                else -> num
            }
        }

        DateTimeFormats.parseServerDateTime(content)?.let { return it }
        return 0L
    }
}

@Serializable
data class ChatMessageDataMap(
    val comment: String? = null,
    val itemType: String? = null,
    val resaleId: Long? = null,
    val title: String? = null,
    val pic: String? = null,
    val price: Double? = null
)

@Serializable
data class LatestMsgIdPayload(
    val latestMsgId: Long = 0L
)

@Serializable
data class BlockListPayload(
    val userBlock: List<BlockItem> = emptyList()
)

@Serializable
data class BlockItem(
    val id: Long = 0L,
    val name: String = "",
    val header: String? = null,
    val intro: String? = null,
    val blockState: Int = 1
)
