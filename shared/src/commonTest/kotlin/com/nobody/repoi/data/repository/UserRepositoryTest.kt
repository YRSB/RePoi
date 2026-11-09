package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.AmazonMoneyData
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.repository.FollowRepository
import com.nobody.repoi.data.repository.FriendState
import com.nobody.repoi.data.repository.UserFinanceRepository
import com.nobody.repoi.data.remote.model.OrderCount
import com.nobody.repoi.data.remote.model.OrderStaticData
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.remote.model.UserCollectCommonItem
import com.nobody.repoi.data.remote.model.UserCollectHobbyItem
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.data.session.SessionState
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 用户仓储测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `UserRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi（含按体分发 `routeWhen`，等价 Dispatcher）；
 * 鉴权依赖 [FakeUserSession]（4001 断言改为会话未认证与 `tokenExpiredCalls` 计数）；
 * JUnit4 → kotlin.test + `runTest`）。
 */
class UserRepositoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private fun setup(token: String = "utoken-test"): Triple<MockApi, FakeUserSession, UserRepository> {
        val mock = MockApi()
        val session = FakeUserSession(token)
        val followRepo = FollowRepository(mock.api(), json, session)
        val financeRepo = UserFinanceRepository(mock.api(), json)
        return Triple(mock, session, UserRepository(mock.api(), session, followRepo, financeRepo))
    }

    @Test
    fun loadFullProfile_1Plus5Concurrency_aggregatesAllData() = runTest {
        val (mock, session, repository) = setup()
        // 会话中已有档案：loadFullProfile 直接复用，不再请求 /api/user/self
        session.setState(
            SessionState.Authenticated(
                userId = 88888L,
                token = "utoken-test",
                profile = UserProfile(id = 88888L, nickname = "手办达人", userName = "master", level = 5),
            )
        )
        // 配置按路径 + 按体分发（等价 Dispatcher 1+5 并发拦截）
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

        val result = repository.loadFullProfile()
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()

        // 验证用户与社交信息
        assertEquals(88888L, data.profile.id)
        assertEquals("手办达人", data.profile.displayName)
        assertEquals(120, data.fellow.fanCount)
        assertEquals(45, data.fellow.friendCount)

        // 验证订单数量
        assertEquals(10, data.order.buy)
        assertEquals(5, data.order.preorder)
        assertEquals(15, data.order.care)

        // 验证 1+5 并发拉取的三个展柜条目
        assertEquals(1, data.preorderItems.size)
        assertEquals(101L, data.preorderItems[0].itemId)
        assertEquals(1, data.buyItems.size)
        assertEquals(201L, data.buyItems[0].itemId)
        assertEquals(1, data.careItems.size)
        assertEquals(301L, data.careItems[0].itemId)

        // 验证财务与行情汇总
        assertEquals(600.0, data.finance.supplementedTotalRmb, 0.001) // 10000 JPY * 0.06
        assertEquals(2000.0, data.finance.historyTotalRmb, 0.001)     // 2000 RMB * 1.0
        assertEquals("+10%", data.finance.amazonFluctuation)
    }

    @Test
    fun loadFullProfile_withForceRefresh_fetchesLatestSelfProfile() = runTest {
        val (mock, session, repository) = setup()
        session.setState(
            SessionState.Authenticated(
                userId = 88888L,
                token = "utoken-test",
                profile = UserProfile(id = 88888L, nickname = "旧昵称", userName = "master", level = 5),
            )
        )
        val freshProfile = UserProfile(id = 88888L, nickname = "新昵称", userName = "master", level = 6)
        session.refreshProfileResult = Result.success(freshProfile)
        mock.route("api/user/fellow/count", """{"success":true,"code":200,"msg":"ok","data":{"data":{"fanCount":0,"friendCount":0,"praiseCount":0}}}""")
        mock.route("api/user/orderStatic", """{"success":true,"code":200,"msg":"ok","data":{"data":{"count":{"buy":0,"preorder":0,"care":0}}}}""")

        val result = repository.loadFullProfile(forceRefresh = true)
        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals(1, session.refreshProfileCalls)
        assertEquals("新昵称", data.profile.displayName)
        assertEquals(6, data.profile.level)
    }

    @Test
    fun currencyType_parsesAllCurrenciesAndCalculatesFormattedText() {
        val usdItem = UserCollectHobbyItem(money = 120.0, currency = "dollar")
        assertEquals("$120", usdItem.formattedOrderMoneyText)

        val eurItem = UserCollectHobbyItem(money = 85.0, currency = "euro")
        assertEquals("€85", eurItem.formattedOrderMoneyText)

        val hkdItem = UserCollectHobbyItem(money = 250.0, currency = "hkd")
        assertEquals("HK$250", hkdItem.formattedOrderMoneyText)

        val cnyItem = UserCollectHobbyItem(money = 399.0, currency = "rmb")
        assertEquals("¥399", cnyItem.formattedOrderMoneyText)

        val jpyItem = UserCollectHobbyItem(money = 18000.0, currency = "yen")
        assertEquals("JP¥18000", jpyItem.formattedOrderMoneyText)
    }

    @Test
    fun userCollectHobbyItem_parsesNestedCollectionAndComputesProperties() {
        val jsonStr = """{
            "id": 501,
            "itemId": 0,
            "itemNodeId": 601,
            "name": "Fallback Name",
            "nameCN": "优先中文名",
            "iconUrl": "icons/pic.jpg",
            "rating": 19.2,
            "ratingCount": 2,
            "collection": {
                "state": "preorder",
                "orderTime": "2026-05-20",
                "orderMoney": 16800.0,
                "orderMoneyCurrency": "yen"
            }
        }"""
        val item = json.decodeFromString<UserCollectHobbyItem>(jsonStr)
        assertEquals(501L, item.actualItemId)
        assertEquals("优先中文名", item.displayName)
        assertEquals("icons/pic.jpg", item.actualCover)
        assertEquals("preorder", item.actualCollectState)
        assertEquals("2026-05-20", item.actualOrderTime)
        assertEquals(16800.0, item.actualOrderMoney ?: 0.0, 0.001)
        assertEquals("yen", item.actualCurrency)
        assertEquals(9.6, item.averageRating, 0.001)
    }

    @Test
    fun userCollectCategory_apiType_matchesOriginalMapping() {
        // 原版 utils/s.java:209-244 + oe/a.java:32-44，注意 charactar 是服务端历史拼写错误
        assertEquals("hobby", UserCollectCategory.HOBBY.apiType)
        assertEquals("company", UserCollectCategory.VENDOR.apiType)
        assertEquals("series", UserCollectCategory.SERIES.apiType)
        assertEquals("works", UserCollectCategory.WORKS.apiType)
        assertEquals("charactar", UserCollectCategory.CHARACTER.apiType)
        assertEquals("person", UserCollectCategory.PERSON.apiType)
        assertEquals(UserCollectCategory.CHARACTER, UserCollectCategory.fromApiType("charactar"))
        assertEquals(UserCollectCategory.VENDOR, UserCollectCategory.fromKey(40000))
        // 未收录的分类 id 不应误命中
        assertNull(UserCollectCategory.fromKey(12345))
    }

    @Test
    fun collectConstants_normalizeOrderByWhitelist() {
        // 通用 4 页白名单仅 actionDate / collect
        assertEquals("actionDate", CollectConstants.normalizeCommonOrder("actionDate"))
        assertEquals("collect", CollectConstants.normalizeCommonOrder("collect"))
        assertEquals("actionDate", CollectConstants.normalizeCommonOrder("orderTime"))
        assertEquals("actionDate", CollectConstants.normalizeCommonOrder(""))

        // 相册/图片白名单 addTime / actionDate / praiseCount
        assertEquals("addTime", CollectConstants.normalizeMediaOrder("addTime"))
        assertEquals("praiseCount", CollectConstants.normalizeMediaOrder("praiseCount"))
        assertEquals("addTime", CollectConstants.normalizeMediaOrder("collect"))

        // 周边白名单 actionDate/release/rating/myRating/updateDate
        assertEquals("release", CollectConstants.normalizeHobbyOrder("release", "preorder"))
        assertEquals("updateDate", CollectConstants.normalizeHobbyOrder("updateDate", "preorder"))
        assertEquals("actionDate", CollectConstants.normalizeHobbyOrder("garbage", "preorder"))
        // orderTime（入手时间）仅「已入」档可用，其余状态回落收藏时间
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
        // 混合实体场景下真实条目在 item 子对象里
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
        // 老结构只有 itemsCount，兼容回落到同一语义
        assertEquals(120, item.actualHobbyCount)
        assertEquals(0, item.actualIssueHobbyCount)
        assertNull(item.collectStateText)
    }

    @Test
    fun getUserPraiseList_success_parsesItemAndUser() = runTest {
        val (mock, _, repository) = setup()
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
    fun updateUserBase_success_postsKeyAndValueAndRefreshesProfile() = runTest {
        val (mock, session, repository) = setup()
        session.refreshProfileResult = Result.success(
            UserProfile(id = 88888L, nickname = "新昵称", sign = "新签名", level = 5)
        )
        // upd 请求响应成功；档案刷新由会话即刻返回，不再发 /api/user/self
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val result = repository.updateUserBase(key = "sign", value = "新签名")
        assertTrue(result.isSuccess)

        val req = mock.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/user/base/upd", req.path)
        val form = req.formEntries()
        assertEquals("sign", form["key"])
        assertEquals("新签名", form["val"])

        // 验证会话档案镜像同步
        val currentProfile = (session.state.value as? SessionState.Authenticated)?.profile
        assertNotNull(currentProfile)
        assertEquals("新签名", currentProfile.sign)
        assertEquals("新昵称", currentProfile.nickname)
    }

    @Test
    fun getUserInfo_successWithId_parsesUserInfo() = runTest {
        val (mock, _, repository) = setup()
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
        val (mock, _, repository) = setup()
        // 第一次以 id 查询失败（未命中）
        mock.enqueue("""{"success": false, "code": -8, "msg": "未知错误！", "data": {}}""")
        // 第二次回退以 userId 查询成功
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

}
