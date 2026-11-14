package com.nobody.repoi.ui.home.components

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.RelateItem
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ActionPosterShape] 纯分类测试（ADR-0021）。
 *
 * 与 `ActionCardPosterLayoutTest` 的真实 Compose 布局断言互补：这里锁分类映射，
 * 那里锁最终槽位尺寸，分类漂移与尺寸漂移都能被单独定位。
 */
class ActionPosterPolicyTest {

    @Test
    fun `hobby and figure keep golden shape`() {
        listOf("hobby", "figure", "Figure", "HOBBY").forEach { type ->
            assertEquals(
                ActionPosterShape.GOLDEN,
                actionOf(RelateItem(itemType = type)).resolvePosterShape(),
                "itemType=$type 应保持 9:13"
            )
        }
        assertEquals(
            ActionPosterShape.GOLDEN,
            actionOf(RelateItem(categoryId = 10000)).resolvePosterShape(),
            "手办分类区间应保持 9:13"
        )
    }

    @Test
    fun `vendor character works series and person use square shape`() {
        listOf("company", "vendor", "character", "charactar", "series", "person", "works").forEach { type ->
            assertEquals(
                ActionPosterShape.SQUARE,
                actionOf(RelateItem(itemType = type)).resolvePosterShape(),
                "itemType=$type 应走 1:1"
            )
        }
    }

    @Test
    fun `entity category ranges use square shape even without itemType`() {
        listOf(20000, 30000, 40000, 40100, 50000, 80000).forEach { categoryId ->
            assertEquals(
                ActionPosterShape.SQUARE,
                actionOf(RelateItem(categoryId = categoryId)).resolvePosterShape(),
                "categoryId=$categoryId 应走 1:1"
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
    fun `missing relate item keeps golden shape`() {
        assertEquals(ActionPosterShape.GOLDEN, actionOf(null).resolvePosterShape())
    }

    private fun actionOf(relateItem: RelateItem?) = ActionItem(id = 1L, relateItem = relateItem)
}
