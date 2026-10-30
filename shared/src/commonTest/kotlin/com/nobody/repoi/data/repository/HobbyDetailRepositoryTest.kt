package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 手办详情仓储测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `HobbyDetailRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；`server.dispatcher` → [MockApi.route]；
 * JUnit4 → kotlin.test）。
 */
class HobbyDetailRepositoryTest {

    private fun setup(): Pair<MockApi, HobbyDetailRepository> {
        val mock = MockApi()
        return mock to HobbyDetailRepository(mock.api())
    }

    @Test
    fun getHobbyDetail_successMapsData() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"itemData":{"id":5483525,"nodeId":0,"itemId":59570,"nameCN":"测试手办"}}}""")
        val result = repository.getHobbyDetail(59570L, "hobby")
        assertTrue(result.isSuccess)
        assertEquals(5483525L, result.getOrThrow().actualNodeId)
    }

    @Test
    fun getHobbyDetail_failureReturnsFailureWithCode() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(404, "未找到"))
        val result = repository.getHobbyDetail(999L, "hobby")
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ApiException
        assertEquals(404, error.code)
    }

    @Test
    fun getComments_emptyListSuccess() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        val result = repository.getComments(5483525L)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun getComments_whenMinus9007_returnsEmptyList() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":false,"code":-9007,"msg":"找不到该评论，可能已被删除","data":{}}""")
        val result = repository.getComments(5483525L)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun getTimeline_successMapsList() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[{"actionData":"delay","actionComment":"出荷时间由2024年5月延期至2024年7月","actionDateString":"2024-05-15"}]}}""")
        val result = repository.getTimeline(5483525L, page = 1, pageSize = 20)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(1, list.size)
        assertEquals("出荷延期", list[0].displayTypeLabel)
        assertEquals("2024-05-15", list[0].actionDateString)
        val recorded = mock.takeRequest()
        assertTrue(recorded.path!!.contains("nodeId=5483525"))
    }

    @Test
    fun getRelatedHobbies_successMergesGroupAndPushDeduplicating() = runTest {
        val (mock, repository) = setup()
        mock.route(
            "api/hobby/group",
            """{"success":true,"code":200,"msg":"ok","data":{"groups":[{"typeName":"再版","items":[{"id":5483525,"itemId":5483525,"nameCN":"当前手办"},{"id":101,"itemId":101,"nameCN":"再版手办"}]}]}}"""
        )
        mock.route(
            "api/hobby/push/list/V2",
            """{"success":true,"code":200,"msg":"ok","data":{"hobbys":[{"id":101,"itemId":101,"nameCN":"再版手办"},{"id":102,"itemId":102,"nameCN":"热门推荐"}]}}"""
        )

        val result = repository.getRelatedHobbies(5483525L)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(2, list.size)
        assertEquals(101L, list[0].actualItemId)
        assertEquals("再版", list[0].typeName)
        assertEquals("再版手办", list[0].displayName)
        assertEquals(102L, list[1].actualItemId)
        assertEquals("热门推荐", list[1].displayName)
    }

    @Test
    fun getRelatedHobbies_excludesCurrentHobbyWhenItemIdDiffersFromNodeId() = runTest {
        val (mock, repository) = setup()
        // 当前条目: nodeId = 5483525, itemId = 59570
        mock.route(
            "api/hobby/group",
            """{"success":true,"code":200,"msg":"ok","data":{"groups":[{"typeName":"同款","items":[{"id":5483525,"itemId":59570,"nameCN":"当前手办"},{"id":201,"itemId":201,"nameCN":"同款不同色"}]}]}}"""
        )
        mock.route(
            "api/hobby/push/list/V2",
            """{"success":true,"code":200,"msg":"ok","data":{"hobbys":[{"id":0,"itemId":59570,"nameCN":"当前手办"},{"id":202,"itemId":202,"nameCN":"其他推荐"}]}}"""
        )

        val result = repository.getRelatedHobbies(itemNodeId = 5483525L, currentItemId = 59570L)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(2, list.size)
        assertEquals(201L, list[0].actualItemId)
        assertEquals(202L, list[1].actualItemId)
        // 验证当前条目无论是按 nodeId(5483525) 还是 itemId(59570) 均已被彻底过滤
        assertTrue(list.none { it.actualItemId == 59570L || it.id == 5483525L })
    }

    @Test
    fun getRelatedHobbies_capsAt15Items() = runTest {
        val (mock, repository) = setup()
        val groupItemsJson = (1..10).joinToString(",") { """{"id":${100 + it},"itemId":${100 + it},"nameCN":"分组手办$it"}""" }
        val pushItemsJson = (1..10).joinToString(",") { """{"id":${200 + it},"itemId":${200 + it},"nameCN":"推荐手办$it"}""" }
        mock.route(
            "api/hobby/group",
            """{"success":true,"code":200,"msg":"ok","data":{"groups":[{"typeName":"系列","items":[$groupItemsJson]}]}}"""
        )
        mock.route(
            "api/hobby/push/list/V2",
            """{"success":true,"code":200,"msg":"ok","data":{"hobbys":[$pushItemsJson]}}"""
        )

        val result = repository.getRelatedHobbies(itemNodeId = 999999L, currentItemId = 888888L)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(15, list.size)
    }

    @Test
    fun getRelatedHobbies_whenOneFails_stillReturnsOtherItems() = runTest {
        val (mock, repository) = setup()
        mock.route("api/hobby/group", "", HttpStatusCode.InternalServerError)
        mock.route(
            "api/hobby/push/list/V2",
            """{"success":true,"code":200,"msg":"ok","data":{"hobbys":[{"id":301,"itemId":301,"nameCN":"降级推荐"}]}}"""
        )

        val result = repository.getRelatedHobbies(5483525L)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(1, list.size)
        assertEquals(301L, list[0].actualItemId)
    }

    @Test
    fun getRelatedHobbies_whenAllFail_returnsFailureApiException() = runTest {
        val (mock, repository) = setup()
        mock.route("api/hobby/group", "", HttpStatusCode.InternalServerError)
        mock.route("api/hobby/push/list/V2", "", HttpStatusCode.InternalServerError)

        val result = repository.getRelatedHobbies(5483525L)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ApiException)
    }
}
