package com.nobody.repoi.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.awaitPendingWrites
import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserHistoryRepositoryTest {

    private val freshHobbyCover = "2026/11/fresh_hobby.jpg"
    private val freshAlbumCover = "2026/11/fresh_album.jpg"
    private val staleCover = "2020/01/stale_cover.jpg"

    private fun TestScope.setup(): Pair<MockApi, UserHistoryRepository> {
        val mock = MockApi()
        return mock to UserHistoryRepository(mock.api(), freshness(TestDataStore.coverFreshnessPrefs()))
    }

    private fun TestScope.freshness(
        preferences: DataStore<Preferences>,
        hobbyMock: MockApi = MockApi(),
        albumMock: MockApi = MockApi(),
    ): CoverFreshnessRepository = testCoverFreshnessRepository(
        scope = backgroundScope,
        preferences = preferences,
        hobbyMock = hobbyMock,
        albumMock = albumMock,
    )

    private suspend fun TestScope.seedPersistedCover(
        preferences: DataStore<Preferences>,
        id: Long,
        type: CoverFreshnessRepository.EntityType,
        cover: String,
    ) {
        val seeder = freshness(preferences)
        seeder.ensureWarmed()
        seeder.rememberFreshCover(id, type, cover)
        advanceTimeBy(501)
        advanceUntilIdle()
        preferences.awaitPendingWrites()
    }

    @Test
    fun getUserHistoryList_success_parsesItems() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"data":{"readList":[{"id":123,"nodeId":456,"itemType":"hobby","title":"手办A"}]}}"""
        )

        val result = repository.getUserHistoryList(itemType = "hobby", page = 1)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(1, list.size)
        assertEquals(123L, list.first().id)
        assertEquals(456L, list.first().nodeId)
        assertEquals("手办A", list.first().title)
    }

    @Test
    fun getUserHistoryList_hobby_rewritesCoverByItemIdFromPersistedCache() = runTest {
        val prefs = TestDataStore.coverFreshnessPrefs()
        seedPersistedCover(prefs, 59570L, CoverFreshnessRepository.EntityType.HOBBY, freshHobbyCover)

        val detailMock = MockApi()
        val albumMock = MockApi()
        val mock = MockApi()
        mock.enqueue(
            """{"success":true,"code":200,"data":{"readList":[
                {"id":123,"nodeId":456,"itemId":59570,"itemType":"hobby","title":"手办A","cover":"$staleCover"}
            ]}}"""
        )
        val repository = UserHistoryRepository(
            mock.api(),
            freshness(prefs, hobbyMock = detailMock, albumMock = albumMock),
        )

        val item = repository.getUserHistoryList(itemType = "hobby", page = 1).getOrThrow().single()

        assertEquals(freshHobbyCover, item.cover)
        assertEquals(0, detailMock.requestCount, "前置重写命中后不应请求手办详情")
        assertEquals(0, albumMock.requestCount, "手办重写不应请求相册详情")
    }

    @Test
    fun getUserHistoryList_album_rewritesCoverByNodeIdFromPersistedCache() = runTest {
        val prefs = TestDataStore.coverFreshnessPrefs()
        seedPersistedCover(prefs, 8888L, CoverFreshnessRepository.EntityType.ALBUM, freshAlbumCover)

        val detailMock = MockApi()
        val mock = MockApi()
        mock.enqueue(
            """{"success":true,"code":200,"data":{"readList":[
                {"id":321,"nodeId":8888,"itemId":999,"itemType":"album","title":"相册A","cover":"$staleCover"}
            ]}}"""
        )
        val repository = UserHistoryRepository(
            mock.api(),
            freshness(prefs, hobbyMock = detailMock),
        )

        val item = repository.getUserHistoryList(itemType = "album", page = 1).getOrThrow().single()

        assertEquals(freshAlbumCover, item.cover, "相册必须按 nodeId 命中缓存")
        assertEquals(0, detailMock.requestCount)
    }

    @Test
    fun getUserHistoryList_hobby_withoutItemId_fallsBackToActualDetailId() = runTest {
        val prefs = TestDataStore.coverFreshnessPrefs()

        seedPersistedCover(prefs, 456L, CoverFreshnessRepository.EntityType.HOBBY, freshHobbyCover)

        val detailMock = MockApi()
        val mock = MockApi()
        mock.enqueue(
            """{"success":true,"code":200,"data":{"readList":[
                {"id":123,"nodeId":456,"itemType":"hobby","title":"手办A","cover":"$staleCover"}
            ]}}"""
        )
        val repository = UserHistoryRepository(
            mock.api(),
            freshness(prefs, hobbyMock = detailMock),
        )

        val item = repository.getUserHistoryList(itemType = "hobby", page = 1).getOrThrow().single()

        assertEquals(freshHobbyCover, item.cover, "itemId 为 0 时应回退 actualDetailId 命中缓存")
        assertEquals(0, detailMock.requestCount, "回退命中同样不应触发详情请求")
    }

    @Test
    fun getUserHistoryList_album_withoutNodeId_fallsBackToActualDetailId() = runTest {
        val prefs = TestDataStore.coverFreshnessPrefs()

        seedPersistedCover(prefs, 777L, CoverFreshnessRepository.EntityType.ALBUM, freshAlbumCover)

        val mock = MockApi()
        mock.enqueue(
            """{"success":true,"code":200,"data":{"readList":[
                {"id":777,"itemType":"album","title":"相册B","cover":"$staleCover"}
            ]}}"""
        )
        val repository = UserHistoryRepository(
            mock.api(),
            freshness(prefs),
        )

        val item = repository.getUserHistoryList(itemType = "album", page = 1).getOrThrow().single()

        assertEquals(freshAlbumCover, item.cover, "nodeId 为 0 时应回退 actualDetailId 命中缓存")
    }

    @Test
    fun getUserHistoryList_unmatched_keepsServerSnapshot() = runTest {
        val prefs = TestDataStore.coverFreshnessPrefs()
        seedPersistedCover(prefs, 59570L, CoverFreshnessRepository.EntityType.HOBBY, freshHobbyCover)

        val mock = MockApi()
        mock.enqueue(
            """{"success":true,"code":200,"data":{"readList":[
                {"id":124,"nodeId":457,"itemId":59571,"itemType":"hobby","title":"手办B","cover":"$staleCover"}
            ]}}"""
        )
        val repository = UserHistoryRepository(
            mock.api(),
            freshness(prefs),
        )

        val item = repository.getUserHistoryList(itemType = "hobby", page = 1).getOrThrow().single()

        assertEquals(staleCover, item.cover, "未命中缓存时保留服务端快照供后续自愈")
    }

    @Test
    fun getUserHistoryList_missingItemType_fallsBackToRequestedType() = runTest {
        val prefs = TestDataStore.coverFreshnessPrefs()
        seedPersistedCover(prefs, 59570L, CoverFreshnessRepository.EntityType.HOBBY, freshHobbyCover)

        val mock = MockApi()
        mock.enqueue(
            """{"success":true,"code":200,"data":{"readList":[
                {"id":123,"nodeId":456,"itemId":59570,"title":"手办A","cover":"$staleCover"}
            ]}}"""
        )
        val repository = UserHistoryRepository(
            mock.api(),
            freshness(prefs),
        )

        val item = repository.getUserHistoryList(itemType = "hobby", page = 1).getOrThrow().single()

        assertEquals(freshHobbyCover, item.cover)
    }

    @Test
    fun getUserHistoryList_itemTypeOverridesRequestParameter() = runTest {
        val prefs = TestDataStore.coverFreshnessPrefs()
        seedPersistedCover(prefs, 59570L, CoverFreshnessRepository.EntityType.HOBBY, freshHobbyCover)

        val mock = MockApi()
        mock.enqueue(
            """{"success":true,"code":200,"data":{"readList":[
                {"id":123,"nodeId":456,"itemId":59570,"itemType":" Hobby ","title":"手办A","cover":"$staleCover"}
            ]}}"""
        )
        val repository = UserHistoryRepository(
            mock.api(),
            freshness(prefs),
        )

        val item = repository.getUserHistoryList(itemType = "album", page = 1).getOrThrow().single()

        assertEquals(freshHobbyCover, item.cover, "条目自带类型有效时应优先于请求参数")
    }

    @Test
    fun getUserHistoryList_failure_throwsApiException() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":false,"code":4001,"msg":"登录令牌已过期"}""")

        val result = repository.getUserHistoryList(itemType = "hobby", page = 1)
        assertTrue(result.isFailure)
        assertIs<ApiException>(result.exceptionOrNull())
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun addUserHistory_success() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok"}""")

        val result = repository.addUserHistory(itemNodeId = 789L)
        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertTrue(recorded.bodyContains("itemNodeId=789"))
    }

    @Test
    fun addUserHistory_failure_throwsApiException() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":false,"code":4001,"msg":"登录态失效"}""")

        val result = repository.addUserHistory(itemNodeId = 789L)
        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun deleteUserHistory_emptyList_returnsImmediately() = runTest {
        val (mock, repository) = setup()
        val result = repository.deleteUserHistory(emptyList())
        assertTrue(result.isSuccess)
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun deleteUserHistory_success() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok"}""")

        val result = repository.deleteUserHistory(listOf(101L, 102L))
        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertTrue(recorded.bodyContains("ids=%5B101%2C102%5D") || recorded.bodyContains("ids=[101,102]"))
    }

    @Test
    fun deleteUserHistory_failure_throwsApiException() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":false,"code":4001,"msg":"凭据过期"}""")

        val result = repository.deleteUserHistory(listOf(101L))
        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
    }
}
