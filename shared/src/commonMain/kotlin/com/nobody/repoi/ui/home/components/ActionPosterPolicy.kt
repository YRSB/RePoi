package com.nobody.repoi.ui.home.components

import com.nobody.repoi.data.remote.model.ActionItem

/**
 * 动态流卡片左侧海报的**非相册**槽位形状（ADR-0021）。
 *
 * 相册传图类不在此枚举内：它们的槽位不锁比例，宽度固定 96dp、高度随照片真实比例撑开，
 * 由 [ActionCard] 的相册分支单独处理。
 */
internal enum class ActionPosterShape {
    /** 手办/周边条目：9:13 竖版黄金定框，96dp × 138.7dp。 */
    GOLDEN,

    /** 厂商/角色/作品/系列/人物等实体：1:1 正方形，96dp × 96dp，贴合其方形封面。 */
    SQUARE
}

/**
 * 手办/周边的 `itemType` 别名集合。
 *
 * Hpoi 接口大小写混用（`Figure` 等），统一小写后比对；`item` 为历史默认条目别名。
 */
private val GoldenItemTypes = setOf("hobby", "figure", "item")

/** 手办/周边的分类区间，与 [com.nobody.repoi.ui.navigation.AppRouteResolver] 的默认落点一致。 */
private fun isHobbyCategory(categoryId: Int): Boolean = categoryId in 10000..19999

/**
 * 解析非相册海报的槽位形状。
 *
 * 只有**能明确识别为手办/周边**（`itemType` 命中别名或分类落在 10000..19999）时才用 9:13 竖框；
 * 其余一律 1:1 正方形——厂商、角色、作品、系列、人物的官方封面多为方图，`picture`/`post`
 * 等用户图片也以方图或横图为主，塞进 9:13 只会留下上下白边。未知类型同样按 1:1 处理，
 * 与 Hpoi 原版海报的 1:1 首帧占位一致。
 */
internal fun ActionItem.resolvePosterShape(): ActionPosterShape {
    val relate = relateItem ?: return ActionPosterShape.GOLDEN
    val itemType = relate.itemType?.trim()?.lowercase()
    return if (itemType in GoldenItemTypes || isHobbyCategory(relate.categoryId)) {
        ActionPosterShape.GOLDEN
    } else {
        ActionPosterShape.SQUARE
    }
}
