package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.ActionListData
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.FellowCountData
import com.nobody.repoi.data.remote.model.JsonRefResolver
import com.nobody.repoi.data.remote.model.UserFellowItem
import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.platform.DateTimeFormats
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

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
 * 好友/关注状态变更广播事件。
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
 * 关注动态分页结果（含翻页游标）。
 */
data class FollowPage(
    val items: List<ActionItem> = emptyList(),
    val lastId: Long = 0L,
    val hasMore: Boolean = true
)

/**
 * 关注条目/用户与社交好友关系数据仓储。
 * 提供关注屏动态分页、用户关注状态查询、关注取关操作、社交列表查询与跨页面好友状态变更广播。
 */
class FollowRepository(
    private val api: HpoiApi,
    private val json: Json,
    private val userSession: UserSession
) {
    private val _friendChangeEvent = MutableSharedFlow<FriendChangeEvent>(extraBufferCapacity = 64)
    val friendChangeEvent: SharedFlow<FriendChangeEvent> = _friendChangeEvent.asSharedFlow()

    /**
     * 拉取关注动态分页（条目动态/用户动态共用，翻页靠 lastId）。
     */
    suspend fun getFollowFeed(
        type: String = "item",
        actionType: Int? = null,
        subType: String? = null,
        lastId: Long = 0L,
        pageSize: Int = 20
    ): Result<FollowPage> = runCatching {
        val response = api.getFollowDynamics(
            page = if (type == "item") 1 else null,
            lastId = lastId,
            pageSize = pageSize,
            actionType = actionType,
            subType = subType,
            type = type,
            isHome = if (type == "item") 0 else null
        )
        if (!response.isSuccess) throw ApiException(response.code, response.msg ?: "数据加载失败")
        val list = decodeList(response.data)
        FollowPage(
            items = list,
            lastId = parseFollowCursor(list.lastOrNull()?.actionDate),
            hasMore = list.isNotEmpty()
        )
    }

    /**
     * 获取指定用户的关注数与粉丝数 (api/user/fellow/count)。
     */
    suspend fun getFellowCount(userId: Long): Result<FellowCountData> = runCatching {
        val response = api.getFellowCount(userNodeId = userId)
        if (response.isSuccess || (response.success && response.data?.data != null)) {
            response.data?.data ?: FellowCountData()
        } else {
            throw ApiException(response.code, response.msg ?: "获取关注粉丝数失败")
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
        val snapshotToken = userSession.currentToken
        val response = api.updateFriend(userId = userId, state = state.code)
        if (response.isSuccess) {
            _friendChangeEvent.emit(
                FriendChangeEvent(
                    userId = userId,
                    isFollow = state.isFollowed,
                    state = state
                )
            )
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
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
        val snapshotToken = userSession.currentToken
        val response = api.getUserFellow(userId = userId, type = type, page = page, pageSize = pageSize)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.list ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
            }
            throw ApiException(response.code, response.msg ?: "获取好友列表失败")
        }
    }

    /**
     * 解码列表载荷（先还原服务端 `@id`/`@ref` 引用去重，再转 DTO）。
     */
    internal fun decodeList(data: JsonObject?): List<ActionItem> {
        if (data == null) return emptyList()
        val resolved = JsonRefResolver.resolveRefs(data)
        return json.decodeFromJsonElement<ActionListData>(resolved).list ?: emptyList()
    }
}

/**
 * 解析翻页游标（兼容数字时间戳与日期字符串两种格式）。
 */
internal fun parseFollowCursor(dateStr: String?): Long {
    if (dateStr.isNullOrBlank()) return 0L
    dateStr.toLongOrNull()?.let { return it }
    return DateTimeFormats.parseServerDateTime(dateStr) ?: 0L
}
