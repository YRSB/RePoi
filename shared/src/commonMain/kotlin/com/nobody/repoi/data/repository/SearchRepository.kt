package com.nobody.repoi.data.repository

import com.nobody.repoi.data.local.SearchHistoryManager
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.AttributeItem
import com.nobody.repoi.data.remote.model.HotwordItem
import com.nobody.repoi.data.remote.model.SearchResponsePayload
import com.nobody.repoi.data.remote.model.UserSearchPayload
import kotlinx.coroutines.flow.Flow

class SearchRepository(
    private val api: HpoiApi,
    private val historyManager: SearchHistoryManager
) {
    val searchHistoryFlow: Flow<List<String>> = historyManager.historyFlow

    suspend fun addSearchHistory(keyword: String) {
        historyManager.addKeyword(keyword)
    }

    suspend fun removeSearchHistory(keyword: String) {
        historyManager.removeKeyword(keyword)
    }

    suspend fun clearSearchHistory() {
        historyManager.clearAll()
    }

    /**
     * 全局主搜 (POST api/search)
     */
    suspend fun search(
        category: Int,
        itemType: String,
        keyword: String,
        page: Int = 1,
        pageSize: Int = 30,
        order: String? = null,
        company: Long? = null,
        releaseYear: String? = null,
        attribute: Int? = null,
        aggregate: Boolean = true
    ): Result<SearchResponsePayload> = runCatching {
        val params = mutableMapOf(
            "category" to category.toString(),
            "itemType" to itemType,
            "keyword" to keyword.trim(),
            "page" to page.toString(),
            "pageSize" to pageSize.toString(),
            "aggregate" to aggregate.toString()
        )

        if (!order.isNullOrBlank() && order != "default") {
            params["order"] = order
        }
        if (company != null && company > 0) {
            params["company"] = company.toString()
        }
        if (!releaseYear.isNullOrBlank() && releaseYear != "全部") {
            params["releaseYear"] = releaseYear
        }
        if (attribute != null && attribute > 0) {
            params["attribute"] = attribute.toString()
        }

        api.search(params)
    }

    suspend fun getElementAttributes(categoryId: Int): Result<List<AttributeItem>> = runCatching {
        api.getElementAttributes(categoryId).attributeList.orEmpty()
    }

    /**
     * 用户类目独立搜索 (POST api/search/user)
     */
    suspend fun searchUsers(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 10
    ): Result<UserSearchPayload> = runCatching {
        api.searchUsers(
            keyword = keyword.trim(),
            page = page,
            pageSize = pageSize
        ) ?: UserSearchPayload(userList = emptyList())
    }

    /**
     * 获取指定分类下的热词 (POST api/search/hotword)
     */
    suspend fun getHotwords(category: Int): Result<List<HotwordItem>> = runCatching {
        api.getHotwords(category).list.orEmpty()
    }

    /**
     * 输入关键词实时联想词 (POST api/search/auto/fill)
     */
    suspend fun getAutoFill(category: Int, keyword: String): Result<List<String>> = runCatching {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return@runCatching emptyList()

        api.getAutoFill(type = category, keywords = trimmed).keywords.orEmpty()
    }
}
