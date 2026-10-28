package com.nobody.poishelf.data.repository

import com.nobody.poishelf.data.local.SearchHistoryManager
import com.nobody.poishelf.data.remote.api.HpoiApi
import com.nobody.poishelf.data.remote.model.ApiException
import com.nobody.poishelf.data.remote.model.AttributeItem
import com.nobody.poishelf.data.remote.model.HotwordItem
import com.nobody.poishelf.data.remote.model.SearchResponsePayload
import com.nobody.poishelf.data.remote.model.UserSearchPayload
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

        val response = api.search(params)
        if (response.success && response.data != null) {
            response.data
        } else {
            throw ApiException(response.code, response.msg ?: "搜索失败")
        }
    }

    suspend fun getElementAttributes(categoryId: Int): Result<List<AttributeItem>> = runCatching {
        val response = api.getElementAttributes(categoryId)
        if (response.success && response.data != null) {
            response.data.attributeList.orEmpty()
        } else {
            throw ApiException(response.code, response.msg ?: "属性加载失败")
        }
    }

    /**
     * 用户类目独立搜索 (POST api/search/user)
     */
    suspend fun searchUsers(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 10
    ): Result<UserSearchPayload> = runCatching {
        val response = api.searchUsers(
            keyword = keyword.trim(),
            page = page,
            pageSize = pageSize
        )
        if (response.success && response.data != null) {
            response.data
        } else {
            throw ApiException(response.code, response.msg ?: "搜索用户失败")
        }
    }

    /**
     * 获取指定分类下的热词 (POST api/search/hotword)
     */
    suspend fun getHotwords(category: Int): Result<List<HotwordItem>> = runCatching {
        val response = api.getHotwords(category)
        if (response.success && response.data != null) {
            response.data.list.orEmpty()
        } else {
            emptyList()
        }
    }

    /**
     * 输入关键词实时联想词 (POST api/search/auto/fill)
     */
    suspend fun getAutoFill(category: Int, keyword: String): Result<List<String>> = runCatching {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return@runCatching emptyList()

        val response = api.getAutoFill(type = category, keywords = trimmed)
        if (response.success && response.data != null) {
            response.data.keywords.orEmpty()
        } else {
            emptyList()
        }
    }
}
