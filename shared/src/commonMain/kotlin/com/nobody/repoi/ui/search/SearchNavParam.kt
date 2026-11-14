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

/**
 * 兼容/过渡映射：将 [SearchResultNavParam] 转为原有 [SearchNavParam]。
 */
fun SearchResultNavParam.toSearchNavParam(): SearchNavParam = SearchNavParam(
    initialKeyword = keyword,
    initialCategory = category,
    initialCompanyNodeId = companyNodeId,
    initialCompanyName = companyName
)

/**
 * 将 [SearchNavParam] 转为 [SearchResultNavParam]。
 */
fun SearchNavParam.toSearchResultNavParam(): SearchResultNavParam = SearchResultNavParam(
    keyword = initialKeyword,
    category = initialCategory,
    companyNodeId = initialCompanyNodeId,
    companyName = initialCompanyName
)

