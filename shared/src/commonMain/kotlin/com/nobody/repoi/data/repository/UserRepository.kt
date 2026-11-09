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
 * 收藏列表读写已归属 [CollectRepository]（收藏数据唯一真相源），本仓储保留同名方法仅作过渡委托，
 * 待调用方迁移完成后删除；自身专注于用户信息查询、签到打卡、魔力值积分、个人资料修改与收藏展柜列表。
 */
class UserRepository(
    private val api: HpoiApi,
    private val userSession: UserSession,
    private val followRepository: FollowRepository,
    private val userFinanceRepository: UserFinanceRepository,
    private val collectRepository: CollectRepository
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

    // ── 收藏列表过渡委托：数据读取已归属 CollectRepository，此处仅保留同名入口以免牵动调用方（T4 删除） ──

    /**
     * 查询用户全量手办收藏列表 (api/collect/hobby-v2, GET)。
     * @see CollectRepository.getUserCollectHobbyList
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
    ): Result<List<UserCollectHobbyItem>> = collectRepository.getUserCollectHobbyList(
        userId = userId,
        collectState = collectState,
        category = category,
        tag = tag,
        order = order,
        sortType = sortType,
        page = page,
        pageSize = pageSize
    )

    /**
     * 查询用户通用实体收藏列表 (厂商/系列/作品/角色/人物) (api/collect/common-v3, GET)。
     * @see CollectRepository.getUserCollectCommonList
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
    ): Result<List<UserCollectCommonItem>> = collectRepository.getUserCollectCommonList(
        userId = userId,
        categoryId = categoryId,
        type = type,
        order = order,
        page = page,
        pageSize = pageSize,
        state = state,
        isAsc = isAsc,
        tagValue = tagValue
    )

    /**
     * 查询用户相册收藏列表 (api/collect/album-v2, GET)。
     * @see CollectRepository.getUserCollectAlbumList
     */
    suspend fun getUserCollectAlbumList(
        userId: Long,
        type: String = "fav",
        order: String = CollectConstants.ORDER_ADD_TIME,
        page: Int,
        pageSize: Int = 20
    ): Result<List<UserCollectAlbumItem>> = collectRepository.getUserCollectAlbumList(
        userId = userId,
        type = type,
        order = order,
        page = page,
        pageSize = pageSize
    )

    /**
     * 查询用户图片收藏列表 (api/collect/pic-v2, GET)。
     * @see CollectRepository.getUserCollectPicList
     */
    suspend fun getUserCollectPicList(
        userId: Long,
        collectState: String = "collect",
        order: String = CollectConstants.ORDER_ADD_TIME,
        page: Int,
        pageSize: Int = 20
    ): Result<List<UserCollectPicItem>> = collectRepository.getUserCollectPicList(
        userId = userId,
        collectState = collectState,
        order = order,
        page = page,
        pageSize = pageSize
    )

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
     * 查询指定用户的详细资料 (api/user/getinfo).
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
