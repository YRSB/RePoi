package com.nobody.repoi.domain.usecase.collect

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.CollectData
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.session.FakeUserSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 收藏写侧 Domain UseCases 单测。
 *
 * 覆盖三类缝隙：校验分支**不发请求**（`requestCount == 0`）、成功路径（请求参数与状态池）、失败透传。
 * 命名刻意避开 `*ViewModelTest`（CI `excludeTestsMatching` 隔离名单）。
 */
class CollectUseCasesTest {

    private class Env(
        val mock: MockApi,
        val session: FakeUserSession,
        val repository: CollectRepository,
    )

    private fun env(userId: Long = 999L): Env {
        val mock = MockApi()
        val session = FakeUserSession(token = "utoken-test", userId = userId)
        return Env(mock, session, CollectRepository(mock.api(), mock.json, session))
    }

    @Test
    fun toggleItemCollect_whenNotCollected_addsAndFlipsResult() = runTest {
        val env = env()
        env.mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val observed = env.repository.observeCollectState(100L)

        val result = ToggleItemCollectUseCase(env.repository).invoke(nodeId = 100L, isCollected = false)

        assertEquals(true, result.getOrThrow())
        assertEquals("/api/collect/item/add", env.mock.takeRequest().encodedPath)
        assertEquals(CollectConstants.STATE_CARE, observed.value?.state)
    }

    @Test
    fun toggleItemCollect_whenCollected_deletesAndFlipsResult() = runTest {
        val env = env()
        env.mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val observed = env.repository.observeCollectState(100L)

        val result = ToggleItemCollectUseCase(env.repository).invoke(nodeId = 100L, isCollected = true)

        assertEquals(false, result.getOrThrow())
        assertEquals("/api/collect/del", env.mock.takeRequest().encodedPath)
        assertNull(observed.value)
    }

    @Test
    fun toggleItemCollect_onApiFailure_propagatesApiException() = runTest {
        val env = env()
        env.mock.enqueue(env.mock.failure(500, "收藏失败"))

        val result = ToggleItemCollectUseCase(env.repository).invoke(nodeId = 100L, isCollected = false)

        assertTrue(result.isFailure)
        assertEquals("收藏失败", (result.exceptionOrNull() as ApiException).message)
    }

    @Test
    fun saveHobbyCollect_withInvalidNodeId_failsWithoutRequest() = runTest {
        val env = env()

        val result = SaveHobbyCollectUseCase(env.repository)
            .invoke(nodeId = 0L, collectData = CollectData(state = CollectConstants.STATE_CARE))

        assertTrue(result.isFailure)
        assertEquals("条目信息缺失", (result.exceptionOrNull() as ApiException).message)
        assertEquals(0, env.mock.requestCount)
    }

    @Test
    fun saveHobbyCollect_withBlankState_failsWithoutRequest() = runTest {
        val env = env()

        val result = SaveHobbyCollectUseCase(env.repository)
            .invoke(nodeId = 100L, collectData = CollectData(state = ""))

        assertTrue(result.isFailure)
        assertEquals("请选择收藏状态", (result.exceptionOrNull() as ApiException).message)
        assertEquals(0, env.mock.requestCount)
    }

    @Test
    fun saveHobbyCollect_withDeleteState_failsWithoutRequest() = runTest {
        val env = env()

        val result = SaveHobbyCollectUseCase(env.repository)
            .invoke(nodeId = 100L, collectData = CollectData(state = CollectConstants.STATE_DELETE))

        assertTrue(result.isFailure)
        assertEquals("请选择收藏状态", (result.exceptionOrNull() as ApiException).message)
        assertEquals(0, env.mock.requestCount)
    }

    @Test
    fun saveHobbyCollect_onSuccess_postsPayloadAndUpdatesStatePool() = runTest {
        val env = env()
        env.mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val observed = env.repository.observeCollectState(100L)
        val data = CollectData(state = CollectConstants.STATE_BUY, rating = 5)

        val result = SaveHobbyCollectUseCase(env.repository).invoke(nodeId = 100L, collectData = data)

        assertTrue(result.isSuccess)
        assertEquals("/api/collect/hobby/upd", env.mock.takeRequest().encodedPath)
        assertEquals(CollectConstants.STATE_BUY, observed.value?.state)
        assertEquals(5, observed.value?.rating)
    }

    @Test
    fun deleteHobbyCollect_onSuccess_clearsStatePool() = runTest {
        val env = env()
        env.mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val observed = env.repository.observeCollectState(100L)

        val result = DeleteHobbyCollectUseCase(env.repository)
            .invoke(nodeId = 100L, currentData = CollectData(state = CollectConstants.STATE_BUY))

        assertTrue(result.isSuccess)
        assertNull(observed.value)
    }

