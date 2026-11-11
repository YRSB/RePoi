package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.UserHistoryItem
import com.nobody.repoi.data.session.UserSession
import kotlinx.coroutines.CancellationException

/**
 * 用户浏览足迹数据仓储。
 * 负责足迹列表查询、浏览足迹记录与足迹删除。
 *
 * 列表解析阶段做**前置主动重写**：接口返回的 `cover` 是写入快照，服务端重新落盘后旧路径必然 404。
 * 解析成功后先 [CoverFreshnessRepository.ensureWarmed]（冷启动读盘落定，幂等），再把每条命中本地
 * 已确认封面的快照重写为最新路径。命中后冷启动零 404、零详情请求、零闪烁；未命中保留原快照，
 * 由 UI 的按条回源自愈。
 */
class UserHistoryRepository(
    private val api: HpoiApi,
    private val userSession: UserSession,
    private val coverFreshnessRepository: CoverFreshnessRepository
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
            val readList = response.data?.readList ?: emptyList()
            rewriteCovers(readList, itemType)
        } else {
            if (response.isTokenExpired) {
                userSession.notifyTokenExpired(snapshotToken)
            }
            throw ApiException(response.code, response.msg ?: "获取浏览历史失败")
        }
    }

    /**
     * 前置主动重写：把每条历史记录中命中本地已确认封面的快照替换为最新路径。
     *
     * 冷启动时持久化预热可能尚未落定，因此先等待 [CoverFreshnessRepository.ensureWarmed]（幂等、不挂死）。
     * 单条解析异常只跳过该条，绝不丢弃整个列表；未命中或封面相同都原样保留服务端快照。
     */
    private suspend fun rewriteCovers(
        items: List<UserHistoryItem>,
        requestedItemType: String
    ): List<UserHistoryItem> {
        if (items.isEmpty()) return items
        coverFreshnessRepository.ensureWarmed()
        return items.map { item -> rewriteCover(item, requestedItemType) }
    }

    private fun rewriteCover(
        item: UserHistoryItem,
        requestedItemType: String
    ): UserHistoryItem {
        return try {
            val type = resolveEntityType(item.itemType, requestedItemType) ?: return item
            val entityId = when (type) {
                CoverFreshnessRepository.EntityType.HOBBY -> item.itemId
                CoverFreshnessRepository.EntityType.ALBUM -> item.nodeId
            }
            val cachedCover = coverFreshnessRepository.getCachedCover(entityId, type)
            if (cachedCover != null && cachedCover != item.cover) item.copy(cover = cachedCover) else item
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            item
        }
    }

    /** 条目自带的有效 `itemType` 优先，缺失时回退到请求参数；未知类型返回 `null` 表示不重写。 */
    private fun resolveEntityType(
        itemType: String?,
        requestedItemType: String
    ): CoverFreshnessRepository.EntityType? {
        val effective = itemType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: requestedItemType.trim().lowercase()
        return when (effective) {
            "hobby" -> CoverFreshnessRepository.EntityType.HOBBY
            "album" -> CoverFreshnessRepository.EntityType.ALBUM
            else -> null
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
