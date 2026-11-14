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
 *
 * `SQUARE` 是默认回退值，因此实体别名/未知类型的 SQUARE 断言主要价值是阻止误把
 * 手办别名或手办分类区间扩进 `GOLDEN`；真正能证伪区间逻辑的是 10000..19999 的
 * 边界用例与 `hobby` / `figure` / `item` 别名用例。
 */
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
        // 区间外一律方形，顺带锁住 9999 / 20000 两个边界
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
