package com.nobody.repoi.data.remote.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.roundToInt

/**
 * 单个专区信息条目（用于关注列表、分组专区及首页 Tab 展示）。
 */
@Serializable
data class ZoneItem(
    val nodeId: Long = 0L,
    val name: String? = null,
    val nameCN: String? = null,
    val icon: String? = null,
    val cover: String? = null,
    val webCover: String? = null,
    val description: String? = null
) {
    /** 优先返回中文名称，若为空则回退到原生名称或“专区” */
    val displayName: String
        get() = nameCN?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: "专区"
}

/** 兼容旧命名别名 */
typealias ZoneItemDto = ZoneItem

/**
 * 专区分组（例如「我关注的」、「活动/推荐专区」、「模玩产品」等）。
 */
@Serializable
data class ZoneGroup(
    val name: String = "",
    val zones: List<ZoneItem> = emptyList()
)

/** 兼容旧命名别名 */
typealias ZoneGroupDto = ZoneGroup

/**
 * 首页动态/相册专区设置。
 * 真实下发结构：
 * - hiddenZoneIds: List<Long>（隐藏专区的 nodeId 数组）
 * - customOrder: List<Long>（自定义排序下的 nodeId 顺序数组）
 * - recentZoneIds: List<Long>（最近访问排序下的 nodeId 顺序数组）
 */
@Serializable
data class ZoneTabsSettings(
    val showAll: Boolean = true,
    val sort: String = "default",
    val hiddenZoneIds: List<Long> = emptyList(),
    val customOrder: List<Long> = emptyList(),
    val recentZoneIds: List<Long> = emptyList()
)

/** 兼容旧命名别名 */
typealias ZoneTabsSettingsDto = ZoneTabsSettings

/**
 * api/zone/home-tabs 接口响应 Payload。
 */
@Serializable
data class ZoneHomeTabsPayload(
    val settings: ZoneTabsSettings? = null,
    val focusZones: List<ZoneItem>? = null,
    val homeZones: List<ZoneItem>? = null,
    val groups: List<ZoneGroup>? = null
)

/**
 * 专区详细资料实体（嵌套于 api/zone/info 响应的 "zone" 字段中）。
 */
@Serializable
data class ZoneDetailData(
    val nodeId: Long = 0L,
    val name: String? = null,
    val nameCN: String? = null,
    val desc: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val cover: String? = null,
    val webCover: String? = null,
    val itemTypes: String? = null,
    val communityLinks: String? = null
) {
    val displayName: String
        get() = nameCN?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: "专区"

    val displayDesc: String?
        get() = description?.takeIf { it.isNotBlank() } ?: desc?.takeIf { it.isNotBlank() }
}

/**
 * 专区 Tab 项（支持类型、标题与条目统计计数，兼容字符串与对象多态）。
 */
@Serializable
data class ZoneTabItem(
    val type: String = "",
    val title: String = "",
    val count: Long = 0L
)

/**
 * 专区 Tab 列表反序列化器，兼容空字符串、空对象、字符串数组与对象数组等多态场景。
 */
object ZoneTabListSerializer : KSerializer<List<ZoneTabItem>> {
    private val delegate = ListSerializer(ZoneTabItem.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<ZoneTabItem> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return delegate.deserialize(decoder)
        val element = jsonDecoder.decodeJsonElement()
        if (element !is JsonArray) return emptyList()
        return element.mapNotNull { itemElement ->
            when (itemElement) {
                is JsonPrimitive -> {
                    val str = itemElement.content
                    if (str.isBlank()) null else ZoneTabItem(type = str, title = str)
                }
                is JsonObject -> {
                    val type = itemElement["type"]?.jsonPrimitive?.contentOrNull ?: ""
                    val title = itemElement["title"]?.jsonPrimitive?.contentOrNull ?: ""
                    val count = itemElement["count"]?.jsonPrimitive?.longOrNull ?: 0L
                    if (type.isBlank()) null else ZoneTabItem(type = type, title = title, count = count)
                }
                else -> null
            }
        }
    }

    override fun serialize(encoder: Encoder, value: List<ZoneTabItem>) {
        delegate.serialize(encoder, value)
    }
}

/**
 * api/zone/info 接口响应 Payload。
 * 真实下发结构：
 * {
 *   "zone": { "nodeId": ..., "name": ..., "nameCN": ..., "desc": ..., "cover": ..., "icon": ... },
 *   "focusState": true/false,
 *   "followCount": 1728,
 *   "tabs": [
 *     { "type": "action", "title": "动态", "count": 44 },
 *     ...
 *   ]
 * }
 */
@Serializable
data class ZoneInfoPayload(
    val zone: ZoneDetailData? = null,
    val focusState: Boolean = false,
    val followCount: Long = 0L,
    @Serializable(with = ZoneTabListSerializer::class)
    val tabs: List<ZoneTabItem> = emptyList()
) {
    val displayName: String
        get() = zone?.displayName ?: "专区"
}

/**
 * api/zone/focus 接口响应 Payload（关注/取消关注）。
 */
@Serializable
data class ZoneFocusPayload(
    val focus: Boolean = false,
    val count: Long = 0L
)

/**
 * 专区关联实体条目（厂商、系列、作品、角色、人物列表，api/zone/items）。
 */
@Serializable
data class ZoneEntityItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val hits: Long = 0L,
    val itemType: String? = null
) {
    /** 目标详情 ID（优先取 itemId，若为 0 回退到 id） */
    val targetId: Long
        get() = if (itemId > 0L) itemId else id

    /** 优先返回中文名称，若为空则回退到原生名称 */
    val displayName: String
        get() = nameCN?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: "未命名"

    /** 浏览热度格式化文本，例如：浏览 12万、浏览 8500 */
    val displayHitsText: String
        get() = when {
            hits >= 10000 -> {
                val value = (hits / 1000.0).roundToInt() / 10.0
                if (value == value.toLong().toDouble()) {
                    "浏览 ${value.toLong()}万"
                } else {
                    "浏览 ${value}万"
                }
            }
            hits > 0 -> "浏览 $hits"
            else -> "浏览 0"
        }
}

/**
 * api/zone/items 响应 Payload。
 */
@Serializable
data class ZoneItemsPayload(
    val total: Int = 0,
    val hasMore: Boolean = false,
    val list: List<ZoneEntityItem> = emptyList()
)
