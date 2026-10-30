package com.nobody.poishelf.data.repository

import com.nobody.poishelf.data.remote.model.ApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 图片列表仓储测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `PictureListRepositoryTest`（逐用例；Retrofit+MockWebServer →
 * [MockApi] MockEngine+真实 HpoiApi；JUnit4 → kotlin.test）。
 */
class PictureListRepositoryTest {

    private fun setup(): Pair<MockApi, PictureListRepository> {
        val mock = MockApi()
        return mock to PictureListRepository(mock.api())
    }

    @Test
    fun getPictureList_sendsAllParamsAndMapsItems() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1,"path":"items/pic_1.jpg","subType":"official"}]}}"""
        )

        val result = repository.getPictureList(
            itemId = 59570L,
            itemType = "hobby",
            subType = "official",
            page = 2,
            pageSize = 20,
            order = "top",
            nodeId = 7788L
        )

        assertTrue(result.isSuccess)
        assertEquals("items/pic_1.jpg", result.getOrThrow().single().path)
        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/pic/list/relate-v2", recorded.path)
        val form = recorded.formEntries()
        assertEquals("59570", form["itemId"])
        assertEquals("hobby", form["itemType"])
        assertEquals("official", form["subType"])
        assertEquals("2", form["page"])
        assertEquals("20", form["pageSize"])
        assertEquals("top", form["order"])
        assertEquals("7788", form["nodeId"])
    }

    @Test
    fun getPictureList_omitsOptionalNulls() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}"""
        )

        val result = repository.getPictureList(
            itemId = 59570L,
            itemType = "hobby",
            subType = null
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
        val form = mock.takeRequest().formEntries()
        assertEquals("59570", form["itemId"])
        assertEquals("hobby", form["itemType"])
        assertEquals("1", form["page"])
        assertEquals("20", form["pageSize"])
        assertFalse(form.containsKey("subType"))
        assertFalse(form.containsKey("order"))
        assertFalse(form.containsKey("nodeId"))
    }

    @Test
    fun getPictureList_failureThrowsApiException() = runTest {
        val (mock, repository) = setup()
        mock.enqueue(mock.failure(4001, "登录过期"))

        val result = repository.getPictureList(1L, "hobby", "official")

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as ApiException
        assertEquals(4001, error.code)
        assertEquals("登录过期", error.message)
    }
}
