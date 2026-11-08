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
     * 获取用户足迹列表 (api/user/history)。
     * @param type 0/1/2/hobby/album 等类型标识
     * @param page 分页页码（1 起始）
     */
    suspend fun getUserHistoryList(
        type: String,
        page: Int
    ): Result<List<UserHistoryItem>> = runCatching {
        val snapshotToken = userSession.currentToken
        val response = api.getUserHistoryList(type = type, page = page)
        if (response.isSuccess || (response.success && response.data?.historyList != null)) {
            response.data?.historyList ?: emptyList()
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
            }
            throw ApiException(response.code, response.msg ?: "获取用户足迹列表失败")
        }
    }

    /**
     * 上报浏览记录 (api/user/history/add)。
     * @param itemNodeId 浏览条目的全局节点 ID
     */
    suspend fun addUserHistory(itemNodeId: Long): Result<Unit> = runCatching {
        val snapshotToken = userSession.currentToken
        val response = api.addUserHistory(itemNodeId = itemNodeId)
        if (response.isSuccess || response.success) {
            Unit
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
            }
            throw ApiException(response.code, response.msg ?: "添加足迹失败")
        }
    }

    /**
     * 删除浏览历史 (api/user/history/del)。
     * @param historyIds 待删除的足迹记录 ID 列表
     */
    suspend fun deleteUserHistory(historyIds: List<Long>): Result<Unit> = runCatching {
        if (historyIds.isEmpty()) return@runCatching
        val snapshotToken = userSession.currentToken
        val response = api.deleteUserHistory(historyIds = historyIds.joinToString(","))
        if (response.isSuccess || response.success) {
            Unit
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
            }
            throw ApiException(response.code, response.msg ?: "删除足迹失败")
        }
    }
}
