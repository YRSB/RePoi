package com.nobody.repoi.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nobody.repoi.platform.nowMillis
import com.nobody.repoi.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 封面新鲜度仓储（内存镜像 + 持久化落盘）。
 *
 * 解决的问题：列表接口（浏览历史、缓存快照等）返回的 `cover` 是**写入那一刻的相对路径**，
 * 服务端重新落盘封面后旧路径必然 404，而详情接口（`api/item/get-v2` / `api/album/detail`）
 * 每次都会返回当前真实封面。因此「列表封面加载失败 → 按条回详情取一次」是唯一可靠的修复路径。
 *
 * 设计约束（重要，别随手放松）：
 * - **读写分离**：查询走 [getCachedCover]（同步、纯内存、零挂起）；写入先改内存再防抖异步落盘，
 *   任何调用线程都不做磁盘 I/O；
 * - **永久正缓存**：已确认封面不设时间 TTL，只受 [MAX_COVERS] 条容量约束，按插入序 FIFO 淘汰
 *   （覆写视为移到队尾）；
 * - **负缓存不落盘**：服务端确实没有封面时窗口期内不再重试，避免每次滚动都打接口；
 *   该状态只存在于内存，进程结束后即失效；
 * - **单飞**：同一实体的并发修复请求只发一次，其余调用方共享同一个结果；
 * - **穿透只穿透正缓存**：[freshCover] 的 `forceRefresh` 只跳过已确认封面，
 *   负缓存窗口仍然生效（否则服务端空白封面会形成请求风暴）；
 * - **详情页沉淀**：[rememberFreshCover] 让「刚进过详情页」的封面直接命中，连修复请求都省掉。
 *
 * 持久化格式：`cover_freshness.preferences_pb` 内的**单个** preferences key，
 * 值为按插入序编码的 `Map<String, String>` JSON（键 `"<type>:<id>"`，值为相对封面路径），
 * 1000 条时约 40KB。单 key 写入天然原子，不需要额外的分片或迁移逻辑。
 *
 * 本类不持有任何 UI 状态；[scope] 只承载预热与防抖落盘这两个内部协程。
 */
