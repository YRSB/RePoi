package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.UserHistoryItem
import kotlinx.coroutines.CancellationException

class UserHistoryRepository(
    private val api: HpoiApi,
    private val coverFreshnessRepository: CoverFreshnessRepository
) {

    suspend fun getUserHistoryList(
        itemType: String,
        page: Int = 1
    ): Result<List<UserHistoryItem>> = runCatching {
        val readList = api.getUserHistoryList(itemType = itemType, page = page)?.readList ?: emptyList()
        rewriteCovers(readList, itemType)
    }

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

                CoverFreshnessRepository.EntityType.HOBBY -> item.itemId.takeIf { it > 0L } ?: item.actualDetailId
                CoverFreshnessRepository.EntityType.ALBUM -> item.nodeId.takeIf { it > 0L } ?: item.actualDetailId
            }
            val cachedCover = coverFreshnessRepository.getCachedCover(entityId, type)
            if (cachedCover != null && cachedCover != item.cover) item.copy(cover = cachedCover) else item
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {

            item
        }
    }

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

    suspend fun addUserHistory(itemNodeId: Long): Result<Unit> = runCatching {
        if (itemNodeId <= 0L) return@runCatching
        api.addUserHistory(itemNodeId = itemNodeId)
    }

    suspend fun deleteUserHistory(historyIds: List<Long>): Result<Unit> = runCatching {
        if (historyIds.isEmpty()) return@runCatching
        val idsJson = historyIds.joinToString(separator = ",", prefix = "[", postfix = "]")
        api.deleteUserHistory(ids = idsJson)
    }
}
