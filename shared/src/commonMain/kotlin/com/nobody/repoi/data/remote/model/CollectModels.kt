package com.nobody.repoi.data.remote.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.nobody.repoi.platform.DateTimeFormats

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, kotlinx.serialization.InternalSerializationApi::class)
object EmbeddedJsonSerializer : KSerializer<JsonElement?> {
    override val descriptor: SerialDescriptor =
        JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: JsonElement?) {
        if (value == null) encoder.encodeNull() else JsonElement.serializer().serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): JsonElement? {
        val input = decoder as? JsonDecoder ?: return null
        val element = input.decodeJsonElement()
        if (element is JsonNull) return null

        if (element is JsonPrimitive && element.isString) {
            val raw = element.content
            if (raw.isBlank()) return null
            return runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: element
        }
        return element
    }
}

@Serializable
data class CollectStatePayload(
    val collect: CollectData? = null
)

@Serializable
data class CollectCountPayload(
    val collect: CollectCountData? = null
)

@Serializable
data class CollectCountData(
    val care: Int = 0,
    val want: Int = 0,
    val preorder: Int = 0,
    val buy: Int = 0,
    val resell: Int = 0
) {

    val total: Int get() = care + want + preorder + buy + resell
    fun getCountForState(state: String): Int = when (state) {
        CollectConstants.STATE_CARE -> care
        CollectConstants.STATE_WANT -> want
        CollectConstants.STATE_PREORDER -> preorder
        CollectConstants.STATE_BUY -> buy
        CollectConstants.STATE_RESELL -> resell
        else -> 0
    }
}

@Serializable
data class CollectTagsPayload(
    val favTags: List<String> = emptyList()
)

@Serializable
data class CollectData(
    val id: Long? = null,
    val collectId: Long? = null,
    val state: String? = null,
    val actionDate: String? = null,
    val rating: Int = 0,
    val orderTime: String? = null,
    val orderWay: String? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val orderMoney: Double? = null,
    @Serializable(with = LenientIntSerializer::class)
    val buyNum: Int? = null,
    val orderMoneyCurrency: String? = null,
    val orderForm: Int = 0,
    val comment: String? = null,
    val textTags: List<CollectTagItem> = emptyList(),
    val praiseCount: Int = 0
) {

    val isCollected: Boolean
        get() = !state.isNullOrBlank() && state != CollectConstants.STATE_DELETE
}

@Serializable
data class CollectTagItem(
    val tagValue: String = ""
)

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object LenientDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value != null) encoder.encodeDouble(value) else encoder.encodeNull()
    }

    override fun deserialize(decoder: Decoder): Double? {
        val jsonDecoder = decoder as? JsonDecoder ?: return runCatching { decoder.decodeDouble() }.getOrNull()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return null
        val content = (element as? JsonPrimitive)?.contentOrNull ?: return null
        return content.toDoubleOrNull()
    }
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object LenientIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientInt", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value != null) encoder.encodeInt(value) else encoder.encodeNull()
    }

    override fun deserialize(decoder: Decoder): Int? {
        val jsonDecoder = decoder as? JsonDecoder ?: return runCatching { decoder.decodeInt() }.getOrNull()
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return null
        val content = (element as? JsonPrimitive)?.contentOrNull ?: return null
        return content.toIntOrNull()
    }
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object LenientLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientLong", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeLong(value)
    }

    override fun deserialize(decoder: Decoder): Long {
        val jsonDecoder = decoder as? JsonDecoder ?: return runCatching { decoder.decodeLong() }.getOrDefault(0L)
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return 0L
        val content = (element as? JsonPrimitive)?.contentOrNull ?: return 0L
        return content.toLongOrNull() ?: 0L
    }
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object LenientDateTimeToEpochSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientDateTimeToEpoch", PrimitiveKind.LONG)

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

object CollectConstants {
    const val STATE_CARE = "care"
    const val STATE_WANT = "want"
    const val STATE_PREORDER = "preorder"
    const val STATE_BUY = "buy"
    const val STATE_RESELL = "resell"
    const val STATE_DELETE = "delete"

