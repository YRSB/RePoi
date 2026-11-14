package com.nobody.repoi.ui.search

import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.SearchHistoryManager
import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.RecordedHttpRequest
import com.nobody.repoi.data.repository.SearchRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.Json

/**
 * 搜索中枢测试共享夹具：环境构建、接口 stub 与请求断言辅助。
 *
 * 路由按 `path == api/search` + form `category` 分发，断言与请求顺序无关。
 */
internal class SearchResultTestEnv(val mock: MockApi, val viewModel: SearchResultViewModel)

internal fun TestScope.createSearchResultTestEnv(
    testDispatcher: CoroutineDispatcher,
    json: Json
): SearchResultTestEnv {
    val mock = MockApi().apply { stubSearchEndpoints() }
    val historyManager = SearchHistoryManager(TestDataStore.inMemory(), json)
    val api = mock.api(testDispatcher)
    val viewModel = SearchResultViewModel(
        SearchRepository(api, historyManager),
        CategoryManager(api, backgroundScope)
    )
    return SearchResultTestEnv(mock, viewModel)
}

internal fun SearchResultViewModel.tabState(tab: SearchTab): TabSearchState =
    uiState.value.tabStates.getValue(tab)

/** 主搜分页响应：第 1 页 2 条、第 2 页 1 条，`pageCount = 3` 便于验证触底加载。 */
internal fun MockApi.searchPageJson(category: String, page: Int): String {
    val ids = when (page) {
        1 -> listOf(1L, 2L)
        2 -> listOf(3L)
        else -> emptyList()
    }
    val items = ids.joinToString(",") { id ->
        """{"id":$id,"itemId":$id,"name":"$category-page$page-item$id"}"""
    }
    // 聚合响应附带厂商/年份候选，用于验证候选列表只归属周边快照
    val aggregates =
        ""","companys":[{"nodeId":42,"itemName":"GoodSmile","docCount":5}],"releaseYear":[{"keyAsString":"2024","docCount":3}]"""
    return ok(
        """{"result":[$items],"query":{"category":${category},"page":$page,"pageCount":3,"totalCount":3}$aggregates}"""
    )
}

/** 用户搜索分页响应：第 1 页 12 条（触发 hasMore），后续页 2 条。 */
internal fun MockApi.userPageJson(page: Int): String {
    val count = if (page == 1) 12 else 2
    val start = page * 100
    val users = (1..count).joinToString(",") { offset ->
        """{"userId":${start + offset},"nickname":"u${page}_$offset"}"""
    }
    return ok("""{"userList":[$users]}""")
}

/** 挂载搜索页涉及的全部接口，避免未命中兜底返回失败。 */
internal fun MockApi.stubSearchEndpoints() {
    routeWhen(
        predicate = { path, form -> path.endsWith("/api/search") && form["category"] != null },
        body = { _, form ->
            val category = form["category"].orEmpty()
            val page = form["page"]?.toIntOrNull() ?: 1
            searchPageJson(category, page)
        }
    )
    routeWhen(
        predicate = { path, _ -> path.endsWith("/api/search/user") },
        body = { _, form -> userPageJson(form["page"]?.toIntOrNull() ?: 1) }
    )
    routeWhen(
        predicate = { path, _ -> path.endsWith("/api/search/auto/fill") },
        body = { _, _ -> ok("""{"keywords":[]}""") }
    )
    routeWhen(
        predicate = { path, _ -> path.endsWith("/api/search/hotword") },
        body = { _, _ -> ok("""{"list":[]}""") }
    )
    routeWhen(
        predicate = { path, _ -> path.endsWith("/api/charactar/attribute/list") },
        body = { _, _ -> ok("""{"attributeList":[]}""") }
    )
}

/** 取走全部已记录请求（调用方按 path/form 过滤断言，顺序无关）。 */
internal fun MockApi.takeAllRequests(): List<RecordedHttpRequest> = buildList {
    while (requestCount > 0) {
        add(takeRequest())
    }
}

/** 主搜索（POST api/search）请求，按发出顺序。 */
internal fun List<RecordedHttpRequest>.searchRequests(): List<RecordedHttpRequest> =
    filter { it.encodedPath?.endsWith("/api/search") == true }

/** 主搜索请求携带的分类 ID，按发出顺序。 */
internal fun List<RecordedHttpRequest>.searchCategories(): List<String> =
    searchRequests().mapNotNull { it.formEntries()["category"] }

/** 最后一次主搜索请求的表单参数。 */
internal fun List<RecordedHttpRequest>.lastSearchForm(): Map<String, String> =
    searchRequests().last().formEntries()
