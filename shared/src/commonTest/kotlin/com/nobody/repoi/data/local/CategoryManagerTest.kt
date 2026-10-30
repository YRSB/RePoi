package com.nobody.repoi.data.local

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.CategoryNode
import com.nobody.repoi.data.remote.model.CategoryTreeData
import com.nobody.repoi.data.remote.model.CommonResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 分类管理器测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `CategoryManagerTest`（逐用例；JUnit4 → kotlin.test，
 * Mockito `mock(HpoiApiService)` → MockEngine 背后的真实 [HpoiApi]，
 * `runBlocking` → `runTest`）。
 */
class CategoryManagerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private fun apiWithCategories(body: String): HpoiApi {
        val mock = MockEngine {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return HpoiApi(HttpClient(mock), json)
    }

    private fun emptyApi(): HpoiApi = apiWithCategories(
        """{"success":true,"code":200,"data":{"list":[]}}"""
    )

    @Test
    fun defaultTree_exposesExpectedCategoriesWithoutNetwork() {
        val manager = CategoryManager(emptyApi())

        assertEquals("手办", manager.getSubCategories(10000).first { it.id == 100 }.name)
        assertTrue(manager.getSubCategories(100).isNotEmpty())
        assertEquals(10000, manager.getParentId(900))
        assertEquals(100, manager.getParentId(1))
        assertNull(manager.getParentId(999999))
    }

    @Test
    fun unknownParent_fallsBackToSingleAllEntry() {
        val manager = CategoryManager(emptyApi())

        assertEquals(
            listOf(CategoryItem(88888, "全部", 88888)),
            manager.getSubCategories(88888)
        )
    }

    @Test
    fun refreshCategoriesAsync_mergesRemoteTreeIntoMap() = runTest {
        val response = CommonResponse(
            success = true,
            code = 200,
            data = CategoryTreeData(
                list = listOf(
                    CategoryNode(
                        id = 1,
                        categoryId = 99999,
                        parentId = 0,
                        name = "新分类",
                        children = listOf(
                            CategoryNode(
                                id = 2,
                                categoryId = 88888,
                                parentId = 99999,
                                name = "新子类"
                            )
                        )
                    )
                )
            )
        )
        val api = apiWithCategories(json.encodeToString(CommonResponse.serializer(CategoryTreeData.serializer()), response))
        val manager = CategoryManager(api, backgroundScope)

        withTimeout(5_000) {
            while (manager.getParentId(88888) != 99999) {
                delay(10)
            }
        }

        assertEquals("新子类", manager.getSubCategories(99999).first { it.id == 88888 }.name)
        assertEquals("手办", manager.getSubCategories(10000).first { it.id == 100 }.name)
    }
}
