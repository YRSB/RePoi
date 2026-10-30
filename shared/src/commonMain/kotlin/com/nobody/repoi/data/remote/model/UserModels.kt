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

private val defaultJson = Json { ignoreUnknownKeys = true }

// ── 4. 用户与登录模型 ───────────────────────────────

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
    val sex: Int? = null,          // 0=女, 1=男, 2=保密
    val birth: String? = null,        // yyyy-MM-dd
    val city: String? = null,
    val regTime: String? = null,      // 注册时间
    val addTime: String? = null,      // 兼容时间字段
    val verify: String? = null,       // 认证说明
    val cares: Int = 0,               // 关注数
    val fans: Int = 0,                // 粉丝数
    val praise: Int = 0,              // 获赞数
    val wantCount: Int = 0,           // 想买数
    val preorderCount: Int = 0,       // 预定数
    val buyCount: Int = 0,            // 已入数
    val allOrderMoneyStr: String? = null, // 总消费金额格式化文本（如 ¥1.5万）
    val similarity: Int = 0,          // 收藏相似度（0-100）
    val isFollowed: Boolean = false,  // 对该用户的关注状态
    val isMutual: Boolean = false     // 是否互相关注
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

/**
 * 用户社交列表响应载荷 (api/user/fellow)
 */
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

/**
 * 用户获赞条目响应载荷 (api/item/praise/forUser)
 */
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

/**
 * 查询关注状态响应载荷 (api/user/fellow/type)
 * friendState: 0=已关注/互关, 2=已关注, 其他(如-1)=未关注
 */
@Serializable
data class FellowTypePayload(
    val friendState: Int = -1
)

/**
 * 收藏相似度响应载荷 (api/user/collect/similarity)
 */
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
    val actualOrderMoney: Double? get() = money ?: collection?.orderMoney
    val actualCurrency: String? get() = currency?.ifBlank { null } ?: collection?.orderMoneyCurrency
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

/**
 * 用户收藏 8 大实体大类枚举（对齐原版 categoryId 与标题）。
 *
 * [apiType] 是 `api/collect/common-v3` 的 `type` 查询参数取值，出处为原版 `utils/s.java:209-244`
 * 的 `s.o(categoryId)` 与 `oe/a.java:32-44` 常量表：
 * 厂商→company、系列→series、作品→works、角色→charactar、人物→person、周边→hobby。
 *
 * ⚠️ `charactar` 是**服务端的历史拼写错误**，原版 2.39.1 写死该值，必须原样保留，不得"修正"为 character。
 */
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
        /** 按原版 categoryId 定位大类（未收录时返回 null） */
        fun fromKey(key: Int): UserCollectCategory? = entries.find { it.key == key }

        /** 按 common-v3 的 type 取值反查大类（未收录时返回 null） */
        fun fromApiType(apiType: String): UserCollectCategory? =
            entries.find { it.apiType == apiType.trim().lowercase() }
    }
}

@Serializable
data class UserCollectCommonPayload(
    val list: List<UserCollectCommonItem>? = null
)

/**
 * 通用收藏条目的多语言名称集合（对应原版 `names` 对象）。
 * 原版 `utils/f3.java:133-145` 的回退顺序为 `names.name_zh[0]` > `nameCN` > `name`，
 * 其中 `name_zh` 是多语言字符串**数组**，故需要下划线键名映射。
 */
@Serializable
data class UserCollectCommonNames(
    @SerialName("name_zh") val nameZh: List<String> = emptyList()
) {
    /** 首个非空中文名（原版取 name_zh[0]，为空则继续回退） */
    val firstName: String? get() = nameZh.firstOrNull { it.isNotBlank() }
}

