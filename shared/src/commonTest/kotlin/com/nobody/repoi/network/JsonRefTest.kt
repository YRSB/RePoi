package com.nobody.repoi.network

import com.nobody.repoi.data.remote.model.CommonResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * [HttpResponse.bodyAsJsonRef] 行为测试，移植自老项目
 * `FastJsonRefConverterFactoryTest`（Retrofit + MockWebServer → Ktor MockEngine）。
 *
 * 注意：完整 `CommentPayload` DTO 在 Wave 2 才迁移，此处用测试本地最小 DTO
 * 复刻同一份响应 JSON 的断言语义。
 */
class JsonRefTest {

    @Serializable
    data class TestUser(
        val id: Long = 0,
        val userId: Long = 0,
        val nickname: String? = null,
        val header: String? = null,
        val level: Int = 0
    )

    @Serializable
    data class TestReply(
        val id: Long = 0,
        val content: String? = null,
        val user: TestUser? = null
    )

    @Serializable
    data class TestComment(
        val id: Long = 0,
        val content: String? = null,
        val user: TestUser? = null,
        val replys: List<TestReply>? = null
    )

    @Serializable
    data class TestCommentPayload(
        val list: List<TestComment>? = null
    )

    private val json = appJson()

    private fun clientRespondingWith(body: String): HttpClient {
        val engine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return HttpClient(engine)
    }

    @Test
    fun responseWithFastJsonRef_restoresDuplicateUserFields() = runTest {
        val mockBody = """
        {
          "@id": "1",
          "code": 200,
          "success": true,
          "data": {
            "list": [
              {
                "id": 101,
                "content": "这厂2024年之前无标盒散特别多的",
                "user": {
                  "@id": "u5",
                  "id": 8888,
                  "userId": 8888,
                  "nickname": "乳不巨何以聚人心",
                  "header": "avatar.jpg",
                  "level": 5
                }
              },
              {
                "id": 102,
                "content": "初版预定1060",
                "user": {
                  "@ref": "u5"
                },
                "replys": [
                  {
                    "id": 201,
                    "content": "再版1120",
                    "user": {
                      "@ref": "u5"
                    }
                  },
                  {
                    "id": 202,
                    "content": "初版出荷都爆雷",
                    "user": {
                      "${'$'}ref": "u5"
                    }
                  }
                ]
              },
              {
                "id": 103,
                "content": "似乎再版比初版更贵了",
                "user": {
                  "@ref": "u5"
                }
              }
            ]
          }
        }
        """.trimIndent()

        val client = clientRespondingWith(mockBody)
        val response: CommonResponse<TestCommentPayload> =
            client.get("https://example.com/api/comment/get").bodyAsJsonRef(json)
        client.close()

        val list = response.data?.list
        assertNotNull(list)
        assertEquals(3, list.size)

        // 第一条正常反序列化
        val user1 = list[0].user
        assertNotNull(user1)
        assertEquals("乳不巨何以聚人心", user1.nickname)
        assertEquals(5, user1.level)

        // 第二条（引用对象）被成功还原，不再是默认空对象
        val user2 = list[1].user
        assertNotNull(user2)
        assertEquals("乳不巨何以聚人心", user2.nickname)
        assertEquals(5, user2.level)
        assertEquals("avatar.jpg", user2.header)

        // 子评论回复列表中的引用用户也被成功还原
        val replys = list[1].replys
        assertNotNull(replys)
        assertEquals(2, replys.size)
        assertEquals("乳不巨何以聚人心", replys[0].user?.nickname)
        assertEquals(5, replys[0].user?.level)
        assertEquals("乳不巨何以聚人心", replys[1].user?.nickname)
        assertEquals(5, replys[1].user?.level)

        // 第三条评论的引用用户也被成功还原
        val user3 = list[2].user
        assertNotNull(user3)
        assertEquals("乳不巨何以聚人心", user3.nickname)
        assertEquals(5, user3.level)
    }

    @Test
    fun responseWithoutRef_decodesNormally() = runTest {
        val mockBody = """{"code":200,"success":true,"msg":"ok","data":{"list":[]}}"""

        val client = clientRespondingWith(mockBody)
        val response: CommonResponse<TestCommentPayload> =
            client.get("https://example.com/api/comment/get").bodyAsJsonRef(json)
        client.close()

        assertEquals(true, response.isSuccess)
        assertEquals("ok", response.msg)
        assertEquals(0, response.data?.list?.size)
    }
}
