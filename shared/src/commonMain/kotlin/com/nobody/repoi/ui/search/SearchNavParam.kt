package com.nobody.repoi.ui.search

import kotlinx.serialization.Serializable

@Serializable
data class SearchNavParam(
    val initialKeyword: String = "",
    val initialCategory: Int = HobbyAllCategory.id,
    val initialCompanyNodeId: Long? = null,
    val initialCompanyName: String? = null
)

/**
 * 搜索结果页强类型导航参数契约。
 *
 * 承载从落地页提交或外部（条目详情、厂商等）直达搜索结果页时所需的查询条件。
 */
@Serializable
data class SearchResultNavParam(
    val keyword: String = "",
    val category: Int = HobbyAllCategory.id,
    val companyNodeId: Long? = null,
    val companyName: String? = null
)

