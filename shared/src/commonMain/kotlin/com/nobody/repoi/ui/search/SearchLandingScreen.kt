package com.nobody.repoi.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import com.nobody.repoi.ui.search.components.EveryoneSearchingSection
import com.nobody.repoi.ui.search.components.SearchHistorySection
import com.nobody.repoi.ui.search.components.SearchSuggestionsList
import com.nobody.repoi.ui.search.components.SearchTopBar
import org.koin.compose.viewmodel.koinViewModel

/**
 * PoiShelf 独立搜索落地页。
 *
 * 承载搜索输入框、搜索历史、大家在找热词榜及实时输入联想。
 * 纯净接入 Navigation 3 单根栈，无私有 BackHandler，原生支持 MaterialSharedAxis.Z 与预测性返回手势。
 */
@Composable
fun SearchLandingScreen(
    navParam: SearchNavParam = SearchNavParam(),
    onBack: () -> Unit,
    onNavigateToResult: (SearchResultNavParam) -> Unit,
    viewModel: SearchLandingViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // 判断大屏环境以适配居中与双栏排版（达到 Expanded 宽度时为大屏双栏，其余为小屏单流）
    val isWideScreen = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

    LaunchedEffect(navParam) {
        viewModel.initParam(navParam)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val onPerformSearchAction: (String) -> Unit = { query ->
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            keyboardController?.hide()
            viewModel.onSearch(trimmed)
            onNavigateToResult(
                SearchResultNavParam(
                    keyword = trimmed,
                    category = navParam.initialCategory,
                    companyNodeId = navParam.initialCompanyNodeId,
                    companyName = navParam.initialCompanyName
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        SearchTopBar(
            query = uiState.keyword,
            onQueryChange = { viewModel.onKeywordChange(it) },
            onSearch = onPerformSearchAction,
            onBackClick = {
                keyboardController?.hide()
                onBack()
            },
            onClearClick = { viewModel.onClearKeyword() },
            placeholderText = "搜索",
            showFilterButton = false,
            focusRequester = focusRequester
        )

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 输入非空时呈现联想建议列表
            if (uiState.keyword.isNotBlank()) {
                SearchSuggestionsList(
                    suggestions = uiState.suggestions,
                    query = uiState.keyword,
                    onSelectWord = onPerformSearchAction
                )
            } else {
                // 输入为空时呈现搜索历史面板与大家在找热词流式排布 (支持小屏单列与大屏双栏排布)
                if (isWideScreen && uiState.searchHistory.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            EveryoneSearchingSection(
                                hotwords = uiState.hotwords,
                                isLoading = uiState.isLoadingHotwords,
                                onRefresh = { viewModel.refreshHotwords() },
                                onSelectWord = onPerformSearchAction
                            )
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            SearchHistorySection(
                                historyList = uiState.searchHistory,
                                onSelectWord = onPerformSearchAction,
                                onDeleteItem = { word -> viewModel.onRemoveHistory(word) },
                                onClearAll = { viewModel.onClearHistory() }
                            )
                        }
                    }
                } else {
                    if (uiState.searchHistory.isNotEmpty()) {
                        SearchHistorySection(
                            historyList = uiState.searchHistory,
                            onSelectWord = onPerformSearchAction,
                            onDeleteItem = { word -> viewModel.onRemoveHistory(word) },
                            onClearAll = { viewModel.onClearHistory() }
                        )
                    }

                    EveryoneSearchingSection(
                        hotwords = uiState.hotwords,
                        isLoading = uiState.isLoadingHotwords,
                        onRefresh = { viewModel.refreshHotwords() },
                        onSelectWord = onPerformSearchAction
                    )
                }
            }
        }
    }
}
