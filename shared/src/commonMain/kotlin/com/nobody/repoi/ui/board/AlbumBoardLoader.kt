package com.nobody.repoi.ui.board

import com.nobody.repoi.data.remote.model.AlbumListItem
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.util.paging.PageChunk
import com.nobody.repoi.util.paging.PagedListLoader
import com.nobody.repoi.util.paging.PagedListState
import com.nobody.repoi.util.paging.hasNewKeysAfterDedup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

data class AlbumBoardQuery(
    val scope: ZoneQueryScope,
    val order: String = AlbumBoardOptions.DEFAULT_ORDER,
    val category: Int? = null
)

class AlbumBoardLoader(
    scope: CoroutineScope,
    private val repository: AlbumRepository
) {
    private var query = AlbumBoardQuery(scope = ZoneQueryScope.All)

    private var existingAlbums: List<AlbumListItem> = emptyList()

    private val loader = PagedListLoader<AlbumListItem>(
        scope = scope,
        initialPage = 1,
        pageSize = AlbumBoardOptions.PAGE_SIZE,
        keySelector = { it.targetAlbumId }
    ) { page, pageSize ->
        repository.getAlbums(
            page = page,
            pageSize = pageSize,
            orderString = query.order,
            zones = query.scope.zonesParam(),
            category = query.category
        ).map { payload ->
            val hasMore = payload.hasMore &&
                (page == 1 || payload.items.hasNewKeysAfterDedup(existingAlbums) { it.targetAlbumId })
            PageChunk(items = payload.items, hasMore = hasMore)
        }
    }

    val state: StateFlow<PagedListState<AlbumListItem>> = loader.state

    fun loadInitial(query: AlbumBoardQuery) {
        this.query = query
        loader.loadInitial()
    }

    fun refresh(query: AlbumBoardQuery) {
        this.query = query
        loader.refresh()
    }

    fun loadMore() {
        existingAlbums = loader.state.value.items
        loader.loadMore()
    }

    fun reset() {
        loader.reset()
    }
}
