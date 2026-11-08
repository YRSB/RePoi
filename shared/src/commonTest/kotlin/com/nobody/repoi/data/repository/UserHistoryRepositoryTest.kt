package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.data.session.SessionState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserHistoryRepositoryTest {

    private fun setup(token: String = "utoken-test"): Triple<MockApi, FakeUserSession, UserHistoryRepository> {
        val mock = MockApi()
        val session = FakeUserSession(token)
        return Triple(mock, session, UserHistoryRepository(mock.api(), session))
    }

    @Test
    fun getUserHistoryList_success_parsesItems() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"data":{"readList":[{"id":123,"itemNodeId":456,"itemType":"hobby","itemName":"手办A"}]}}"""
        )

        val result = repository.getUserHistoryList(itemType = "hobby", page = 1)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(1, list.size)
        assertEquals(123L, list.first().id)
        assertEquals(456L, list.first().itemNodeId)
        assertEquals("手办A", list.first().itemName)
    }

    @Test
    fun getUserHistoryList_tokenExpired_selfHealsSessionAndThrows() = runTest {
        val (mock, session, repository) = setup("expired-token")
        mock.enqueue("""{"success":false,"code":4001,"msg":"登录令牌已过期"}""")

        val result = repository.getUserHistoryList(itemType = "hobby", page = 1)
        assertTrue(result.isFailure)
        assertIs<ApiException>(result.exceptionOrNull())
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
        assertEquals(SessionState.Unauthenticated, session.state.value)
    }

    @Test
    fun addUserHistory_success() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok"}""")

        val result = repository.addUserHistory(itemNodeId = 789L)
        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertTrue(recorded.bodyContains("itemNodeId=789"))
    }

    @Test
    fun addUserHistory_tokenExpired_selfHealsSessionAndThrows() = runTest {
        val (mock, session, repository) = setup("token-will-expire")
        mock.enqueue("""{"success":false,"code":4001,"msg":"登录态失效"}""")

        val result = repository.addUserHistory(itemNodeId = 789L)
        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
        assertEquals(SessionState.Unauthenticated, session.state.value)
    }

    @Test
    fun deleteUserHistory_emptyList_returnsImmediately() = runTest {
        val (mock, _, repository) = setup()
        val result = repository.deleteUserHistory(emptyList())
        assertTrue(result.isSuccess)
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun deleteUserHistory_success() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok"}""")

        val result = repository.deleteUserHistory(listOf(101L, 102L))
        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertTrue(recorded.bodyContains("ids=%5B101%2C102%5D") || recorded.bodyContains("ids=[101,102]"))
    }

    @Test
    fun deleteUserHistory_tokenExpired_selfHealsSessionAndThrows() = runTest {
        val (mock, session, repository) = setup("token-del")
        mock.enqueue("""{"success":false,"code":4001,"msg":"凭据过期"}""")

        val result = repository.deleteUserHistory(listOf(101L))
        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
        assertEquals(SessionState.Unauthenticated, session.state.value)
    }
}
