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
