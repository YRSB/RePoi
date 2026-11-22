package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.ExHobbyApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.ExHobbyImageItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ExHobbyRepository(
    private val api: ExHobbyApi
) {
    companion object {
        const val MAX_CACHE_PAGES = 30
    }

    private val cacheMutex = Mutex()
    private val pageCache = mutableMapOf<Pair<Long, Int>, List<ExHobbyImageItem>>()

    suspend fun getPictures(id: Long, page: Int = 1): Result<List<ExHobbyImageItem>> = runCatching {
        val cacheKey = id to page
        val cached = cacheMutex.withLock { pageCache[cacheKey] }
        if (cached != null) {
            return@runCatching cached
        }

        try {
            val list = api.getPictureList(
                id = id,
                page = page,
                item = id,
                type = "all"
            )
            cacheMutex.withLock {
                if (!pageCache.containsKey(cacheKey) && pageCache.size >= MAX_CACHE_PAGES) {
                    val oldestKey = pageCache.keys.firstOrNull()
                    if (oldestKey != null) {
                        pageCache.remove(oldestKey)
                    }
                }
                pageCache[cacheKey] = list
            }
            list
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(code = -1, message = e.message ?: "拉取 ExHobby 扩展图库失败")
        }
    }

    suspend fun clearCache(id: Long? = null) {
        cacheMutex.withLock {
            if (id == null) {
                pageCache.clear()
            } else {
                pageCache.keys.removeIf { (cachedId, _) -> cachedId == id }
            }
        }
    }
}
