package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.serialization.json.Json

class WorksDetailRepositoryTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var repository: WorksDetailRepository

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        repository = WorksDetailRepository(api)
    }

    @AfterTest
    fun tearDown() {
        client.close()
    }

    @Test
    fun getWorksDetail_successMapsNodeId() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"result":{"works":{"id":2706,"itemId":28,"nameCN":"命运-冠位指定"},"worksSeries":null,"worksRatingAvg":9.5}}}""")
        val result = repository.getWorksDetail(28L)
        assertTrue(result.isSuccess)
        assertEquals(2706L, result.getOrThrow().works?.actualNodeId)
    }

    @Test
    fun getWorksDetail_failureThrowsWithCode() = runTest {
        mock.enqueue("""{"success":false,"code":4001,"msg":"登录过期","data":null}""")
        val result = repository.getWorksDetail(28L)
        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun getWorksHobbys_successMapsItemId() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1,"itemId":24630}]}}""")
        val result = repository.getWorksHobbys(2706L)
        assertTrue(result.isSuccess)
        assertEquals(24630L, result.getOrThrow().first().itemId)
    }

    @Test
    fun getWorksHobbys_failureEmptiesInsteadOfThrowing() = runTest {
        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")
        val result = repository.getWorksHobbys(2706L)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun getWorksCharacters_failureEmptiesInsteadOfThrowing() = runTest {
        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")
        val result = repository.getWorksCharacters(28L)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun getComments_failureThrows() = runTest {
        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")
        val result = repository.getComments(2706L)
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ApiException
        assertEquals(500, error.code)
        assertEquals("error", error.message)
    }
}
