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
import com.nobody.repoi.util.DefaultJson
import com.nobody.repoi.util.PriceFormatter
import com.nobody.repoi.util.parseEmoticonUrls

@Serializable
data class UserSelfPayload(
    val user: UserProfile? = null
)

@Serializable
data class UserProfile(
    val id: Long = 0L,
    val nickname: String? = null,
    val userName: String? = null,
    val sign: String? = null,
    val intro: String? = null,
    val header: String? = null,
    val bnr: String? = null,
    val level: Int = 0,
    val sex: Int? = null,
    val birth: String? = null,
    val city: String? = null,
    val regTime: String? = null,
    val addTime: String? = null,
    val verify: String? = null,
    val cares: Int = 0,
    val fans: Int = 0,
    val praise: Int = 0,
    val wantCount: Int = 0,
    val preorderCount: Int = 0,
    val buyCount: Int = 0,
    val allOrderMoneyStr: String? = null,
    val similarity: Int = 0,
    val isFollowed: Boolean = false,
    val isMutual: Boolean = false
) {
    val displayName: String
        get() = nickname?.ifBlank { null } ?: userName ?: "手办收藏家"

    val genderText: String
        get() = when (sex) {
            1 -> "男"
            0 -> "女"
            2 -> "保密"
            else -> "未设置"
        }

    val isVerified: Boolean
        get() = !verify.isNullOrBlank()

    val displayRegisterTime: String
        get() = regTime?.ifBlank { null } ?: addTime?.ifBlank { null } ?: "未知"
}

@Serializable
data class UserFellowPayload(
    val list: List<UserFellowItem> = emptyList(),
    val count: Int = 0
)

@Serializable
data class UserFellowItem(
    val user: UserFellowData? = null,
    val both: Int = -1
) {
    val userId: Long get() = user?.actualUserId ?: 0L
    val displayName: String get() = user?.displayName ?: "Hpoi 用户"
    val avatarUrl: String? get() = user?.header
    val signature: String? get() = user?.sign?.ifBlank { null } ?: user?.intro
    val isMutual: Boolean get() = both == 1 || both == 2
    val isFollowed: Boolean get() = both == 0 || both == 1 || both == 2

    fun isFollowedInTab(tab: Int): Boolean = if (tab == 1) (both == 1 || both == 2) else (both == 0 || both == 1 || both == 2)
}

@Serializable
data class UserFellowData(
    val id: Long = 0L,
    val userId: Long = 0L,
    val nickname: String? = null,
    val userName: String? = null,
    val sign: String? = null,
    val intro: String? = null,
    val header: String? = null,
    val level: Int = 0
) {
    val actualUserId: Long
        get() = if (id > 0L) id else userId

    val displayName: String
        get() = nickname?.ifBlank { null } ?: userName ?: "Hpoi 用户"
}

@Serializable
data class UserPraisePayload(
    val list: List<UserPraiseItem> = emptyList()
)

@Serializable
data class UserPraiseItem(
    val user: UserProfileSummary? = null,
    val item: RelateItem? = null
)

@Serializable
data class FellowCountPayload(
    val data: FellowCountData? = null
)

@Serializable
data class FellowCountData(
    val fanCount: Int = 0,
    val friendCount: Int = 0,
    val praiseCount: Int = 0
)

@Serializable
data class FellowTypePayload(
    val friendState: Int = -1
)

@Serializable
data class CollectSimilarityPayload(
    val similarity: Int = 0
)

@Serializable
data class OrderStaticPayload(
    val data: OrderStaticData? = null
)

@Serializable
data class OrderStaticData(
    val count: OrderCount? = null,
    val allOrderMoney: JsonElement? = null,
    val allPreorderMoney: JsonElement? = null,
    val amazonMoney: AmazonMoneyData? = null
)

@Serializable
data class AmazonMoneyData(
    val initialPrice: Int? = null,
    val price: Int? = null
) {
    val fluctuationText: String
        get() {
            val init = initialPrice ?: return "0%"
            val cur = price ?: return "0%"
            if (init <= 0) return "0%"
            val diff = cur - init
            val percent = (diff * 100) / init
            return if (percent > 0) "+$percent%" else "$percent%"
        }
}

