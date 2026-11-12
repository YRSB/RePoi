package com.nobody.repoi.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.awaitPendingWrites
import com.nobody.repoi.platform.nowMillis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle

/**
 * 构造封面新鲜度仓储的测试工厂。
 *
 * 所有测试统一走这里，构造签名变化只需改本文件，避免每加一个依赖就散弹式修改十余处测试。
 * `scope` 必须归测试所有（通常是 `backgroundScope`），用于预热与防抖落盘协程。
 */
fun testCoverFreshnessRepository(
    scope: CoroutineScope,
    preferences: DataStore<Preferences> = TestDataStore.coverFreshnessPrefs(),
    hobbyMock: MockApi = MockApi(),
    albumMock: MockApi = MockApi(),
    dispatcher: CoroutineDispatcher? = null,
    clock: () -> Long = ::nowMillis,
): CoverFreshnessRepository = CoverFreshnessRepository(
    hobbyDetailRepository = HobbyDetailRepository(hobbyMock.api(dispatcher)),
    albumDetailRepository = AlbumDetailRepository(albumMock.api(dispatcher)),
    preferences = preferences,
    scope = scope,
    clock = clock,
)

/** 已持有仓储依赖时使用：直接注入两个详情仓储，其余参数同上。 */
fun testCoverFreshnessRepository(
    scope: CoroutineScope,
    hobbyDetailRepository: HobbyDetailRepository,
    albumDetailRepository: AlbumDetailRepository,
    preferences: DataStore<Preferences> = TestDataStore.coverFreshnessPrefs(),
    clock: () -> Long = ::nowMillis,
): CoverFreshnessRepository = CoverFreshnessRepository(
    hobbyDetailRepository = hobbyDetailRepository,
    albumDetailRepository = albumDetailRepository,
    preferences = preferences,
    scope = scope,
    clock = clock,
)

/**
 * 等待最新封面真正落盘。
 *
 * 连续写入可能跨越不同的防抖窗口：上一窗口的 `edit` 尚未返回时，下一窗口的 `delay` 还没被调度，
 * 单次 `advanceUntilIdle` 无法覆盖。这里逐窗口推进，并在每个窗口后重建仓储观察。
 *
 * 该屏障只服务真实文件 DataStore 的 I/O 同步；「500ms 前不落盘、之后才落盘」由独立用例直接断言，
 * 不依赖本函数的多次尝试。
 */
suspend fun TestScope.awaitPersistedCover(
    preferences: DataStore<Preferences>,
    id: Long,
    type: CoverFreshnessRepository.EntityType,
    expected: String?,
    message: String? = null,
    attempts: Int = 2,
): String? {
    var observed: String? = null
    repeat(attempts) {
        advanceTimeBy(501)
        advanceUntilIdle()
        preferences.awaitPendingWrites()
        val restored = testCoverFreshnessRepository(backgroundScope, preferences = preferences)
        restored.ensureWarmed()
        observed = restored.getCachedCover(id, type)
        if (observed == expected) return observed
    }
    if (observed != expected) {
        throw AssertionError(
            message ?: "封面未落盘：期望 $expected，实际 $observed（id=$id, type=$type）"
        )
    }
    return observed
}
