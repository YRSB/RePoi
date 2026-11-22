package com.nobody.repoi.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class ZoneCategoriesPayload(
    val visible: Boolean = true,
    val categories: List<ZoneCategoryItem>? = null
)

@Serializable
data class ZoneCategoryItem(
    val categoryId: Int = 0,
    val name: String? = null
)
