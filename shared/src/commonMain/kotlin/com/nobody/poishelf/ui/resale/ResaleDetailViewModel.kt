package com.nobody.poishelf.ui.resale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.poishelf.data.remote.model.CollectConstants
import com.nobody.poishelf.data.remote.model.ResaleDetailItem
import com.nobody.poishelf.data.repository.CollectRepository
import com.nobody.poishelf.data.repository.ResaleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 二手出售详情页 UI 状态。
 */
data class ResaleDetailUiState(
    val detail: ResaleDetailItem? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val isReporting: Boolean = false,
    val reportSuccess: Boolean = false
)

/**
 * 二手出售详情 ViewModel。
 *
 * 遵循单向数据流 (UDF) 架构，从 [ResaleRepository] 加载二手交易详情，
 * 并支持实物图多图浏览、关联手办路由跳转与二手条目举报。
 */
class ResaleDetailViewModel(
    private val resaleRepository: ResaleRepository,
    private val collectRepository: CollectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResaleDetailUiState())
    val uiState: StateFlow<ResaleDetailUiState> = _uiState.asStateFlow()

    private var currentResaleId: Long = 0L

    /**
     * 设置待加载的二手交易条目 ID 并触发首次数据拉取。
     */
    fun setResaleId(resaleId: Long) {
        if (resaleId <= 0L || currentResaleId == resaleId) return
        currentResaleId = resaleId
        loadDetail(isRefresh = false)
    }

    /**
     * 刷新或加载详情数据。
     */
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

    /**
     * 提交对当前二手的举报。
     */
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
