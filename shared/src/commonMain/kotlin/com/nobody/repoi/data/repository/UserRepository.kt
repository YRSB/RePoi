package com.nobody.repoi.data.repository

import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CategoryMoneyGroup
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.CollectTopItem
import com.nobody.repoi.data.remote.model.CurrencyItem
import com.nobody.repoi.data.remote.model.CurrencyType
import com.nobody.repoi.data.remote.model.FellowCountData
import com.nobody.repoi.data.remote.model.LoginData
import com.nobody.repoi.data.remote.model.OrderCount
import com.nobody.repoi.data.remote.model.OrderFinanceSummary
import com.nobody.repoi.data.remote.model.OrderStaticData
import com.nobody.repoi.data.remote.model.UserCollectHobbyItem
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.remote.model.UserCollectCommonItem
import com.nobody.repoi.data.remote.model.UserCollectAlbumItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.CheckInStatusPayload
import com.nobody.repoi.data.remote.model.UserMpPayload
import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.data.remote.model.UserFellowItem
import com.nobody.repoi.data.remote.model.UserPraiseItem
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.CollectSimilarityPayload
import com.nobody.repoi.data.remote.model.UserHistoryItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * 完整个人中心聚合数据模型。
 */
data class FullProfileData(
    val profile: UserProfile,
    val fellow: FellowCountData,
    val order: OrderCount,
    val finance: OrderFinanceSummary = OrderFinanceSummary(),
    val preorderItems: List<CollectTopItem>,
    val buyItems: List<CollectTopItem>,
    val careItems: List<CollectTopItem> = emptyList(),
    val userAlbums: List<AlbumListItem> = emptyList(),
    val checkInStatus: CheckInStatusPayload? = null,
    val userMp: Int = 0
)

/**
 * 分页数据包装结果，保留根据底层未过滤条数计算得出的 hasMore 标志。
 */
data class CreationPageResult<T>(
    val items: List<T> = emptyList(),
    val hasMore: Boolean = false
)

/**
 * 好友/关注关系状态枚举。
 * 0=已关注/单向关注, 2=互相关注, 5=未关注/取关, -1=未知
 */
enum class FriendState(val code: Int) {
    FOLLOWING(0),      // 已关注 / 单向关注
    MUTUAL(2),         // 互相关注
    NOT_FOLLOWING(5),  // 未关注 / 取消关注
    UNKNOWN(-1);

    val isFollowed: Boolean get() = this == FOLLOWING || this == MUTUAL

    companion object {
        fun fromCode(code: Int): FriendState = entries.find { it.code == code } ?: UNKNOWN
    }
}

/**
 * 用户关注状态变更事件。
 * 当在任何界面关注或取消关注某位用户并请求成功时发射。
 *
 * @param userId 目标用户 UID
 * @param isFollow true 为已关注，false 为未关注
 * @param state 关联的 FriendState
 */
data class FriendChangeEvent(
    val userId: Long,
    val isFollow: Boolean,
    val state: FriendState = if (isFollow) FriendState.FOLLOWING else FriendState.NOT_FOLLOWING
)

/**
 * 用户与个人中心数据仓储。
 * 参考 FluentHpoi MyPageService，统一负责登录、登出、用户信息、社交统计、订单统计与收藏封面列表。
 */
