package com.nobody.poishelf.data.remote.model

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
import com.nobody.poishelf.platform.DateTimeFormats

/**
 * 「对象或转义 JSON 字符串」双形态解析器。
 *
 * Hpoi 的 `api/collect/common-v3` 会把部分子对象序列化成**转义后的 JSON 字符串**，
 * 实测响应中 `names` 即为 `"{\"name_zh\":[\"...\"],...}"` 而不是对象。
 * kotlinx.serialization 默认按声明类型严格解析，遇到字符串会直接抛
 * `Unexpected symbol 'c' in numeric literal`，导致整个收藏页白屏。
 *
 * 因此这些字段统一用本解析器：先按常规对象解析，失败则把字符串内容
 * 再解析一次 JSON。
 */
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
        // 双引号字符串 → 把内容再当 JSON 解析一次
        if (element is JsonPrimitive && element.isString) {
            val raw = element.content
            if (raw.isBlank()) return null
            return runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: element
        }
        return element
    }
}

/**
 * 查询条目收藏状态响应载荷 (api/collect/state)
 */
@Serializable
data class CollectStatePayload(
    val collect: CollectData? = null
)

/**
 * 查询条目收藏统计人数响应载荷 (api/hobby/collectCount)
 */
@Serializable
data class CollectCountPayload(
    val collect: CollectCountData? = null
)

/**
 * 5 种收藏分类全站人数统计数据
 */
@Serializable
data class CollectCountData(
    val care: Int = 0,
    val want: Int = 0,
    val preorder: Int = 0,
    val buy: Int = 0,
    val resell: Int = 0
) {
    fun getCountForState(state: String): Int = when (state) {
        CollectConstants.STATE_CARE -> care
        CollectConstants.STATE_WANT -> want
        CollectConstants.STATE_PREORDER -> preorder
        CollectConstants.STATE_BUY -> buy
        CollectConstants.STATE_RESELL -> resell
        else -> 0
    }
}

/**
 * 候选推荐标签响应载荷 (api/collect/tag)
 */
@Serializable
data class CollectTagsPayload(
    val favTags: List<String> = emptyList()
)

/**
 * 核心收藏详情数据实体
 */
@Serializable
data class CollectData(
    val id: Long? = null,
    val collectId: Long? = null,
    val state: String? = null, // "care", "want", "preorder", "buy", "resell", "delete"
    val actionDate: String? = null,
    val rating: Int = 0,
    val orderTime: String? = null,
    val orderWay: String? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val orderMoney: Double? = null,
    @Serializable(with = LenientIntSerializer::class)
    val buyNum: Int? = null,
    val orderMoneyCurrency: String? = null, // "rmb", "yen", "dollar", "euro", "hkd"
    val orderForm: Int = 0,
    val comment: String? = null,
    val textTags: List<CollectTagItem> = emptyList(),
    val praiseCount: Int = 0
) {
    /** 判断该条目当前是否处于已收藏有效状态 */
    val isCollected: Boolean
        get() = !state.isNullOrBlank() && state != CollectConstants.STATE_DELETE
}

/**
 * 收藏标签项
 */
@Serializable
data class CollectTagItem(
    val tagValue: String = ""
)

/**
 * 容错 Double 序列化器，兼容数字、字符串及 null
 */
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

/**
 * 容错 Int 序列化器，兼容数字、字符串及 null
 */
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

/**
 * 容错 Long 序列化器，兼容数字、字符串及 null，异常回退默认值 0L
 */
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

/**
 * 容错时间戳序列化器：支持毫秒/秒级数字时间戳，以及形如 "yyyy-MM-dd HH:mm:ss" 的日期字符串，
 * 统一反序列化为毫秒级时间戳 (Long)。
 */
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

        // 1. 优先尝试纯数字解析（兼容量级：毫秒/秒）
        content.toLongOrNull()?.let { num ->
            return when {
                num > 100_000_000_000L -> num
                num > 1_000_000_000L -> num * 1000L
                else -> num
            }
        }

        // 2. 尝试标准日期时间格式解析（kotlinx-datetime 通用实现）
        DateTimeFormats.parseServerDateTime(content)?.let { return it }
        return 0L
    }
}

