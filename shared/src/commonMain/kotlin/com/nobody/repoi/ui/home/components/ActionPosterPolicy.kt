package com.nobody.repoi.ui.home.components

import com.nobody.repoi.data.remote.model.ActionItem

internal enum class ActionPosterShape {

    GOLDEN,

    SQUARE
}

private val GoldenItemTypes = setOf("hobby", "figure", "item")

private fun isHobbyCategory(categoryId: Int): Boolean = categoryId in 10000..19999

internal fun ActionItem.resolvePosterShape(): ActionPosterShape {
    val relate = relateItem ?: return ActionPosterShape.SQUARE
    val itemType = relate.itemType?.trim()?.lowercase()
    return if (itemType in GoldenItemTypes || isHobbyCategory(relate.categoryId)) {
        ActionPosterShape.GOLDEN
    } else {
        ActionPosterShape.SQUARE
    }
}