class UserRepository(
    private val api: HpoiApi,
    private val tokenManager: TokenManager
) {
    val tokenFlow: Flow<String> = tokenManager.tokenFlow
    val currentToken: String get() = tokenManager.currentToken
    val isLoggedIn: Boolean get() = tokenManager.currentToken.isNotBlank()
    val currentUserId: Long get() = tokenManager.currentUserId

    suspend fun awaitTokenReady() = tokenManager.awaitReady()

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    private val _friendChangeEvent = MutableSharedFlow<FriendChangeEvent>(extraBufferCapacity = 64)
    val friendChangeEvent: SharedFlow<FriendChangeEvent> = _friendChangeEvent.asSharedFlow()

    suspend fun login(account: String, password: String): Result<LoginData> = runCatching {
        val response = api.login(account = account, password = password)
        if (response.isSuccess || (response.success && response.data?.utoken?.isNotBlank() == true)) {
            val data = response.data ?: throw ApiException(response.code, "服务器未返回登录数据")
            val token = data.utoken ?: throw ApiException(response.code, "未获取到有效的登录令牌")
            val userName = data.user?.userName
            tokenManager.saveToken(token, userName)
            data
        } else {
            throw ApiException(response.code, response.msg ?: "登录失败")
        }
    }

    suspend fun logout() {
        tokenManager.clearToken()
        _currentUserProfile.value = null
    }

    suspend fun getUserProfile(): Result<UserProfile> = runCatching {
        val response = api.getUserSelf()
        if (response.isSuccess || (response.success && response.data?.user != null)) {
            val profile = response.data?.user ?: throw ApiException(response.code, "用户信息为空")
            runCatching { tokenManager.saveCurrentUser(profile.id, profile.nickname?.ifBlank { null } ?: profile.userName) }
            _currentUserProfile.value = profile
            profile
        } else {
            if (response.isTokenExpired) {
                tokenManager.clearToken()
                _currentUserProfile.value = null
            }
            throw ApiException(response.code, response.msg ?: "获取用户信息失败")
        }
    }

    suspend fun getFellowCount(userId: Long): Result<FellowCountData> = runCatching {
        val response = api.getFellowCount(userNodeId = userId)
        if (response.isSuccess || (response.success && response.data?.data != null)) {
            response.data?.data ?: FellowCountData()
        } else {
            throw ApiException(response.code, response.msg ?: "获取关注统计失败")
        }
    }

    suspend fun getOrderStaticData(userId: Long): Result<OrderStaticData> = runCatching {
        val response = api.getOrderStatic(userId = userId)
        if (response.isSuccess || (response.success && response.data?.data != null)) {
            response.data?.data ?: OrderStaticData()
        } else {
            throw ApiException(response.code, response.msg ?: "获取订单统计失败")
        }
    }

    suspend fun getOrderStatic(userId: Long): Result<OrderCount> = runCatching {
        getOrderStaticData(userId).map { it.count ?: OrderCount() }.getOrThrow()
    }

    suspend fun getCollectTop(userId: Long, state: String): Result<List<CollectTopItem>> = runCatching {
        val response = api.getCollectTop(id = userId, state = state)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取收藏列表失败")
        }
    }

    /**
     * 获取用户自己发布的相册列表 (api/album/user-v2)。
     */
    suspend fun getUserAlbumList(userId: Long, page: Int = 1, pageSize: Int = 6): Result<List<AlbumListItem>> = runCatching {
        val response = api.getUserAlbumList(userNodeId = userId, page = page, pageSize = pageSize)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list?.map { it.toAlbumListItem() } ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取用户相册失败")
        }
    }

    /**
     * 获取用户发布页的相册列表 (api/album/user-v2)。
     * 返回原版完整相册条目 [UserCollectAlbumItem]。
     */
    suspend fun getUserCreationAlbumList(
        userId: Long,
        page: Int = 1,
        pageSize: Int = 20,
        order: String = "addTime"
    ): Result<CreationPageResult<UserCollectAlbumItem>> = runCatching {
        val response = api.getUserAlbumList(
            userNodeId = userId,
            page = page,
            pageSize = pageSize,
            type = "upload",
            order = order
        )
        if (response.isSuccess || (response.success && response.data != null)) {
            val rawList = response.data?.list ?: emptyList()
            CreationPageResult(
                items = rawList,
                hasMore = rawList.size >= pageSize
            )
        } else {
            throw ApiException(response.code, response.msg ?: "获取用户发布相册失败")
        }
    }

    /**
     * 获取用户发布页的图片列表 (api/pic/user)。
     * 返回原版图片条目 [UserCollectPicItem]。
     */
    suspend fun getUserCreationPicList(
        userId: Long,
        page: Int = 1,
        pageSize: Int = 20,
        order: String = "addTime"
    ): Result<CreationPageResult<UserCollectPicItem>> = runCatching {
        val response = api.getUserPicList(
            userNodeId = userId,
            page = page,
            pageSize = pageSize,
            collectState = "upload",
            order = order
        )
        if (response.isSuccess || (response.success && response.data != null)) {
            val rawList = response.data?.list ?: emptyList()
            CreationPageResult(
                items = rawList.filter { it.actualUrl.isNotBlank() },
                hasMore = rawList.size >= pageSize
            )
        } else {
            throw ApiException(response.code, response.msg ?: "获取用户发布图片失败")
        }
    }

    /**
     * 获取用户签到状态 (api/mp/task/checkIn/get)。
     */
    suspend fun getCheckInStatus(): Result<CheckInStatusPayload> = runCatching {
        val response = api.getCheckInStatus()
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data ?: CheckInStatusPayload()
        } else {
            throw ApiException(response.code, response.msg ?: "获取签到状态失败")
        }
    }

    /**
     * 执行每日签到打卡 (api/mp/task/checkIn)。
     */
    suspend fun checkIn(): Result<Unit> = runCatching {
        val response = api.checkIn()
        if (response.isSuccess || response.success) {
            Unit
        } else {
            throw ApiException(response.code, response.msg ?: "签到失败")
        }
    }

    /**
     * 获取用户魔力值积分 (api/mp/get)。
     */
    suspend fun getUserMp(): Result<Int> = runCatching {
        val response = api.getUserMp()
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.mp ?: 0
        } else {
            throw ApiException(response.code, response.msg ?: "获取魔力值失败")
        }
    }

    /**
     * 并发聚合拉取个人中心所有数据（self -> fellow + orderStatic + preorder + buy + care + album + checkIn + mp）。
     */
    suspend fun loadFullProfile(): Result<FullProfileData> = runCatching {
        // 1. 先获取基础用户信息（以取得 userId）
        val profile = getUserProfile().getOrThrow()
        val userId = profile.id

        // 2. 并发拉取社交统计、订单统计、手办展柜、相册展柜及签到积分
        coroutineScope {
            val fellowDeferred = async { getFellowCount(userId).getOrDefault(FellowCountData()) }
            val orderDeferred = async { getOrderStaticData(userId).getOrNull() }
            val preorderDeferred = async { getCollectTop(userId, "preorder").getOrDefault(emptyList()) }
            val buyDeferred = async { getCollectTop(userId, "buy").getOrDefault(emptyList()) }
            val careDeferred = async { getCollectTop(userId, "care").getOrDefault(emptyList()) }
            val albumDeferred = async { getUserAlbumList(userId).getOrDefault(emptyList()) }
            val checkInDeferred = async { getCheckInStatus().getOrNull() }
            val mpDeferred = async { getUserMp().getOrDefault(0) }

            val orderPayload = orderDeferred.await()
            val orderCount = orderPayload?.count ?: OrderCount()
            val financeSummary = parseOrderFinance(orderPayload)

            FullProfileData(
                profile = profile,
                fellow = fellowDeferred.await(),
                order = orderCount,
                finance = financeSummary,
                preorderItems = preorderDeferred.await(),
                buyItems = buyDeferred.await(),
                careItems = careDeferred.await(),
                userAlbums = albumDeferred.await(),
                checkInStatus = checkInDeferred.await(),
                userMp = mpDeferred.await()
            )
        }
    }

    /**
     * 解析订单财务与行情指标（多币种待补款、已入总额换算人民币及亚马逊涨跌幅）。
     */
    fun parseOrderFinance(orderPayload: OrderStaticData?): OrderFinanceSummary {
        if (orderPayload == null) return OrderFinanceSummary()
        val supplementedGroups = parseCategoryMoneyGroups(orderPayload.allPreorderMoney)
        val historyGroups = parseCategoryMoneyGroups(orderPayload.allOrderMoney)
        val supplementedTotalRmb = supplementedGroups.sumOf { it.totalRmb }
        val historyTotalRmb = historyGroups.sumOf { it.totalRmb }
        val amazonFluctuation = orderPayload.amazonMoney?.fluctuationText ?: "0%"

        return OrderFinanceSummary(
            supplementedTotalRmb = supplementedTotalRmb,
            historyTotalRmb = historyTotalRmb,
            amazonFluctuation = amazonFluctuation,
            supplementedGroups = supplementedGroups,
            historyGroups = historyGroups
        )
    }

    internal fun parseCategoryMoneyGroups(jsonElement: JsonElement?): List<CategoryMoneyGroup> {
        if (jsonElement == null) return emptyList()
        val array = when (jsonElement) {
            is JsonArray -> jsonElement
            is JsonPrimitive -> runCatching {
                jsonParser.parseToJsonElement(jsonElement.content) as? JsonArray
            }.getOrNull()
            else -> null
        } ?: return emptyList()

        val groups = mutableListOf<CategoryMoneyGroup>()
        for (element in array) {
            if (element !is JsonObject) continue
            val key = (element["key"] as? JsonPrimitive)?.content ?: ""
            val categoryName = mapCategoryName(key)
            val valElement = element["val"]
            val valArray = when (valElement) {
                is JsonArray -> valElement
                is JsonPrimitive -> runCatching {
                    jsonParser.parseToJsonElement(valElement.content) as? JsonArray
                }.getOrNull()
                else -> null
            }
            val items = mutableListOf<CurrencyItem>()
            if (valArray != null) {
                for (valElem in valArray) {
                    if (valElem !is JsonObject) continue
                    val currency = (valElem["currency"] as? JsonPrimitive)?.content?.trim() ?: "rmb"
                    val money = (valElem["money"] as? JsonPrimitive)?.doubleOrNull
                        ?: (valElem["money"] as? JsonPrimitive)?.content?.toDoubleOrNull()
                        ?: 0.0
                    items.add(CurrencyItem(currency = currency, money = money))
                }
            } else if (element.containsKey("currency") && element.containsKey("money")) {
                val currency = (element["currency"] as? JsonPrimitive)?.content?.trim() ?: "rmb"
                val money = (element["money"] as? JsonPrimitive)?.doubleOrNull
                    ?: (element["money"] as? JsonPrimitive)?.content?.toDoubleOrNull()
                    ?: 0.0
                items.add(CurrencyItem(currency = currency, money = money))
            }
            val groupTotalRmb = items.sumOf { it.money * getExchangeRate(it.currency) }
            groups.add(
                CategoryMoneyGroup(
                    categoryKey = key,
                    categoryName = categoryName,
                    items = items,
                    totalRmb = groupTotalRmb
                )
            )
        }
        return groups
    }

    internal fun mapCategoryName(key: String): String {
        return when (key.lowercase().trim()) {
            "category_100", "100", "category_figure", "figure", "手办" -> "手办"
            "category_200", "200", "category_model", "model", "动漫模型", "模型" -> "动漫模型"
            "category_300", "300", "category_doll", "doll", "doll娃娃", "娃娃" -> "Doll娃娃"
            "category_400", "400", "category_moppet", "moppet", "毛绒布偶", "布偶", "毛绒" -> "毛绒布偶"
            "category_500", "500", "category_real", "real", "真实模型", "周边" -> "真实模型"
            else -> key.ifBlank { "其他" }
        }
    }

    internal fun getExchangeRate(currency: String): Double {
        return CurrencyType.fromString(currency).exchangeRateToRmb
    }

    /**
     * 查询用户全量手办收藏列表 (api/collect/hobby-v2, GET)。
     *
     * @param category 周边细分类，「全部」传 [CollectConstants.CATEGORY_ALL]（10000，原版不用 null）
     * @param sortType 升降序：1 升序 / 2 降序
     * @param order 排序 key，白名单 actionDate/release/rating/myRating/updateDate；
     *   `orderTime`（入手时间）语义上仅 `collectState == "buy"` 时可用，其余状态自动回落 actionDate
     */
    suspend fun getUserCollectHobbyList(
        userId: Long,
        collectState: String,
        category: Int = CollectConstants.CATEGORY_ALL,
        tag: String? = null,
        order: String = CollectConstants.ORDER_ACTION_DATE,
        sortType: Int = SORT_TYPE_DESC,
        page: Int,
        pageSize: Int = 20
    ): Result<List<UserCollectHobbyItem>> = runCatching {
        val params = buildMap {
            put("userId", userId.toString())
            put("userNodeId", userId.toString())
            if (collectState.isNotEmpty()) {
                put("collectState", collectState)
            }
            put("order", CollectConstants.normalizeHobbyOrder(order, collectState))
            put("category", category.toString())
            put("sortType", sortType.toString())
            put("page", page.toString())
            put("limit", pageSize.toString())
            put("pageSize", pageSize.toString())
            if (!tag.isNullOrBlank()) {
                put("tag", tag)
            }
        }
        val response = api.getUserCollectHobby(params)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                tokenManager.clearToken()
            }
            throw ApiException(response.code, response.msg ?: "获取收藏列表失败")
        }
    }

    /**
     * 查询用户通用实体收藏列表 (厂商/系列/作品/角色/人物) (api/collect/common-v3, GET)。
     *
     * @param type 分类查询的唯一载体，取值见 [UserCollectCategory.apiType]
     *   （works/company/series/charactar/person）。
     *   ⚠️ 原版从不传 `categoryId`，`type` 才是分类维度；此处为兼容服务端双条件支持仍保留 categoryId。
     * @param order 排序 key，白名单仅 `actionDate` / `collect`（原版 `UserCollectItemSubPageFragment.java:55-64`），
     *   越界值统一回落 `actionDate`
     * @param state 作品三态：1 想看 / 2 在看 / 3 看过；null 时不下发
     * @param isAsc 作品页收藏时间升降序；null 时不下发
     * @param tagValue 作品页标签筛选；null 或空串时不下发
     *
     * ⚠️ [state] / [isAsc] / [tagValue] 是作品页**独立于 order 的正交维度**，
     * 且仅在 `type == "works"` 时下发，厂商/系列/角色/人物 4 页的请求参数保持原样。
     */
    suspend fun getUserCollectCommonList(
        userId: Long,
        categoryId: Int,
        type: String,
        order: String = CollectConstants.ORDER_ACTION_DATE,
        page: Int,
        pageSize: Int = 20,
        state: Int? = null,
        isAsc: Boolean? = null,
        tagValue: String? = null
    ): Result<List<UserCollectCommonItem>> = runCatching {
        val resolvedType = type.ifBlank {
            UserCollectCategory.fromKey(categoryId)?.apiType.orEmpty()
        }
        val params = buildMap {
            put("userId", userId.toString())
            put("userNodeId", userId.toString())
            put("categoryId", categoryId.toString())
            put("type", resolvedType)
            put("order", CollectConstants.normalizeCommonOrder(order))
            put("page", page.toString())
            put("limit", pageSize.toString())
            put("pageSize", pageSize.toString())
            if (resolvedType == UserCollectCategory.WORKS.apiType) {
                state?.let { put("state", it.toString()) }
                isAsc?.let { put("isAsc", it.toString()) }
                if (!tagValue.isNullOrBlank()) {
                    put("tagValue", tagValue)
                }
            }
        }
        val response = api.getUserCollectCommon(params)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                tokenManager.clearToken()
            }
            throw ApiException(response.code, response.msg ?: "获取通用实体收藏列表失败")
        }
    }

    /**
     * 查询用户相册收藏列表 (api/collect/album-v2, GET)。
     *
     * @param type 相册归属，默认 fav（我收藏的）
     * @param order 排序 key，白名单 addTime（发布时间）/ actionDate（收藏时间）/ praiseCount（点赞量）
     */
    suspend fun getUserCollectAlbumList(
        userId: Long,
        type: String = "fav",
        order: String = CollectConstants.ORDER_ADD_TIME,
        page: Int,
        pageSize: Int = 20
    ): Result<List<UserCollectAlbumItem>> = runCatching {
        val params = buildMap {
            put("userId", userId.toString())
            put("userNodeId", userId.toString())
            put("type", type)
            put("order", CollectConstants.normalizeMediaOrder(order))
            put("page", page.toString())
            put("limit", pageSize.toString())
            put("pageSize", pageSize.toString())
        }
        val response = api.getUserCollectAlbum(params)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                tokenManager.clearToken()
            }
            throw ApiException(response.code, response.msg ?: "获取相册收藏列表失败")
        }
    }

    /**
     * 查询用户图片收藏列表 (api/collect/pic-v2, GET)。
     *
     * @param collectState 收藏状态，默认 collect（图片页原版硬编码）
     * @param order 排序 key，白名单 addTime（发布时间）/ actionDate（收藏时间）/ praiseCount（点赞量），
     *   与相册页共用同一套排序组件
     */
    suspend fun getUserCollectPicList(
        userId: Long,
        collectState: String = "collect",
        order: String = CollectConstants.ORDER_ADD_TIME,
        page: Int,
        pageSize: Int = 20
    ): Result<List<UserCollectPicItem>> = runCatching {
        val params = buildMap {
            put("userId", userId.toString())
            put("userNodeId", userId.toString())
            put("collectState", collectState)
            put("order", CollectConstants.normalizeMediaOrder(order))
            put("page", page.toString())
            put("limit", pageSize.toString())
            put("pageSize", pageSize.toString())
        }
        val response = api.getUserCollectPic(params)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                tokenManager.clearToken()
            }
            throw ApiException(response.code, response.msg ?: "获取图片收藏列表失败")
        }
    }

    /**
     * 获取用户收藏相似度 (api/user/collect/similarity)。
     */
    suspend fun getCollectSimilarity(userId: Long): Result<Int> = runCatching {
        val response = api.getCollectSimilarity(userNodeId = userId)
        if (response.isSuccess) {
            response.data?.similarity ?: 0
        } else {
            0
        }
    }

    /**
     * 获取用户个人动态列表 (api/user-action/user/list)。
     */
    suspend fun getUserActionList(userId: Long, pageSize: Int = 20, lastTime: Long = 0L): Result<List<ActionItem>> = runCatching {
        val response = api.getUserActionList(userNodeId = userId, pageSize = pageSize, lastTime = lastTime)
        if (response.isSuccess) {
            response.data?.list ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * 查询指定用户的关注状态 (api/user/fellow/type)。
     */
    suspend fun getFellowType(userId: Long): Result<FriendState> = runCatching {
        val response = api.getFellowType(userNId = userId)
        if (response.isSuccess) {
            val stateCode = response.data?.friendState ?: -1
            FriendState.fromCode(stateCode)
        } else {
            throw ApiException(response.code, response.msg ?: "获取用户关注状态失败")
        }
    }

    /**
     * 更新用户关注/好友状态 (api/user/friend/upd)。
     */
    suspend fun updateFriend(userId: Long, state: FriendState): Result<Unit> = runCatching {
        val response = api.updateFriend(userId = userId, state = state.code)
        if (response.isSuccess) {
            _friendChangeEvent.emit(
                FriendChangeEvent(
                    userId = userId,
                    isFollow = state.isFollowed,
                    state = state
                )
            )
            Unit
        } else {
            if (response.isTokenExpired) {
                tokenManager.clearToken()
                _currentUserProfile.value = null
            }
            throw ApiException(response.code, response.msg ?: "更新用户关注状态失败")
        }
    }

    /**
     * 更新用户关注/好友状态重载 (isFollow: true=关注, false=取消关注)。
     */
    suspend fun updateFriend(userId: Long, isFollow: Boolean): Result<Unit> {
        val targetState = if (isFollow) FriendState.FOLLOWING else FriendState.NOT_FOLLOWING
        return updateFriend(userId = userId, state = targetState)
    }

    /**
     * 获取用户社交列表 (关注/粉丝) (api/user/fellow)。
     * @param userId 目标用户 ID
     * @param type 0=关注, 1=粉丝
     * @param page 分页页码（1 起始）
     * @param pageSize 每页条数
     */
    suspend fun getUserFellowList(
        userId: Long,
        type: Int,
        page: Int,
        pageSize: Int = 20
    ): Result<List<UserFellowItem>> = runCatching {
        val response = api.getUserFellow(userId = userId, type = type, page = page, pageSize = pageSize)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                tokenManager.clearToken()
                _currentUserProfile.value = null
            }
            throw ApiException(response.code, response.msg ?: "获取好友列表失败")
        }
    }

    /**
     * 获取用户获赞条目列表 (api/item/praise/forUser)。
     * @param userId 目标用户 ID
     * @param page 分页页码（1 起始）
     * @param pageSize 每页条数
     */
    suspend fun getUserPraiseList(
        userId: Long,
        page: Int,
        pageSize: Int = 20
    ): Result<List<UserPraiseItem>> = runCatching {
        val response = api.getUserPraiseForUser(userId = userId, page = page, pageSize = pageSize)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                tokenManager.clearToken()
                _currentUserProfile.value = null
            }
            throw ApiException(response.code, response.msg ?: "获取获赞列表失败")
        }
    }

    /**
     * 更新用户基本资料 (api/user/base/upd)。
     * @param key 字段名（如 sign, intro, nickname 等）
     * @param value 字段值
     * @param autoRefresh 是否在单次成功后立即刷新个人资料状态流（批量更新时置为 false 并在最后统一刷新）
     */
    suspend fun updateUserBase(key: String, value: String, autoRefresh: Boolean = true): Result<Unit> = runCatching {
        val response = api.updateUserBase(key = key, value = value)
        if (response.isSuccess) {
            if (autoRefresh) {
                getUserProfile()
            }
            Unit
        } else {
            if (response.isTokenExpired) {
                tokenManager.clearToken()
                _currentUserProfile.value = null
            }
            throw ApiException(response.code, response.msg ?: "更新用户资料失败")
        }
    }

    /**
     * 查询指定用户的详细资料 (api/user/getinfo)。
     * 优先尝试主节点 ID (id)，若未匹配则回退尝试账号 ID (userId)，双重保障兼顾节点 ID 与历史账号 ID。
     */
    suspend fun getUserInfo(userId: Long): Result<UserProfile> = runCatching {
        // 先尝试以主节点 ID (id) 查询
        val idResponse = try {
            api.getUserInfo(id = userId)
        } catch (_: Exception) {
            null
        }
        val response = if (idResponse != null && (idResponse.isSuccess || (idResponse.success && idResponse.data?.user != null))) {
            idResponse
        } else {
            // 若主节点 ID 未命中或接口报错（如传入的是历史账号 ID），回退尝试以 userId 查询
            api.getUserInfo(userId = userId)
        }
        if (response.isSuccess || (response.success && response.data?.user != null)) {
            response.data?.user ?: throw ApiException(response.code, "用户信息为空")
        } else {
            if (response.isTokenExpired) {
                tokenManager.clearToken()
                _currentUserProfile.value = null
            }
            throw ApiException(response.code, response.msg ?: "获取用户信息失败")
        }
    }

    /**
     * 获取用户浏览历史列表 (api/user/read/item/get)。
     * @param itemType "hobby" (手办) / "album" (相册)
     * @param page 分页页码，从 1 开始
     */
    suspend fun getUserHistoryList(
        itemType: String,
        page: Int = 1
    ): Result<List<UserHistoryItem>> = runCatching {
        val response = api.getUserHistoryList(itemType = itemType, page = page)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.readList ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取浏览历史失败")
        }
    }

    /**
     * 上报/添加浏览足迹 (api/user/read/item/add)。
     * @param itemNodeId 手办条目节点 ID 或相册 ID
     */
    suspend fun addUserHistory(itemNodeId: Long): Result<Unit> = runCatching {
        if (itemNodeId <= 0L) return@runCatching
        val response = api.addUserHistory(itemNodeId = itemNodeId)
        if (!response.isSuccess && !response.success) {
            throw ApiException(response.code, response.msg ?: "添加浏览历史失败")
        }
    }

    /**
     * 批量删除浏览历史 (api/user/read/item/del)。
     * @param historyIds 浏览历史记录条目的记录 ID 列表
     */
    suspend fun deleteUserHistory(historyIds: List<Long>): Result<Unit> = runCatching {
        if (historyIds.isEmpty()) return@runCatching
        val idsJson = historyIds.joinToString(separator = ",", prefix = "[", postfix = "]")
        val response = api.deleteUserHistory(ids = idsJson)
        if (!response.isSuccess && !response.success) {
            throw ApiException(response.code, response.msg ?: "删除浏览历史失败")
        }
    }

    companion object {
        /** 周边列表排序：升序 */
        const val SORT_TYPE_ASC = 1

        /** 周边列表排序：降序（原版默认值） */
        const val SORT_TYPE_DESC = 2

        private val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
