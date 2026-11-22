package com.nobody.repoi.ui.collect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.domain.usecase.collect.DeleteHobbyCollectUseCase
import com.nobody.repoi.domain.usecase.collect.SaveHobbyCollectUseCase
import com.nobody.repoi.ui.navigation.CollectEditNavParam
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nobody.repoi.platform.DateTimeFormats
import com.nobody.repoi.data.remote.model.CollectConstants

class CollectEditViewModel(
    private val collectRepository: CollectRepository,
    private val saveHobbyCollectUseCase: SaveHobbyCollectUseCase,
    private val deleteHobbyCollectUseCase: DeleteHobbyCollectUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectEditUiState())
    val uiState: StateFlow<CollectEditUiState> = _uiState.asStateFlow()

    private var initJob: Job? = null

    fun initData(param: CollectEditNavParam, force: Boolean = false) {
        val resolvedNodeId = if (param.nodeId > 0L) param.nodeId else param.itemId
        if (!force && _uiState.value.nodeId == resolvedNodeId && !_uiState.value.isLoading) {
            return
        }

        initJob?.cancel()
        initJob = viewModelScope.launch {
            val today = DateTimeFormats.todayYmd()
            _uiState.update {
                it.copy(
                    itemId = param.itemId,
                    nodeId = resolvedNodeId,
                    name = param.initialName,
                    cover = param.initialCover,
                    isLoading = true,
                    loadFailed = false,
                    errorMessage = null,
                    orderTime = today
                )
            }

            launch {
                collectRepository.getCollectCount(resolvedNodeId).onSuccess { countData ->
                    _uiState.update { it.copy(countData = countData) }
                }
            }

            launch {
                collectRepository.getCollectTags().onSuccess { tags ->
                    _uiState.update { it.copy(suggestedTags = tags) }
                }
            }

            val stateResult = collectRepository.refreshCollectState(resolvedNodeId)
            stateResult.onSuccess { collectData ->
                if (collectData != null && collectData.isCollected) {
                    val formattedMoney = collectData.orderMoney?.let {
                        if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
                    } ?: ""

                    val dateStr = collectData.orderTime?.take(10)?.ifBlank { today } ?: today

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            initialCollectData = collectData,
                            isAlreadyCollected = true,
                            selectedState = collectData.state ?: CollectConstants.STATE_CARE,
                            rating = collectData.rating,
                            orderTime = dateStr,
                            orderWay = collectData.orderWay ?: "",
                            orderMoney = formattedMoney,
                            orderMoneyCurrency = collectData.orderMoneyCurrency ?: "rmb",
                            buyNum = collectData.buyNum ?: 1,
                            orderForm = collectData.orderForm,
                            comment = collectData.comment ?: "",
                            myTags = collectData.textTags.map { tag -> tag.tagValue }
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            initialCollectData = collectData,
                            isAlreadyCollected = false,
                            selectedState = CollectConstants.STATE_CARE
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadFailed = true,
                        errorMessage = "加载收藏信息失败: ${err.message ?: "网络异常"}"
                    )
                }
            }
        }
    }

    fun retryLoad(param: CollectEditNavParam) {
        initData(param, force = true)
    }

    fun selectState(state: String) {
        _uiState.update { it.copy(selectedState = state) }
    }

    fun setRating(rating: Int) {
        _uiState.update {
            val newRating = if (it.rating == rating) 0 else rating
            it.copy(rating = newRating)
        }
    }

    fun clearRating() {
        _uiState.update { it.copy(rating = 0) }
    }

    fun setOrderTime(date: String) {
        _uiState.update { it.copy(orderTime = date) }
    }

    fun setOrderWay(way: String) {
        _uiState.update { it.copy(orderWay = way) }
    }

    fun setOrderMoney(money: String) {
        _uiState.update { it.copy(orderMoney = money) }
    }

    fun setCurrency(currency: String) {
        _uiState.update { it.copy(orderMoneyCurrency = currency) }
    }

    fun setBuyNum(num: Int) {
        _uiState.update { it.copy(buyNum = num.coerceAtLeast(1)) }
    }

    fun setOrderForm(form: Int) {
        _uiState.update { it.copy(orderForm = form) }
    }

    fun setComment(comment: String) {
        _uiState.update { it.copy(comment = comment.takeByCodePoints(MAX_COMMENT_LENGTH)) }
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isBlank()) return
        _uiState.update { current ->
            if (current.myTags.contains(trimmed)) {
                current
            } else {
                current.copy(myTags = current.myTags + trimmed)
            }
        }
    }

    fun requestDeleteTag(tag: String) {
        _uiState.update { it.copy(tagToDelete = tag, showDeleteTagDialog = true) }
    }

    fun confirmDeleteTag() {
        _uiState.update { current ->
            val target = current.tagToDelete
            current.copy(
                myTags = if (target != null) current.myTags.filterNot { it == target } else current.myTags,
                tagToDelete = null,
                showDeleteTagDialog = false
            )
        }
    }

    fun dismissDeleteTagDialog() {
        _uiState.update { it.copy(tagToDelete = null, showDeleteTagDialog = false) }
    }

    fun setDatePickerVisible(visible: Boolean) {
        _uiState.update { it.copy(showDatePicker = visible) }
    }

    fun setAddTagDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showAddTagDialog = visible) }
    }

    fun setDeleteConfirmDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showDeleteConfirmDialog = visible) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun saveCollect() {
        val snapshot = _uiState.value
        if (snapshot.isSaving || snapshot.nodeId <= 0L || snapshot.isLoading) return
        if (snapshot.loadFailed) {
            _uiState.update { it.copy(errorMessage = "加载失败请重试") }
            return
        }
        if (!snapshot.isOrderMoneyValid) {
            _uiState.update { it.copy(errorMessage = "金额格式不正确") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val fresh = _uiState.value
            val collectData = fresh.toCollectData()

            val result = saveHobbyCollectUseCase(fresh.nodeId, collectData)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                    refreshCountSnapshot(fresh.nodeId)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isSaving = false, errorMessage = err.message ?: "保存收藏失败")
                    }
                }
            )
        }
    }

    fun deleteCollect() {
        val snapshot = _uiState.value
        if (snapshot.isDeleting || snapshot.nodeId <= 0L || snapshot.isLoading) return
        if (snapshot.loadFailed) {
            _uiState.update { it.copy(errorMessage = "加载失败请重试") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isDeleting = true, showDeleteConfirmDialog = false, errorMessage = null)
            }
            val fresh = _uiState.value
            val currentData = fresh.toCollectData()
            val result = deleteHobbyCollectUseCase(fresh.nodeId, currentData)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isDeleting = false, deleteSuccess = true) }
                    refreshCountSnapshot(fresh.nodeId)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isDeleting = false, errorMessage = err.message ?: "取消收藏失败")
                    }
                }
            )
        }
    }

    private suspend fun refreshCountSnapshot(nodeId: Long) {
        collectRepository.getCollectCount(nodeId).onSuccess { count ->
            collectRepository.updateCollectCountSnapshot(nodeId, count.total.toLong())
        }
    }
}

private const val MAX_COMMENT_LENGTH = 500

private fun String.takeByCodePoints(max: Int): String {
    if (codePointCount(0, length) <= max) return this
    return substring(0, offsetByCodePoints(0, max))
}
