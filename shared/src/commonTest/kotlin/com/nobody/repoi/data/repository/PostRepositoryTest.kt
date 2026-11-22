package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostRepositoryTest {

    private fun setup(): Pair<MockApi, PostRepository> {
        val mock = MockApi()
        return mock to PostRepository(mock.api())
    }

    @Test
    fun getPostDetail_success_parsesAllFields() = runTest {
        val (mock, repository) = setup()
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
        assertTrue(recorded.path?.contains("api/posts/info-v2") == true)
        assertTrue(recorded.path?.contains("nodeId=12345") == true)
        assertTrue(recorded.path?.contains("itemId=12345") == true)
    }

    @Test
    fun getPostDetail_failure_throwsApiException() = runTest {
        val (mock, repository) = setup()

        mock.enqueue("""{"success":false,"code":404,"msg":"帖子不存在或已删除","data":null}""")
        mock.enqueue("""{"success":false,"code":404,"msg":"帖子不存在或已删除","data":null}""")

        val result = repository.getPostDetail(99999L)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is ApiException)
        assertEquals(404, ex.code)
        assertEquals("帖子不存在或已删除", ex.message)
    }

    @Test
    fun getPostDetail_withNodeId_resolvesViaV1AndEnrichesViaV2() = runTest {
        val (mock, repository) = setup()

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
        val (mock, repository) = setup()
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
        assertTrue(recorded.path?.contains("api/discuss/get") == true)
        assertTrue(recorded.bodyContains("itemNodeId=12345"))
    }

    @Test
    fun getDiscussList_codeMinusOne_success() = runTest {
        val (mock, repository) = setup()

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
    fun addDiscuss_success() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = repository.addDiscuss(12345L, "测试回帖", emoticon = "[{\"key\":\"stickers/1\"}]")
        assertTrue(result.isSuccess)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path?.contains("api/discuss/add") == true)

        val form = recorded.formEntries()
        assertEquals("12345", form["itemNodeId"])
        assertEquals("测试回帖", form["content"])
    }

    @Test
    fun addDiscussReply_success() = runTest {
        val (mock, repository) = setup()
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
        assertTrue(recorded.path?.contains("api/discuss/reply/add") == true)
        val form = recorded.formEntries()
        assertEquals("12345", form["itemNodeId"])
        assertEquals("1001", form["discussNodeId"])
        assertEquals("777", form["toUserNodeId"])
    }

    @Test
    fun deleteDiscuss_subReply_sendsCorrectType() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = repository.deleteDiscuss(id = 2001L, isSubReply = true)
        assertTrue(result.isSuccess)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path?.contains("api/discuss/del") == true)
        val form = recorded.formEntries()
        assertEquals("2001", form["nodeId"])
        assertEquals("discuss_reply", form["type"])
    }

    @Test
    fun deletePost_success() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = repository.deletePost(12345L)
        assertTrue(result.isSuccess)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path?.contains("api/posts/del") == true)
        assertTrue(recorded.bodyContains("nodeId=12345"))
    }

    @Test
    fun operations_withInvalidId_failEarly() = runTest {
        val (_, repository) = setup()
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
    @Test
    fun addDiscuss_failure_throwsApiException() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":false,"code":4001,"msg":"登录已失效"}""")
        val result = repository.addDiscuss(postId = 12345L, content = "回帖内容")
        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun deletePost_failure_throwsApiException() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":false,"code":4001,"msg":"登录已失效"}""")
        val result = repository.deletePost(postId = 12345L)
        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
    }
}
