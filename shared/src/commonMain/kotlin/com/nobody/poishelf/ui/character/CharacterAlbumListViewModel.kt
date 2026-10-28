package com.nobody.poishelf.ui.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.poishelf.data.repository.CharacterDetailRepository
import com.nobody.poishelf.ui.navigation.CharacterAlbumListNavParam
import com.nobody.poishelf.util.paging.PagedListLoader
import com.nobody.poishelf.util.paging.pagedListLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 角色相关相册列表 ViewModel。
 */
class CharacterAlbumListViewModel(
    private val repository: CharacterDetailRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CharacterAlbumListUiState())
    val uiState: StateFlow<CharacterAlbumListUiState> = _uiState.asStateFlow()

    private var currentCharacterId: Long = 0L
    private val pageSize = 20

    private val loader: PagedListLoader<AlbumData> = pagedListLoader(
        scope = viewModelScope,
        pageSize = pageSize,
        keySelector = { it.actualAlbumId }
    ) { page, size ->
        repository.getCharacterAlbums(
            characterId = currentCharacterId,
            page = page,
            pageSize = size
        )
    }

    init {
        viewModelScope.launch {
            loader.state.collect { listState ->
                _uiState.update { current ->
                    current.copy(
                        albums = listState.items,
                        page = listState.page,
                        isInitialLoading = listState.isLoading,
                        isRefreshing = listState.isRefreshing,
                        isLoadingMore = listState.isLoadingMore,
                        hasMore = listState.hasMore,
                        errorMessage = listState.errorMessage
                    )
                }
            }
        }
    }

    fun loadInitial(param: CharacterAlbumListNavParam) {
        if (currentCharacterId == param.characterId && _uiState.value.albums.isNotEmpty()) {
            return
        }
        currentCharacterId = param.characterId
        _uiState.update {
            it.copy(
                title = param.title.ifBlank { "相关相册" },
                errorMessage = null
            )
        }
        loader.loadInitial()
    }

    fun refresh() {
        if (currentCharacterId <= 0L || _uiState.value.isRefreshing) return
        loader.refresh()
    }

    fun loadMore() {
        if (currentCharacterId <= 0L) return
        loader.loadMore()
    }

    fun retry() {
        if (currentCharacterId > 0L) {
            loader.retry()
        }
    }
}