/**
 * 收藏业务相关枚举常量与映射工具
 */
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

    // ── 排序 key 常量（出处：报告附录 B，对齐原版各页排序文案表） ──

    /** 收藏时间 */
    const val ORDER_ACTION_DATE = "actionDate"
    /** 入手时间（⚠️ 周边页仅「已入(buy)」档可用） */
    const val ORDER_TIME = "orderTime"
    /** 发布时间（相册/图片默认排序） */
    const val ORDER_ADD_TIME = "addTime"
    /** 点赞量 */
    const val ORDER_PRAISE_COUNT = "praiseCount"
    /** 人气（通用收藏页排序） */
    const val ORDER_COLLECT = "collect"
    /** 发售时间 */
    const val ORDER_RELEASE = "release"
    /** 全站评分 */
    const val ORDER_RATING = "rating"
    /** 我的评分 */
    const val ORDER_MY_RATING = "myRating"
    /** 录入时间 */
    const val ORDER_UPDATE_DATE = "updateDate"

    /** 周边分类「全部」的分类 ID（原版用 10000 而非 null） */
    const val CATEGORY_ALL = 10000

    /** 作品收藏三态：想看 / 在看 / 看过 */
    val WORKS_STATES = listOf(1, 2, 3)

    /** 作品收藏条目的 itemType 固定值（原版硬编码字符串 "Works"） */
    const val WORKS_ITEM_TYPE = "Works"

    // ── 举报对象类型（api/web-report/add 的 type 字段） ──

    /** 图片信息举报（大图画廊入口，原版 `PictureGalleryActivity.e0()` 传 "picture_info"） */
    const val REPORT_TYPE_PICTURE = "picture_info"

    /** 二手交易举报（原版 `ResaleReportActivity.java:125` 传 "resale_item"） */
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

    // ── 收藏页排序值域白名单（出处见报告附录 B，对齐原版各页排序文案表） ──

    /** 通用收藏页（厂商/系列/角色/人物）排序白名单：收藏时间 / 人气 */
    val COMMON_ORDERS = listOf(ORDER_ACTION_DATE, ORDER_COLLECT)

    /** 相册 / 图片排序白名单：发布时间 / 收藏时间 / 点赞量 */
    val MEDIA_ORDERS = listOf(ORDER_ADD_TIME, ORDER_ACTION_DATE, ORDER_PRAISE_COUNT)

    /** 周边排序白名单：收藏时间 / 发售时间 / 全站评分 / 我的评分 / 录入时间 */
    val HOBBY_ORDERS = listOf(ORDER_ACTION_DATE, ORDER_RELEASE, ORDER_RATING, ORDER_MY_RATING, ORDER_UPDATE_DATE)

    /** 通用收藏页排序收敛：不在白名单内一律回落收藏时间 */
    fun normalizeCommonOrder(order: String): String =
        order.trim().takeIf { it in COMMON_ORDERS } ?: ORDER_ACTION_DATE

    /** 相册 / 图片排序收敛：不在白名单内一律回落发布时间 */
    fun normalizeMediaOrder(order: String): String =
        order.trim().takeIf { it in MEDIA_ORDERS } ?: ORDER_ADD_TIME

    /**
     * 周边排序收敛：不在白名单内一律回落收藏时间。
     * ⚠️ [ORDER_TIME]（入手时间）语义上仅「已入(buy)」档可用，
     * 其余状态档原版会主动回落收藏时间（`UserCollectHobbyFragment.java:779-781`）。
     */
    fun normalizeHobbyOrder(order: String, collectState: String): String {
        val trimmed = order.trim()
        if (trimmed == ORDER_TIME) {
            return if (collectState == STATE_BUY) ORDER_TIME else ORDER_ACTION_DATE
        }
        return trimmed.takeIf { it in HOBBY_ORDERS } ?: ORDER_ACTION_DATE
    }
}

// ── 作品收藏写侧模型 (api/works/glean/*、api/user/glean/textTag/get) ─────────

/**
 * 作品收藏详情 (api/works/glean/get/{worksId})。
 *
 * ⚠️ 不同服务端版本存在两种响应形态：把收藏详情包在 `glean` 键内
 * （原版 `RequestCollectWorksViewModel` 传了 `"glean"` 作为取值键），或直接铺平返回。
 * 因此接口层统一声明为 `JsonElement`，由仓储层 `CollectRepository.getWorksCollect` 做两种形态兼容。
 *
 * ⚠️ 作品标签池 `api/user/glean/textTag/get` 同样返回 `JsonElement`：
 * 原版 `UserCollectWorksSubFragment.java:133` 读取 `data.textTagList`，
 * 而 `CollectWorksActivity.java:373` 又把同一响应当作 JSONArray 使用，
 * 数组元素还可能是纯字符串或 `{ "tagValue": "..." }` 对象，故不在 DTO 层强行定型。
 */
@Serializable
data class WorksCollectData(
    val id: Long = 0L,
    val itemId: Long = 0L,
    /** 条目类型，作品收藏恒为 Works */
    val itemType: String? = null,
    /** 收藏状态：1 想看 / 2 在看 / 3 看过 */
    val state: Int = 0,
    /** 观看/阅读进度（集数、章节），仅部分作品类型有意义 */
    val current: Int = 0,
    /** 用户备注 */
    val comment: String? = null,
    /** 收藏文本标签 */
    val textTags: List<CollectTagItem> = emptyList(),
    /** 收藏时间 */
    val actionDate: String? = null
) {
    /** 作品条目 ID（写侧接口使用，优先 itemId） */
    val actualItemId: Long get() = if (itemId > 0L) itemId else id

    /** 是否已处于有效收藏态（state 落在想看/在看/看过三态内） */
    val isCollected: Boolean get() = state in CollectConstants.WORKS_STATES

    /** 收藏状态文案，1 想看 / 2 在看 / 3 看过 */
    val stateText: String
        get() = when (state) {
            1 -> "想看"
            2 -> "在看"
            3 -> "看过"
            else -> "未收藏"
        }

    /** 标签值列表（写侧回传 tagStr 用，英文逗号分隔） */
    val tagValues: List<String>
        get() = textTags.mapNotNull { it.tagValue.trim().ifBlank { null } }

    /** 写侧提交用的标签串（英文逗号分隔，对齐原版 `CollectWorksActivity.Y()`） */
    val tagStr: String get() = tagValues.joinToString(",")

    val actualComment: String? get() = comment?.trim()?.ifBlank { null }
}

// ── 图片关联条目模型 (api/pic/relate) ──────────────────────────────────────

/**
 * 图片关联条目响应载荷 (api/pic/relate)。
 * 原版 `PictureGalleryActivity.java:294-300` 逐项取 `relateItem`，
 * 类型覆盖相册(album)、手办(hobby)、文章(article) 三类。
 */
@Serializable
data class PictureRelatePayload(
    val list: List<PictureRelateData>? = null
)

/** 关联条目外层数据，仅承载内层实体 */
@Serializable
data class PictureRelateData(
    val relateItem: PictureRelateItem? = null
)

/**
 * 图片关联条目实体，结构与信息流的 [RelateItem] 完全一致（同一套 `relateItem` 契约），故以别名复用。
 */
typealias PictureRelateItem = RelateItem
