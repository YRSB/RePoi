package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.UserHistoryItem
import com.nobody.repoi.data.session.UserSession

/**
 * 用户浏览足迹数据仓储。
 * 负责足迹列表查询、浏览足迹记录与足迹删除。
 */
class UserHistoryRepository(
    private val api: HpoiApi,
    private val userSession: UserSession
) {
    /**
     * 获取用户浏览历史列表 (api/user/read/item/get)。
     * @param itemType "hobby" (手办) / "album" (相册)
     * @param page 分页页码，从 1 开始
     */
    suspend fun getUserHistoryList(
        itemType: String,
        page: Int = 1
    ): Result<List<UserHistoryItem>> = runCatching {
        val snapshotToken = userSession.currentToken
        val response = api.getUserHistoryList(itemType = itemType, page = page)
        if (response.isSuccess || (response.success && response.data != null)) {
            response.data?.readList ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
            }
            throw ApiException(response.code, response.msg ?: "获取浏览历史失败")
        }
    }

    /**
     * 上报/添加浏览足迹 (api/user/read/item/add)。
     * @param itemNodeId 手办条目节点 ID 或相册 ID
     */
    suspend fun addUserHistory(itemNodeId: Long): Result<Unit> = runCatching {
        if (itemNodeId <= 0L) return@runCatching
        val snapshotToken = userSession.currentToken
        val response = api.addUserHistory(itemNodeId = itemNodeId)
        if (!response.isSuccess && !response.success) {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
            }
            throw ApiException(response.code, response.msg ?: "添加浏览历史失败")
        }
    }

    /**
     * 批量删除浏览历史 (api/user/read/item/del)。
     * @param historyIds 浏览历史记录条目的记录 ID 列表
     */
    suspend fun deleteUserHistory(historyIds: List<Long>): Result<Unit> = runCatching {
        if (historyIds.isEmpty()) return@runCatching
        val snapshotToken = userSession.currentToken
        val idsJson = historyIds.joinToString(separator = ",", prefix = "[", postfix = "]")
        val response = api.deleteUserHistory(ids = idsJson)
        if (!response.isSuccess && !response.success) {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
            }
            throw ApiException(response.code, response.msg ?: "删除浏览历史失败")
        }
    }
}
