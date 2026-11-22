package com.nobody.repoi.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.local.SearchHistoryManager
import com.nobody.repoi.data.remote.model.HotwordItem
import com.nobody.repoi.data.repository.SearchRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchLandingUiState(
    val keyword: String = "",
    val searchHistory: List<String> = emptyList(),
    val hotwords: List<HotwordItem> = emptyList(),
    val isLoadingHotwords: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val category: Int = HobbyAllCategory.id
)

class SearchLandingViewModel(
    private val searchHistoryManager: SearchHistoryManager,
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchLandingUiState())
    val uiState: StateFlow<SearchLandingUiState> = _uiState.asStateFlow()

    private var autoFillJob: Job? = null
    private var paramsInitialized = false

    init {
        viewModelScope.launch {
            searchHistoryManager.historyFlow.collect { historyList ->
                _uiState.update { it.copy(searchHistory = historyList) }
            }
        }
        loadHotwords(HobbyAllCategory.id)
    }

    fun initParam(navParam: SearchNavParam) {
        if (paramsInitialized) return
        paramsInitialized = true

        val targetCategory = if (navParam.initialCategory != 0) navParam.initialCategory else HobbyAllCategory.id
        val initialKeyword = navParam.initialKeyword

        _uiState.update {
            it.copy(
                keyword = initialKeyword,
                category = targetCategory
            )
        }

        if (targetCategory != HobbyAllCategory.id) {
            loadHotwords(targetCategory)
        }

        if (initialKeyword.isNotBlank()) {
            loadSuggestions(initialKeyword, debounce = false)
        }
    }

    fun onKeywordChange(newKeyword: String) {
        if (newKeyword.isBlank()) {
            autoFillJob?.cancel()
            _uiState.update {
                it.copy(
                    keyword = newKeyword,
                    suggestions = emptyList()
                )
            }
            return
        }

        _uiState.update { it.copy(keyword = newKeyword) }
        loadSuggestions(newKeyword, debounce = true)
    }

    fun onClearKeyword() {
        autoFillJob?.cancel()
        _uiState.update {
            it.copy(
                keyword = "",
                suggestions = emptyList()
            )
        }
    }

    fun loadHotwords(category: Int = _uiState.value.category) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHotwords = true) }
            val result = searchRepository.getHotwords(category)
            result.onSuccess { words ->
                _uiState.update {
                    it.copy(
                        hotwords = words,
                        isLoadingHotwords = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingHotwords = false) }
            }
        }
    }

    fun refreshHotwords() {
        loadHotwords(_uiState.value.category)
    }

    fun loadSuggestions(query: String, debounce: Boolean = true) {
        autoFillJob?.cancel()
        autoFillJob = viewModelScope.launch {
            if (debounce) {
                delay(300L)
            }
            val trimmed = query.trim()
            if (trimmed.isBlank()) {
                _uiState.update { it.copy(suggestions = emptyList()) }
                return@launch
            }
            val result = searchRepository.getAutoFill(_uiState.value.category, trimmed)
            result.onSuccess { suggestions ->
                if (_uiState.value.keyword.trim() == trimmed) {
                    _uiState.update { it.copy(suggestions = suggestions) }
                }
            }.onFailure {

            }
        }
    }

    fun onRemoveHistory(keyword: String) {
        viewModelScope.launch {
            searchHistoryManager.removeKeyword(keyword)
        }
    }

    fun onClearHistory() {
        viewModelScope.launch {
            searchHistoryManager.clearAll()
        }
    }

    fun onSearch(targetKeyword: String? = null) {
        val searchWord = (targetKeyword ?: _uiState.value.keyword).trim()
        if (searchWord.isBlank()) return
        viewModelScope.launch {
            searchHistoryManager.addKeyword(searchWord)
        }
    }
}
