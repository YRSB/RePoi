package com.nobody.repoi.data.local

import com.nobody.repoi.data.remote.model.CategoryNode
import com.nobody.repoi.data.remote.model.CategoryTreeData
import com.nobody.repoi.data.remote.model.CommonResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryManagerTest {

    private val throwingApi = CategoryApi {
        throw UnsupportedOperationException("no network in test")
    }

    @Test
    fun defaultTree_exposesExpectedCategoriesWithoutNetwork() {
        val manager = CategoryManager(throwingApi)

        assertEquals("手办", manager.getSubCategories(10000).first { it.id == 100 }.name)
        assertTrue(manager.getSubCategories(100).isNotEmpty())
        assertEquals(10000, manager.getParentId(900))
        assertEquals(100, manager.getParentId(1))
        assertNull(manager.getParentId(999999))
    }

    @Test
    fun unknownParent_fallsBackToSingleAllEntry() {
        val manager = CategoryManager(throwingApi)

        assertEquals(
            listOf(CategoryItem(88888, "全部", 88888)),
            manager.getSubCategories(88888)
        )
    }

    @Test
    fun refreshCategoriesAsync_mergesRemoteTreeIntoMap() = runTest {
        val api = CategoryApi {
            CommonResponse(
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
        }

        val manager = CategoryManager(api)

        withTimeout(2_000) {
            while (manager.getParentId(88888) != 99999) {
                delay(10)
            }
        }

        assertEquals("新子类", manager.getSubCategories(99999).first { it.id == 88888 }.name)
        assertEquals("手办", manager.getSubCategories(10000).first { it.id == 100 }.name)
    }
}
