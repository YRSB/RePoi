package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.CollectData
import com.nobody.repoi.data.remote.model.UserCollectCategory
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 收藏写侧链路单测：批量取消收藏、作品收藏整链（标签池/读取/保存/取消）、图片关联条目（KMP commonTest 移植）。
 * T1 新增：手办/通用条目/作品/批量取消全部写操作的 collectEvents 成功发射与失败零发射断言。
 *
 * 对齐 PoiShelf `CollectRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；JUnit4 → kotlin.test；
 * `URLDecoder` form 解析 → [RecordedHttpRequest.formEntries]）。
 */
class CollectRepositoryTest {

    private fun setup(): Pair<MockApi, CollectRepository> {
        val mock = MockApi()
        return mock to CollectRepository(mock.api(), mock.json)
    }

    @Test
    fun cancelCollectBatch_postsJsonArrayOfOuterNodeIds() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"取消成功","data":{}}""")

        val result = repository.cancelCollectBatch(listOf(1L, 2L, 3L), UserCollectCategory.ALBUM)

        assertTrue(result.isSuccess)
        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        // 服务端要求 "[1,2,3]" 形式的 JSON 数组字符串
        assertEquals("[1,2,3]", recorded.formEntries()["idArr"])
    }

    @Test
    fun cancelCollectBatch_emptyIds_shortCircuitsWithoutRequest() = runTest {
        val (mock, repository) = setup()
        val result = repository.cancelCollectBatch(emptyList(), UserCollectCategory.ALBUM)
        assertTrue(result.isSuccess)
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun cancelCollectBatch_onApiFailure_throwsApiException() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(500, "请稍后再试"))

        val result = repository.cancelCollectBatch(listOf(9L), UserCollectCategory.ALBUM)

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

    // ── T1：collectEvents 成功发射与失败零发射 ──

    /** 订阅收集器：后台收集 collectEvents，调用方按序取事件。 */
    private fun collectEventsOf(repository: CollectRepository): MutableList<CollectEvent> {
        val events = mutableListOf<CollectEvent>()
        // UnconfinedTestDispatcher 让发射与收集同调度同步执行
        backgroundScope.launch(UnconfinedTestDispatcher()) { repository.collectEvents.collect { events.add(it) } }
        return events
    }

    @Test
    fun saveCollect_emitsHobbyCollectChanged() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val events = collectEventsOf(repository)
        val data = CollectData(id = 100L, state = CollectConstants.STATE_BUY)

        assertTrue(repository.saveCollect(100L, data).isSuccess)

        assertEquals(listOf(CollectEvent.HobbyCollectChanged(100L, data)), events)
    }

    @Test
    fun saveCollect_onApiFailure_emitsNothing() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(500, "请稍后再试"))
        val events = collectEventsOf(repository)

        assertTrue(repository.saveCollect(100L, CollectData(id = 100L)).isFailure)

        assertTrue(events.isEmpty())
    }

    @Test
    fun deleteCollect_emitsHobbyCollectChangedWithNullData() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val events = collectEventsOf(repository)

        assertTrue(repository.deleteCollect(100L, CollectData(id = 100L, state = CollectConstants.STATE_BUY)).isSuccess)

        assertEquals(listOf(CollectEvent.HobbyCollectChanged(100L, null)), events)
    }

    @Test
    fun deleteCollect_onApiFailure_emitsNothing() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(500, "请稍后再试"))
        val events = collectEventsOf(repository)

        assertTrue(repository.deleteCollect(100L, CollectData(id = 100L)).isFailure)

        assertTrue(events.isEmpty())
    }

    @Test
    fun collectItemAdd_emitsItemCollectChangedCollected() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val events = collectEventsOf(repository)

        assertTrue(repository.collectItemAdd(70001L, UserCollectCategory.ALBUM).isSuccess)

        assertEquals(listOf(CollectEvent.ItemCollectChanged(70001L, UserCollectCategory.ALBUM, true)), events)
    }

    @Test
    fun collectItemAdd_onApiFailure_emitsNothing() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(500, "请稍后再试"))
        val events = collectEventsOf(repository)

        assertTrue(repository.collectItemAdd(70001L, UserCollectCategory.ALBUM).isFailure)

        assertTrue(events.isEmpty())
    }

    @Test
    fun collectItemDel_emitsItemCollectChangedUncollected() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val events = collectEventsOf(repository)

        assertTrue(repository.collectItemDel(90001L, UserCollectCategory.PICTURE).isSuccess)

        assertEquals(listOf(CollectEvent.ItemCollectChanged(90001L, UserCollectCategory.PICTURE, false)), events)
    }

    @Test
    fun collectItemDel_onApiFailure_emitsNothing() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(500, "请稍后再试"))
        val events = collectEventsOf(repository)

        assertTrue(repository.collectItemDel(90001L, UserCollectCategory.PICTURE).isFailure)

        assertTrue(events.isEmpty())
    }

    @Test
    fun cancelCollectBatch_emitsBatchCollectCancelled() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val events = collectEventsOf(repository)

        assertTrue(repository.cancelCollectBatch(listOf(1L, 2L), UserCollectCategory.HOBBY).isSuccess)

        assertEquals(listOf(CollectEvent.BatchCollectCancelled(setOf(1L, 2L), UserCollectCategory.HOBBY)), events)
    }

    @Test
    fun cancelCollectBatch_emptyIds_emitsNothing() = runTest {
        val (_, repository) = setup()
        val events = collectEventsOf(repository)

        assertTrue(repository.cancelCollectBatch(emptyList(), UserCollectCategory.HOBBY).isSuccess)

        assertTrue(events.isEmpty())
    }

    @Test
    fun cancelCollectBatch_onApiFailure_emitsNothing() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(500, "请稍后再试"))
        val events = collectEventsOf(repository)

        assertTrue(repository.cancelCollectBatch(listOf(9L), UserCollectCategory.HOBBY).isFailure)

        assertTrue(events.isEmpty())
    }

    @Test
    fun saveWorksCollect_emitsWorksCollectChanged() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val events = collectEventsOf(repository)

        assertTrue(repository.saveWorksCollect(20001L, 2, "热血", "追番中").isSuccess)

        assertEquals(1, events.size)
        val event = events.single() as CollectEvent.WorksCollectChanged
        assertEquals(20001L, event.worksId)
        assertEquals(2, event.data?.state)
        assertEquals("追番中", event.data?.comment)
    }

    @Test
    fun saveWorksCollect_onApiFailure_emitsNothing() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(500, "请稍后再试"))
        val events = collectEventsOf(repository)

        assertTrue(repository.saveWorksCollect(20001L, 2, "热血", "追番中").isFailure)

        assertTrue(events.isEmpty())
    }

    @Test
    fun deleteWorksCollect_emitsWorksCollectChangedWithNullData() = runTest {
        val (mock, repository) = setup()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        val events = collectEventsOf(repository)

        assertTrue(repository.deleteWorksCollect(20001L).isSuccess)

        assertEquals(listOf(CollectEvent.WorksCollectChanged(20001L, null)), events)
    }

    @Test
    fun deleteWorksCollect_onApiFailure_emitsNothing() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(500, "请稍后再试"))
        val events = collectEventsOf(repository)

        assertTrue(repository.deleteWorksCollect(20001L).isFailure)

        assertTrue(events.isEmpty())
    }
}
