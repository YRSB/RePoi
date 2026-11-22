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

class CoverFreshnessRepository(
    private val hobbyDetailRepository: HobbyDetailRepository,
    private val albumDetailRepository: AlbumDetailRepository,
    private val preferences: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val clock: () -> Long = ::nowMillis,
) {

    enum class EntityType { HOBBY, ALBUM }

    @kotlin.concurrent.Volatile
    private var covers: Map<String, String> = emptyMap()

    private val negativeUntil = object : LinkedHashMap<String, Long>(MAX_COVERS, 0.75f) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean =
            size > MAX_COVERS
    }

    private val inFlight = mutableMapOf<String, CompletableDeferred<Result<String?>>>()

    private val guard = Mutex()

    private val persistGuard = Mutex()

    private var cacheEpoch: Long = 0L

    private val warmGuard = Mutex()

    @kotlin.concurrent.Volatile
    private var warmed: Boolean = false

    private var warmAttempt: CompletableDeferred<Unit>? = null

    private val persistSignal = Channel<Unit>(Channel.CONFLATED)

    private val json = Json.Default
    private val coverMapSerializer = MapSerializer(String.serializer(), String.serializer())

    init {
        scope.launch { ensureWarmed() }
        scope.launch { persistLoop() }
    }

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

            val (epoch, loaded) = persistGuard.withLock { cacheEpoch to readPersistedCovers() }
            mergeWarmed(loaded, epoch)
            warmed = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AppLogger.w("CoverFreshness", "预热封面新鲜度缓存失败：${error.message}")
        } finally {

            withContext(NonCancellable) {
                warmGuard.withLock {
                    if (warmAttempt === ticket.deferred) warmAttempt = null
                }
                ticket.deferred.complete(Unit)
            }
        }
    }

    fun getCachedCover(id: Long, type: EntityType): String? {
        if (id <= 0L) return null
        return covers[cacheKey(id, type)]
    }

    suspend fun freshCover(
        id: Long,
        type: EntityType,
        forceRefresh: Boolean = false,
    ): Result<String?> {
        if (id <= 0L) return Result.success(null)
        val key = cacheKey(id, type)

        val lookup = guard.withLock {
            val cached = covers[key]
            when {

                cached != null && !forceRefresh -> Lookup(cachedCover = cached)
                isNegativeLocked(key) -> Lookup(isNegative = true)
                else -> {
                    val existing = inFlight[key]
                    if (existing != null) {
                        Lookup(deferred = existing)
                    } else {
                        val created = CompletableDeferred<Result<String?>>()
                        inFlight[key] = created
                        Lookup(isOwner = true, deferred = created, epoch = cacheEpoch)
                    }
                }
            }
        }
        lookup.cachedCover?.let { return Result.success(it) }
        if (lookup.isNegative) return Result.success(null)
        val deferred = requireNotNull(lookup.deferred) { "单飞占位缺失" }
        if (!lookup.isOwner) return deferred.await()

        var shouldPersist = false

        var outcome: Result<String?> = Result.failure(IllegalStateException("封面修复未完成"))
        try {
            outcome = fetch(id, type)
            val freshCover = outcome.getOrNull()
            guard.withLock {
                when {

                    lookup.epoch != cacheEpoch -> Unit

                    outcome.isFailure -> Unit

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

                    if (inFlight[key] === deferred) inFlight.remove(key)
                }
                deferred.complete(outcome)
            }
        }
        return outcome
    }

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

    suspend fun clear() {
        try {

            withContext(NonCancellable) {
                persistGuard.withLock {
                    invalidate()

                    preferences.edit { prefs -> prefs.remove(COVERS_KEY) }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AppLogger.w("CoverFreshness", "清空封面新鲜度持久化缓存失败（内存已清空）：${error.message}")
        }
    }

    private suspend fun invalidate() {
        guard.withLock {
            cacheEpoch++
            covers = emptyMap()
            negativeUntil.clear()
        }
    }

    private class Lookup(
        val cachedCover: String? = null,
        val isNegative: Boolean = false,
        val isOwner: Boolean = false,
        val deferred: CompletableDeferred<Result<String?>>? = null,

        val epoch: Long = 0L,
    )

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

    private suspend fun mergeWarmed(loaded: Map<String, String>, epoch: Long) {
        if (loaded.isEmpty()) return
        guard.withLock {

            if (epoch != cacheEpoch) return@withLock
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

                AppLogger.w("CoverFreshness", "解析封面新鲜度缓存失败，按空缓存处理：${error.message}")
                emptyMap()
            }
    }

    private suspend fun persistLoop() {
        for (unused in persistSignal) {
            var attempt = 1
            while (true) {
                delay(PERSIST_DEBOUNCE_MS)
                if (persistSnapshot()) break
                if (attempt >= PERSIST_MAX_ATTEMPTS) {
                    AppLogger.w("CoverFreshness", "预热始终未落定，放弃本次封面缓存落盘（已重试 $attempt 次）")
                    break
                }
                attempt++
            }
        }
    }

    private suspend fun persistSnapshot(): Boolean {

        ensureWarmed()
        if (!warmed) return false
        try {
            persistGuard.withLock {
                val snapshot = covers
                if (snapshot.isEmpty()) {
                    preferences.edit { prefs -> prefs.remove(COVERS_KEY) }
                } else {
                    val encoded = json.encodeToString(coverMapSerializer, snapshot)
                    preferences.edit { prefs -> prefs[COVERS_KEY] = encoded }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AppLogger.w("CoverFreshness", "写入封面新鲜度缓存失败：${error.message}")
        }
        return true
    }

    private fun isNegativeLocked(key: String): Boolean {
        val until = negativeUntil[key] ?: return false
        if (clock() >= until) {
            negativeUntil.remove(key)
            return false
        }
        return true
    }

    private fun recordNegativeLocked(key: String): Boolean {
        val hadCover = key in covers
        if (hadCover) {
            covers = LinkedHashMap<String, String>(covers).also { it.remove(key) }
        }
        negativeUntil[key] = clock() + NEGATIVE_TTL_MS
        return hadCover
    }

    private companion object {

        const val NEGATIVE_TTL_MS = 10L * 60L * 1000L

        const val MAX_COVERS = 1000

        const val PERSIST_DEBOUNCE_MS = 500L

        const val PERSIST_MAX_ATTEMPTS = 3

        val COVERS_KEY = stringPreferencesKey("cover_freshness_covers")
    }
}