    @Test
    fun batchCancelCollect_whenNotOwner_failsWithoutRequest() = runTest {
        val env = env(userId = 999L)

        val result = BatchCancelCollectUseCase(env.repository, env.session)
            .invoke(ids = listOf(1L, 2L), ownerUserId = 888L)

        assertTrue(result.isFailure)
        assertEquals("仅能取消本人收藏", (result.exceptionOrNull() as ApiException).message)
        assertEquals(0, env.mock.requestCount)
    }

    @Test
    fun batchCancelCollect_whenOwner_postsIds() = runTest {
        val env = env(userId = 999L)
        env.mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = BatchCancelCollectUseCase(env.repository, env.session)
            .invoke(ids = listOf(1L, 2L), ownerUserId = 999L)

        assertTrue(result.isSuccess)
        val recorded = env.mock.takeRequest()
        assertEquals("/api/collect/batch/cancel", recorded.encodedPath)
        assertEquals("[1,2]", recorded.formEntries()["idArr"])
    }

    @Test
    fun batchCancelCollect_onApiFailure_propagatesApiException() = runTest {
        val env = env(userId = 999L)
        env.mock.enqueue(env.mock.failure(500, "批量取消失败"))

        val result = BatchCancelCollectUseCase(env.repository, env.session)
            .invoke(ids = listOf(3L), ownerUserId = 999L)

        assertTrue(result.isFailure)
        assertEquals(500, (result.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun saveWorksCollect_withIllegalState_failsWithoutRequest() = runTest {
        val env = env()

        val result = SaveWorksCollectUseCase(env.repository)
            .invoke(worksId = 20001L, state = 9, tags = emptyList(), comment = "")

        assertTrue(result.isFailure)
        assertEquals("收藏状态不合法", (result.exceptionOrNull() as ApiException).message)
        assertEquals(0, env.mock.requestCount)
    }

    @Test
    fun saveWorksCollect_cleansTagStringBeforeSending() = runTest {
        val env = env()
        env.mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = SaveWorksCollectUseCase(env.repository).invoke(
            worksId = 20001L,
            state = 2,
            tags = listOf(" 热血 ", "校园", "", "热血", "  "),
            comment = "追到第八集"
        )

        assertTrue(result.isSuccess)
        val form = env.mock.takeRequest().formEntries()
        assertEquals("热血,校园", form["tagStr"])
        assertEquals("Works", form["itemType"])
        assertEquals("2", form["state"])
    }

    @Test
    fun saveWorksCollect_onApiFailure_propagatesApiException() = runTest {
        val env = env()
        env.mock.enqueue(env.mock.failure(400, "标签过长"))

        val result = SaveWorksCollectUseCase(env.repository)
            .invoke(worksId = 20001L, state = 1, tags = listOf("日常"), comment = "")

        assertTrue(result.isFailure)
        assertEquals("标签过长", (result.exceptionOrNull() as ApiException).message)
    }

    @Test
    fun deleteWorksCollect_withInvalidWorksId_failsWithoutRequest() = runTest {
        val env = env()

        val result = DeleteWorksCollectUseCase(env.repository).invoke(worksId = 0L)

        assertTrue(result.isFailure)
        assertEquals("作品信息缺失", (result.exceptionOrNull() as ApiException).message)
        assertEquals(0, env.mock.requestCount)
    }

    @Test
    fun deleteWorksCollect_onSuccess_callsDeleteEndpoint() = runTest {
        val env = env()
        env.mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = DeleteWorksCollectUseCase(env.repository).invoke(worksId = 20001L)

        assertTrue(result.isSuccess)
        val recorded = env.mock.takeRequest()
        assertEquals("/api/works/glean/del", recorded.encodedPath)
        assertEquals("Works", recorded.formEntries()["itemType"])
        assertEquals("20001", recorded.formEntries()["itemId"])
    }

    @Test
    fun deleteWorksCollect_onApiFailure_propagatesApiException() = runTest {
        val env = env()
        env.mock.enqueue(env.mock.failure(500, "取消作品收藏失败"))

        val result = DeleteWorksCollectUseCase(env.repository).invoke(worksId = 20001L)

        assertTrue(result.isFailure)
        assertEquals(500, (result.exceptionOrNull() as ApiException).code)
        assertEquals("取消作品收藏失败", (result.exceptionOrNull() as ApiException).message)
    }
}
