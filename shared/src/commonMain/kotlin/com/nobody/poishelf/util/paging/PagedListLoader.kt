package com.nobody.poishelf.util.paging

import kotlinx.coroutines.CoroutineScope

/**
 * 基于整型页码（默认 1-based）的高层深度分页控制器。
 *
 * 继承自通用 [KeyedListLoader]，专注于标准化页码递增、单页容量判定与并发防重。
 *
 * @param scope 协程作用域（通常传入 viewModelScope）
 * @param initialPage 起始页码（默认为 1）
 * @param pageSize 单页条目容量（默认为 20）
 * @param keySelector 稳定键提取器（用于列表项追加去重）
 * @param fetcher 挂起分页请求函数，接收 (page, pageSize) 并返回 Result<PageChunk<T>>
 */
open class PagedListLoader<T>(
    scope: CoroutineScope,
    val initialPage: Int = 1,
    val pageSize: Int = 20,
    keySelector: ((T) -> Any?)? = null,
    fetcher: suspend (page: Int, pageSize: Int) -> Result<PageChunk<T>>
) : KeyedListLoader<T, Int>(
    scope = scope,
    initialKey = initialPage,
    keySelector = keySelector,
    nextKeyProvider = { currentKey, _ -> currentKey + 1 },
    fetcher = { page -> fetcher(page, pageSize) }
) {
    companion object {
        /**
         * 便捷工厂：适配仅返回纯列表 [Result<List<T>>] 的仓储接口，
         * 自动按 [PageChunk.of]（即条目数达到 pageSize）判定 hasMore。
         */
        fun <T> fromList(
            scope: CoroutineScope,
            initialPage: Int = 1,
            pageSize: Int = 20,
            keySelector: ((T) -> Any?)? = null,
            fetcher: suspend (page: Int, pageSize: Int) -> Result<List<T>>
        ): PagedListLoader<T> {
            return PagedListLoader(
                scope = scope,
                initialPage = initialPage,
                pageSize = pageSize,
                keySelector = keySelector,
                fetcher = { page, size ->
                    fetcher(page, size).map { PageChunk.of(it, size) }
                }
            )
        }
    }
}

/**
 * 快速创建返回纯列表 [Result<List<T>>] 的 [PagedListLoader]。
 */
fun <T> pagedListLoader(
    scope: CoroutineScope,
    initialPage: Int = 1,
    pageSize: Int = 20,
    keySelector: ((T) -> Any?)? = null,
    fetcher: suspend (page: Int, pageSize: Int) -> Result<List<T>>
): PagedListLoader<T> = PagedListLoader.fromList(
    scope = scope,
    initialPage = initialPage,
    pageSize = pageSize,
    keySelector = keySelector,
    fetcher = fetcher
)

/**
 * 快速创建返回批次分块 [Result<PageChunk<T>>] 的 [PagedListLoader]。
 */
fun <T> pagedChunkLoader(
    scope: CoroutineScope,
    initialPage: Int = 1,
    pageSize: Int = 20,
    keySelector: ((T) -> Any?)? = null,
    fetcher: suspend (page: Int, pageSize: Int) -> Result<PageChunk<T>>
): PagedListLoader<T> = PagedListLoader(
    scope = scope,
    initialPage = initialPage,
    pageSize = pageSize,
    keySelector = keySelector,
    fetcher = fetcher
)
