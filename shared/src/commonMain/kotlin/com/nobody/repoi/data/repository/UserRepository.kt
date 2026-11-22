package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CheckInStatusPayload
import com.nobody.repoi.data.remote.model.CollectTopItem
import com.nobody.repoi.data.remote.model.FellowCountData
import com.nobody.repoi.data.remote.model.OrderCount
import com.nobody.repoi.data.remote.model.OrderFinanceSummary
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.data.remote.model.UserPraiseItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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

class UserRepository(
    private val api: HpoiApi,
    private val followRepository: FollowRepository,
    private val userFinanceRepository: UserFinanceRepository,
) {

    suspend fun getCollectTop(userId: Long, state: String): Result<List<CollectTopItem>> = runCatching {
        api.getCollectTop(id = userId, state = state)?.list ?: emptyList()
    }

    suspend fun getUserAlbumList(userId: Long, page: Int = 1, pageSize: Int = 6): Result<List<AlbumListItem>> = runCatching {
        api.getUserAlbumList(userNodeId = userId, page = page, pageSize = pageSize)
            ?.list?.map { it.toAlbumListItem() } ?: emptyList()
    }

    suspend fun getCheckInStatus(): Result<CheckInStatusPayload> = runCatching {
        api.getCheckInStatus() ?: CheckInStatusPayload()
    }

    suspend fun checkIn(): Result<Unit> = runCatching {
        api.checkIn()
    }

    suspend fun getUserMp(): Result<Int> = runCatching {
        api.getUserMp()?.mp ?: 0
    }

    suspend fun loadFullProfile(profile: UserProfile): Result<FullProfileData> = runCatching {
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

    suspend fun getCollectSimilarity(userId: Long): Result<Int> =
        runCatching { api.getCollectSimilarity(userNodeId = userId)?.similarity ?: 0 }

    suspend fun getUserActionList(userId: Long, pageSize: Int = 20, lastTime: Long = 0L): Result<List<ActionItem>> =
        runCatching { api.getUserActionList(userNodeId = userId, pageSize = pageSize, lastTime = lastTime)?.list ?: emptyList() }

    suspend fun getUserPraiseList(
        userId: Long,
        page: Int,
        pageSize: Int = 20
    ): Result<List<UserPraiseItem>> = runCatching {
        api.getUserPraiseForUser(userId = userId, page = page, pageSize = pageSize)?.list ?: emptyList()
    }

    suspend fun updateUserBase(key: String, value: String): Result<Unit> = runCatching {
        api.updateUserBase(key = key, value = value)
    }

    suspend fun getUserInfo(userId: Long): Result<UserProfile> = runCatching {
        val idPayload = runCatching { api.getUserInfo(id = userId) }.getOrNull()
        val payload = if (idPayload?.user != null) idPayload else api.getUserInfo(userId = userId)
        payload?.user ?: throw ApiException(-9005, "用户信息为空")
    }
}