/**
 * 通用收藏条目内层 `item` 对象。
 * 原版 `CommonItemListAdapter.java:103-105`、`CommonItemGridAdapter.java:94-95` 在响应含 `item` 键时
 * 会整体替换外层对象做渲染，因此真实实体信息一律以本对象为准。
 */
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
    /** 真实实体 ID（进入详情页使用） */
    val actualItemId: Long get() = if (itemId > 0L) itemId else id

    val actualCover: String? get() = cover?.ifBlank { null } ?: iconUrl

    /** 多语言名称集合（`names` 在响应里可能是对象，也可能是转义 JSON 字符串，这里统一解析） */
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
    // ── 以下为原版 `CommonItemListAdapter` / `CommonItemGridAdapter` 实际读取的键 ──
    /** 关联周边数量（原版文案「制作周边:」/「周边:」，厂商页为双统计的第一行） */
    val hobbyCount: Int = 0,
    /** 发行周边数量（仅厂商页有意义，原版文案「发行周边:」） */
    val issueHobbyCount: Int = 0,
    /** 条目类型（works/album/hobby/article 等，用于混合实体分流） */
    val itemType: String? = null,
    /** 页面态：1 表示走系列专属详情路由（原版 `SeriesDetailActivity`） */
    val pageState: Int = 0,
    /**
     * 收藏状态。
     *
     * **同一接口 `common-v3` 在不同分类下 `state` 的类型不同**：
     * - 作品分类（categoryId=20000）返回数字：`1 想看 / 2 在看 / 3 看过`；
     * - 厂商/系列/角色/人物分类返回字符串：`"care"/"want"/"preorder"/"buy"/"resell"`。
     *
     * 早前这里声明为 `Int`，厂商等分类的响应（`"state":"care"`）会直接让
     * kotlinx 抛出 `Unexpected symbol 'c' in numeric literal`，导致整页解析失败白屏。
     * 现改为容错解析：字符串形式的收藏状态归一为 `null`（该分类用不到作品三态）。
     */
    @Serializable(with = LenientIntSerializer::class)
    val state: Int? = null,
    /** 作品页进度（集数/章节，仅作品分类有意义） */
    val current: Int = 0,
    /** 作品页发售/首映/上映时间文案（仅作品分类有意义） */
    val releaseDateString: String? = null,
    /** 条目所属分类 ID（作品卡片据此决定时间前缀） */
    val categoryId: Int = 0,
    /** 内层实体对象，存在时以其为准 */
    val item: UserCollectCommonInnerItem? = null
) {
    /**
     * 真实实体 ID（进入详情页使用）。
     * 服务端在混合实体场景下会把真实条目包在 `item` 里（报告 §3.8 #8），因此优先取内层。
     */
    val actualItemId: Long
        get() = item?.let { inner ->
            if (inner.itemId > 0L) inner.itemId else inner.id
        }?.takeIf { it > 0L }
            ?: if (itemId > 0L) itemId else id

    /** 多语言名称集合（`names` 可能是对象，也可能是转义 JSON 字符串，统一在这里解析） */
    val parsedNames: UserCollectCommonNames? by lazy {
        val obj = names as? kotlinx.serialization.json.JsonObject ?: return@lazy null
        runCatching {
            kotlinx.serialization.json.Json.decodeFromJsonElement(UserCollectCommonNames.serializer(), obj)
        }.getOrNull()
    }

    val actualCover: String?
        get() = item?.actualCover ?: cover?.ifBlank { null } ?: iconUrl

    /** 显示名称，回退顺序对齐原版 `utils/f3.java`：names.name_zh[0] > nameCN > name */
    val displayName: String
        get() = item?.displayName?.ifBlank { null }
            ?: parsedNames?.firstName?.ifBlank { null }
            ?: nameCN?.ifBlank { null }
            ?: name ?: ""

    /**
     * 有效周边统计数。
     * 服务端真实键为 `hobbyCount`，老结构为 `itemsCount`，这里做双字段兼容。
     */
    val actualHobbyCount: Int get() = hobbyCount.takeIf { it > 0 } ?: itemsCount

    /** 有效发行周边统计数（厂商页专用），服务端未返回时为 0 */
    val actualIssueHobbyCount: Int get() = issueHobbyCount

    /** 是否为系列页的专属详情路由场景（pageState == 1） */
    val isSeriesSubPage: Boolean get() = pageState == 1

    /** 作品页收藏状态文案，1 想看 / 2 在看 / 3 看过 */
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
    /** 收藏时间（相册页「按收藏时间」排序 order=actionDate 的依据） */
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
    /** 收藏时间（相册页「按收藏时间」排序 order=actionDate 的依据） */
    val actionDate: String? = null,
    val user: HobbyAlbumUser? = null,
    val userName: String? = null,
    val userNickName: String? = null,
    val userHeader: String? = null,
    val item: UserCollectAlbumInnerItem? = null
) {
    /** 真实的相册条目 ID（进入相册详情使用，优先内层 item，降级自身） */
    val actualAlbumId: Long
        get() = item?.let { if (it.id > 0L) it.id else it.itemId } ?: (if (id > 0L) id else itemId)

    /** 收藏时间（内层优先），用于「按收藏时间」排序后的时间展示 */
    val actualActionDate: String?
        get() = actionDate?.ifBlank { null } ?: item?.actionDate?.ifBlank { null }

    /** 显示标题（优先中文名、原名、title） */
    val actualTitle: String
        get() = item?.nameCN?.ifBlank { null }
            ?: item?.name?.ifBlank { null }
            ?: item?.title?.ifBlank { null }
            ?: nameCN?.ifBlank { null }
            ?: name?.ifBlank { null }
            ?: title?.ifBlank { null }
            ?: "相册"

    /** 封面相对路径（过滤 none.jpg 与空值） */
    val actualCover: String?
        get() {
            val c = item?.cover ?: cover
            return if (c.isNullOrBlank() || c == "none.jpg") null else c
        }

    val hasCover: Boolean
        get() = !actualCover.isNullOrBlank()

    /** 摄影师/创建者昵称（若无则为 null） */
    val actualUserNickName: String?
        get() = item?.user?.nickname?.ifBlank { null }
            ?: item?.userNickName?.ifBlank { null }
            ?: item?.userName?.ifBlank { null }
            ?: user?.nickname?.ifBlank { null }
            ?: userNickName?.ifBlank { null }
            ?: userName?.ifBlank { null }

    /** 摄影师/创建者头像 */
    val actualUserAvatar: String?
        get() = item?.user?.header?.ifBlank { null }
            ?: item?.userHeader?.ifBlank { null }
            ?: user?.header?.ifBlank { null }
            ?: userHeader?.ifBlank { null }

    /** 照片张数 */
    val actualPhotosCount: Int
        get() = item?.photosCount?.takeIf { it > 0 }
            ?: item?.picCount?.takeIf { it > 0 }
            ?: photosCount.takeIf { it > 0 }
            ?: picCount

    /** 点赞数 */
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

/**
 * 图片收藏项（`api/collect/pic-v2` 的 `data.list` 元素）。
 *
 * **字段以真机实测响应为准**（2026-10-02，`api/collect/pic-v2` 实抓）：
 * ```json
 * {
 *   "id": 35581972, "categoryId": 80001, "itemId": 4235086, "itemType": "picture",
 *   "addTime": "...", "updTime": "...", "commentCount": 0,
 *   "hits": 0, "hitsDay": 0, "hits7Day": 0,
 *   "collect": 2, "delStatus": 0, "banAction": 0,
 *   "collection": { "id": 40387417, "state": "care", "actionDate": "...", "praiseCount": 0 },
 *   "path": "2026/09/f8e12c472e2f4fc2b63f54f96f3a6f9b.jpg",
 *   "state": "normal", "r18": 15, "original": 1,
 *   "width": 0, "height": 0, "size": 0
 * }
 * ```
 *
 * ⚠️ 该接口是**扁平结构**（不像相册页有 `item` 子对象）：
 * - 图片地址**只有 [path] 一个字段**，必须经 [CdnUrlUtils] 拼 CDN 前缀（列表 `pic/n/`、原图 `pic/raw/`）；
 * - 原版 `UserCollectPictureAdapter` / `PictureGalleryActivity` 的收藏、举报、关联条目
 *   **一律取 `pic.id`**（即本类的 [id]），此前虚构的 `pictureId` 字段并不存在。
 */
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
    /** 提取关联条目/相册名称（如《还是中招了》） */
    val relateAlbumTitle: String?
        get() = relate?.firstOrNull()?.relateItem?.let {
            it.nameCN?.ifBlank { null } ?: it.name?.ifBlank { null }
        }
}

