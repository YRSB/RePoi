package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.recordedBody
import com.nobody.repoi.test.recordedPath
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
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
import com.nobody.repoi.data.remote.model.CollectConstants
import kotlinx.serialization.json.Json

/**
 * 收藏写侧链路单测：批量取消收藏、作品收藏整链（标签池/读取/保存/取消）、图片关联条目。
 */
class CollectRepositoryTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var repository: CollectRepository

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        repository = CollectRepository(api, json)
    }

    @AfterTest
    fun tearDown() {
        client.close()
    }

    @Test
    fun cancelCollectBatch_postsJsonArrayOfOuterNodeIds() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"取消成功","data":{}}""")

        val result = repository.cancelCollectBatch(listOf(1L, 2L, 3L))

        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertEquals(HttpMethod.Post, recorded.method)
        // 服务端要求 "[1,2,3]" 形式的 JSON 数组字符串
        assertEquals("[1,2,3]", formOf(recorded.recordedBody())["idArr"])
    }

    @Test
    fun cancelCollectBatch_emptyIds_shortCircuitsWithoutRequest() = runTest {
        val result = repository.cancelCollectBatch(emptyList())
        assertTrue(result.isSuccess)
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun cancelCollectBatch_onApiFailure_throwsApiException() = runTest {
        mock.enqueue("""{"success":false,"code":500,"msg":"请稍后再试","data":null}""")

        val result = repository.cancelCollectBatch(listOf(9L))

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is ApiException)
        assertEquals(500, (exception as ApiException).code)
    }

    @Test
    fun getWorksCollectTags_parsesBothStringAndObjectElements() = runTest {
        mock.enqueue(
                """{"success":true,"code":200,"msg":"ok","data":{"textTagList":["热血","校园",{"tagValue":"悬疑"},"  "]}}"""
            )

        val tags = repository.getWorksCollectTags(page = 1, pageSize = 20).getOrThrow()

        // 空串与纯空白项被过滤
        assertEquals(listOf("热血", "校园", "悬疑"), tags)

        val recorded = mock.takeRequest()
        assertEquals(HttpMethod.Get, recorded.method)
        val query = queryOf(recorded.recordedPath ?: "")
        assertEquals("1", query["page"])
        assertEquals("20", query["pageSize"])
    }

    @Test
    fun getWorksCollect_unwrapsGleanEnvelope() = runTest {
        mock.enqueue(
                """
                {"success":true,"code":200,"msg":"ok","data":{"glean":{
                    "itemId":20001,
                    "itemType":"Works",
                    "state":2,
                    "current":8,
                    "comment":"追到第八集",
                    "textTags":[{"tagValue":"热血"},{"tagValue":"校园"}],
                    "actionDate":"2026-04-01"
                }}}
                """.trimIndent()
            )

        val data = repository.getWorksCollect(20001L).getOrThrow()

        assertEquals(20001L, data?.actualItemId)
        assertEquals(2, data?.state)
        assertEquals("在看", data?.stateText)
        assertTrue(data?.isCollected == true)
        assertEquals(8, data?.current)
        assertEquals("追到第八集", data?.actualComment)
        assertEquals("热血,校园", data?.tagStr)

        val recorded = mock.takeRequest()
        assertEquals(HttpMethod.Get, recorded.method)
        assertEquals("/api/works/glean/get/20001", recorded.recordedPath)
    }

    @Test
    fun getWorksCollect_supportsFlatResponseShape() = runTest {
        mock.enqueue(
                """{"success":true,"code":200,"msg":"ok","data":{"itemId":30001,"state":1,"textTags":[{"tagValue":"日常"}]}}"""
            )

        val data = repository.getWorksCollect(30001L).getOrThrow()

        assertEquals(30001L, data?.actualItemId)
        assertEquals(1, data?.state)
        assertEquals("想看", data?.stateText)
        assertEquals("日常", data?.tagStr)
    }

    @Test
    fun getWorksCollect_notCollected_returnsNull() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val data = repository.getWorksCollect(40001L).getOrThrow()

        assertNull(data)
    }

    @Test
    fun saveWorksCollect_postsWorksItemTypeAndAllFields() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"保存成功","data":{}}""")

        val result = repository.saveWorksCollect(
            worksId = 20001L,
            state = 2,
            tagStr = "热血,校园",
            comment = "追到第八集"
        )

        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertEquals(HttpMethod.Post, recorded.method)
        val form = formOf(recorded.recordedBody())
        assertEquals("Works", form["itemType"])
        assertEquals("20001", form["itemId"])
        assertEquals("热血,校园", form["tagStr"])
        assertEquals("追到第八集", form["comment"])
        assertEquals("2", form["state"])
    }

    @Test
    fun deleteWorksCollect_postsWorksItemTypeAndId() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"取消成功","data":{}}""")

        val result = repository.deleteWorksCollect(20001L)

        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertEquals(HttpMethod.Post, recorded.method)
        val form = formOf(recorded.recordedBody())
        assertEquals("Works", form["itemType"])
        assertEquals("20001", form["itemId"])
    }

    @Test
    fun getPictureRelate_unwrapsRelateItemAndSkipsEmptyEntries() = runTest {
        mock.enqueue(
                """
                {"success":true,"code":200,"msg":"ok","data":{"list":[
                    {"relateItem":{"id":1,"itemId":11,"itemType":"album","name":"Album","categoryId":70000}},
                    {"relateItem":{"id":2,"itemId":22,"itemType":"hobby","nameCN":"手办","categoryId":10000}},
                    {"relateItem":{"id":3,"itemId":33,"itemType":"article","name":"文章"}},
                    {"relateItem":null}
                ]}}
                """.trimIndent()
            )

        val items = repository.getPictureRelate(90001L).getOrThrow()

        assertEquals(3, items.size)
        assertEquals("album", items[0].itemType)
        assertEquals(11L, items[0].itemId)
        assertEquals("手办", items[1].nameCN)
        assertEquals("article", items[2].itemType)

        val recorded = mock.takeRequest()
        assertEquals(HttpMethod.Post, recorded.method)
        assertEquals("90001", formOf(recorded.recordedBody())["picId"])
    }

    @Test
    fun getPictureRelate_onEmptyList_returnsEmptyList() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")

        val items = repository.getPictureRelate(90001L).getOrThrow()

        assertTrue(items.isEmpty())
    }

    @Test
    fun submitReport_postsOriginalFieldNamesObjIdUserReasonAndType() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"举报已提交","data":{}}""")

        val result = repository.submitReport(nodeId = 90001L, content = "图片内容违规")

        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertEquals(HttpMethod.Post, recorded.method)
        // ⚠️ 原版字段名是 objId / userReason / type，不是 nodeId / content
        val form = formOf(recorded.recordedBody())
        assertEquals("90001", form["objId"])
        assertEquals("图片内容违规", form["userReason"])
        assertEquals("picture_info", form["type"])
        assertFalse(form.containsKey("nodeId"))
        assertFalse(form.containsKey("content"))
    }

    @Test
    fun submitReport_supportsOtherReportTypes() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"举报已提交","data":{}}""")

        repository.submitReport(
            nodeId = 123L,
            content = "二手描述与实物不符",
            type = CollectConstants.REPORT_TYPE_RESALE
        ).getOrThrow()

        val form = formOf(mock.takeRequest().recordedBody())
        assertEquals("resale_item", form["type"])
    }

    @Test
    fun submitReport_blankReason_shortCircuitsWithoutRequest() = runTest {
        val result = repository.submitReport(nodeId = 90001L, content = "   ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ApiException)
        assertEquals(0, mock.requestCount)
    }

    /** 解析 x-www-form-urlencoded 请求体 */
    private fun formOf(body: String): Map<String, String> =
        body.split("&").filter { it.isNotBlank() }.associate {
            val parts = it.split("=")
            parts[0].decodeURLPart() to
                (if (parts.size > 1) parts[1].decodeURLPart() else "")
        }

    /** 解析 URL 查询串 */
    private fun queryOf(path: String): Map<String, String> = formOf(path.substringAfter("?", ""))
}