package com.nobody.repoi.ui.collect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.WorksCollectData
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.domain.usecase.collect.DeleteWorksCollectUseCase
import com.nobody.repoi.domain.usecase.collect.SaveWorksCollectUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@kotlinx.serialization.Serializable
data class WorksCollectEditNavParam(

    val worksId: Long,

    val categoryId: Int = DEFAULT_WORKS_CATEGORY_ID,
    val initialName: String? = null,
    val initialCover: String? = null
)

const val DEFAULT_WORKS_CATEGORY_ID = 10000

const val GAME_WORKS_CATEGORY_ID = 10200

const val MAX_WORKS_TAG_LENGTH = 8

const val MAX_WORKS_TAG_COUNT = 15

const val MAX_WORKS_COMMENT_LENGTH = 500

data class WorksCollectEditUiState(
    val worksId: Long = 0L,
    val categoryId: Int = DEFAULT_WORKS_CATEGORY_ID,
    val initialName: String? = null,
    val initialCover: String? = null,

    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,

    val saveSuccess: Boolean = false,

    val deleteSuccess: Boolean = false,

    val isAlreadyCollected: Boolean = false,

    val hasDelete: Boolean = false,

    val selectedState: Int = WorksStateOption.WANT,

    val officialTags: List<String> = emptyList(),

    val myTags: List<String> = emptyList(),
    val comment: String = "",

    val showDeleteConfirmDialog: Boolean = false,
    val showAddTagDialog: Boolean = false,
    val tagToDelete: String? = null
) {

    val commentLength: Int get() = comment.codePointCount(0, comment.length)

    val isGameVariant: Boolean get() = categoryId == GAME_WORKS_CATEGORY_ID

    val selectableOfficialTags: List<String>
        get() = officialTags.filterNot { it in myTags }

    fun stateLabel(state: Int): String = when (state) {
        WorksStateOption.WANT -> if (isGameVariant) "想玩" else "想看"
        WorksStateOption.WATCHING -> if (isGameVariant) "在玩" else "在看"
        WorksStateOption.WATCHED -> if (isGameVariant) "玩过" else "看过"
        else -> ""
    }
}

class WorksCollectEditViewModel(
    private val collectRepository: CollectRepository,
    private val saveWorksCollectUseCase: SaveWorksCollectUseCase,
    private val deleteWorksCollectUseCase: DeleteWorksCollectUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorksCollectEditUiState())
    val uiState: StateFlow<WorksCollectEditUiState> = _uiState.asStateFlow()

    private var initJob: Job? = null

    fun initData(param: WorksCollectEditNavParam, force: Boolean = false) {
        if (param.worksId <= 0L) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    loadFailed = true,
                    errorMessage = "作品信息无效"
                )
            }
            return
        }
        if (!force && _uiState.value.worksId == param.worksId && !_uiState.value.isLoading) {

            if (_uiState.value.categoryId != param.categoryId) {
                _uiState.update { it.copy(categoryId = param.categoryId) }
            }
            return
        }

        initJob?.cancel()
        initJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    worksId = param.worksId,
                    categoryId = param.categoryId,
                    initialName = param.initialName,
                    initialCover = param.initialCover,
                    isLoading = true,
                    loadFailed = false,
                    errorMessage = null
                )
            }

            launch {
                collectRepository.getWorksCollectTags().onSuccess { tags ->
                    _uiState.update { it.copy(officialTags = tags) }
                }
            }

            collectRepository.getWorksCollect(param.worksId).fold(
                onSuccess = { data -> applyCollectData(data) },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            loadFailed = true,
                            errorMessage = err.message ?: "加载收藏信息失败"
                        )
                    }
                }
            )
        }
    }

    private fun applyCollectData(data: WorksCollectData?) {
        if (data == null || !data.isCollected) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    loadFailed = false,
                    isAlreadyCollected = false,
                    hasDelete = false,
                    selectedState = WorksStateOption.WANT,
                    myTags = emptyList(),
                    comment = ""
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                loadFailed = false,
                isAlreadyCollected = true,
                hasDelete = true,
                selectedState = if (WorksStateOption.isValid(data.state)) {
                    data.state
                } else {
                    WorksStateOption.WANT
                },
                myTags = data.tagValues,
                comment = data.comment.orEmpty()
            )
        }
    }

    fun selectState(state: Int) {
        if (!WorksStateOption.isValid(state)) return
        _uiState.update { it.copy(selectedState = state) }
    }

    fun setComment(comment: String) {
        _uiState.update { it.copy(comment = comment.takeByCodePoints(MAX_WORKS_COMMENT_LENGTH)) }
    }

    fun addTag(tag: String): Boolean {
        val trimmed = tag.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_WORKS_TAG_LENGTH) {
            _uiState.update { it.copy(errorMessage = "单个标签需为 1~$MAX_WORKS_TAG_LENGTH 字") }
            return false
        }
        val current = _uiState.value
        if (current.myTags.size >= MAX_WORKS_TAG_COUNT) {
            _uiState.update { it.copy(errorMessage = "单个作品最多设置15个标签") }
            return false
        }
        if (current.myTags.any { it.equals(trimmed, ignoreCase = true) }) {
            _uiState.update { it.copy(errorMessage = "标签已存在") }
            return false
        }
        _uiState.update { it.copy(myTags = it.myTags + trimmed, showAddTagDialog = false) }
        return true
    }

    fun requestRemoveTag(tag: String) {
        _uiState.update { it.copy(tagToDelete = tag) }
    }

    fun confirmRemoveTag() {
        _uiState.update { current ->
            val target = current.tagToDelete
            current.copy(
                myTags = if (target != null) current.myTags.filterNot { it == target } else current.myTags,
                tagToDelete = null
            )
        }
    }

    fun dismissRemoveTag() {
        _uiState.update { it.copy(tagToDelete = null) }
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
        if (snapshot.isSaving || snapshot.isDeleting || snapshot.worksId <= 0L) return
        if (snapshot.loadFailed) {
            _uiState.update { it.copy(errorMessage = "加载失败请重试") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val fresh = _uiState.value

            saveWorksCollectUseCase(
                worksId = fresh.worksId,
                state = fresh.selectedState,
                tags = fresh.myTags,
                comment = fresh.comment.trim()
            ).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isSaving = false, saveSuccess = true, isAlreadyCollected = true, hasDelete = true)
                    }
                    refreshCountSnapshot(fresh.worksId)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = err.message ?: "保存失败") }
                }
            )
        }
    }

    fun deleteCollect() {
        val snapshot = _uiState.value
        if (snapshot.isDeleting || snapshot.worksId <= 0L) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isDeleting = true, showDeleteConfirmDialog = false, errorMessage = null)
            }
            deleteWorksCollectUseCase(snapshot.worksId).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isDeleting = false, deleteSuccess = true, isAlreadyCollected = false, hasDelete = false)
                    }
                    refreshCountSnapshot(snapshot.worksId)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isDeleting = false, errorMessage = err.message ?: "取消收藏失败") }
                }
            )
        }
    }

    private suspend fun refreshCountSnapshot(worksId: Long) {
        collectRepository.getCollectCount(worksId).onSuccess { count ->
            collectRepository.updateCollectCountSnapshot(worksId, count.total.toLong())
        }
    }
}

private fun String.takeByCodePoints(max: Int): String {
    if (codePointCount(0, length) <= max) return this
    return substring(0, offsetByCodePoints(0, max))
}
