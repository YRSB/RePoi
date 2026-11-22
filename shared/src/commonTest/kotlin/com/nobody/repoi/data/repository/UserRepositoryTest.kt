package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.AmazonMoneyData
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.repository.FollowRepository
import com.nobody.repoi.data.repository.FriendState
import com.nobody.repoi.data.repository.UserFinanceRepository
import com.nobody.repoi.data.remote.model.OrderCount
import com.nobody.repoi.data.remote.model.OrderStaticData
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.remote.model.UserCollectCommonItem
import com.nobody.repoi.data.remote.model.UserProfile

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserRepositoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private fun setup(): Pair<MockApi, UserRepository> {
        val mock = MockApi()
        val followRepo = FollowRepository(mock.api(), json)
        val financeRepo = UserFinanceRepository(mock.api(), json)
        return mock to UserRepository(mock.api(), followRepo, financeRepo)
    }

    @Test
    fun loadFullProfile_1Plus5Concurrency_aggregatesAllData() = runTest {
        val (mock, repository) = setup()

        val profile = UserProfile(id = 88888L, nickname = "手办达人", userName = "master", level = 5)

        mock.route("api/user/fellow/count", """{"success":true,"code":200,"msg":"ok","data":{"data":{"fanCount":120,"friendCount":45,"praiseCount":666}}}""")
        mock.route(
            "api/user/orderStatic",
            """{"success":true,"code":200,"msg":"ok","data":{"data":{"count":{"buy":10,"preorder":5,"care":15},"allOrderMoney":[{"key":"category_100","val":[{"currency":"rmb","money":2000.0}]}],"allPreorderMoney":[{"key":"category_100","val":[{"currency":"yen","money":10000.0}]}],"amazonMoney":{"initialPrice":1000,"price":1100}}}}"""
        )
        mock.routeWhen(
            predicate = { path, form -> path.contains("api/user/collect/top") && form["state"] == "preorder" },
            body = { _, _ -> """{"success":true,"code":200,"msg":"ok","data":{"list":[{"itemId":101,"nameCN":"预定手办A"}]}}""" }
        )
        mock.routeWhen(
            predicate = { path, form -> path.contains("api/user/collect/top") && form["state"] == "buy" },
            body = { _, _ -> """{"success":true,"code":200,"msg":"ok","data":{"list":[{"itemId":201,"nameCN":"已入手办B"}]}}""" }
        )
        mock.routeWhen(
            predicate = { path, form -> path.contains("api/user/collect/top") && form["state"] == "care" },
            body = { _, _ -> """{"success":true,"code":200,"msg":"ok","data":{"list":[{"itemId":301,"nameCN":"想要手办C"}]}}""" }
        )

        val result = repository.loadFullProfile(profile)
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()

        assertEquals(88888L, data.profile.id)
        assertEquals("手办达人", data.profile.displayName)
        assertEquals(120, data.fellow.fanCount)
        assertEquals(45, data.fellow.friendCount)

        assertEquals(10, data.order.buy)
        assertEquals(5, data.order.preorder)
        assertEquals(15, data.order.care)

        assertEquals(1, data.preorderItems.size)
        assertEquals(101L, data.preorderItems[0].itemId)
        assertEquals(1, data.buyItems.size)
        assertEquals(201L, data.buyItems[0].itemId)
        assertEquals(1, data.careItems.size)
        assertEquals(301L, data.careItems[0].itemId)

        assertEquals(600.0, data.finance.supplementedTotalRmb, 0.001)
        assertEquals(2000.0, data.finance.historyTotalRmb, 0.001)
        assertEquals("+10%", data.finance.amazonFluctuation)
    }

    @Test
    fun loadFullProfile_usesProvidedProfile() = runTest {
        val (mock, repository) = setup()
        val profile = UserProfile(id = 88888L, nickname = "新昵称", userName = "master", level = 6)
        mock.route("api/user/fellow/count", """{"success":true,"code":200,"msg":"ok","data":{"data":{"fanCount":0,"friendCount":0,"praiseCount":0}}}""")
        mock.route("api/user/orderStatic", """{"success":true,"code":200,"msg":"ok","data":{"data":{"count":{"buy":0,"preorder":0,"care":0}}}}""")

        val result = repository.loadFullProfile(profile)
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("新昵称", data.profile.displayName)
        assertEquals(6, data.profile.level)
    }

    @Test
    fun userCollectCategory_apiType_matchesOriginalMapping() {

        assertEquals("hobby", UserCollectCategory.HOBBY.apiType)
        assertEquals("company", UserCollectCategory.VENDOR.apiType)
        assertEquals("series", UserCollectCategory.SERIES.apiType)
        assertEquals("works", UserCollectCategory.WORKS.apiType)
        assertEquals("charactar", UserCollectCategory.CHARACTER.apiType)
        assertEquals("person", UserCollectCategory.PERSON.apiType)
        assertEquals(UserCollectCategory.CHARACTER, UserCollectCategory.fromApiType("charactar"))
        assertEquals(UserCollectCategory.VENDOR, UserCollectCategory.fromKey(40000))

        assertNull(UserCollectCategory.fromKey(12345))
    }

    @Test
    fun collectConstants_normalizeOrderByWhitelist() {

        assertEquals("actionDate", CollectConstants.normalizeCommonOrder("actionDate"))
        assertEquals("collect", CollectConstants.normalizeCommonOrder("collect"))
        assertEquals("actionDate", CollectConstants.normalizeCommonOrder("orderTime"))
        assertEquals("actionDate", CollectConstants.normalizeCommonOrder(""))

        assertEquals("addTime", CollectConstants.normalizeMediaOrder("addTime"))
        assertEquals("praiseCount", CollectConstants.normalizeMediaOrder("praiseCount"))
        assertEquals("addTime", CollectConstants.normalizeMediaOrder("collect"))

        assertEquals("release", CollectConstants.normalizeHobbyOrder("release", "preorder"))
        assertEquals("updateDate", CollectConstants.normalizeHobbyOrder("updateDate", "preorder"))
        assertEquals("actionDate", CollectConstants.normalizeHobbyOrder("garbage", "preorder"))

        assertEquals("orderTime", CollectConstants.normalizeHobbyOrder("orderTime", "buy"))
        assertEquals("actionDate", CollectConstants.normalizeHobbyOrder("orderTime", "preorder"))
        assertEquals("actionDate", CollectConstants.normalizeHobbyOrder("orderTime", ""))
    }

    @Test
    fun userCollectCommonItem_unwrapsInnerItemAndCounts() {
        val item = json.decodeFromString<UserCollectCommonItem>(
            """
            {
                "id": 4001,
                "itemId": 0,
                "name": "Fallback",
                "names": { "name_zh": ["", "中文优先名"] },
                "itemType": "hobby",
                "pageState": 1,
                "hobbyCount": 12,
                "issueHobbyCount": 7,
                "item": {
                    "id": 9002,
                    "itemId": 9003,
                    "name": "inner name",
                    "cover": "covers/inner.jpg",
                    "categoryId": 100
                }
            }
            """.trimIndent()
        )

        assertEquals(9003L, item.actualItemId)
        assertEquals("covers/inner.jpg", item.actualCover)
        assertEquals("inner name", item.displayName)
        assertEquals("hobby", item.itemType)
        assertTrue(item.isSeriesSubPage)
        assertEquals(12, item.actualHobbyCount)
        assertEquals(7, item.actualIssueHobbyCount)
    }

    @Test
    fun userCollectCommonItem_fallsBackToOuterFieldsWhenNoInnerItem() {
        val item = json.decodeFromString<UserCollectCommonItem>(
            """{"id":4001,"itemId":5001,"nameCN":"良笑社","itemsCount":120}"""
        )
        assertEquals(5001L, item.actualItemId)
        assertEquals("良笑社", item.displayName)

        assertEquals(120, item.actualHobbyCount)
        assertEquals(0, item.actualIssueHobbyCount)
        assertNull(item.collectStateText)
    }

    @Test
    fun getUserPraiseList_success_parsesItemAndUser() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success": true,"code": 200,"msg": "ok","data": {"list": [{"user": {"level": 4,"header": "header/user_20001.jpg","nickname": "点赞达人","userName": "praiser_1","sign": "天天点赞"},"item": {"id": 50001,"itemId": 50001,"itemType": "hobby","name": "Hatsune Miku","nameCN": "初音未来","path": "hobby/50001","cover": "covers/miku.jpg","categoryId": 100,"commentCount": 30,"collect": 500}}]}}"""
        )

        val result = repository.getUserPraiseList(userId = 88888L, page = 1, pageSize = 20)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(1, list.size)

        val praiseItem = list[0]
        assertEquals("点赞达人", praiseItem.user?.nickname)
        assertEquals(50001L, praiseItem.item?.id)
        assertEquals("初音未来", praiseItem.item?.nameCN)
        assertEquals("covers/miku.jpg", praiseItem.item?.cover)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        val form = recorded.formEntries()
        assertEquals("88888", form["userNId"])
        assertEquals("1", form["page"])
        assertEquals("20", form["pageSize"])
    }

    @Test
    fun updateUserBase_success_postsKeyAndValue() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = repository.updateUserBase(key = "sign", value = "新签名")
        assertTrue(result.isSuccess)

        val req = mock.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/user/base/upd", req.path)
        val form = req.formEntries()
        assertEquals("sign", form["key"])
        assertEquals("新签名", form["val"])
    }

    @Test
    fun getUserInfo_successWithId_parsesUserInfo() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success": true,"code": 200,"msg": "ok","data": {"user": {"id": 77777,"nickname": "其他用户","level": 2}}}"""
        )

        val result = repository.getUserInfo(userId = 77777L)
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals(77777L, profile.id)
        assertEquals("其他用户", profile.displayName)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("77777", recorded.formEntries()["id"])
    }

    @Test
    fun getUserInfo_whenIdFails_fallsBackToUserId() = runTest {
        val (mock, repository) = setup()

        mock.enqueue("""{"success": false, "code": -8, "msg": "未知错误！", "data": {}}""")

        mock.enqueue(
            """{"success": true,"code": 200,"msg": "ok","data": {"user": {"id": 88888,"userId": 77777,"nickname": "回退成功用户","level": 3}}}"""
        )

        val result = repository.getUserInfo(userId = 77777L)
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()
        assertEquals(88888L, profile.id)
        assertEquals("回退成功用户", profile.displayName)

        val req1 = mock.takeRequest()
        assertEquals("77777", req1.formEntries()["id"])

        val req2 = mock.takeRequest()
        assertEquals("77777", req2.formEntries()["userId"])
    }

    @Test
    fun getCollectSimilarity_failureBubblesUp() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(4001, "登录已过期"))

        val result = repository.getCollectSimilarity(88888L)

        assertTrue(result.isFailure)
        assertEquals(4001, (result.exceptionOrNull() as ApiException).code)
    }

    @Test
    fun getUserActionList_failureBubblesUp() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(500, "服务异常"))

        val result = repository.getUserActionList(88888L)

        assertTrue(result.isFailure)
        assertEquals(500, (result.exceptionOrNull() as ApiException).code)
    }

}