enum class CurrencyType(
    val code: String,
    val displayName: String,
    val symbol: String,
    val exchangeRateToRmb: Double
) {
    CNY("cny", "人民币", "¥", 1.0),
    JPY("jpy", "日元", "JP¥", 0.06),
    USD("usd", "美元", "$", 6.4),
    EUR("euro", "欧元", "€", 6.9),
    HKD("hkd", "港元", "HK$", 0.92),
    UNKNOWN("", "其他", "", 1.0);

    companion object {
        fun fromString(currency: String?): CurrencyType {
            if (currency.isNullOrBlank()) return UNKNOWN
            return when (currency.lowercase().trim()) {
                "yen", "jpy", "日元" -> JPY
                "dollar", "usd", "美元" -> USD
                "euro", "eur", "欧元" -> EUR
                "hkd", "港元", "港币" -> HKD
                "rmb", "cny", "人民币" -> CNY
                else -> UNKNOWN
            }
        }
    }
}

data class CurrencyItem(
    val currency: String,
    val money: Double
) {
    val currencyType: CurrencyType get() = CurrencyType.fromString(currency)

    val displayName: String
        get() = if (currencyType != CurrencyType.UNKNOWN) currencyType.displayName else currency

    val symbol: String
        get() = currencyType.symbol
}

data class CategoryMoneyGroup(
    val categoryKey: String,
    val categoryName: String,
    val items: List<CurrencyItem>,
    val totalRmb: Double
)

data class OrderFinanceSummary(
    val supplementedTotalRmb: Double = 0.0,
    val historyTotalRmb: Double = 0.0,
    val amazonFluctuation: String = "0%",
    val supplementedGroups: List<CategoryMoneyGroup> = emptyList(),
    val historyGroups: List<CategoryMoneyGroup> = emptyList()
)

@Serializable
data class OrderCount(
    val buy: Int = 0,
    val preorder: Int = 0,
    val care: Int = 0,
    val want: Int = 0
) {
    val actualWant: Int get() = if (want > 0) want else care
}

@Serializable
data class CollectTopPayload(
    val list: List<CollectTopItem>? = null
)

@Serializable
data class CollectTopItem(
    val itemId: Long = 0L,
    val itemType: String? = null,
    val nameCN: String? = null,
    val name: String? = null,
    val cover: String? = null
)

@Serializable
data class UserCollectHobbyPayload(
    val list: List<UserCollectHobbyItem>? = null
)

@Serializable
data class UserCollectHobbyCollectionData(
    val state: String? = null,
    val orderTime: String? = null,
    val orderMoney: Double? = null,
    val orderMoneyCurrency: String? = null,
    val orderWay: String? = null,
    val orderForm: Int? = null,
    val buyNum: Int? = null,
    val comment: String? = null
)

