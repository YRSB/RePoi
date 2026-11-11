package com.nobody.repoi.data.repository

import com.nobody.repoi.platform.nowMillis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 封面新鲜度修复仓储。
 *
 * 解决的问题：列表接口（浏览历史、缓存快照等）返回的 `cover` 是**写入那一刻的相对路径**，
 * 服务端重新落盘封面后旧路径必然 404，而详情接口（`api/item/get-v2` / `api/album/detail`）
 * 每次都会返回当前真实封面。因此「列表封面加载失败 → 按条回详情取一次」是唯一可靠的修复路径。
 *
 * 设计约束（重要，别随手放松）：
 * - **按条修复**：只在图片真的加载失败时被调用，正常滚动与翻页零额外请求；
 * - **单飞**：同一实体的并发修复请求只发一次，其余调用方共享同一个结果；
 * - **负缓存**：服务端确实没有封面（返回空）时窗口期内不再重试，避免每次滚动都打接口；
 * - **详情页沉淀**：[rememberFreshCover] 让「刚进过详情页」的封面直接命中，连修复请求都省掉。
 *
 * 本类只负责「取到当前真实封面」这一件事，不持有任何 UI 状态与协程作用域。
 */
class CoverFreshnessRepository(
    private val hobbyDetailRepository: HobbyDetailRepository,
    private val albumDetailRepository: AlbumDetailRepository,
    private val clock: () -> Long = ::nowMillis,
) {

    /** 封面所属实体类型（与浏览历史的两大分类一一对应）。 */
    enum class EntityType { HOBBY, ALBUM }

    /** 已确认的当前封面（实体键 → 相对路径），按插入序 FIFO 淘汰。 */
    private val covers = object : LinkedHashMap<String, CachedCover>(MAX_COVERS, 0.75f) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedCover>?): Boolean =
            size > MAX_COVERS
    }

    /** 负缓存：该实体在 [NEGATIVE_TTL_MS] 内被判定为「服务端也没有封面」。 */
    private val negativeUntil = object : LinkedHashMap<String, Long>(MAX_COVERS, 0.75f) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > MAX_COVERS
    }

    /** 在途修复请求（单飞）。 */
    private val inFlight = mutableMapOf<String, CompletableDeferred<Result<String?>>>()

    /** 保护 [covers] / [negativeUntil] / [inFlight] 三张表的并发读写。 */
    private val guard = Mutex()

    /**
     * 取某实体当前真实的封面路径。
     *
     * @param id 业务实体 ID（手办为 `itemId`，相册为 `albumId`）
     * @param type 实体类型
     * @return 成功时携带封面相对路径（服务端未提供时为 `null`）；
     *   网络/接口失败时为 `failure`（不写负缓存，允许后续重试）
     */
    suspend fun freshCover(id: Long, type: EntityType): Result<String?> {
        if (id <= 0L) return Result.success(null)
        val key = cacheKey(id, type)

        // 1) 命中已确认封面（含 TTL）
        guard.withLock { readFreshCoverLocked(key) }?.let { cached ->
            return Result.success(cached)
        }

        // 2) 命中负缓存：直接放弃，不再打接口
        if (guard.withLock { isNegativeLocked(key) }) return Result.success(null)

        // 3) 单飞：同一实体的并发修复共享结果
        val (isOwner, deferred) = guard.withLock {
            val existing = inFlight[key]
            if (existing != null) {
                false to existing
            } else {
                val created = CompletableDeferred<Result<String?>>()
                inFlight[key] = created
                true to created
            }
        }
        if (!isOwner) return deferred.await()

        // 单飞占位必须在所有出口清理并唤醒等待者，否则该实体会被永久卡在 inFlight 表里；
        // 清理阶段放进 NonCancellable，避免调用方会话被取消时把等待者一起悬空。
        var outcome: Result<String?> = Result.failure(IllegalStateException("封面修复未完成"))
        try {
            outcome = fetch(id, type)
            val freshCover = outcome.getOrNull()
            guard.withLock {
                when {
                    // 网络/接口失败不写负缓存：可能只是瞬时抖动，允许下次滚动再试
                    outcome.isFailure -> Unit
                    // 成功但服务端也没有封面：窗口期内不再重试，避免空请求轰炸
                    freshCover.isNullOrBlank() -> recordNegativeLocked(key)
                    else -> {
                        covers[key] = CachedCover(freshCover, clock())
                        negativeUntil.remove(key)
                    }
                }
            }
        } finally {
            withContext(NonCancellable) {
                guard.withLock { inFlight.remove(key) }
                deferred.complete(outcome)
            }
        }
        return outcome
    }

    /**
     * 详情页拿到最新数据时顺手沉淀，供列表页直接命中，避免一次多余的修复请求。
     *
     * 非挂起：Compose 回调线程不可阻塞，抢不到锁就跳过（下次会走接口修复），不影响正确性。
     */
    fun rememberFreshCover(id: Long, type: EntityType, cover: String?) {
        if (id <= 0L) return
        val normalized = cover?.trim()?.ifBlank { null } ?: return
        val key = cacheKey(id, type)
        if (!guard.tryLock()) return
        try {
            covers[key] = CachedCover(normalized, clock())
            negativeUntil.remove(key)
        } finally {
            guard.unlock()
        }
    }

    /** 清理全部缓存（测试与登出场景使用）。 */
    fun clear() {
        if (!guard.tryLock()) return
        try {
            covers.clear()
            negativeUntil.clear()
            inFlight.clear()
        } finally {
            guard.unlock()
        }
    }

    // ------------------------------------------------------------------ 内部实现

    private suspend fun fetch(id: Long, type: EntityType): Result<String?> = when (type) {
        EntityType.HOBBY -> hobbyDetailRepository.getHobbyDetail(itemId = id, itemType = "hobby")
            .map { it.cover?.trim()?.ifBlank { null } }

        EntityType.ALBUM -> albumDetailRepository.getAlbumDetail(albumId = id)
            .map { it.cover?.trim()?.ifBlank { null } }
    }

    private fun cacheKey(id: Long, type: EntityType): String = "${type.name.lowercase()}:$id"

    private fun readFreshCoverLocked(key: String): String? {
        val cached = covers[key] ?: return null
        if (clock() - cached.recordedAt > FRESH_TTL_MS) {
            covers.remove(key)
            return null
        }
        return cached.cover
    }

    private fun isNegativeLocked(key: String): Boolean {
        val until = negativeUntil[key] ?: return false
        if (clock() >= until) {
            negativeUntil.remove(key)
            return false
        }
        return true
    }

    private fun recordNegativeLocked(key: String) {
        covers.remove(key)
        negativeUntil[key] = clock() + NEGATIVE_TTL_MS
    }

    private data class CachedCover(val cover: String, val recordedAt: Long)

    private companion object {
        /** 已确认封面的有效期：长期会话内仍允许在封面再次失效后重新取。 */
        const val FRESH_TTL_MS = 30L * 60L * 1000L

        /** 负缓存窗口：服务端也没有封面时别每次滚动都打接口。 */
        const val NEGATIVE_TTL_MS = 10L * 60L * 1000L

        /** 缓存容量上限（与 `ImageAspectRatioCache` 同量级，防无界增长）。 */
        const val MAX_COVERS = 300
    }
}
