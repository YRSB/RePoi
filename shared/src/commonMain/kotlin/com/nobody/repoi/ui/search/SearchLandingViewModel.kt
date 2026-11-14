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

/**
 * 搜索落地页 UI 状态模型。
 *
 * 聚合搜索输入草稿、搜索历史、大家在找热词及实时输入联想建议。
 */
data class SearchLandingUiState(
    val keyword: String = "",
    val searchHistory: List<String> = emptyList(),
    val hotwords: List<HotwordItem> = emptyList(),
    val isLoadingHotwords: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val category: Int = HobbyAllCategory.id
)

/**
 * 搜索落地页专用 ViewModel。
 *
 * 职责单一：管理搜索历史记录的响应式流变迁与增删清空、异步拉取并刷新大家在找热词榜、
 * 300ms 防抖的实时输入联想词建议拉取以及输入草稿生命周期管理。
 */
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

    /**
     * 响应式初始化导航参数。仅在首次进入时生效，防止配置变更或重组覆写用户当前草稿。
     */
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

    /**
     * 响应搜索草稿输入变迁。当内容变为空时立即清空联想列表并取消防抖任务；非空时触发 300ms 防抖联想。
     */
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

    /**
     * 一键清空搜索输入草稿。
     */
    fun onClearKeyword() {
        autoFillJob?.cancel()
        _uiState.update {
            it.copy(
                keyword = "",
                suggestions = emptyList()
            )
        }
    }

    /**
     * 异步拉取大家在找热词榜。
     */
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

    /**
     * 刷新大家在找热词榜。
     */
    fun refreshHotwords() {
        loadHotwords(_uiState.value.category)
    }

    /**
     * 拉取关键词联想建议列表。
     *
     * @param query 查询关键词
     * @param debounce 是否启用 300ms 防抖延迟（直接填充或由外部参数拉起时可传入 false）
     */
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
                // 失败时保持现有建议不变
            }
        }
    }

    /**
     * 单项删除历史记录。
     */
    fun onRemoveHistory(keyword: String) {
        viewModelScope.launch {
            searchHistoryManager.removeKeyword(keyword)
        }
    }

    /**
     * 一键清空全部历史记录。
     */
    fun onClearHistory() {
        viewModelScope.launch {
            searchHistoryManager.clearAll()
        }
    }

    /**
     * 提交搜索时持久化保存到历史记录。
     */
    fun onSearch(targetKeyword: String? = null) {
        val searchWord = (targetKeyword ?: _uiState.value.keyword).trim()
        if (searchWord.isBlank()) return
        viewModelScope.launch {
            searchHistoryManager.addKeyword(searchWord)
        }
    }
}