@Serializable
data class UserCollectHobbyItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val itemNodeId: Long = 0L,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val iconUrl: String? = null,
    val price: String? = null,
    val money: Double? = null,
    val currency: String? = null,
    val releaseDate: String? = null,
    val actionDate: String? = null,
    val orderTime: String? = null,
    val collectState: String? = null,
    val companyId: Long? = null,
    val companyName: String? = null,
    val tax: Int? = null,
    val moneyString: String? = null,
    val rating: Double = 0.0,
    val ratingCount: Int = 0,
    val collection: UserCollectHobbyCollectionData? = null
) {
    val actualItemId: Long get() = if (itemId > 0L) itemId else if (id > 0L) id else itemNodeId
    val actualCover: String? get() = cover?.ifBlank { null } ?: iconUrl
    val displayName: String get() = nameCN?.ifBlank { null } ?: name ?: ""
    val actualCollectState: String? get() = collectState?.ifBlank { null } ?: collection?.state
    val actualOrderTime: String? get() = orderTime?.ifBlank { null } ?: collection?.orderTime
    val actualOrderMoney: Double? get() = collection?.orderMoney
    val actualCurrency: String? get() = collection?.orderMoneyCurrency?.ifBlank { null }
    val averageRating: Double get() = if (ratingCount > 0 && rating > 0.0) rating / ratingCount else rating

    val displayReleaseDateText: String
        get() {
            val raw = releaseDate?.ifBlank { null } ?: actionDate?.ifBlank { null } ?: return ""
            val clean = raw.substringBefore(" ").replace("-", "/")
            val parts = clean.split("/")
            return if (parts.size >= 3) {
                val year = parts[0]
                val month = parts[1].trimStart('0').ifEmpty { "0" }
                val day = parts[2].trimStart('0').ifEmpty { "0" }
                if (day == "0" || day == "00" || day == "31" || day == "30" || day == "28") {
                    "$year/$month"
                } else {
                    "$year/$month/$day"
                }
            } else if (parts.size == 2) {
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

    val collectStateText: String
        get() = when (actualCollectState) {
            "care" -> "关注"
            "want" -> "想买"
            "preorder" -> "预定"
            "buy" -> "已入"
            "resell" -> "有过"
            else -> "已收藏"
        }

    val actualComment: String? get() = collection?.comment?.trim()?.ifBlank { null }
    val orderWayText: String? get() = collection?.orderWay?.trim()?.ifBlank { null }
    val buyNumText: String? get() = collection?.buyNum?.let { if (it > 0) "${it}件" else null }
    val orderFormText: String? get() = when (collection?.orderForm) {
        1 -> "预定"
        2 -> "现货"
        3 -> "二手"
        4 -> "赠品"
        5 -> "散货"
        else -> null
    }

    val formattedOrderMoneyText: String?
        get() {
            val amount = actualOrderMoney ?: return null
            if (amount <= 0.0) return null
            val type = CurrencyType.fromString(actualCurrency)
            val sym = if (type.symbol.isNotBlank()) type.symbol else "¥"
            return "$sym${amount.toLong()}"
        }
}

enum class UserCollectCategory(val key: Int, val title: String, val apiType: String) {
    HOBBY(10000, "周边", "hobby"),
    ALBUM(70000, "相册", "album"),
    PICTURE(90000, "图片", "picture"),
    VENDOR(40000, "厂商", "company"),
    SERIES(80000, "系列", "series"),
    WORKS(20000, "作品", "works"),
    CHARACTER(50000, "角色/要素", "charactar"),
    PERSON(30000, "人物", "person");

    companion object {

        fun fromKey(key: Int): UserCollectCategory? = entries.find { it.key == key }

        fun fromApiType(apiType: String): UserCollectCategory? =
            entries.find { it.apiType == apiType.trim().lowercase() }
    }
}

@Serializable
data class UserCollectCommonPayload(
    val list: List<UserCollectCommonItem>? = null
)

@Serializable
data class UserCollectCommonNames(
    @SerialName("name_zh") val nameZh: List<String> = emptyList()
) {

    val firstName: String? get() = nameZh.firstOrNull { it.isNotBlank() }
}

@Serializable
data class UserCollectCommonInnerItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val name: String? = null,
    val nameCN: String? = null,
    @Serializable(with = EmbeddedJsonSerializer::class)
    val names: kotlinx.serialization.json.JsonElement? = null,
    val cover: String? = null,
    val iconUrl: String? = null,
    val categoryId: Int = 0,
    val hits: Long = 0L
) {

    val actualItemId: Long get() = if (itemId > 0L) itemId else id

    val actualCover: String? get() = cover?.ifBlank { null } ?: iconUrl

    val parsedNames: UserCollectCommonNames? by lazy {
        val obj = names as? kotlinx.serialization.json.JsonObject ?: return@lazy null
        runCatching {
            kotlinx.serialization.json.Json.decodeFromJsonElement(UserCollectCommonNames.serializer(), obj)
        }.getOrNull()
    }

    val displayName: String
        get() = parsedNames?.firstName?.ifBlank { null }
            ?: nameCN?.ifBlank { null }
            ?: name ?: ""
}

@Serializable
data class UserCollectCommonItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val name: String? = null,
    val nameCN: String? = null,
    @Serializable(with = EmbeddedJsonSerializer::class)
    val names: kotlinx.serialization.json.JsonElement? = null,
    val cover: String? = null,
    val iconUrl: String? = null,
    val actionDate: String? = null,
    val itemsCount: Int = 0,
    val collectCount: Int = 0,

    val hobbyCount: Int = 0,

    val issueHobbyCount: Int = 0,

    val itemType: String? = null,

    val pageState: Int = 0,

    @Serializable(with = LenientIntSerializer::class)
    val state: Int? = null,

    val current: Int = 0,

    val releaseDateString: String? = null,

    val categoryId: Int = 0,

    val item: UserCollectCommonInnerItem? = null
) {

    val actualItemId: Long
        get() = item?.let { inner ->
            if (inner.itemId > 0L) inner.itemId else inner.id
        }?.takeIf { it > 0L }
            ?: if (itemId > 0L) itemId else id

    val parsedNames: UserCollectCommonNames? by lazy {
        val obj = names as? kotlinx.serialization.json.JsonObject ?: return@lazy null
        runCatching {
            kotlinx.serialization.json.Json.decodeFromJsonElement(UserCollectCommonNames.serializer(), obj)
        }.getOrNull()
    }

    val actualCover: String?
        get() = item?.actualCover ?: cover?.ifBlank { null } ?: iconUrl

    val displayName: String
        get() = item?.displayName?.ifBlank { null }
            ?: parsedNames?.firstName?.ifBlank { null }
            ?: nameCN?.ifBlank { null }
            ?: name ?: ""

    val actualHobbyCount: Int get() = hobbyCount.takeIf { it > 0 } ?: itemsCount

    val actualIssueHobbyCount: Int get() = issueHobbyCount

    val isSeriesSubPage: Boolean get() = pageState == 1

    val collectStateText: String?
        get() = when (state) {
            1 -> "想看"
            2 -> "在看"
            3 -> "看过"
            else -> null
        }
}

