package com.nobody.poishelf.data.remote.model

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
import com.nobody.poishelf.util.PriceFormatter
import com.nobody.poishelf.util.parseEmoticonUrls

// ── 2. 手办与条目详情模型 ─────────────────────────────

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
            PriceFormatter.formatFixed(rating / ratingCount, 2)
        } else if (rating in 0.1..5.0) {
            PriceFormatter.formatFixed(rating, 2)
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
        get() = PriceFormatter.format(
            money = money,
            currency = currency,
            tax = tax,
            price = price,
            moneyString = moneyString
        )
}

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
            Math.round(score * 10.0) / 10.0
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
        get() = PriceFormatter.format(
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
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
                .replace(Regex("(?i)</p>"), "\n\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
        }
}

// ── 2.1 发售情报时间轴模型 ─────────────────────────

@Serializable
data class HobbyTimelinePayload(
    val list: List<HobbyTimelineItem>? = null
)

@Serializable
data class HobbyTimelineItem(
    val actionData: String? = null,
    val actionComment: String? = null,
    val actionDateString: String? = null
) {
    /** 映射情报事件类型的中文语义化标签 */
    val displayTypeLabel: String
        get() = when (actionData) {
            "preorder" -> "开订时间"
            "delay" -> "出荷延期"
            "confirm" -> "制作决定"
            "release" -> "出荷时间"
            "reorder" -> "再贩确定"
            "official_pic" -> "官图更新"
            else -> "情报更新"
        }
}

@Serializable
data class InfoListPayload(
    val list: List<InfoListEntry>? = null
)

@Serializable
data class InfoListTag(
    val nofollow: Boolean = false,
    val link: String? = null,
    val value: String? = null
)

@Serializable
data class InfoListEntry(
    val type: Int = 1,
    val key: String? = null,
    val title: String? = null,
    val value: JsonElement? = null,
    val link: String? = null,
    val cover: String? = null
) {
    /** 字段展示标签名称（优先 key，如“中文名”、“定价”、“比例”、“出货日”） */
    val displayKey: String
        get() = key?.ifBlank { null } ?: title ?: ""

    /** 是否为可点击跳转的创作者/厂商链接 */
    val isNavigatable: Boolean
        get() = type == 2 && !link.isNullOrBlank()

    /** 是否为属性标签列表 */
    val isTagList: Boolean
        get() = type == 4

    /** 是否为标签/属性组（属性、兼售材质等） */
    val isTagGroup: Boolean
        get() = type == 4 || displayKey.contains("属性") || displayKey.contains("兼售材质") || displayKey.contains("标签")

    /** 格式化显示的值（支持纯文本、标签数组、字符串数组的多态提取） */
    val displayValue: String
        get() {
            if (value == null) return ""
            if (value is JsonPrimitive) {
                return value.content.trim()
            }
            if (value is kotlinx.serialization.json.JsonArray) {
                val items = value.mapNotNull { element ->
                    when (element) {
                        is JsonPrimitive -> element.content.trim().ifBlank { null }
                        is kotlinx.serialization.json.JsonObject -> {
                            val v = element["value"]
                            if (v is JsonPrimitive) v.content.trim().ifBlank { null } else null
                        }
                        else -> null
                    }
                }
                if (items.isNotEmpty()) {
                    return items.joinToString(" · ")
                }
            }
            return value.toString().trim('"', ' ')
        }

    val tags: List<String>
        get() = tagLinks.mapNotNull { it.value?.trim()?.ifBlank { null } }.ifEmpty {
            if (isTagGroup && value is JsonPrimitive) {
                value.content.split(",", "，", "·", "/").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }
        }

    val tagLinks: List<InfoListTag>
        get() {
            if (value !is kotlinx.serialization.json.JsonArray) return emptyList()
            return value.mapNotNull { element ->
                when (element) {
                    is JsonPrimitive -> element.content.trim().ifBlank { null }?.let { InfoListTag(value = it) }
                    is kotlinx.serialization.json.JsonObject -> {
                        val rawValue = element["value"] as? JsonPrimitive
                        val text = rawValue?.content?.trim()?.ifBlank { null } ?: return@mapNotNull null
                        val linkElement = element["link"] as? kotlinx.serialization.json.JsonPrimitive
                        val link = if (linkElement?.isString == true) linkElement.content.trim().ifBlank { null } else null
                        val nofollow = (element["nofollow"] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull ?: false
                        InfoListTag(nofollow = nofollow, link = link, value = text)
                    }
                    else -> null
                }
            }
        }

    /** 多行文本列表（针对发售历程、版本数组等） */
    val lines: List<String>
        get() {
            if (value !is kotlinx.serialization.json.JsonArray) {
                val str = displayValue
                return if (str.isNotBlank()) listOf(str) else emptyList()
            }
            return value.mapNotNull { element ->
                when (element) {
                    is JsonPrimitive -> element.content.trim().ifBlank { null }
                    is kotlinx.serialization.json.JsonObject -> {
                        val v = element["value"]
                        if (v is JsonPrimitive) v.content.trim().ifBlank { null } else null
                    }
                    else -> null
                }
            }
        }
}
