package com.nobody.repoi.ui.search

import kotlinx.serialization.Serializable

@Serializable
data class SearchNavParam(
    val initialKeyword: String = "",
    val initialCategory: Int = HobbyAllCategory.id,
    val initialCompanyNodeId: Long? = null,
    val initialCompanyName: String? = null
)

@Serializable
data class SearchResultNavParam(
    val keyword: String = "",
    val category: Int = HobbyAllCategory.id,
    val companyNodeId: Long? = null,
    val companyName: String? = null
)