@Serializable
data class UserCollectPicItem(
    /** 图片记录 id（`picture_info` 主键）；收藏 / 举报 / 关联条目均用它 */
    @Serializable(with = LenientLongSerializer::class)
    val id: Long = 0L,
    /** 关联的条目 id（`hpoi_item` 主键），仅作溯源，不参与展示 */
    @Serializable(with = LenientLongSerializer::class)
    val itemId: Long = 0L,
    /** 图片相对路径，如 `2026/09/xxx.jpg` */
    val path: String? = null,
    /** 图片名称（大图查看器无障碍描述与「保存到相册」的文件名） */
    val name: String? = null,
    /** 图片备注（大图查看器展示；列表页原版强制隐藏） */
    val remark: String? = null,
    /** 该图片的收藏人数（大图查看器收藏徽标） */
    val collect: Int = 0,
    /** Elasticsearch 封装字段（用户发布 api/pic/user 返回） */
    val sourceAsMap: UserCollectPicSourceData? = null
) {
    /** 真实有效的主键 ID */
    val actualId: Long
        get() = sourceAsMap?.id?.takeIf { it > 0L } ?: id

    /** 拼接 CDN 前缀前的原始相对路径（优先提取 sourceAsMap 内真实 path） */
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

    /** 关联条目/相册名称（如《还是中招了》） */
    val relateItemTitle: String?
        get() = sourceAsMap?.relateAlbumTitle

    /** 兼容历史/大图查看器字段，指向图片记录 id */
    val actualPictureId: Long get() = actualId
}