@Serializable
data class UserCollectAlbumPayload(
    val list: List<UserCollectAlbumItem>? = null
)

@Serializable
data class UserCollectAlbumInnerItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val name: String? = null,
    val nameCN: String? = null,
    val title: String? = null,
    val cover: String? = null,
    val praiseCount: JsonElement? = null,
    val photosCount: Int = 0,
    val picCount: Int = 0,
    val addTime: String? = null,
    val updTime: String? = null,

    val actionDate: String? = null,
    val user: HobbyAlbumUser? = null,
    val userName: String? = null,
    val userNickName: String? = null,
    val userHeader: String? = null
)

@Serializable
data class UserCollectAlbumItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val name: String? = null,
    val nameCN: String? = null,
    val title: String? = null,
    val cover: String? = null,
    val praiseCount: JsonElement? = null,
    val photosCount: Int = 0,
    val picCount: Int = 0,
    val addTime: String? = null,
    val updTime: String? = null,

    val actionDate: String? = null,
    val user: HobbyAlbumUser? = null,
    val userName: String? = null,
    val userNickName: String? = null,
    val userHeader: String? = null,
    val item: UserCollectAlbumInnerItem? = null
) {

    val actualAlbumId: Long
        get() = item?.let { if (it.id > 0L) it.id else it.itemId } ?: (if (id > 0L) id else itemId)

    val actualActionDate: String?
        get() = actionDate?.ifBlank { null } ?: item?.actionDate?.ifBlank { null }

    val actualTitle: String
        get() = item?.nameCN?.ifBlank { null }
            ?: item?.name?.ifBlank { null }
            ?: item?.title?.ifBlank { null }
            ?: nameCN?.ifBlank { null }
            ?: name?.ifBlank { null }
            ?: title?.ifBlank { null }
            ?: "相册"

    val actualCover: String?
        get() {
            val c = item?.cover ?: cover
            return if (c.isNullOrBlank() || c == "none.jpg") null else c
        }

    val hasCover: Boolean
        get() = !actualCover.isNullOrBlank()

    val actualUserNickName: String?
        get() = item?.user?.nickname?.ifBlank { null }
            ?: item?.userNickName?.ifBlank { null }
            ?: item?.userName?.ifBlank { null }
            ?: user?.nickname?.ifBlank { null }
            ?: userNickName?.ifBlank { null }
            ?: userName?.ifBlank { null }

    val actualUserAvatar: String?
        get() = item?.user?.header?.ifBlank { null }
            ?: item?.userHeader?.ifBlank { null }
            ?: user?.header?.ifBlank { null }
            ?: userHeader?.ifBlank { null }

    val actualPhotosCount: Int
        get() = item?.photosCount?.takeIf { it > 0 }
            ?: item?.picCount?.takeIf { it > 0 }
            ?: photosCount.takeIf { it > 0 }
            ?: picCount

    val actualPraiseCount: Long
        get() {
            val target = item?.praiseCount ?: praiseCount
            return when (target) {
                is JsonPrimitive -> target.longOrNull ?: target.intOrNull?.toLong() ?: target.content.toLongOrNull() ?: 0L
                else -> 0L
            }
        }

    fun toAlbumListItem(): AlbumListItem = AlbumListItem(
        id = actualAlbumId,
        itemId = actualAlbumId,
        name = item?.name ?: name,
        nameCN = item?.nameCN ?: nameCN,
        cover = actualCover,
        praiseCount = item?.praiseCount ?: praiseCount,
        addTime = item?.addTime ?: addTime ?: item?.updTime ?: updTime,
        userId = item?.user?.id ?: user?.id ?: 0L,
        userName = actualUserNickName,
        userHeader = actualUserAvatar,
        user = item?.user ?: user
    )
}

