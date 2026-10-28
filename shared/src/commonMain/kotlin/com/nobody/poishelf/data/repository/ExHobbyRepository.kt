package com.nobody.poishelf.data.repository

import com.nobody.poishelf.data.remote.api.ExHobbyApi
import com.nobody.poishelf.data.remote.model.ApiException
import com.nobody.poishelf.data.remote.model.ExHobbyImageItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ExHobby 扩展图库数据仓储。
 *
 * 遵循具体类单例规范与 runCatching Result<T> 包装，提供内存防重缓存与优雅降级。
 */
class ExHobbyRepository(
    private val api: ExHobbyApi
) {
    // 内存缓存：key = (id to page)，Mutex 守卫（commonMain 无 ConcurrentHashMap）
    private val cacheMutex = Mutex()
    private val pageCache = mutableMapOf<Pair<Long, Int>, List<ExHobbyImageItem>>()

    /**
     * 分页拉取 ExHobby 扩展图片。
     *
     * @param id 条目 ID (手办 nodeId 或相册 albumId)
     * @param page 分页页码，从 1 开始
     * @return 成功返回图片列表，翻页至末尾时返回空列表
     */
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
            cacheMutex.withLock { pageCache[cacheKey] = list }
            list
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(code = -1, message = e.message ?: "拉取 ExHobby 扩展图库失败")
        }
    }

    /**
     * 清理指定条目或全部内存缓存。
     */
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
