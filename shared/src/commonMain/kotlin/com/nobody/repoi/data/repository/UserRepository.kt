package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CheckInStatusPayload
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.CollectTopItem
import com.nobody.repoi.data.remote.model.FellowCountData
import com.nobody.repoi.data.remote.model.OrderCount
import com.nobody.repoi.data.remote.model.OrderFinanceSummary
import com.nobody.repoi.data.remote.model.UserCollectAlbumItem
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.remote.model.UserCollectCommonItem
import com.nobody.repoi.data.remote.model.UserCollectHobbyItem
import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.data.remote.model.UserPraiseItem
import com.nobody.repoi.data.session.SessionState
import com.nobody.repoi.data.session.UserSession
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 完整个人中心聚合数据模型。
 */
data class FullProfileData(
    val profile: UserProfile,
    val fellow: FellowCountData = FellowCountData(),
    val order: OrderCount = OrderCount(),
    val finance: OrderFinanceSummary = OrderFinanceSummary(),
    val preorderItems: List<CollectTopItem> = emptyList(),
    val buyItems: List<CollectTopItem> = emptyList(),
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
 * 用户档案、展柜与个人创作数据仓储。
 *
 * 会话职责已全面收敛至 [UserSession]，足迹划归 [UserHistoryRepository]，
 * 社交关注划归 [FollowRepository]，订单财务划归 [UserFinanceRepository]。
 * 本仓储专注于用户信息查询、签到打卡、魔力值积分、个人资料修改与收藏展柜列表。
 */
class UserRepository(
    private val api: HpoiApi,
    private val userSession: UserSession,
    private val followRepository: FollowRepository,
    private val userFinanceRepository: UserFinanceRepository
) {
    /**
     * 获取用户收藏顶置/展柜条目 (api/user/collect/top)。
     * @param userId 目标用户 ID
     * @param state 收藏状态："preorder" (预订), "buy" (已入), "care" (心愿)
     */
    suspend fun getCollectTop(userId: Long, state: String): Result<List<CollectTopItem>> = runCatching {
        val response = api.getCollectTop(id = userId, state = state)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取展柜列表失败")
        }
    }

    /**
     * 获取用户自己发布的相册简要列表 (api/album/user-v2)。
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
        if (!response.isSuccess && !response.success) {
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
     *
     * @param forceRefresh 是否强制向服务端刷新基础用户档案；为 false 时优先复用当前会话内存档案。
     */
    suspend fun loadFullProfile(forceRefresh: Boolean = false): Result<FullProfileData> = runCatching {
        val profile = if (forceRefresh) {
            userSession.refreshProfile().getOrThrow()
        } else {
            (userSession.state.value as? SessionState.Authenticated)?.profile
                ?: userSession.refreshProfile().getOrThrow()
        }
        val userId = profile.id

        coroutineScope {
            val fellowDeferred = async { followRepository.getFellowCount(userId).getOrDefault(FellowCountData()) }
            val orderDeferred = async { userFinanceRepository.getOrderStaticData(userId).getOrNull() }
            val preorderDeferred = async { getCollectTop(userId, "preorder").getOrDefault(emptyList()) }
            val buyDeferred = async { getCollectTop(userId, "buy").getOrDefault(emptyList()) }
            val careDeferred = async { getCollectTop(userId, "care").getOrDefault(emptyList()) }
            val albumDeferred = async { getUserAlbumList(userId).getOrDefault(emptyList()) }
            val checkInDeferred = async { getCheckInStatus().getOrNull() }
            val mpDeferred = async { getUserMp().getOrDefault(0) }

            val orderPayload = orderDeferred.await()
            val orderCount = orderPayload?.count ?: OrderCount()
            val financeSummary = userFinanceRepository.parseOrderFinance(orderPayload)

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
        val snapshotToken = userSession.currentToken
        val response = api.getUserCollectHobby(params)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
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
        val snapshotToken = userSession.currentToken
        val response = api.getUserCollectCommon(params)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
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
        val snapshotToken = userSession.currentToken
        val response = api.getUserCollectAlbum(params)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
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
        val snapshotToken = userSession.currentToken
        val response = api.getUserCollectPic(params)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
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
        val snapshotToken = userSession.currentToken
        val response = api.getUserPraiseForUser(userId = userId, page = page, pageSize = pageSize)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
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
        val snapshotToken = userSession.currentToken
        val response = api.updateUserBase(key = key, value = value)
        if (response.isSuccess) {
            if (autoRefresh) {
                getUserProfile()
            }
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
            }
            throw ApiException(response.code, response.msg ?: "更新用户资料失败")
        }
    }

    /**
     * 获取当前登录用户档案（转发至 [UserSession]）。
     */
    suspend fun getUserProfile(): Result<UserProfile> = userSession.refreshProfile()

    /**
     * 查询指定用户的详细资料 (api/user/getinfo)。
     * 优先尝试主节点 ID (id)，若未匹配则回退尝试账号 ID (userId)，双重保障兼顾节点 ID 与历史账号 ID。
     */
    suspend fun getUserInfo(userId: Long): Result<UserProfile> = runCatching {
        val snapshotToken = userSession.currentToken
        val idResponse = try {
            api.getUserInfo(id = userId)
        } catch (_: Exception) {
            null
        }
        val response = if (idResponse != null && (idResponse.isSuccess || (idResponse.success && idResponse.data?.user != null))) {
            idResponse
        } else {
            api.getUserInfo(userId = userId)
        }
        if (response.isSuccess || (response.success && response.data?.user != null)) {
            response.data?.user ?: throw ApiException(response.code, "用户信息为空")
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
            }
            throw ApiException(response.code, response.msg ?: "获取用户信息失败")
        }
    }

    companion object {
        /** 周边列表排序：升序 */
        const val SORT_TYPE_ASC = 1

        /** 周边列表排序：降序（原版默认值） */
        const val SORT_TYPE_DESC = 2
    }
}
