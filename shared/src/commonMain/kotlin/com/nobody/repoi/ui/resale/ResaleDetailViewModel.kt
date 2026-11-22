package com.nobody.repoi.ui.resale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.ResaleDetailItem
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.ResaleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nobody.repoi.data.remote.model.ApiException

data class ResaleDetailUiState(
    val detail: ResaleDetailItem? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val isReporting: Boolean = false,
    val reportSuccess: Boolean = false
)

class ResaleDetailViewModel(
    private val resaleRepository: ResaleRepository,
    private val collectRepository: CollectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResaleDetailUiState())
    val uiState: StateFlow<ResaleDetailUiState> = _uiState.asStateFlow()

    private var currentResaleId: Long = 0L

    fun setResaleId(resaleId: Long) {
        if (resaleId <= 0L || currentResaleId == resaleId) return
        currentResaleId = resaleId
        loadDetail(isRefresh = false)
    }

    fun refresh() {
        if (currentResaleId > 0L) {
            loadDetail(isRefresh = true)
        }
    }

    private fun loadDetail(isRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                if (isRefresh) it.copy(isRefreshing = true, errorMessage = null)
                else it.copy(isLoading = true, errorMessage = null)
            }
            resaleRepository.getResaleDetail(currentResaleId)
                .onSuccess { data ->
                    _uiState.update {
                        it.copy(
                            detail = data,
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { throwable ->
                    val msg = (throwable as? ApiException)?.message ?: "加载二手详情失败，请检查网络"
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = msg
                        )
                    }
                }
        }
    }

    fun submitReport(reason: String, onResult: (Boolean, String) -> Unit) {
        if (currentResaleId <= 0L) return
        viewModelScope.launch {
            _uiState.update { it.copy(isReporting = true) }
            collectRepository.submitReport(
                nodeId = currentResaleId,
                content = reason,
                type = CollectConstants.REPORT_TYPE_RESALE
            ).onSuccess {
                _uiState.update { it.copy(isReporting = false, reportSuccess = true) }
                onResult(true, "举报成功，感谢您的反馈")
            }.onFailure { err ->
                _uiState.update { it.copy(isReporting = false) }
                val errorMsg = err.message ?: "举报提交失败，请稍后重试"
                onResult(false, errorMsg)
            }
        }
    }
}