class CoverFreshnessRepository(
    private val hobbyDetailRepository: HobbyDetailRepository,
    private val albumDetailRepository: AlbumDetailRepository,
    private val preferences: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val clock: () -> Long = ::nowMillis,
) {

    /** 封面所属实体类型（与浏览历史的两大分类一一对应）。 */
    enum class EntityType { HOBBY, ALBUM }

    /**
     * 已确认封面的只读快照（实体键 → 相对路径），按插入序 FIFO 淘汰。
     *
     * 读路径必须同步、零挂起，因此采用「写时复制 + volatile 引用」：
     * 所有变更在 [guard] 内构造新的 `LinkedHashMap` 后整体替换，读者永远看到自洽的快照，
     * 不会观察到正在修改的哈希表。
     */
    @kotlin.concurrent.Volatile
    private var covers: Map<String, String> = emptyMap()

    /** 负缓存：该实体在 [NEGATIVE_TTL_MS] 内被判定为「服务端也没有封面」。仅内存。 */
    private val negativeUntil = object : LinkedHashMap<String, Long>(MAX_COVERS, 0.75f) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > MAX_COVERS
    }

    /** 在途修复请求（单飞）。 */
    private val inFlight = mutableMapOf<String, CompletableDeferred<Result<String?>>>()

    /** 保护 [covers] 的写时复制、[negativeUntil] 与 [inFlight] 的并发读写。 */
    private val guard = Mutex()

    /** 保护预热状态（[warmed] / [warmAttempt]）。 */
    private val warmGuard = Mutex()

    /** 首次预热是否成功完成；失败后保持 `false`，允许后续调用重试。 */
    @kotlin.concurrent.Volatile
    private var warmed: Boolean = false

    /** 进行中的预热尝试；并发调用者共享同一个信号，避免重复读盘。 */
    private var warmAttempt: CompletableDeferred<Unit>? = null

    /** 落盘信号：`CONFLATED` 保证连续写入只保留最新一次（快照在落盘时统一取最新）。 */
    private val persistSignal = Channel<Unit>(Channel.CONFLATED)

    /** 持久化值（单个 key 存有序映射的 JSON）。 */
    private val json = Json.Default
    private val coverMapSerializer = MapSerializer(String.serializer(), String.serializer())

    init {
        scope.launch { ensureWarmed() }
        scope.launch { persistLoop() }
    }

    /**
     * 确保首次读盘预热已落定。
     *
     * 幂等且并发安全：并发调用者等待同一个预热信号。失败（读盘或解析异常）不会抛给调用方，
     * 也不会挂死——本方法直接返回、缓存保持为空，下一次调用会重新尝试；
     * 所有缓存查询在预热完成前都安全返回 `null`。
     */
    suspend fun ensureWarmed() {
        val ticket = warmGuard.withLock {
            if (warmed) return
            val existing = warmAttempt
            if (existing != null) {
                WarmTicket(existing, isOwner = false)
            } else {
                CompletableDeferred<Unit>().let { created ->
                    warmAttempt = created
                    WarmTicket(created, isOwner = true)
                }
            }
        }
        if (!ticket.isOwner) {
            ticket.deferred.await()
            return
        }

        try {
            mergeWarmed(readPersistedCovers())
            warmed = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AppLogger.w("CoverFreshness", "预热封面新鲜度缓存失败：${error.message}")
        } finally {
            // 清占位与唤醒等待者必须在取消场景下也能完成，否则等待者会被永久悬空。
            withContext(NonCancellable) {
                warmGuard.withLock {
                    if (warmAttempt === ticket.deferred) warmAttempt = null
                }
                ticket.deferred.complete(Unit)
            }
        }
    }

    /**
     * 同步读取某实体已确认的封面路径（列表前置重写用）。
     *
     * 纯内存、零 I/O、不挂起；未预热或未命中时返回 `null`，调用方沿用服务端快照。
     */
    fun getCachedCover(id: Long, type: EntityType): String? {
        if (id <= 0L) return null
        return covers[cacheKey(id, type)]
    }

    /**
     * 取某实体当前真实的封面路径。
     *
     * @param id 业务实体 ID（手办为 `itemId`，相册为 `albumId`）
     * @param type 实体类型
     * @param forceRefresh 为 `true` 时**跳过正缓存**，直接发起详情单飞回源：
     *   - 已有同类在途请求则并入等待，不重复发请求；
     *   - 负缓存窗口（服务端明确无封面，10 分钟、纯内存）**仍然生效**，避免空白封面请求风暴；
     *   - 成功原子覆写内存正缓存并触发 500ms 防抖落盘；失败绝不修改旧正缓存。
     *
     * @return 成功时携带封面相对路径（服务端未提供时为 `null`）；
     *   网络/接口失败时为 `failure`（不写负缓存，允许后续重试）
     */
    suspend fun freshCover(
        id: Long,
        type: EntityType,
        forceRefresh: Boolean = false,
    ): Result<String?> {
        if (id <= 0L) return Result.success(null)
        val key = cacheKey(id, type)

        // 命中判定与单飞登记必须在同一临界区完成：分三次加锁时，owner 可能在两次加锁之间
        // 完成请求并清理在途表，调用方就会看不到刚写入的缓存/负缓存而重复发请求（单飞与负缓存同时失效）。
        val lookup = guard.withLock {
            val cached = covers[key]
            when {
                // 穿透只穿透正缓存；负缓存代表「服务端确实没有封面」，穿透它只会形成请求风暴。
                cached != null && !forceRefresh -> Lookup(cachedCover = cached)
                isNegativeLocked(key) -> Lookup(isNegative = true)
                else -> {
                    val existing = inFlight[key]
                    if (existing != null) {
                        Lookup(deferred = existing)
                    } else {
                        val created = CompletableDeferred<Result<String?>>()
                        inFlight[key] = created
                        Lookup(isOwner = true, deferred = created)
                    }
                }
            }
        }
        lookup.cachedCover?.let { return Result.success(it) }
        if (lookup.isNegative) return Result.success(null)
        val deferred = requireNotNull(lookup.deferred) { "单飞占位缺失" }
        if (!lookup.isOwner) return deferred.await()

        var shouldPersist = false
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
                    // 成功但服务端也没有封面：窗口期内不再重试，避免空请求轰炸；且绝不落盘。
                    // 若内存里还留着旧正缓存（并发沉淀窗口），需要一并落盘删除，避免磁盘残留失效路径。
                    freshCover.isNullOrBlank() -> if (recordNegativeLocked(key)) shouldPersist = true
                    else -> {
                        putCoversLocked(key, freshCover)
                        negativeUntil.remove(key)
                        shouldPersist = true
                    }
                }
            }
            if (shouldPersist) persistSignal.trySend(Unit)
        } finally {
            withContext(NonCancellable) {
                guard.withLock {
                    // 按身份删除：若占位已被替换（例如 clear() 后的新 owner），不能误删别人的 deferred
                    if (inFlight[key] === deferred) inFlight.remove(key)
                }
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
            putCoversLocked(key, normalized)
            negativeUntil.remove(key)
        } finally {
            guard.unlock()
        }
        persistSignal.trySend(Unit)
    }

    /**
     * 清理已确认封面、负缓存与持久化内容（测试与登出场景使用）。
     *
     * 不清 [inFlight]：在途请求生命周期很短，清空会让随后登记的同类请求与旧 owner 失去去重关系；
     * 旧 owner 结束时也会按身份校验，只清理自己的占位。
     */
    fun clear() {
        if (!guard.tryLock()) return
        try {
            covers = emptyMap()
            negativeUntil.clear()
        } finally {
            guard.unlock()
        }
        // 落盘是异步的：worker 取的是信号到达后的最新快照（此时为空映射）。
        persistSignal.trySend(Unit)
    }

    // ------------------------------------------------------------------ 内部实现

    /** 一次查询的结论：命中缓存 / 命中负缓存 / 成为 owner / 等待在途 owner。 */
    private class Lookup(
        val cachedCover: String? = null,
        val isNegative: Boolean = false,
        val isOwner: Boolean = false,
        val deferred: CompletableDeferred<Result<String?>>? = null,
    )

    /** 一次预热尝试的票据：只有 owner 真正读盘，其余等待同一个信号。 */
    private class WarmTicket(
        val deferred: CompletableDeferred<Unit>,
        val isOwner: Boolean,
    )

    private suspend fun fetch(id: Long, type: EntityType): Result<String?> = when (type) {
        EntityType.HOBBY -> hobbyDetailRepository.getHobbyDetail(itemId = id, itemType = "hobby")
            .map { it.cover?.trim()?.ifBlank { null } }

        EntityType.ALBUM -> albumDetailRepository.getAlbumDetail(albumId = id)
            .map { it.cover?.trim()?.ifBlank { null } }
    }

    private fun cacheKey(id: Long, type: EntityType): String = "${type.name.lowercase()}:$id"

    /** 写时复制：覆写视为移到队尾（先删后插），随后按容量上限淘汰最旧条目。 */
    private fun putCoversLocked(key: String, cover: String) {
        val next = LinkedHashMap<String, String>(covers)
        next.remove(key)
        next[key] = cover
        covers = trimToCapacity(next)
    }

    private fun trimToCapacity(source: LinkedHashMap<String, String>): LinkedHashMap<String, String> {
        while (source.size > MAX_COVERS) {
            val eldest = source.keys.firstOrNull() ?: break
            source.remove(eldest)
        }
        return source
    }

    /** 预热结果合并：落盘条目更旧，内存中已有的值优先，且保持在队尾。 */
    private suspend fun mergeWarmed(loaded: Map<String, String>) {
        if (loaded.isEmpty()) return
        guard.withLock {
            val merged = LinkedHashMap<String, String>(loaded.size + covers.size)
            loaded.forEach { (key, value) -> if (value.isNotBlank()) merged[key] = value }
            covers.forEach { (key, value) ->
                merged.remove(key)
                merged[key] = value
            }
            covers = trimToCapacity(merged)
        }
    }

    private suspend fun readPersistedCovers(): Map<String, String> {
        val raw = preferences.data.first()[COVERS_KEY] ?: return emptyMap()
        if (raw.isBlank()) return emptyMap()
        return runCatching { json.decodeFromString(coverMapSerializer, raw) }
            .getOrElse { error ->
                // 损坏/旧格式不阻塞启动：按空缓存处理，后续写入会覆盖掉坏数据。
                AppLogger.w("CoverFreshness", "解析封面新鲜度缓存失败，按空缓存处理：${error.message}")
                emptyMap()
            }
    }

    /**
     * 落盘 worker：收到信号后等待 [PERSIST_DEBOUNCE_MS] 再写最新快照。
     *
     * `CONFLATED` 通道让同一窗口内的连续写入合并为一次落盘；worker 本体常驻于 [scope]，
     * 查询与写入调用方永远不会被磁盘 I/O 阻塞。
     */
    private suspend fun persistLoop() {
        for (unused in persistSignal) {
            delay(PERSIST_DEBOUNCE_MS)
            persistSnapshot()
        }
    }

    private suspend fun persistSnapshot() {
        // 预热未落定前不写盘：否则会用「只含新写入」的快照覆盖掉尚未读出的历史条目。
        ensureWarmed()
        if (!warmed) return
        val snapshot = covers
        val encoded = json.encodeToString(coverMapSerializer, snapshot)
        try {
            preferences.edit { prefs -> prefs[COVERS_KEY] = encoded }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AppLogger.w("CoverFreshness", "写入封面新鲜度缓存失败：${error.message}")
        }
    }

    private fun isNegativeLocked(key: String): Boolean {
        val until = negativeUntil[key] ?: return false
        if (clock() >= until) {
            negativeUntil.remove(key)
            return false
        }
        return true
    }

    /** 记录负缓存；若移除了内存中的旧正缓存则返回 `true`（调用方需落盘这次删除）。 */
    private fun recordNegativeLocked(key: String): Boolean {
        val hadCover = key in covers
        if (hadCover) {
            covers = LinkedHashMap<String, String>(covers).also { it.remove(key) }
        }
        negativeUntil[key] = clock() + NEGATIVE_TTL_MS
        return hadCover
    }

    private companion object {
        /** 负缓存窗口：服务端也没有封面时别每次滚动都打接口。 */
        const val NEGATIVE_TTL_MS = 10L * 60L * 1000L

        /** 正缓存容量上限；同时决定持久化 JSON 的体积上界（约 40KB）。 */
        const val MAX_COVERS = 1000

        /** 落盘防抖窗口。 */
        const val PERSIST_DEBOUNCE_MS = 500L

        /** 单个 preferences key 承载整张映射（原子写入）。 */
        val COVERS_KEY = stringPreferencesKey("cover_freshness_covers")
    }
}