@Serializable
data class UserCollectPicPayload(
    val list: List<UserCollectPicItem>? = null
)

@Serializable
data class UserCollectPicRelateItem(
    @Serializable(with = LenientLongSerializer::class)
    val id: Long = 0L,
    @Serializable(with = LenientLongSerializer::class)
    val itemId: Long = 0L,
    val itemType: String? = null,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val categoryId: Int? = null
)

@Serializable
data class UserCollectPicRelateData(
    @Serializable(with = LenientLongSerializer::class)
    val id: Long = 0L,
    val rank: Int = 0,
    val state: Int = 0,
    val relateItem: UserCollectPicRelateItem? = null
)

@Serializable
data class UserCollectPicSourceData(
    @Serializable(with = LenientLongSerializer::class)
    val id: Long = 0L,
    @Serializable(with = LenientLongSerializer::class)
    val itemId: Long = 0L,
    val itemType: String? = null,
    val categoryId: Int? = null,
    val path: String? = null,
    val name: String? = null,
    val remark: String? = null,
    val collect: Int = 0,
    val praiseCount: Int = 0,
    val addTime: String? = null,
    val relate: List<UserCollectPicRelateData>? = null
) {

    val relateAlbumTitle: String?
        get() = relate?.firstOrNull()?.relateItem?.let {
            it.nameCN?.ifBlank { null } ?: it.name?.ifBlank { null }
        }
}

@Serializable
data class UserCollectPicItem(

    @Serializable(with = LenientLongSerializer::class)
    val id: Long = 0L,

    @Serializable(with = LenientLongSerializer::class)
    val itemId: Long = 0L,

    val path: String? = null,

    val name: String? = null,

    val remark: String? = null,

    val collect: Int = 0,

    val sourceAsMap: UserCollectPicSourceData? = null
) {

    val actualId: Long
        get() = sourceAsMap?.id?.takeIf { it > 0L } ?: id

    val actualUrl: String
        get() = sourceAsMap?.path?.trim()?.ifBlank { null }
            ?: path?.trim()?.ifBlank { null }
            ?: ""

    val actualName: String?
        get() = sourceAsMap?.name?.trim()?.ifBlank { null }
            ?: name?.trim()?.ifBlank { null }

    val actualRemark: String?
        get() = sourceAsMap?.remark?.trim()?.ifBlank { null }
            ?: remark?.trim()?.ifBlank { null }

    val actualCollect: Int
        get() = sourceAsMap?.collect ?: collect

    val relateItemTitle: String?
        get() = sourceAsMap?.relateAlbumTitle

    val actualPictureId: Long get() = actualId
}