@Serializable
data class LoginData(
    val utoken: String? = null,
    val user: UserProfileSummary? = null
)

// ── 4.3 浏览历史 (Browse History) 模型 ─────────────────────────

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
    /** 详情目标业务/条目 ID（原版手办取 itemId，相册取 nodeId，归一化兜底取 id） */
    val actualDetailId: Long
        get() = when {
            itemId > 0L -> itemId
            nodeId > 0L -> nodeId
            else -> id
        }

    /** 归一化后的浏览毫秒时间戳（原版以 updTime 优先，降级到 addTime/time） */
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

    /** 解析相册作者信息（原版从 data 或 user 对象/JSON 字符串中获取） */
    val parsedUser: UserHistoryUserInfoData? by lazy {
        fun tryExtract(element: kotlinx.serialization.json.JsonElement?): UserHistoryUserInfoData? {
            if (element == null) return null
            if (element is kotlinx.serialization.json.JsonObject) {
                // 1. 直接反序列化
                val direct = runCatching {
                    defaultJson.decodeFromJsonElement(UserHistoryUserInfoData.serializer(), element)
                }.getOrNull()
                if (direct != null && (direct.authorNickname.isNotBlank() || direct.authorAvatar.isNotBlank())) {
                    return direct
                }
                // 2. 检查嵌套 user
                if (element.containsKey("user")) {
                    val nested = tryExtract(element["user"])
                    if (nested != null && (nested.authorNickname.isNotBlank() || nested.authorAvatar.isNotBlank())) {
                        return nested
                    }
                }
                // 3. 检查嵌套 data
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

// ── 签到与魔力值体系模型 ──────────────────────────────────────────

@Serializable
data class CheckInStatusPayload(
    val checkInLastTime: Long = 0L,
    val checkInContinuityDays: Int = 0
)

@Serializable
data class UserMpPayload(
    val mp: Int = 0
)
