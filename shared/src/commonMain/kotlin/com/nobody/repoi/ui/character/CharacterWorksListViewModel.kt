package com.nobody.repoi.ui.character

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.repository.CharacterDetailRepository
import com.nobody.repoi.ui.navigation.CharacterWorksListNavParam
import com.nobody.repoi.util.paging.PagedListLoader
import com.nobody.repoi.util.paging.pagedListLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nobody.repoi.data.remote.model.CharacterWorksItem

/**
 * 角色登场作品列表 ViewModel。
 */
class CharacterWorksListViewModel(
    private val repository: CharacterDetailRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CharacterWorksListUiState())
    val uiState: StateFlow<CharacterWorksListUiState> = _uiState.asStateFlow()

    private var currentCharacterId: Long = 0L
    private val pageSize = 20

    private val loader: PagedListLoader<CharacterWorksItem> = pagedListLoader(
        scope = viewModelScope,
        pageSize = pageSize,
        keySelector = { it.worksId }
    ) { page, size ->
        repository.getCharacterWorks(
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
                        works = listState.items,
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

    fun loadInitial(param: CharacterWorksListNavParam) {
        if (currentCharacterId == param.characterId && _uiState.value.works.isNotEmpty()) {
            return
        }
        currentCharacterId = param.characterId
        _uiState.update {
            it.copy(
                title = param.title.ifBlank { "登场作品" },
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