@Serializable
data class LoginData(
    val utoken: String? = null,
    val user: UserProfileSummary? = null
)

@Serializable
data class UserHistoryPayload(
    val readList: List<UserHistoryItem> = emptyList()
)

@Serializable
data class UserHistoryUserInfoData(
    val userName: String? = null,
    val userHeader: String? = null,
    val header: String? = null,
    val name: String? = null,
    val albumTitle: String? = null,
    val title: String? = null,
    @Serializable(with = LenientLongSerializer::class)
    val userId: Long = 0L,
    @Serializable(with = LenientLongSerializer::class)
    val id: Long = 0L
) {
    val authorNickname: String
        get() = userName?.trim()?.ifBlank { null }
            ?: name?.trim()?.ifBlank { null }
            ?: ""

    val authorAvatar: String
        get() = userHeader?.trim()?.ifBlank { null }
            ?: header?.trim()?.ifBlank { null }
            ?: ""

    val nickname: String? get() = authorNickname.ifBlank { null }
    val avatar: String? get() = authorAvatar.ifBlank { null }
}

@Serializable
data class UserHistoryItem(
    @Serializable(with = LenientLongSerializer::class)
    val id: Long = 0L,
    @Serializable(with = LenientLongSerializer::class)
    val itemId: Long = 0L,
    @Serializable(with = LenientLongSerializer::class)
    val nodeId: Long = 0L,
    val itemType: String? = null,
    val title: String? = null,
    val cover: String? = null,
    @Serializable(with = LenientDateTimeToEpochSerializer::class)
    val time: Long = 0L,
    @Serializable(with = LenientDateTimeToEpochSerializer::class)
    val addTime: Long = 0L,
    @Serializable(with = LenientDateTimeToEpochSerializer::class)
    val updTime: Long = 0L,
    @Serializable(with = EmbeddedJsonSerializer::class)
    val user: kotlinx.serialization.json.JsonElement? = null,
    @Serializable(with = EmbeddedJsonSerializer::class)
    val data: kotlinx.serialization.json.JsonElement? = null
) {

    val actualDetailId: Long
        get() = when {
            itemId > 0L -> itemId
            nodeId > 0L -> nodeId
            else -> id
        }

    val actualViewTime: Long
        get() = when {
            updTime > 0L -> updTime
            addTime > 0L -> addTime
            time > 0L -> time
            else -> 0L
        }

    val actualTitle: String
        get() = title?.trim()?.ifBlank { null }
            ?: parsedUser?.albumTitle?.trim()?.ifBlank { null }
            ?: parsedUser?.title?.trim()?.ifBlank { null }
            ?: ""

    val actualCover: String
        get() = cover?.trim().orEmpty()

    val parsedUser: UserHistoryUserInfoData? by lazy {
        fun tryExtract(element: kotlinx.serialization.json.JsonElement?): UserHistoryUserInfoData? {
            if (element == null) return null
            if (element is kotlinx.serialization.json.JsonObject) {

                val direct = runCatching {
                    DefaultJson.decodeFromJsonElement(UserHistoryUserInfoData.serializer(), element)
                }.getOrNull()
                if (direct != null && (direct.authorNickname.isNotBlank() || direct.authorAvatar.isNotBlank())) {
                    return direct
                }

                if (element.containsKey("user")) {
                    val nested = tryExtract(element["user"])
                    if (nested != null && (nested.authorNickname.isNotBlank() || nested.authorAvatar.isNotBlank())) {
                        return nested
                    }
                }

                if (element.containsKey("data")) {
                    val nested = tryExtract(element["data"])
                    if (nested != null && (nested.authorNickname.isNotBlank() || nested.authorAvatar.isNotBlank())) {
                        return nested
                    }
                }
                return direct
            }
            return null
        }

        tryExtract(data) ?: tryExtract(user)
    }
}

@Serializable
data class CheckInStatusPayload(
    val checkInLastTime: Long = 0L,
    val checkInContinuityDays: Int = 0
)

@Serializable
data class UserMpPayload(
    val mp: Int = 0
)
