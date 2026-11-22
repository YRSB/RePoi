package com.nobody.repoi.util.paging

import kotlinx.coroutines.CoroutineScope

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
