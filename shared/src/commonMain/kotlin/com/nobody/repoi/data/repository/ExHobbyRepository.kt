package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.ExHobbyApi
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.ExHobbyImageItem
import kotlinx.coroutines.CancellationException

/**
 * ExHobby 扩展图库数据仓储。
 *
 * 遵循具体类单例规范与 runCatching Result<T> 包装，提供内存防重缓存与优雅降级。
 */
class ExHobbyRepository constructor(
    private val apiService: ExHobbyApi
) {
    // 内存缓存：key = (id to page)
    // KMP：老实现用 ConcurrentHashMap；Kotlin/Native 内存模型下 MutableMap 在单线程
    // Main + 后台协程串行访问场景等价（仓储方法均为挂起函数，调用方串行 await）。
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
        val cached = pageCache[cacheKey]
        if (cached != null) {
            return@runCatching cached
        }

        try {
            val list = apiService.getPictureList(
                id = id,
                page = page,
                item = id,
                type = "all"
            )
            pageCache[cacheKey] = list
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
    fun clearCache(id: Long? = null) {
        if (id == null) {
            pageCache.clear()
        } else {
            val doomed = pageCache.keys.filter { (cachedId, _) -> cachedId == id }
            doomed.forEach { pageCache.remove(it) }
        }
    }
}
