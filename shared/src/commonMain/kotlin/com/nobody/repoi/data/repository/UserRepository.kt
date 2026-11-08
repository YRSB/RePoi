package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CheckInStatusPayload
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.CollectSimilarityPayload
import com.nobody.repoi.data.remote.model.CollectTopItem
import com.nobody.repoi.data.remote.model.FellowCountData
import com.nobody.repoi.data.remote.model.OrderCount
import com.nobody.repoi.data.remote.model.OrderFinanceSummary
import com.nobody.repoi.data.remote.model.UserCollectAlbumItem
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.remote.model.UserCollectCommonItem
import com.nobody.repoi.data.remote.model.UserCollectHobbyItem
import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.data.remote.model.UserMpPayload
import com.nobody.repoi.data.remote.model.UserPraiseItem
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.data.session.SessionState
import com.nobody.repoi.data.session.UserSession
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** 兼容类型别名（原 FriendState / FriendChangeEvent 已收敛至 FollowRepository） */
typealias FriendState = com.nobody.repoi.data.repository.FriendState
typealias FriendChangeEvent = com.nobody.repoi.data.repository.FriendChangeEvent

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
 * 用户创作相册分页响应模型。
 */
data class UserCreationAlbumPage(
    val list: List<AlbumListItem>,
    val hasMore: Boolean
)

/**
 * 用户创作图片分页响应模型。
 */
