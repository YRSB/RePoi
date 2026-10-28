package com.nobody.poishelf.ui.collect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.poishelf.data.repository.CollectRepository
import com.nobody.poishelf.ui.navigation.CollectEditNavParam
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.nobody.poishelf.platform.DateTimeFormats
import com.nobody.poishelf.data.remote.model.CollectConstants

/**
 * 收藏管理与编辑页面 ViewModel。
 * 驱动状态单选切换、入库价格/途径/日期录入、打分与多选标签池联动。
 */
class CollectEditViewModel(
    private val collectRepository: CollectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectEditUiState())
    val uiState: StateFlow<CollectEditUiState> = _uiState.asStateFlow()

    private var initJob: Job? = null

    /**
     * 初始化页面数据，并发加载当前用户收藏状态、全站人数统计与热门推荐标签。
     */
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

            // 1. 并发请求全站统计人数与推荐标签
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

            // 2. 拉取当前条目的用户收藏详情
            val stateResult = collectRepository.getCollectState(resolvedNodeId)
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

    // 初始加载失败后重试，强制绕过相同条目去重门控
    fun retryLoad(param: CollectEditNavParam) {
        initData(param, force = true)
    }

    /**
     * 切换 5 种互斥分类状态。
     */
    fun selectState(state: String) {
        _uiState.update { it.copy(selectedState = state) }
    }

    /**
     * 点击星星设置评分；若点击当前评分则反选重置为 0。
     */
    fun setRating(rating: Int) {
        _uiState.update {
            val newRating = if (it.rating == rating) 0 else rating
            it.copy(rating = newRating)
        }
    }

    // 已评分态需独立清除入口，避免依赖重复点击反选
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

    // 后端简评上限 500 字，粘贴超长时截断保留前 500 字
    fun setComment(comment: String) {
        _uiState.update { it.copy(comment = comment.takeByCodePoints(MAX_COMMENT_LENGTH)) }
    }

    /**
     * 添加自定义或推荐标签。
     */
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

    /**
     * 发起删除已有标签请求，弹出二次确认弹窗。
     */
    fun requestDeleteTag(tag: String) {
        _uiState.update { it.copy(tagToDelete = tag, showDeleteTagDialog = true) }
    }

    /**
     * 确认删除当前选中的标签。
     */
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

    /**
     * 取消/关闭删除标签二次确认弹窗。
     */
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
            val result = collectRepository.saveCollect(fresh.nodeId, collectData)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isSaving = false, errorMessage = err.message ?: "保存收藏失败")
                    }
                }
            )
        }
    }

    /**
     * 取消收藏当前条目。
     */
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
            val result = collectRepository.deleteCollect(fresh.nodeId, currentData)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isDeleting = false, deleteSuccess = true) }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isDeleting = false, errorMessage = err.message ?: "取消收藏失败")
                    }
                }
            )
        }
    }
}

private const val MAX_COMMENT_LENGTH = 500

// 按码点截断，避免 Emoji 代理对被切半
private fun String.takeByCodePoints(max: Int): String {
    if (codePointCount(0, length) <= max) return this
    return substring(0, offsetByCodePoints(0, max))
}
