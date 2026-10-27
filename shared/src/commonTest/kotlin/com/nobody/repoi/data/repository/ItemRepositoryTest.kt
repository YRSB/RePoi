package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.recordedBody
import com.nobody.repoi.test.recordedPath
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.todayIn
import kotlin.time.Clock
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

class ItemRepositoryTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var repository: ItemRepository

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        repository = ItemRepository(api)
    }

    @AfterTest
    fun tearDown() {
        client.close()
    }

    private fun parseForm(body: String): Map<String, String> {
        return body.split("&")
            .filter { it.isNotBlank() }
            .associate {
                val parts = it.split("=", limit = 2)
                parts[0].decodeURLPart() to
                    (parts.getOrElse(1) { "" }).decodeURLPart()
            }
    }

    private fun hobbyListJson(name: String): String {
        return """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1,"nameCN":"$name"}]}}"""
    }

    @Test
    fun getItemHomePreview_sendsTwoDatedQueriesAndMapsBothSections() = runTest {
        // KMP：runTest 单线程，无需 synchronizedList
        val forms = mutableListOf<Map<String, String>>()
        mock.dynamicResponder = { request ->
            val form = parseForm(request.recordedBody())
            forms.add(form)
            val name = if (form.containsKey("releaseEnd")) "出荷手办" else "预订手办"
            hobbyListJson(name) to HttpStatusCode.OK
        }

        val result = repository.getItemHomePreview(10000)

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("预订手办", data.preOrderItems.single().nameCN)
        assertEquals("出荷手办", data.releaseItems.single().nameCN)

        assertEquals(2, forms.size)
        val preOrderForm = forms.first { !it.containsKey("releaseEnd") }
        val shipForm = forms.first { it.containsKey("releaseEnd") }

        listOf(preOrderForm, shipForm).forEach { form ->
            assertEquals("hits7Day", form["order"])
            assertEquals("1", form["page"])
            assertEquals("9", form["pageSize"])
            assertEquals("10000", form["category"])
            assertEquals("1", form["hasShopItem"])
            assertTrue(form["releaseStart"]!!.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        }
        assertEquals(today(), shipForm["releaseEnd"])
        assertTrue(LocalDate.parse(preOrderForm["releaseStart"]!!) > LocalDate.parse(today()))
        assertTrue(LocalDate.parse(shipForm["releaseStart"]!!) < LocalDate.parse(today()))
    }

    @Test
    fun getItemHomePreview_partialFailureKeepsSuccessfulSection() = runTest {
        mock.enqueue(hobbyListJson("预订手办"))
        mock.enqueue("""{"success":false,"code":500,"msg":"error","data":null}""")

        val result = repository.getItemHomePreview(10000)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().preOrderItems.size + result.getOrThrow().releaseItems.size)
    }

    @Test
    fun queryHobbyList_sendsParamsAndMapsItems() = runTest {
        mock.enqueue(hobbyListJson("筛选手办"))

        val result = repository.queryHobbyList(
            mapOf("category" to "100", "keyword" to "初音", "page" to "2")
        )

        assertTrue(result.isSuccess)
        assertEquals("筛选手办", result.getOrThrow().single().nameCN)
        val form = parseForm(mock.takeRequest().recordedBody())
        assertEquals("100", form["category"])
        assertEquals("初音", form["keyword"])
        assertEquals("2", form["page"])
    }

    @Test
    fun queryHobbyList_failureThrowsApiException() = runTest {
        mock.enqueue("""{"success":false,"code":400,"msg":"参数错误","data":null}""")

        val result = repository.queryHobbyList(mapOf("category" to "100"))

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ApiException
        assertEquals(400, error.code)
        assertEquals("参数错误", error.message)
    }

    @Test
    fun dateHelpers_matchCalendarContract() {
        // KMP：kotlinx-datetime 等价（Date.plus(MONTH) 为溢出钳制语义，与 Calendar 一致）
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        assertEquals(today.plus(2, DateTimeUnit.MONTH).toString(), repository.calculatePreOrderStartDate())
        val shipRange = repository.calculateShipDateRange()
        assertEquals(today.minus(3, DateTimeUnit.MONTH).toString(), shipRange.first)
        assertEquals(today.toString(), shipRange.second)
    }

    private fun today(): String = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
}
