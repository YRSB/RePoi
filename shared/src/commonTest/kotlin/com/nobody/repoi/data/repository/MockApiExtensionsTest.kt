package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.model.ZoneCategoriesPayload
import com.nobody.repoi.data.remote.model.ZoneCategoryItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 验证 [MockApi] 强类型扩展函数的序列化与路由行为。
 */
class MockApiExtensionsTest {

    @Test
    fun testRouteCommonResponseAndReplace() = runTest {
        val mock = MockApi()
        val payload1 = ZoneCategoriesPayload(
            visible = true,
            categories = listOf(ZoneCategoryItem(categoryId = 1, name = "手办"))
        )
        mock.routeCommonResponse("api/zone/categories", payload1)

        val api = mock.api()
        val resp1 = api.getZoneCategories(nodeId = 100L, itemType = "album")
        assertTrue(resp1.isSuccess)
        assertEquals(1, resp1.data?.categories?.size)
        assertEquals("手办", resp1.data?.categories?.firstOrNull()?.name)

        val payload2 = ZoneCategoriesPayload(
            visible = true,
            categories = listOf(ZoneCategoryItem(categoryId = 2, name = "周边"))
        )
        mock.replaceRouteCommonResponse("api/zone/categories", payload2)

        val resp2 = api.getZoneCategories(nodeId = 100L, itemType = "album")
        assertTrue(resp2.isSuccess)
        assertEquals(1, resp2.data?.categories?.size)
        assertEquals("周边", resp2.data?.categories?.firstOrNull()?.name)
    }

    @Test
    fun testRouteJsonDirect() = runTest {
        val mock = MockApi()
        val payload = ZoneCategoriesPayload(
            visible = true,
            categories = listOf(ZoneCategoryItem(categoryId = 10, name = "模型"))
        )
        mock.routeJson("api/zone/categories/raw", payload)

        val reqBody = mock.okJson(payload)
        assertTrue(reqBody.contains("模型"))
    }
}
