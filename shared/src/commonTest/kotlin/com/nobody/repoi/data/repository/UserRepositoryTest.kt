package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.AmazonMoneyData
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.repository.FriendState
import com.nobody.repoi.data.remote.model.OrderCount
import com.nobody.repoi.data.remote.model.OrderStaticData
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.remote.model.UserCollectCommonItem
import com.nobody.repoi.data.remote.model.UserCollectHobbyItem
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
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
 * Mockito `mock(TokenManager)` → [FakeTokenManager]（`verify clearToken` →
 * `clearTokenCalls` 计数）；JUnit4 → kotlin.test + `runTest`）。
 */
class UserRepositoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private fun setup(token: String = "utoken-test"): Triple<MockApi, FakeTokenManager, UserRepository> {
        val mock = MockApi()
        val tokenManager = FakeTokenManager(token)
        return Triple(mock, tokenManager, UserRepository(mock.api(), tokenManager))
    }

    @Test
    fun parseOrderFinance_computesAllCurrenciesAndFluctuation() {
        val (_, _, repository) = setup()
        val preorderJson = buildJsonArray {
            add(buildJsonObject {
                put("key", "category_100")
                put("val", buildJsonArray {
                    add(buildJsonObject {
                        put("currency", "yen")
                        put("money", 10000.0)
                    })
                    add(buildJsonObject {
                        put("currency", "dollar")
                        put("money", 100.0)
                    })
                })
            })
            add(buildJsonObject {
                put("key", "category_200")
                put("val", buildJsonArray {
                    add(buildJsonObject {
                        put("currency", "euro")
                        put("money", 50.0)
                    })
                    add(buildJsonObject {
                        put("currency", "hkd")
                        put("money", 200.0)
                    })
                    add(buildJsonObject {
                        put("currency", "rmb")
                        put("money", 1000.0)
                    })
                })
            })
        }

        val orderJson = buildJsonArray {
            add(buildJsonObject {
                put("key", "category_300")
                put("val", buildJsonArray {
                    add(buildJsonObject {
                        put("currency", "cny")
                        put("money", 888.0)
                    })
                })
            })
        }

        val orderData = OrderStaticData(
            count = OrderCount(buy = 1, preorder = 2, care = 3),
            allPreorderMoney = preorderJson,
            allOrderMoney = orderJson,
            amazonMoney = AmazonMoneyData(initialPrice = 1000, price = 1250)
        )

        val finance = repository.parseOrderFinance(orderData)

        // 待补款验证：
        // 手办：JPY 10000 * 0.06 = 600.0 + USD 100 * 6.4 = 640.0 => 1240.0
        // 模型：EUR 50 * 6.9 = 345.0 + HKD 200 * 0.92 = 184.0 + RMB 1000 * 1.0 = 1000.0 => 1529.0
        // 待补款总计: 1240.0 + 1529.0 = 2769.0
        assertEquals(2, finance.supplementedGroups.size)
        assertEquals("手办", finance.supplementedGroups[0].categoryName)
        assertEquals(1240.0, finance.supplementedGroups[0].totalRmb, 0.001)
        assertEquals("动漫模型", finance.supplementedGroups[1].categoryName)
        assertEquals(1529.0, finance.supplementedGroups[1].totalRmb, 0.001)
        assertEquals(2769.0, finance.supplementedTotalRmb, 0.001)

        // 已入总额验证：CNY 888.0 * 1.0 = 888.0
        assertEquals(1, finance.historyGroups.size)
        assertEquals("Doll娃娃", finance.historyGroups[0].categoryName)
        assertEquals(888.0, finance.historyTotalRmb, 0.001)

        // 亚马逊涨跌幅：(1250 - 1000) * 100 / 1000 = 25% => "+25%"
        assertEquals("+25%", finance.amazonFluctuation)
    }

    @Test
    fun parseOrderFinance_handlesEdgeCasesAndNullSafely() {
        val (_, _, repository) = setup()
        // null orderData
        val emptyFinance = repository.parseOrderFinance(null)
        assertEquals(0.0, emptyFinance.supplementedTotalRmb, 0.001)
        assertEquals(0.0, emptyFinance.historyTotalRmb, 0.001)
        assertEquals("0%", emptyFinance.amazonFluctuation)
        assertTrue(emptyFinance.supplementedGroups.isEmpty())
        assertTrue(emptyFinance.historyGroups.isEmpty())

        // 负涨跌幅验证
        val negativeAmazon = AmazonMoneyData(initialPrice = 1000, price = 850)
        assertEquals("-15%", negativeAmazon.fluctuationText)

        // 持平 0% 验证
        val flatAmazon = AmazonMoneyData(initialPrice = 1000, price = 1000)
        assertEquals("0%", flatAmazon.fluctuationText)

        // 初始价格为 0 或空
        val zeroAmazon = AmazonMoneyData(initialPrice = 0, price = 500)
        assertEquals("0%", zeroAmazon.fluctuationText)
        val nullAmazon = AmazonMoneyData(initialPrice = null, price = 500)
        assertEquals("0%", nullAmazon.fluctuationText)
    }

    @Test
    fun loadFullProfile_1Plus5Concurrency_aggregatesAllData() = runTest {
        val (mock, _, repository) = setup()
        // 配置按路径 + 按体分发（等价 Dispatcher 1+5 并发拦截）
        mock.route("api/user/self", """{"success":true,"code":200,"msg":"ok","data":{"user":{"id":88888,"nickname":"手办达人","userName":"master","level":5}}}""")
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
    fun getUserCollectHobbyList_passesAllRequiredParamsAndParsesItems() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1001,"itemId":2001,"name":"Original Name","nameCN":"中文名称","cover":"covers/hobby1.jpg","price":"15000日元","money":15000.0,"currency":"yen","releaseDate":"2026-10-01","collectState":"preorder","rating":9.6,"ratingCount":2},{"id":1002,"name":"No CN Name","iconUrl":"icons/hobby2.jpg"}]}}"""
        )

        val result = repository.getUserCollectHobbyList(
            userId = 99999L,
            collectState = "preorder",
            category = CollectConstants.CATEGORY_ALL,
            tag = "WF限定",
            order = "release",
            sortType = UserRepository.SORT_TYPE_ASC,
            page = 2,
            pageSize = 24
        )

        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(2, items.size)

        // 验证计算属性
        val item1 = items[0]
        assertEquals(2001L, item1.actualItemId)
        assertEquals("covers/hobby1.jpg", item1.actualCover)
        assertEquals("中文名称", item1.displayName)
        assertEquals("JP¥15000", item1.formattedOrderMoneyText)

        val item2 = items[1]
        assertEquals(1002L, item2.actualItemId)
        assertEquals("icons/hobby2.jpg", item2.actualCover)
        assertEquals("No CN Name", item2.displayName)

        // 验证请求中传递的参数 (GET Query)
        val recorded = mock.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/collect/hobby-v2", recorded.encodedPath)
        assertEquals("99999", recorded.queryParameter("userId"))
        assertEquals("99999", recorded.queryParameter("userNodeId"))
        assertEquals("preorder", recorded.queryParameter("collectState"))
        assertEquals("release", recorded.queryParameter("order"))
        assertEquals("10000", recorded.queryParameter("category"))
        assertEquals("1", recorded.queryParameter("sortType"))
        assertEquals("2", recorded.queryParameter("page"))
        assertEquals("24", recorded.queryParameter("limit"))
        assertEquals("24", recorded.queryParameter("pageSize"))
        // tag 需 URL 解码后比对
        assertTrue(recorded.path!!.contains("tag="))
    }

    @Test
    fun getUserCollectHobbyList_whenCollectStateIsCare_passesCareParameter() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")

        val result = repository.getUserCollectHobbyList(
            userId = 88888L,
            collectState = "care",
            order = "release",
            page = 1
        )
        assertTrue(result.isSuccess)

        val recorded = mock.takeRequest()
        assertEquals("care", recorded.queryParameter("collectState"))
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
    fun getUserCollectHobbyList_onApiFailure_throwsApiExceptionAndClearsTokenOnExpired() = runTest {
        val (mock, tokenManager, repository) = setup()
        mock.enqueue(mock.failure(4001, "登录凭证已过期"))

        val result = repository.getUserCollectHobbyList(
            userId = 123L,
            collectState = "buy",
            order = "orderTime",
            page = 1
        )

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is ApiException)
        assertEquals(4001, (exception as ApiException).code)
        assertEquals(1, tokenManager.clearTokenCalls)
    }

    @Test
    fun getOrderStaticData_successAndDelegation() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"data":{"count":{"buy":8,"preorder":4,"care":2},"amazonMoney":{"initialPrice":500,"price":600}}}}"""
        )

        val dataResult = repository.getOrderStaticData(12345L)
        assertTrue(dataResult.isSuccess)
        val data = dataResult.getOrThrow()
        assertEquals(8, data.count?.buy)
        assertEquals("+20%", data.amazonMoney?.fluctuationText)

        // 验证 getOrderStatic 委托行为
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"data":{"count":{"buy":8,"preorder":4,"care":2}}}}"""
        )
        val countResult = repository.getOrderStatic(12345L)
        assertTrue(countResult.isSuccess)
        assertEquals(8, countResult.getOrThrow().buy)
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
    fun parseCategoryMoneyGroups_supportsStringifiedJsonAndAliases() {
        val (_, _, repository) = setup()
        // 测试 stringified JSON array 作为 val，以及币种别名与大小写容错
        val stringifiedJson = buildJsonArray {
            add(buildJsonObject {
                put("key", "category_figure")
                put("val", "[{\"currency\":\" JPY \",\"money\":\"1000\"},{\"currency\":\"港币\",\"money\":50}]")
            })
        }
        val groups = repository.parseCategoryMoneyGroups(stringifiedJson)
        assertEquals(1, groups.size)
        assertEquals("手办", groups[0].categoryName)
        assertEquals(2, groups[0].items.size)
        assertEquals("日元", groups[0].items[0].displayName)
        assertEquals("JP¥", groups[0].items[0].symbol)
        assertEquals("港元", groups[0].items[1].displayName)
        assertEquals("HK$", groups[0].items[1].symbol)
        // 1000 * 0.06 = 60.0 + 50 * 0.92 = 46.0 => 106.0
        assertEquals(106.0, groups[0].totalRmb, 0.001)
    }

    @Test
    fun getUserCollectHobbyList_withSubCategoryAndEmptyState_passesCorrectParams() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")

        val result = repository.getUserCollectHobbyList(
            userId = 88888L,
            collectState = "",
            category = 200,
            order = "release",
            page = 1,
            pageSize = 20
        )

        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/collect/hobby-v2", recorded.encodedPath)
        assertEquals("88888", recorded.queryParameter("userId"))
        assertEquals("88888", recorded.queryParameter("userNodeId"))
        assertEquals("200", recorded.queryParameter("category"))
        // 全部状态时 collectState 参数为空字符串，不应包含非空 collectState
        assertTrue(recorded.queryParameter("collectState").isNullOrEmpty())
    }

    @Test
    fun getUserCollectCommonList_passesParamsAndParsesItems() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":4001,"itemId":5001,"name":"Good Smile Company","nameCN":"良笑社","cover":"covers/gsc.jpg","actionDate":"2026-03-15","itemsCount":120,"collectCount":5000}]}}"""
        )

        val result = repository.getUserCollectCommonList(
            userId = 88888L,
            categoryId = 40000,
            type = UserCollectCategory.VENDOR.apiType,
            order = "orderTime",
            page = 1,
            pageSize = 20
        )

        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals(5001L, item.actualItemId)
        assertEquals("良笑社", item.displayName)
        assertEquals("covers/gsc.jpg", item.actualCover)
        assertEquals(120, item.itemsCount)
        assertEquals(5000, item.collectCount)

        val recorded = mock.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.encodedPath!!.startsWith("/api/collect/common-v3"))
        // R1：type 必须按分类下发 company，绝不是原版不存在的 "care"
        assertEquals("company", recorded.queryParameter("type"))
        // R4：orderTime 不在原版白名单内，必须收敛为 actionDate
        assertEquals("actionDate", recorded.queryParameter("order"))
        assertEquals("88888", recorded.queryParameter("userId"))
        assertEquals("40000", recorded.queryParameter("categoryId"))
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
    fun getUserCollectCommonList_worksPage_appendsStateIsAscAndTagValue() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")

        val result = repository.getUserCollectCommonList(
            userId = 88888L,
            categoryId = UserCollectCategory.WORKS.key,
            type = UserCollectCategory.WORKS.apiType,
            order = "actionDate",
            page = 1,
            pageSize = 20,
            state = 2,
            isAsc = false,
            tagValue = "热血"
        )
        assertTrue(result.isSuccess)

        val recorded = mock.takeRequest()
        assertEquals("works", recorded.queryParameter("type"))
        assertEquals("actionDate", recorded.queryParameter("order"))
        assertEquals("2", recorded.queryParameter("state"))
        assertEquals("false", recorded.queryParameter("isAsc"))
        // tagValue 需解码后比对
        assertTrue(recorded.path!!.contains("tagValue="))
    }

    @Test
    fun getUserCollectCommonList_nonWorksPage_neverSendsStateIsAscOrTagValue() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")

        val result = repository.getUserCollectCommonList(
            userId = 88888L,
            categoryId = UserCollectCategory.SERIES.key,
            type = UserCollectCategory.SERIES.apiType,
            order = "collect",
            page = 1,
            state = 1,
            isAsc = true,
            tagValue = "校园"
        )
        assertTrue(result.isSuccess)

        val recorded = mock.takeRequest()
        assertEquals("series", recorded.queryParameter("type"))
        assertEquals("collect", recorded.queryParameter("order"))
        assertFalse(recorded.path!!.contains("state="))
        assertFalse(recorded.path!!.contains("isAsc="))
        assertFalse(recorded.path!!.contains("tagValue="))
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
    fun getUserCollectAlbumList_passesParamsAndParsesItems() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id": 9999,"addTime": "2024-05-01 12:00:00","item": {"id": 7001,"name": "摄影精选相册","cover": "covers/album.jpg","user": {"nickname": "摄影大师","header": "headers/avatar.jpg"},"photosCount": 15,"praiseCount": 88}}]}}"""
        )

        val result = repository.getUserCollectAlbumList(
            userId = 88888L,
            page = 1,
            pageSize = 20
        )

        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals(9999L, item.id)
        assertEquals(7001L, item.actualAlbumId)
        assertEquals("摄影精选相册", item.actualTitle)
        assertEquals("covers/album.jpg", item.actualCover)
        assertEquals("摄影大师", item.actualUserNickName)
        assertEquals(15, item.actualPhotosCount)
        assertEquals(88L, item.actualPraiseCount)

        val recorded = mock.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.encodedPath!!.startsWith("/api/collect/album-v2"))
        assertEquals("88888", recorded.queryParameter("userId"))
        assertEquals("fav", recorded.queryParameter("type"))
    }

    @Test
    fun getUserCollectPicList_passesParamsAndParsesItems() = runTest {
        val (mock, _, repository) = setup()
        // JSON 结构对齐真机实测响应（2026-10-02）：图片地址只有 `path`，记录为扁平结构
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":9001,"categoryId":80001,"itemId":4235086,"itemType":"picture","collect":2,"path":"2026/09/f8e12c472e2f4fc2b63f54f96f3a6f9b.jpg"}]}}"""
        )

        val result = repository.getUserCollectPicList(
            userId = 88888L,
            page = 1,
            pageSize = 20
        )

        assertTrue(result.isSuccess)
        val items = result.getOrThrow()
        assertEquals(1, items.size)
        val item = items[0]
        assertEquals(9001L, item.id)
        assertEquals(4235086L, item.itemId)
        assertEquals(2, item.collect)
        assertEquals("2026/09/f8e12c472e2f4fc2b63f54f96f3a6f9b.jpg", item.actualUrl)

        val recorded = mock.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.encodedPath!!.startsWith("/api/collect/pic-v2"))
        assertEquals("88888", recorded.queryParameter("userId"))
        assertEquals("88888", recorded.queryParameter("userNodeId"))
        assertEquals("1", recorded.queryParameter("page"))
        assertEquals("20", recorded.queryParameter("limit"))
        assertEquals("20", recorded.queryParameter("pageSize"))
        assertEquals("collect", recorded.queryParameter("collectState"))
        assertEquals("addTime", recorded.queryParameter("order"))
    }

    @Test
    fun getUserCollectAlbumList_sendsOrderAndDefaultsToAddTime() = runTest {
        val (mock, _, repository) = setup()
        // 默认排序 = 发布时间
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        repository.getUserCollectAlbumList(userId = 88888L, page = 1).getOrThrow()
        assertEquals("addTime", mock.takeRequest().queryParameter("order"))

        // 显式指定收藏时间 / 点赞量
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        repository.getUserCollectAlbumList(userId = 88888L, order = "actionDate", page = 2).getOrThrow()
        assertEquals("actionDate", mock.takeRequest().queryParameter("order"))

        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        repository.getUserCollectAlbumList(userId = 88888L, order = "praiseCount", page = 3).getOrThrow()
        assertEquals("praiseCount", mock.takeRequest().queryParameter("order"))

        // 越界值收敛回发布时间
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        repository.getUserCollectAlbumList(userId = 88888L, order = "orderTime", page = 4).getOrThrow()
        assertEquals("addTime", mock.takeRequest().queryParameter("order"))
    }

    @Test
    fun getUserCollectPicList_sendsOrderAndDefaultsToAddTime() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        repository.getUserCollectPicList(userId = 88888L, page = 1).getOrThrow()
        val defaults = mock.takeRequest()
        assertEquals("addTime", defaults.queryParameter("order"))
        assertEquals("collect", defaults.queryParameter("collectState"))

        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        repository.getUserCollectPicList(userId = 88888L, order = "actionDate", page = 2).getOrThrow()
        assertEquals("actionDate", mock.takeRequest().queryParameter("order"))

        // 越界值收敛
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        repository.getUserCollectPicList(userId = 88888L, order = "garbage", page = 3).getOrThrow()
        assertEquals("addTime", mock.takeRequest().queryParameter("order"))
    }

    @Test
    fun getUserCollectHobbyList_orderTimeOnlyAllowedForBuyState() = runTest {
        val (mock, _, repository) = setup()
        // 已入档：入手时间合法
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        repository.getUserCollectHobbyList(
            userId = 88888L,
            collectState = "buy",
            order = "orderTime",
            page = 1
        ).getOrThrow()
        assertEquals("orderTime", mock.takeRequest().queryParameter("order"))

        // 非已入档：回落收藏时间
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        repository.getUserCollectHobbyList(
            userId = 88888L,
            collectState = "preorder",
            order = "orderTime",
            page = 1
        ).getOrThrow()
        assertEquals("actionDate", mock.takeRequest().queryParameter("order"))

        // 越界值回落收藏时间
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        repository.getUserCollectHobbyList(
            userId = 88888L,
            collectState = "preorder",
            order = "collect",
            page = 1
        ).getOrThrow()
        assertEquals("actionDate", mock.takeRequest().queryParameter("order"))
    }

    @Test
    fun getUserCollectHobbyList_categoryDefaultsToAllNotNull() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")

        repository.getUserCollectHobbyList(
            userId = 88888L,
            collectState = "preorder",
            page = 1
        ).getOrThrow()

        val recorded = mock.takeRequest()
        // 原版用 10000 表示「全部」，不使用 null
        assertEquals("10000", recorded.queryParameter("category"))
        // 缺省为降序
        assertEquals("2", recorded.queryParameter("sortType"))
    }

    @Test
    fun getUserFellowList_success_parsesUserAndBothStatus() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            """{"success": true,"code": 200,"msg": "ok","data": {"list": [{"user": {"id": 10001,"nickname": "手办同好A","userName": "fellow_a","sign": "喜欢初音和阿尔托莉雅","intro": "个人简介A","header": "header/user_10001.jpg","level": 3},"both": 2},{"user": {"id": 10002,"nickname": "手办同好B","userName": "fellow_b","sign": "","intro": "只看模型","header": "header/user_10002.jpg","level": 1},"both": 0}],"count": 2}}"""
        )

        val result = repository.getUserFellowList(userId = 88888L, type = 0, page = 1, pageSize = 20)
        assertTrue(result.isSuccess)
        val list = result.getOrThrow()
        assertEquals(2, list.size)

        val item1 = list[0]
        assertEquals(10001L, item1.userId)
        assertEquals("手办同好A", item1.displayName)
        assertEquals("喜欢初音和阿尔托莉雅", item1.signature)
        assertEquals("header/user_10001.jpg", item1.avatarUrl)
        assertTrue(item1.isFollowed)
        assertTrue(item1.isMutual)

        val item2 = list[1]
        assertEquals(10002L, item2.userId)
        assertEquals("手办同好B", item2.displayName)
        assertEquals("只看模型", item2.signature)
        assertTrue(item2.isFollowed)
        assertFalse(item2.isMutual)

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        val form = recorded.formEntries()
        assertEquals("88888", form["id"])
        assertEquals("0", form["type"])
        assertEquals("1", form["page"])
        assertEquals("20", form["pageSize"])
    }

    @Test
    fun getUserFellowList_tokenExpired_clearsTokenAndThrowsApiException() = runTest {
        val (mock, tokenManager, repository) = setup()
        mock.enqueue(mock.failure(4001, "登录已过期"))

        val result = repository.getUserFellowList(userId = 88888L, type = 1, page = 1)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is ApiException)
        assertEquals(4001, (exception as ApiException).code)

        assertEquals(1, tokenManager.clearTokenCalls)
        assertNull(repository.currentUserProfile.value)
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
    fun updateFriend_followAndUnfollow_postsCorrectState() = runTest {
        val (mock, _, repository) = setup()
        // 关注 (isFollow = true -> state = 0)
        mock.enqueue("""{"success":true,"code":200,"msg":"ok"}""")
        val followResult = repository.updateFriend(userId = 99999L, isFollow = true)
        assertTrue(followResult.isSuccess)

        val followReq = mock.takeRequest()
        assertEquals("POST", followReq.method)
        assertEquals("/api/user/friend/upd", followReq.path)
        val followForm = followReq.formEntries()
        assertEquals("99999", followForm["userId"])
        assertEquals("0", followForm["state"])

        // 取关 (isFollow = false -> state = 5)
        mock.enqueue("""{"success":true,"code":200,"msg":"ok"}""")
        val unfollowResult = repository.updateFriend(userId = 99999L, isFollow = false)
        assertTrue(unfollowResult.isSuccess)

        val unfollowReq = mock.takeRequest()
        assertEquals("POST", unfollowReq.method)
        assertEquals("/api/user/friend/upd", unfollowReq.path)
        val unfollowForm = unfollowReq.formEntries()
        assertEquals("99999", unfollowForm["userId"])
        assertEquals("5", unfollowForm["state"])
    }

    @Test
    fun updateFriend_success_emitsFriendChangeEvent() = runTest {
        val (mock, _, repository) = setup()
        val events = mutableListOf<FriendChangeEvent>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.friendChangeEvent.collect { events.add(it) }
        }

        // 1. 关注
        mock.enqueue("""{"success":true,"code":200,"msg":"ok"}""")
        val followResult = repository.updateFriend(userId = 88888L, isFollow = true)
        assertTrue(followResult.isSuccess)
        mock.takeRequest()

        // 2. 取关
        mock.enqueue("""{"success":true,"code":200,"msg":"ok"}""")
        val unfollowResult = repository.updateFriend(userId = 88888L, isFollow = false)
        assertTrue(unfollowResult.isSuccess)
        mock.takeRequest()

        yield()
        collectJob.cancel()

        assertEquals(2, events.size)
        assertEquals(88888L, events[0].userId)
        assertTrue(events[0].isFollow)
        assertEquals(FriendState.FOLLOWING, events[0].state)

        assertEquals(88888L, events[1].userId)
        assertFalse(events[1].isFollow)
        assertEquals(FriendState.NOT_FOLLOWING, events[1].state)
    }

    @Test
    fun updateUserBase_success_postsKeyAndValueAndRefreshesProfile() = runTest {
        val (mock, _, repository) = setup()
        // 1. upd 请求响应成功
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        // 2. getUserProfile 刷新调用响应
        mock.enqueue(
            """{"success": true,"code": 200,"msg": "ok","data": {"user": {"id": 88888,"nickname": "新昵称","sign": "新签名","level": 5}}}"""
        )

        val result = repository.updateUserBase(key = "sign", value = "新签名")
        assertTrue(result.isSuccess)

        // 验证第 1 个请求：updateUserBase
        val req1 = mock.takeRequest()
        assertEquals("POST", req1.method)
        assertEquals("/api/user/base/upd", req1.path)
        val form1 = req1.formEntries()
        assertEquals("sign", form1["key"])
        assertEquals("新签名", form1["val"])

        // 验证第 2 个请求：getUserProfile (getUserSelf)
        val req2 = mock.takeRequest()
        assertEquals("POST", req2.method)
        assertEquals("/api/user/self", req2.path)

        // 验证缓存流更新
        val currentProfile = repository.currentUserProfile.value
        assertNotNull(currentProfile)
        assertEquals("新签名", currentProfile?.sign)
        assertEquals("新昵称", currentProfile?.nickname)
    }

    @Test
    fun getUserProfile_parsesAllExtendedR2Fields() = runTest {
        val (mock, _, repository) = setup()
        mock.enqueue(
            """{"success": true,"code": 200,"msg": "ok","data": {"user": {"id": 88888,"nickname": "资深胶佬","userName": "collector888","sign": "入坑十年","intro": "专注比例手办与雕像收藏","header": "header/collector.jpg","bnr": "banner/collector.jpg","level": 6,"sex": 1,"birth": "1995-08-18","city": "上海","regTime": "2016-04-20 12:00:00","addTime": "2016-04-20","verify": "知名手办摄影师"}}}"""
        )

        val result = repository.getUserProfile()
        assertTrue(result.isSuccess)
        val profile = result.getOrThrow()

        assertEquals(88888L, profile.id)
        assertEquals("资深胶佬", profile.nickname)
        assertEquals("collector888", profile.userName)
        assertEquals("资深胶佬", profile.displayName)
        assertEquals("入坑十年", profile.sign)
        assertEquals("专注比例手办与雕像收藏", profile.intro)
        assertEquals("header/collector.jpg", profile.header)
        assertEquals("banner/collector.jpg", profile.bnr)
        assertEquals(6, profile.level)
        assertEquals(1, profile.sex)
        assertEquals("男", profile.genderText)
        assertEquals("1995-08-18", profile.birth)
        assertEquals("上海", profile.city)
        assertEquals("2016-04-20 12:00:00", profile.regTime)
        assertEquals("2016-04-20", profile.addTime)
        assertEquals("2016-04-20 12:00:00", profile.displayRegisterTime)
        assertEquals("知名手办摄影师", profile.verify)
        assertTrue(profile.isVerified)

        // 验证性别的另外取值与未认证兜底
        val femaleProfile = profile.copy(sex = 0, verify = null)
        assertEquals("女", femaleProfile.genderText)
        assertFalse(femaleProfile.isVerified)

        val secretProfile = profile.copy(sex = 2, verify = "")
        assertEquals("保密", secretProfile.genderText)
        assertFalse(secretProfile.isVerified)

        val unsetProfile = profile.copy(sex = null)
        assertEquals("未设置", unsetProfile.genderText)

        // 验证注册时间回退到 addTime
        val addTimeOnlyProfile = profile.copy(regTime = null)
        assertEquals("2016-04-20", addTimeOnlyProfile.displayRegisterTime)

        // 验证内存缓存同步
        assertEquals(profile, repository.currentUserProfile.value)
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
