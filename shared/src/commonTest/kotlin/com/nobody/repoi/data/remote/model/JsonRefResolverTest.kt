package com.nobody.repoi.data.remote.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `JsonRefResolver` KMP 移植版（原 `JsonRefResolverTest` 逐用例对齐，JUnit4 → kotlin.test）。
 */
class JsonRefResolverTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun noRefs_returnsEquivalentTree() {
        val element = json.parseToJsonElement("""{"list":[{"id":1,"cover":"a.jpg"}]}""")
        val resolved = JsonRefResolver.resolveRefs(element).jsonObject
        assertEquals("a.jpg", resolved["list"]!!.jsonArray[0].jsonObject["cover"]!!.toString().trim('"'))
    }

    @Test
    fun refDedup_restoresFullObject() {
        val element = json.parseToJsonElement(
            """{"list":[
            |{"id":1,"relateItem":{"@id":"h1","id":100,"cover":"c.jpg"}},
            |{"id":2,"relateItem":{"@ref":"h1"}}
            |]}""".trimMargin()
        )
        val list = JsonRefResolver.resolveRefs(element).jsonObject["list"]!!.jsonArray
        val restored = list[1].jsonObject["relateItem"]!!.jsonObject
        assertEquals(100, restored["id"].toString().toInt())
        assertEquals("c.jpg", restored["cover"].toString().trim('"'))
    }

    @Test
    fun sameRefUsedByManyItems_restoresEveryItem() {
        val element = json.parseToJsonElement(
            """{"list":[
            |{"id":1,"relateItem":{"@id":"h1","id":100,"cover":"c.jpg"}},
            |{"id":2,"relateItem":{"@ref":"h1"}},
            |{"id":3,"relateItem":{"@ref":"h1"}}
            |]}""".trimMargin()
        )
        val list = JsonRefResolver.resolveRefs(element).jsonObject["list"]!!.jsonArray
        assertEquals("c.jpg", list[1].jsonObject["relateItem"]!!.jsonObject["cover"].toString().trim('"'))
        assertEquals("c.jpg", list[2].jsonObject["relateItem"]!!.jsonObject["cover"].toString().trim('"'))
    }

    @Test
    fun refWithSiblingFields_keepsSiblings() {
        val element = json.parseToJsonElement(
            """{"list":[
            |{"id":1,"relateItem":{"@id":"h1","id":100,"cover":"c.jpg"}},
            |{"id":2,"relateItem":{"@ref":"h1"},"collectState":"care"}
            |]}""".trimMargin()
        )
        val list = JsonRefResolver.resolveRefs(element).jsonObject["list"]!!.jsonArray
        val item = list[1].jsonObject
        assertEquals("c.jpg", item["relateItem"]!!.jsonObject["cover"].toString().trim('"'))
        assertEquals("care", item["collectState"].toString().trim('"'))
    }

    @Test
    fun refBodySiblingFields_mergedOntoTarget() {
        val element = json.parseToJsonElement(
            """{"list":[
            |{"id":1,"relateItem":{"@id":"h1","id":100,"cover":"c.jpg"}},
            |{"id":2,"relateItem":{"@ref":"h1","extra":"keep","cover":"override.jpg"}}
            |]}""".trimMargin()
        )
        val list = JsonRefResolver.resolveRefs(element).jsonObject["list"]!!.jsonArray
        val restored = list[1].jsonObject["relateItem"]!!.jsonObject
        assertEquals(100, restored["id"].toString().toInt())
        assertEquals("override.jpg", restored["cover"].toString().trim('"'))
        assertEquals("keep", restored["extra"].toString().trim('"'))
    }

    @Test
    fun danglingRef_keepsOriginalNode() {
        // 必须存在至少一个 @id，否则 resolveRefs 会在空索引处提前返回，悬空引用分支不可达
        val element = json.parseToJsonElement(
            """{"target":{"@id":"h1","name":"A"},"relateItem":{"@ref":"missing"}}"""
        )
        val resolved = JsonRefResolver.resolveRefs(element).jsonObject
        assertTrue(resolved["relateItem"]!!.jsonObject.containsKey("@ref"))
        assertEquals("A", resolved["target"]!!.jsonObject["name"]!!.toString().trim('"'))
    }

    @Test
    fun commentUsersWithRef_restoresAllUsersAndReplies() {
        val element = json.parseToJsonElement(
            """{
            |  "@id": "1",
            |  "code": 200,
            |  "success": true,
            |  "data": {
            |    "list": [
            |      {
            |        "id": 101,
            |        "content": "第一条评论",
            |        "user": {
            |          "@id": "u5",
            |          "userId": 8888,
            |          "nickname": "乳不巨何以聚人心",
            |          "header": "2024/01/avatar.jpg",
            |          "level": 5
            |        }
            |      },
            |      {
            |        "id": 102,
            |        "content": "第二条评论初版预定1060",
            |        "user": {
            |          "@ref": "u5"
            |        },
            |        "replys": [
            |          {
            |            "id": 201,
            |            "content": "再版1120",
            |            "user": {
            |              "@ref": "u5"
            |            }
            |          },
            |          {
            |            "id": 202,
            |            "content": "初版出荷都爆雷",
            |            "user": {
            |              "${'$'}ref": "u5"
            |            }
            |          }
            |        ]
            |      },
            |      {
            |        "id": 103,
            |        "content": "第三条评论似乎再版比初版更贵了",
            |        "user": {
            |          "@ref": "u5"
            |        }
            |      }
            |    ]
            |  }
            |}""".trimMargin()
        )
        val resolved = JsonRefResolver.resolveRefs(element).jsonObject
        val comments = resolved["data"]!!.jsonObject["list"]!!.jsonArray

        val user1 = comments[0].jsonObject["user"]!!.jsonObject
        assertEquals("乳不巨何以聚人心", user1["nickname"]!!.toString().trim('"'))
        assertEquals(5, user1["level"]!!.toString().toInt())

        val user2 = comments[1].jsonObject["user"]!!.jsonObject
        assertEquals("乳不巨何以聚人心", user2["nickname"]!!.toString().trim('"'))
        assertEquals(5, user2["level"]!!.toString().toInt())
        assertEquals("2024/01/avatar.jpg", user2["header"]!!.toString().trim('"'))

        val replies = comments[1].jsonObject["replys"]!!.jsonArray
        val replyUser1 = replies[0].jsonObject["user"]!!.jsonObject
        assertEquals("乳不巨何以聚人心", replyUser1["nickname"]!!.toString().trim('"'))
        assertEquals(5, replyUser1["level"]!!.toString().toInt())

        val replyUser2 = replies[1].jsonObject["user"]!!.jsonObject
        assertEquals("乳不巨何以聚人心", replyUser2["nickname"]!!.toString().trim('"'))
        assertEquals(5, replyUser2["level"]!!.toString().toInt())

        val user3 = comments[2].jsonObject["user"]!!.jsonObject
        assertEquals("乳不巨何以聚人心", user3["nickname"]!!.toString().trim('"'))
        assertEquals(5, user3["level"]!!.toString().toInt())
    }

    @Test
    fun envelopeWithSameId_doesNotShadowEntity() {
        val element = json.parseToJsonElement(
            """{
            |  "@id": "1",
            |  "code": 200,
            |  "success": true,
            |  "data": {
            |    "list": [
            |      {
            |        "id": 1,
            |        "user": {
            |          "@id": "1",
            |          "nickname": "实体用户",
            |          "level": 3
            |        }
            |      },
            |      {
            |        "id": 2,
            |        "user": {
            |          "@ref": "1"
            |        }
            |      }
            |    ]
            |  }
            |}""".trimMargin()
        )
        val resolved = JsonRefResolver.resolveRefs(element).jsonObject
        val comments = resolved["data"]!!.jsonObject["list"]!!.jsonArray
        val user2 = comments[1].jsonObject["user"]!!.jsonObject
        assertEquals("实体用户", user2["nickname"]!!.toString().trim('"'))
        assertEquals(3, user2["level"]!!.toString().toInt())
    }
}
