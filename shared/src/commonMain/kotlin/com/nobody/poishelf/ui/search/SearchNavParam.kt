package com.nobody.poishelf.ui.search

import kotlinx.serialization.Serializable

@Serializable
data class SearchNavParam(
    val initialKeyword: String = "",
    val initialCategory: Int = HobbyAllCategory.id,
    val initialCompanyNodeId: Long? = null,
    val initialCompanyName: String? = null
)
