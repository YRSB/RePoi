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

    val displayName: String
        get() = nameCN?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: "专区"
}

typealias ZoneItemDto = ZoneItem

@Serializable
data class ZoneGroup(
    val name: String = "",
    val zones: List<ZoneItem> = emptyList()
)

typealias ZoneGroupDto = ZoneGroup

@Serializable
data class ZoneTabsSettings(
    val showAll: Boolean = true,
    val sort: String = "default",
    val hiddenZoneIds: List<Long> = emptyList(),
    val customOrder: List<Long> = emptyList(),
    val recentZoneIds: List<Long> = emptyList()
)

typealias ZoneTabsSettingsDto = ZoneTabsSettings

@Serializable
data class ZoneHomeTabsPayload(
    val settings: ZoneTabsSettings? = null,
    val focusZones: List<ZoneItem>? = null,
    val homeZones: List<ZoneItem>? = null,
    val groups: List<ZoneGroup>? = null
)

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

@Serializable
data class ZoneTabItem(
    val type: String = "",
    val title: String = "",
    val count: Long = 0L
)

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

@Serializable
data class ZoneFocusPayload(
    val focus: Boolean = false,
    val count: Long = 0L
)

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

    val targetId: Long
        get() = if (itemId > 0L) itemId else id

    val displayName: String
        get() = nameCN?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: "未命名"

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

const val ENTITY_BOARD_PAGE_SIZE: Int = 20

@Serializable
data class ZoneItemsPayload(
    val total: Int = 0,
    val hasMore: Boolean = false,
    val list: List<ZoneEntityItem> = emptyList()
)
