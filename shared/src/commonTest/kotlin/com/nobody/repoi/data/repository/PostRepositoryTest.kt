package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDataStores
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
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.serialization.json.Json

/**
 * 讨论贴仓储单测：帖子详情、回帖列表适配、发表回帖、楼中楼回复与删除。
 */
class PostRepositoryTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var client: HttpClient
    private lateinit var api: HpoiApi
    private lateinit var json: Json
    private lateinit var repository: PostRepository
    private lateinit var tokenManager: TokenManager
    private lateinit var stores: TestDataStores

    @BeforeTest
    fun setUp() {
        mock = MockRepoEngine()
        json = appJson()
        client = HttpClient(mock.engine) { expectSuccess = true }
        api = HpoiApi(client, json)
        stores = TestDataStores()
        tokenManager = TokenManager(stores.createDataStore("token"))
        tokenManager.currentToken = "utoken-test"
        repository = PostRepository(api, tokenManager)
    }

    @AfterTest
    fun tearDown() {
        client.close()
        stores.cleanUp()
    }

    @Test
    fun getPostDetail_success_parsesAllFields() = runTest {
        mock.enqueue(
                """
                {
                    "success": true,
                    "code": 200,
                    "msg": "ok",
                    "data": {
                        "posts": {
                            "id": 12345,
                            "itemId": 12345,
                            "title": "【开箱】测试帖子标题",
                            "content": "<p>正文内容</p>",
                            "hits": 108,
                            "discussCount": 6,
                            "pics": "/posts/n/1.jpg,/posts/n/2.jpg",
                            "addTime": 1700000000000,
                            "user": {
                                "id": 888,
                                "nickname": "模型玩家",
                                "header": "/head/s/avatar.jpg",
                                "level": 5
                            },
                            "forum": {
                                "id": 1,
                                "name": "手办交流"
                            },
                            "relateItem": {
                                "itemId": 999,
                                "nameCN": "阿尔托莉雅",
                                "cover": "/cover/s/saber.jpg",
                                "itemType": "hobby"
                            },
                            "zoneIds": [101, 102]
                        }
                    }
                }
                """.trimIndent()
            )

        val result = repository.getPostDetail(12345L, 12345L)
        assertTrue(result.isSuccess)
        val post = result.getOrThrow()
        assertEquals(12345L, post.id)
        assertEquals("【开箱】测试帖子标题", post.title)
        assertEquals("<p>正文内容</p>", post.content)
        assertEquals(108, post.hits)
        assertEquals(6, post.discussCount)
        assertEquals(2, post.picList.size)
        assertEquals("/posts/n/1.jpg", post.picList[0])
        assertEquals("模型玩家", post.user?.nickname)
        assertEquals(5, post.user?.level)
        assertEquals("手办交流", post.forum?.name)
        assertEquals("阿尔托莉雅", post.relateItem?.displayName)
        assertEquals(2, post.zoneIds?.size)

        val recorded = mock.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.recordedPath?.contains("api/posts/info-v2") == true)
        assertTrue(recorded.recordedPath?.contains("nodeId=12345") == true)
        assertTrue(recorded.recordedPath?.contains("itemId=12345") == true)
    }

    @Test
    fun getPostDetail_failure_throwsApiException() = runTest {
        // info-v2 失败后 fallback 到 info-v1 也失败
        mock.enqueue(
                """{"success":false,"code":404,"msg":"帖子不存在或已删除","data":null}"""
            )
        mock.enqueue(
                """{"success":false,"code":404,"msg":"帖子不存在或已删除","data":null}"""
            )

        val result = repository.getPostDetail(99999L)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is ApiException)
        assertEquals(404, (ex as ApiException).code)
        assertEquals("帖子不存在或已删除", ex.message)
    }

    @Test
    fun getPostDetail_withNodeId_resolvesViaV1AndEnrichesViaV2() = runTest {
        // 1. info-v1 响应基础数据（包含 itemId = 5450）
        mock.enqueue(
                """
                {
                    "success": true,
                    "code": 200,
                    "msg": "ok",
                    "data": {
                        "posts": {
                            "id": 40819834,
                            "itemId": 5450,
                            "title": "大号节点号帖子",
                            "content": "<p>正文</p>"
                        }
                    }
                }
                """.trimIndent()
            )
        // 2. info-v2 补齐 relateItem
        mock.enqueue(
                """
                {
                    "success": true,
                    "code": 200,
                    "msg": "ok",
                    "data": {
                        "posts": {
                            "id": 40819834,
                            "itemId": 5450,
                            "title": "大号节点号帖子",
                            "content": "<p>正文</p>",
                            "relateItem": {
                                "itemId": 999,
                                "nameCN": "补齐的手办"
                            }
                        }
                    }
                }
                """.trimIndent()
            )

        val result = repository.getPostDetail(40819834L)
        assertTrue(result.isSuccess)
        val post = result.getOrThrow()
        assertEquals(40819834L, post.id)
        assertEquals(5450L, post.itemId)
        assertEquals("补齐的手办", post.relateItem?.displayName)

        val req1 = mock.takeRequest()
        assertTrue(req1.path?.contains("api/posts/info") == true)
        val req2 = mock.takeRequest()
        assertTrue(req2.path?.contains("api/posts/info-v2") == true)
        assertTrue(req2.path?.contains("itemId=5450") == true)
    }

    @Test
    fun getDiscussList_success_mapsToCommentItem() = runTest {
        mock.enqueue(
                """
                {
                    "success": true,
                    "code": 200,
                    "msg": "ok",
                    "data": {
                        "discussList": [
                            {
                                "id": 1001,
                                "floor": 1,
                                "content": "沙发支持！",
                                "addTime": "2024-01-01 12:00:00",
                                "replyCount": 1,
                                "user": {
                                    "id": 777,
                                    "nickname": "楼友小明",
                                    "level": 3
                                },
                                "replyArr": [
                                    {
                                        "id": 2001,
                                        "content": "感谢支持",
                                        "addTime": "2024-01-01 12:05:00",
                                        "user": {
                                            "id": 888,
                                            "nickname": "楼主"
                                        }
                                    }
                                ]
                            }
                        ]
                    }
                }
                """.trimIndent()
            )

        val result = repository.getDiscussList(12345L, order = "", page = 1)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(1, list.size)
        val comment = list[0]
        assertEquals(1001L, comment.id)
        assertEquals(1, comment.floor)
        assertEquals("沙发支持！", comment.content)
        assertEquals("楼友小明", comment.user?.nickname)
        assertEquals(1, comment.reply)
        assertEquals(1, comment.replys?.size)
        val reply = comment.replys!![0]
        assertEquals(2001L, reply.id)
        assertEquals("感谢支持", reply.content)
        assertEquals("楼主", reply.user?.nickname)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.recordedPath?.contains("api/discuss/get") == true)
        val body = recorded.recordedBody()
        assertTrue(body.contains("itemNodeId=12345"))
    }

    @Test
    fun getDiscussList_codeMinusOne_success() = runTest {
        // Hpoi 服务端 discuss 接口成功时特异性返回 code: -1
        mock.enqueue(
                """
                {
                    "success": true,
                    "code": -1,
                    "msg": "",
                    "data": {
                        "discussList": [
                            {
                                "id": 8801,
                                "floor": 1,
                                "content": "特异性状态码回帖测试",
                                "addTime": "2024-01-01 12:00:00"
                            }
                        ]
                    }
                }
                """.trimIndent()
            )

        val result = repository.getDiscussList(40819834L, order = "", page = 1)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(1, list.size)
        assertEquals(8801L, list[0].id)
        assertEquals("特异性状态码回帖测试", list[0].content)
    }

    @Test
    fun addDiscuss_notLoggedIn_failsEarly() = runTest {
        tokenManager.currentToken = ""
        val result = repository.addDiscuss(12345L, "测试回帖")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is ApiException)
        assertEquals(4001, (ex as ApiException).code)
    }

    @Test
    fun addDiscuss_success() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = repository.addDiscuss(12345L, "测试回帖", emoticon = "[{\"key\":\"stickers/1\"}]")
        assertTrue(result.isSuccess)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.recordedPath?.contains("api/discuss/add") == true)
        val body = recorded.recordedBody()
        val decoded = java.net.decodeURLPart(body)
        assertTrue(decoded.contains("itemNodeId=12345"))
        assertTrue(decoded.contains("content=测试回帖"))
    }

    @Test
    fun addDiscussReply_success() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = repository.addDiscussReply(
            postId = 12345L,
            discussId = 1001L,
            content = "楼中楼回复",
            toUserId = 777L
        )
        assertTrue(result.isSuccess)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.recordedPath?.contains("api/discuss/reply/add") == true)
        val body = recorded.recordedBody()
        assertTrue(body.contains("itemNodeId=12345"))
        assertTrue(body.contains("discussNodeId=1001"))
        assertTrue(body.contains("toUserNodeId=777"))
    }

    @Test
    fun deleteDiscuss_subReply_sendsCorrectType() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = repository.deleteDiscuss(id = 2001L, isSubReply = true)
        assertTrue(result.isSuccess)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.recordedPath?.contains("api/discuss/del") == true)
        val body = recorded.recordedBody()
        assertTrue(body.contains("nodeId=2001"))
        assertTrue(body.contains("type=discuss_reply"))
    }

    @Test
    fun deletePost_success() = runTest {
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = repository.deletePost(12345L)
        assertTrue(result.isSuccess)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.recordedPath?.contains("api/posts/del") == true)
        val body = recorded.recordedBody()
        assertTrue(body.contains("nodeId=12345"))
    }

    @Test
    fun operations_withInvalidId_failEarly() = runTest {
        val detailResult = repository.getPostDetail(-1L)
        assertTrue(detailResult.isFailure)
        assertEquals(400, (detailResult.exceptionOrNull() as ApiException).code)

        val discussResult = repository.getDiscussList(0L)
        assertTrue(discussResult.isFailure)
        assertEquals(400, (discussResult.exceptionOrNull() as ApiException).code)

        val addDiscussResult = repository.addDiscuss(0L, "内容")
        assertTrue(addDiscussResult.isFailure)
        assertEquals(400, (addDiscussResult.exceptionOrNull() as ApiException).code)

        val addReplyResult = repository.addDiscussReply(0L, 0L, "回复")
        assertTrue(addReplyResult.isFailure)
        assertEquals(400, (addReplyResult.exceptionOrNull() as ApiException).code)

        val deleteDiscussResult = repository.deleteDiscuss(0L, false)
        assertTrue(deleteDiscussResult.isFailure)
        assertEquals(400, (deleteDiscussResult.exceptionOrNull() as ApiException).code)

        val deletePostResult = repository.deletePost(0L)
        assertTrue(deletePostResult.isFailure)
        assertEquals(400, (deletePostResult.exceptionOrNull() as ApiException).code)
    }
}