    val ORDERED_STATES = listOf(
        STATE_CARE,
        STATE_WANT,
        STATE_PREORDER,
        STATE_BUY,
        STATE_RESELL
    )

    const val ORDER_ACTION_DATE = "actionDate"

    const val ORDER_TIME = "orderTime"

    const val ORDER_ADD_TIME = "addTime"

    const val ORDER_PRAISE_COUNT = "praiseCount"

    const val ORDER_COLLECT = "collect"

    const val ORDER_RELEASE = "release"

    const val ORDER_RATING = "rating"

    const val ORDER_MY_RATING = "myRating"

    const val ORDER_UPDATE_DATE = "updateDate"

    const val CATEGORY_ALL = 10000

    val WORKS_STATES = listOf(1, 2, 3)

    const val WORKS_ITEM_TYPE = "Works"

    const val REPORT_TYPE_PICTURE = "picture_info"

    const val REPORT_TYPE_RESALE = "resale_item"

    fun getStateLabel(state: String): String = when (state) {
        STATE_CARE -> "关注"
        STATE_WANT -> "想买"
        STATE_PREORDER -> "预定"
        STATE_BUY -> "已入"
        STATE_RESELL -> "有过"
        else -> "未知"
    }

    val ORDER_FORMS = listOf(
        1 to "预定",
        2 to "现货",
        3 to "二手",
        4 to "赠品",
        0 to "其他"
    )

    val CURRENCIES = listOf(
        "rmb" to "人民币",
        "yen" to "日元",
        "dollar" to "美元",
        "euro" to "欧元",
        "hkd" to "港元"
    )

    fun getCurrencyLabel(code: String?): String {
        return CURRENCIES.firstOrNull { it.first == code }?.second ?: "人民币"
    }

    fun getRatingLabel(rating: Int): String = when (rating) {
        1 -> "邪神"
        2 -> "微妙"
        3 -> "眼缘"
        4 -> "满足"
        5 -> "神物"
        else -> "未评"
    }

    val COMMON_ORDERS = listOf(ORDER_ACTION_DATE, ORDER_COLLECT)

    val MEDIA_ORDERS = listOf(ORDER_ADD_TIME, ORDER_ACTION_DATE, ORDER_PRAISE_COUNT)

    val HOBBY_ORDERS = listOf(ORDER_ACTION_DATE, ORDER_RELEASE, ORDER_RATING, ORDER_MY_RATING, ORDER_UPDATE_DATE)

    fun normalizeCommonOrder(order: String): String =
        order.trim().takeIf { it in COMMON_ORDERS } ?: ORDER_ACTION_DATE

    fun normalizeMediaOrder(order: String): String =
        order.trim().takeIf { it in MEDIA_ORDERS } ?: ORDER_ADD_TIME

    fun normalizeHobbyOrder(order: String, collectState: String): String {
        val trimmed = order.trim()
        if (trimmed == ORDER_TIME) {
            return if (collectState == STATE_BUY) ORDER_TIME else ORDER_ACTION_DATE
        }
        return trimmed.takeIf { it in HOBBY_ORDERS } ?: ORDER_ACTION_DATE
    }
}

@Serializable
data class WorksCollectData(
    val id: Long = 0L,
    val itemId: Long = 0L,

    val itemType: String? = null,

    val state: Int = 0,

    val current: Int = 0,

    val comment: String? = null,

    val textTags: List<CollectTagItem> = emptyList(),

    val actionDate: String? = null
) {

    val actualItemId: Long get() = if (itemId > 0L) itemId else id

    val isCollected: Boolean get() = state in CollectConstants.WORKS_STATES

    val stateText: String
        get() = when (state) {
            1 -> "想看"
            2 -> "在看"
            3 -> "看过"
            else -> "未收藏"
        }

    val tagValues: List<String>
        get() = textTags.mapNotNull { it.tagValue.trim().ifBlank { null } }

    val tagStr: String get() = tagValues.joinToString(",")

    val actualComment: String? get() = comment?.trim()?.ifBlank { null }
}

@Serializable
data class PictureRelatePayload(
    val list: List<PictureRelateData>? = null
)

@Serializable
data class PictureRelateData(
    val relateItem: PictureRelateItem? = null
)

typealias PictureRelateItem = RelateItem
