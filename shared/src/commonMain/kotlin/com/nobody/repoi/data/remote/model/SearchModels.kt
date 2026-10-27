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

// ── 5. 全局搜索 (Global Search) 模型 ─────────────────────────

@Serializable
data class SearchResponsePayload(
    val result: List<SearchResultItem>? = null,
    val query: SearchQueryInfo? = null,
    val companys: List<SearchCompanyAggregate>? = null,
    val releaseYear: List<SearchYearAggregate>? = null
)

@Serializable
data class SearchQueryInfo(
    val itemType: String? = null,
    val keyword: String? = null,
    val page: Int = 1,
    val pageSize: Int = 30,
    val totalCount: Int = 0,
    val pageCount: Int = 0,
    val category: Int = 10000
)

@Serializable
data class SearchCompanyAggregate(
    val nodeId: Long = 0L,
    val itemName: String? = null,
    val docCount: Int = 0
)

@Serializable
data class SearchYearAggregate(
    val keyAsString: String? = null,
    val docCount: Int = 0
)

@Serializable
data class SearchCategoryRef(
    val id: Long = 0L,
    val name: String? = null,
    val level: Int = 0,
    val categoryId: Int = 0,
    val parentId: Long = 0L
)

@Serializable
data class SearchResultUser(
    val id: Long = 0L,
    val userId: Long = 0L,
    val nickname: String? = null,
    val header: String? = null,
    val sign: String? = null
)

@Serializable
data class SearchResultItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val albumId: Long = 0L,
    val itemNodeId: Long = 0L,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val itemType: String? = null,
    val detail: String? = null,
    val categoryId: Int = 0,
    val category: SearchCategoryRef? = null,
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
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val picCount: Int = 0,
    val user: SearchResultUser? = null,
    val praiseCount: JsonElement? = null
) {
    val actualId: Long
        get() = when {
            itemType == "album" && id > 0L -> id
            itemId > 0L -> itemId
            albumId > 0L -> albumId
            id > 0L -> id
            itemNodeId > 0L -> itemNodeId
            else -> 0L
        }

    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: ""

    val displayPriceText: String
        get() = PriceFormatter.format(
            money = money,
            currency = currency,
            tax = tax,
            price = price,
            moneyString = moneyString
        )

    val categoryBadge: String
        get() = category?.name?.ifBlank { null } ?: ""

    val praiseCountText: String
        get() = when (val p = praiseCount) {
            is JsonPrimitive -> p.content
            else -> ""
        }

    val relativeTimeText: String
        get() {
            val timeStr = addTime?.ifBlank { null } ?: return ""
            return com.nobody.repoi.util.DateTimeFormats.relativeTimeText(timeStr)
        }
}

@Serializable
data class HotwordPayload(
    val list: List<HotwordItem>? = null
)

@Serializable
data class HotwordItem(
    val word: String = "",
    val count: Int = 0
)

@Serializable
data class AttributeListPayload(
    val attributeList: List<AttributeItem>? = null
)

@Serializable
data class AttributeItem(
    val id: Int = 0,
    val name: String = "",
    val categoryId: Int = 0
)

@Serializable
data class AutoFillPayload(
    val keywords: List<String>? = null
)

@Serializable
data class UserSearchPayload(
    val userList: List<SearchUserItem>? = null
)

@Serializable
data class SearchUserItem(
    val userId: Long = 0L,
    val id: Long = 0L,
    val nickname: String? = null,
    val header: String? = null,
    val sign: String? = null,
    val def: Int = 0,
    val hp: Int = 0,
    val atk: Int = 0
)