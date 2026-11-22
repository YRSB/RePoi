package com.nobody.repoi.ui.home.components

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.RelateItem
import kotlin.test.Test
import kotlin.test.assertEquals

class ActionPosterPolicyTest {

    @Test
    fun `hobby item types keep golden shape`() {
        listOf("hobby", "figure", "item", "Hobby", " hobby ").forEach { type ->
            assertEquals(
                ActionPosterShape.GOLDEN,
                actionOf(RelateItem(itemType = type)).resolvePosterShape(),
                "itemType='$type' 应保持 9:13"
            )
        }
    }

    @Test
    fun `hobby category range keeps golden shape at its boundaries`() {
        listOf(10000, 19999).forEach { categoryId ->
            assertEquals(
                ActionPosterShape.GOLDEN,
                actionOf(RelateItem(categoryId = categoryId)).resolvePosterShape(),
                "categoryId=$categoryId 应保持 9:13"
            )
        }

        listOf(9999, 20000).forEach { categoryId ->
            assertEquals(
                ActionPosterShape.SQUARE,
                actionOf(RelateItem(categoryId = categoryId)).resolvePosterShape(),
                "categoryId=$categoryId 应走 1:1"
            )
        }
    }

    @Test
    fun `entity item types use square shape`() {
        listOf("company", "vendor", "character", "charactar", "series", "person", "works").forEach { type ->
            assertEquals(
                ActionPosterShape.SQUARE,
                actionOf(RelateItem(itemType = type)).resolvePosterShape(),
                "itemType=$type 应走 1:1"
            )
        }
    }

    @Test
    fun `unknown relation falls back to square shape`() {
        assertEquals(
            ActionPosterShape.SQUARE,
            actionOf(RelateItem()).resolvePosterShape(),
            "既无 itemType 也无分类时按 1:1 处理"
        )
        assertEquals(
            ActionPosterShape.SQUARE,
            actionOf(RelateItem(itemType = "picture")).resolvePosterShape(),
            "picture 等用户图片按 1:1 处理"
        )
    }

    @Test
    fun `missing relate item keeps square default`() {
        assertEquals(ActionPosterShape.SQUARE, actionOf(null).resolvePosterShape())
    }

    private fun actionOf(relateItem: RelateItem?) = ActionItem(id = 1L, relateItem = relateItem)
}
