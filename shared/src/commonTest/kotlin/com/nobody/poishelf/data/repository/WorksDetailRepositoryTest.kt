package com.nobody.poishelf.data.repository

import com.nobody.poishelf.data.remote.model.ApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 作品详情仓储测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `WorksDetailRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；JUnit4 → kotlin.test）。
 */
class WorksDetailRepositoryTest {

    private fun setup(): Pair<MockApi, WorksDetailRepository> {
        val mock = MockApi()
        return mock to WorksDetailRepository(mock.api())
    }

    @Test
    fun getWorksDetail_successMapsNodeId() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"result":{"works":{"id":2706,"itemId":28,"nameCN":"命运-冠位指定"},"worksSeries":null,"worksRatingAvg":9.5}}}""")
        val result = repository.getWorksDetail(28L)
        assertTrue(result.isSuccess)
        assertEquals(2706L, result.getOrThrow().works?.actualNodeId)
    }

    @Test
    fun getWorksDetail_failureThrowsWithCode() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(4001, "登录过期"))
        val result = repository.getWorksDetail(28L)
        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun getWorksHobbys_successMapsItemId() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1,"itemId":24630}]}}""")
        val result = repository.getWorksHobbys(2706L)
        assertTrue(result.isSuccess)
        assertEquals(24630L, result.getOrThrow().first().itemId)
    }

    @Test
    fun getWorksHobbys_failureEmptiesInsteadOfThrowing() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(500, "error"))
        val result = repository.getWorksHobbys(2706L)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun getWorksCharacters_failureEmptiesInsteadOfThrowing() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(500, "error"))
        val result = repository.getWorksCharacters(28L)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun getComments_failureThrows() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(500, "error"))
        val result = repository.getComments(2706L)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ApiException
        assertEquals(500, error.code)
        assertEquals("error", error.message)
    }
}
