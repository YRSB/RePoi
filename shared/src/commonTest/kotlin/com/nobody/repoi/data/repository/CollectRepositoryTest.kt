package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.data.session.SessionState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 收藏仓储单测：批量取消收藏、作品收藏整链（标签池/读取/保存/取消）、图片关联条目、
 * 以及收藏中心 4 类列表查询（周边 / 通用实体 / 相册 / 图片，自 `UserRepositoryTest` 迁入）。
 *
 * 对齐 PoiShelf `CollectRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；JUnit4 → kotlin.test；
 * `URLDecoder` form 解析 → [RecordedHttpRequest.formEntries]）。
 */
class CollectRepositoryTest {

    private fun setup(): Pair<MockApi, CollectRepository> {
        val mock = MockApi()
        return mock to CollectRepository(mock.api(), mock.json, FakeUserSession("utoken-test"))
    }

    /** 需要断言 token 自愈的用例用这个夹具，额外暴露会话状态与自愈调用计数 */
    private fun setupWithSession(): Triple<MockApi, FakeUserSession, CollectRepository> {
        val mock = MockApi()
        val session = FakeUserSession("utoken-test")
        return Triple(mock, session, CollectRepository(mock.api(), mock.json, session))
    }

    @Test
    fun cancelCollectBatch_postsJsonArrayOfOuterNodeIds() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"取消成功","data":{}}""")

        val result = repository.cancelCollectBatch(listOf(1L, 2L, 3L))

        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        // 服务端要求 "[1,2,3]" 形式的 JSON 数组字符串
        assertEquals("[1,2,3]", recorded.formEntries()["idArr"])
    }

    @Test
    fun cancelCollectBatch_emptyIds_shortCircuitsWithoutRequest() = runTest {
        val (mock, repository) = setup()
        val result = repository.cancelCollectBatch(emptyList())
        assertTrue(result.isSuccess)
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun cancelCollectBatch_onApiFailure_throwsApiException() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(500, "请稍后再试"))

        val result = repository.cancelCollectBatch(listOf(9L))

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is ApiException)
        assertEquals(500, exception.code)
    }

    @Test
    fun getWorksCollectTags_parsesBothStringAndObjectElements() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"textTagList":["热血","校园",{"tagValue":"悬疑"},"  "]}}"""
        )

        val tags = repository.getWorksCollectTags(page = 1, pageSize = 20).getOrThrow()

        // 空串与纯空白项被过滤
        assertEquals(listOf("热血", "校园", "悬疑"), tags)

        val recorded = mock.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("1", recorded.queryParameter("page"))
        assertEquals("20", recorded.queryParameter("pageSize"))
    }

    @Test
    fun getWorksCollect_unwrapsGleanEnvelope() = runTest {
        val (mock, repository) = setup()
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
        assertEquals(8, data.current)
        assertEquals("追到第八集", data.actualComment)
        assertEquals("热血,校园", data.tagStr)

        val recorded = mock.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/works/glean/get/20001", recorded.path)
    }

    @Test
    fun getWorksCollect_supportsFlatResponseShape() = runTest {
        val (mock, repository) = setup()
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
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")

        val data = repository.getWorksCollect(40001L).getOrThrow()

        assertNull(data)
    }

    @Test
    fun saveWorksCollect_postsWorksItemTypeAndAllFields() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"保存成功","data":{}}""")

        val result = repository.saveWorksCollect(
            worksId = 20001L,
            state = 2,
            tagStr = "热血,校园",
            comment = "追到第八集"
        )

        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        val form = recorded.formEntries()
        assertEquals("Works", form["itemType"])
        assertEquals("20001", form["itemId"])
        assertEquals("热血,校园", form["tagStr"])
        assertEquals("追到第八集", form["comment"])
        assertEquals("2", form["state"])
    }

    @Test
    fun deleteWorksCollect_postsWorksItemTypeAndId() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"取消成功","data":{}}""")

        val result = repository.deleteWorksCollect(20001L)

        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        val form = recorded.formEntries()
        assertEquals("Works", form["itemType"])
        assertEquals("20001", form["itemId"])
    }

    @Test
    fun getPictureRelate_unwrapsRelateItemAndSkipsEmptyEntries() = runTest {
        val (mock, repository) = setup()
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
        assertEquals("POST", recorded.method)
        assertEquals("90001", recorded.formEntries()["picId"])
    }

    @Test
    fun getPictureRelate_onEmptyList_returnsEmptyList() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")

        val items = repository.getPictureRelate(90001L).getOrThrow()

        assertTrue(items.isEmpty())
    }

    @Test
    fun submitReport_postsOriginalFieldNamesObjIdUserReasonAndType() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"举报已提交","data":{}}""")

        val result = repository.submitReport(nodeId = 90001L, content = "图片内容违规")

        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        // ⚠️ 原版字段名是 objId / userReason / type，不是 nodeId / content
        val form = recorded.formEntries()
        assertEquals("90001", form["objId"])
        assertEquals("图片内容违规", form["userReason"])
        assertEquals("picture_info", form["type"])
        assertFalse(form.containsKey("nodeId"))
        assertFalse(form.containsKey("content"))
    }

    @Test
    fun submitReport_supportsOtherReportTypes() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"举报已提交","data":{}}""")

        repository.submitReport(
            nodeId = 123L,
            content = "二手描述与实物不符",
            type = CollectConstants.REPORT_TYPE_RESALE
        ).getOrThrow()

        val form = mock.takeRequest().formEntries()
        assertEquals("resale_item", form["type"])
    }

    @Test
    fun submitReport_blankReason_shortCircuitsWithoutRequest() = runTest {
        val (mock, repository) = setup()
        val result = repository.submitReport(nodeId = 90001L, content = "   ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ApiException)
        assertEquals(0, mock.requestCount)
    }

    // ==================== 收藏中心 4 类列表查询（自 UserRepositoryTest 迁入） ====================

    @Test
    fun getUserCollectHobbyList_passesAllRequiredParamsAndParsesItems() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1001,"itemId":2001,"name":"Original Name","nameCN":"中文名称","cover":"covers/hobby1.jpg","price":"15000日元","money":15000.0,"currency":"yen","releaseDate":"2026-10-01","collectState":"preorder","rating":9.6,"ratingCount":2},{"id":1002,"name":"No CN Name","iconUrl":"icons/hobby2.jpg"}]}}"""
        )

        val result = repository.getUserCollectHobbyList(
            userId = 99999L,
            collectState = "preorder",
            category = CollectConstants.CATEGORY_ALL,
            tag = "WF限定",
            order = "release",
            sortType = CollectRepository.SORT_TYPE_ASC,
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
        val (mock, repository) = setup()
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
    fun getUserCollectHobbyList_onApiFailure_throwsApiExceptionAndSelfHealsSession() = runTest {
        val (mock, session, repository) = setupWithSession()
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
        assertEquals(4001, exception.code)
        assertEquals(1, session.tokenExpiredCalls)
        assertEquals(SessionState.Unauthenticated, session.state.value)
    }

    @Test
    fun getUserCollectHobbyList_withSubCategoryAndEmptyState_passesCorrectParams() = runTest {
        val (mock, repository) = setup()
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
        val (mock, repository) = setup()
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
    fun getUserCollectCommonList_worksPage_appendsStateIsAscAndTagValue() = runTest {
        val (mock, repository) = setup()
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
        val (mock, repository) = setup()
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
    fun getUserCollectAlbumList_passesParamsAndParsesItems() = runTest {
        val (mock, repository) = setup()
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

    /**
     * 「我创建的」相册走 `api/album/user-v2`（type=upload），与「我收藏的」
     * （`api/collect/album-v2`，type=fav）是两套语料（issue #97 Story 5）。
     */
    @Test
    fun getUserCreationAlbumList_sendsUploadTypeAndDerivesHasMore() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":7001,"addTime":"2024-05-01 12:00:00","name":"我的相册","cover":"covers/mine.jpg"}]}}"""
        )

        val result = repository.getUserCreationAlbumList(userId = 88888L, page = 2, pageSize = 20)

        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        assertEquals(1, page.items.size)
        assertEquals(7001L, page.items[0].id)
        // 未满一页 → 没有下一页
        assertFalse(page.hasMore)

        val recorded = mock.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.encodedPath!!.startsWith("/api/album/user-v2"))
        assertEquals("88888", recorded.queryParameter("userNodeId"))
        assertEquals("upload", recorded.queryParameter("type"))
        assertEquals("2", recorded.queryParameter("page"))
        assertEquals("20", recorded.queryParameter("pageSize"))
        assertEquals("addTime", recorded.queryParameter("order"))
    }

    @Test
    fun getUserCreationAlbumList_onApiFailurePropagatesMessage() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(500, "相册服务异常"))

        val result = repository.getUserCreationAlbumList(userId = 88888L)

        assertTrue(result.isFailure)
        assertEquals("相册服务异常", (result.exceptionOrNull() as ApiException).message)
    }

    /**
     * 空白 URL 条目被过滤，但 `hasMore` 仍以**未过滤**条数判定，
     * 避免服务端补全路径时提前终止翻页。
     */
    @Test
    fun getUserCreationPicList_filtersBlankUrlsAndKeepsRawHasMore() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":9001,"path":"2026/09/a.jpg"},{"id":9002,"path":""}]}}"""
        )

        val result = repository.getUserCreationPicList(userId = 88888L, page = 1, pageSize = 2)

        assertTrue(result.isSuccess)
        val page = result.getOrThrow()
        assertEquals(listOf(9001L), page.items.map { it.id })
        assertTrue(page.hasMore)

        val recorded = mock.takeRequest()
        assertTrue(recorded.encodedPath!!.startsWith("/api/pic/user"))
        assertEquals("upload", recorded.queryParameter("collectState"))
        assertEquals("addTime", recorded.queryParameter("order"))
    }

    @Test
    fun getUserCollectPicList_passesParamsAndParsesItems() = runTest {
        val (mock, repository) = setup()
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
        val (mock, repository) = setup()
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
        val (mock, repository) = setup()
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
        val (mock, repository) = setup()
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
        val (mock, repository) = setup()
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
}