data class UserCreationPicPage(
    val list: List<UserCollectPicItem>,
    val hasMore: Boolean
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
        val response = api.getCollectTop(userId = userId, state = state)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取展柜列表失败")
        }
    }

    /**
     * 获取用户相册简要列表 (api/user/album)。
     */
    suspend fun getUserAlbumList(userId: Long, page: Int = 1, pageSize: Int = 6): Result<List<AlbumListItem>> = runCatching {
        val response = api.getUserAlbum(userId = userId, page = page, pageSize = pageSize)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取用户相册列表失败")
        }
    }

    /**
     * 获取用户创作/发布相册列表 (api/user/album)。
     */
    suspend fun getUserCreationAlbumList(
        userId: Long,
        page: Int = 1,
        pageSize: Int = 10
    ): Result<UserCreationAlbumPage> = runCatching {
        val response = api.getUserAlbum(userId = userId, page = page, pageSize = pageSize)
        if (response.isSuccess || (response.success && response.data != null)) {
            val rawList = response.data?.list ?: emptyList()
            UserCreationAlbumPage(
                list = rawList,
                hasMore = rawList.size >= pageSize
            )
        } else {
            throw ApiException(response.code, response.msg ?: "获取用户发布相册失败")
        }
    }

    /**
     * 获取用户创作/发布图片列表 (api/user/pic)。
     */
    suspend fun getUserCreationPicList(
        userId: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): Result<UserCreationPicPage> = runCatching {
        val response = api.getUserPic(userId = userId, page = page, pageSize = pageSize)
        if (response.isSuccess || (response.success && response.data != null)) {
            val rawList = response.data?.list ?: emptyList()
            UserCreationPicPage(
                list = rawList,
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
     */
    suspend fun getUserCollectHobbyList(
        userId: Long,
        page: Int = 1,
        pageSize: Int = 50,
        category: Int = CollectConstants.CATEGORY_ALL,
        collectState: String? = null,
        order: String = "actionDate",
        sortType: Int = SORT_TYPE_DESC,
        subCategory: String? = null
    ): Result<List<UserCollectHobbyItem>> = runCatching {
        val snapshotToken = userSession.currentToken
        val effectiveCare: String?
        val effectiveState: String?
        if (collectState == "care") {
            effectiveCare = "care"
            effectiveState = ""
        } else {
            effectiveCare = null
            effectiveState = collectState
        }

        val effectiveOrder = if (order == "orderTime" && collectState != "buy") {
            "actionDate"
        } else {
            order
        }

        val response = api.getUserCollectHobbyList(
            userId = userId,
            page = page,
            pageSize = pageSize,
            category = category,
            state = effectiveState,
            care = effectiveCare,
            order = effectiveOrder,
            sortType = sortType,
            subCategory = subCategory
        )

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
     * 查询用户通用实体收藏列表 (api/collect/common, GET)。
     */
    suspend fun getUserCollectCommonList(
        userId: Long,
        category: UserCollectCategory,
        page: Int = 1,
        pageSize: Int = 50,
        order: String = "actionDate",
        sortType: Int = SORT_TYPE_DESC,
        type: String? = null
    ): Result<List<UserCollectCommonItem>> = runCatching {
        val snapshotToken = userSession.currentToken
        val effectiveType = type ?: category.defaultType
        val response = api.getUserCollectCommonList(
            userId = userId,
            page = page,
            pageSize = pageSize,
            category = category.paramValue,
            order = order,
            sortType = sortType,
            type = effectiveType
        )

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
     * 查询用户相册收藏列表 (api/collect/album, GET)。
     */
    suspend fun getUserCollectAlbumList(
        userId: Long,
        page: Int = 1,
        pageSize: Int = 50,
        order: String = "actionDate",
        sortType: Int = SORT_TYPE_DESC
    ): Result<List<UserCollectAlbumItem>> = runCatching {
        val snapshotToken = userSession.currentToken
        val response = api.getUserCollectAlbumList(
            userId = userId,
            page = page,
            pageSize = pageSize,
            order = order,
            sortType = sortType
        )

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
     * 查询用户图片收藏列表 (api/collect/pic, GET)。
     */
    suspend fun getUserCollectPicList(
        userId: Long,
        page: Int = 1,
        pageSize: Int = 50,
        order: String = "addTime",
        sortType: Int = SORT_TYPE_DESC
    ): Result<List<UserCollectPicItem>> = runCatching {
        val snapshotToken = userSession.currentToken
        val response = api.getUserCollectPicList(
            userId = userId,
            page = page,
            pageSize = pageSize,
            order = order,
            sortType = sortType
        )

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
     * 获取与其他用户的收藏契合度 (api/collect/similarity)。
     */
    suspend fun getCollectSimilarity(userId: Long): Result<Int> = runCatching {
        val response = api.getCollectSimilarity(toUserId = userId)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.similarity ?: 0
        } else {
            throw ApiException(response.code, response.msg ?: "获取收藏契合度失败")
        }
    }

    /**
     * 获取用户近期动态列表 (api/user/action/list)。
     */
    suspend fun getUserActionList(userId: Long, pageSize: Int = 20, lastTime: Long = 0L): Result<List<ActionItem>> = runCatching {
        val response = api.getUserActionList(userId = userId, pageSize = pageSize, lastTime = lastTime)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            throw ApiException(response.code, response.msg ?: "获取用户动态列表失败")
        }
    }

    /**
     * 获取用户获赞条目列表 (api/item/praise/forUser)。
     */
    suspend fun getUserPraiseList(
        userId: Long,
        page: Int,
        pageSize: Int = 20
    ): Result<List<UserPraiseItem>> = runCatching {
        val snapshotToken = userSession.currentToken
        val response = api.getUserPraiseList(userId = userId, page = page, pageSize = pageSize)
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
     */
    suspend fun updateUserBase(key: String, value: String, autoRefresh: Boolean = true): Result<Unit> = runCatching {
        val snapshotToken = userSession.currentToken
        val response = api.updateUserBase(key = key, value = value)
        if (response.isSuccess) {
            if (autoRefresh) {
                userSession.refreshProfile()
            }
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
            }
            throw ApiException(response.code, response.msg ?: "更新用户资料失败")
        }
    }

    /**
     * 查询指定用户的详细资料 (api/user/getinfo)。
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
        const val SORT_TYPE_ASC = 1
        const val SORT_TYPE_DESC = 2
    }
}
