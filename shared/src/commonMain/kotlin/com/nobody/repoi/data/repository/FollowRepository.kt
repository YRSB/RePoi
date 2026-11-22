package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.ActionListData
import com.nobody.repoi.data.remote.model.FellowCountData
import com.nobody.repoi.data.remote.model.JsonRefResolver
import com.nobody.repoi.data.remote.model.UserFellowItem
import com.nobody.repoi.platform.DateTimeFormats
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

enum class FriendState(val code: Int) {
    FOLLOWING(0),
    MUTUAL(2),
    NOT_FOLLOWING(5),
    UNKNOWN(-1);

    val isFollowed: Boolean get() = this == FOLLOWING || this == MUTUAL

    companion object {
        fun fromCode(code: Int): FriendState = entries.find { it.code == code } ?: UNKNOWN
    }
}

data class FriendChangeEvent(
    val userId: Long,
    val isFollow: Boolean,
    val state: FriendState = if (isFollow) FriendState.FOLLOWING else FriendState.NOT_FOLLOWING
)

data class FollowPage(
    val items: List<ActionItem> = emptyList(),
    val lastId: Long = 0L,
    val hasMore: Boolean = true
)

class FollowRepository(
    private val api: HpoiApi,
    private val json: Json,
) {
    private val _friendChangeEvent = MutableSharedFlow<FriendChangeEvent>(extraBufferCapacity = 64)
    val friendChangeEvent: SharedFlow<FriendChangeEvent> = _friendChangeEvent.asSharedFlow()

    suspend fun getFollowFeed(
        type: String = "item",
        actionType: Int? = null,
        subType: String? = null,
        lastId: Long = 0L,
        pageSize: Int = 20
    ): Result<FollowPage> = runCatching {
        val payload = api.getFollowDynamics(
            page = if (type == "item") 1 else null,
            lastId = lastId,
            pageSize = pageSize,
            actionType = actionType,
            subType = subType,
            type = type,
            isHome = if (type == "item") 0 else null
        )
        val list = decodeList(payload)
        FollowPage(
            items = list,
            lastId = parseFollowCursor(list.lastOrNull()?.actionDate),
            hasMore = list.isNotEmpty()
        )
    }

    suspend fun getFellowCount(userId: Long): Result<FellowCountData> = runCatching {
        api.getFellowCount(userNodeId = userId)?.data ?: FellowCountData()
    }

    suspend fun getFellowType(userId: Long): Result<FriendState> = runCatching {
        FriendState.fromCode(api.getFellowType(userNId = userId)?.friendState ?: -1)
    }

    suspend fun updateFriend(userId: Long, state: FriendState): Result<Unit> = runCatching {
        api.updateFriend(userId = userId, state = state.code)
        _friendChangeEvent.emit(
            FriendChangeEvent(
                userId = userId,
                isFollow = state.isFollowed,
                state = state
            )
        )
    }

    suspend fun updateFriend(userId: Long, isFollow: Boolean): Result<Unit> {
        val targetState = if (isFollow) FriendState.FOLLOWING else FriendState.NOT_FOLLOWING
        return updateFriend(userId = userId, state = targetState)
    }

    suspend fun getUserFellowList(
        userId: Long,
        type: Int,
        page: Int,
        pageSize: Int = 20
    ): Result<List<UserFellowItem>> = runCatching {
        api.getUserFellow(userId = userId, type = type, page = page, pageSize = pageSize)?.list ?: emptyList()
    }

    internal fun decodeList(data: JsonObject?): List<ActionItem> {
        if (data == null) return emptyList()
        val resolved = JsonRefResolver.resolveRefs(data)
        return json.decodeFromJsonElement<ActionListData>(resolved).list ?: emptyList()
    }
}

internal fun parseFollowCursor(dateStr: String?): Long {
    if (dateStr.isNullOrBlank()) return 0L
    dateStr.toLongOrNull()?.let { return it }
    return DateTimeFormats.parseServerDateTime(dateStr) ?: 0L
}
