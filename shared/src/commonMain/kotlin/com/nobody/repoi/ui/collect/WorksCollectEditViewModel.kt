package com.nobody.repoi.ui.collect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.WorksCollectData
import com.nobody.repoi.data.repository.CollectRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 作品收藏编辑页导航参数（原版 `CollectWorksActivity` 的 intent extra）。
 *
 * ⚠️ 路由键 `WorksCollectEditNavKey` 需在 `ui/navigation/NavRoutes.kt` 补齐
 * （该文件不在本页写入范围，由宿主统一接线）。
 */
@kotlinx.serialization.Serializable
data class WorksCollectEditNavParam(
    /** 作品条目 ID */
    val worksId: Long,
    /** 条目分类 ID，`10200` 为游戏（三态文案变体），默认沿用原版兜底值 10000 */
    val categoryId: Int = DEFAULT_WORKS_CATEGORY_ID,
    val initialName: String? = null,
    val initialCover: String? = null
)

/** 原版 `CollectWorksActivity.java:532` 的 `categoryId` 兜底值 */
const val DEFAULT_WORKS_CATEGORY_ID = 10000

/** 游戏分类：三态文案变体为「想玩 / 在玩 / 玩过」 */
const val GAME_WORKS_CATEGORY_ID = 10200

/** 单个标签字数上限（原版 `CollectWorksActivity.java:420`：`tag.length > 8` 直接拒绝） */
const val MAX_WORKS_TAG_LENGTH = 8

/** 单个作品标签总数上限（原版 `CollectWorksActivity.java:427`） */
const val MAX_WORKS_TAG_COUNT = 15

/** 简评上限（原版后端口径，与手办收藏编辑页一致） */
const val MAX_WORKS_COMMENT_LENGTH = 500

/** 作品收藏编辑页 UI 状态机。 */
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
    /** 保存成功（宿主据此回传结果刷新作品详情页） */
    val saveSuccess: Boolean = false,
    /** 取消收藏成功 */
    val deleteSuccess: Boolean = false,

    /** 是否已收藏：决定顶栏标题（「编辑收藏」/「收藏作品」）与取消收藏入口 */
    val isAlreadyCollected: Boolean = false,
    /** 是否允许「取消收藏」：服务端未收藏时必须隐藏（原版 `CollectWorksActivity.java:469-474` 的 `a0()`） */
    val hasDelete: Boolean = false,

    /** 三档收藏状态：1 想看 / 2 在看 / 3 看过 */
    val selectedState: Int = WorksStateOption.WANT,
    /** 官方标签候选池（`api/user/glean/textTag/get`） */
    val officialTags: List<String> = emptyList(),
    /** 已选标签 */
    val myTags: List<String> = emptyList(),
    val comment: String = "",

    val showDeleteConfirmDialog: Boolean = false,
    val showAddTagDialog: Boolean = false,
    val tagToDelete: String? = null
) {
    /** 简评字数（按码点计数，与输入截断口径一致） */
    val commentLength: Int get() = comment.codePointCount(0, comment.length)

    /** 是否为游戏分类（三态文案变体） */
    val isGameVariant: Boolean get() = categoryId == GAME_WORKS_CATEGORY_ID

    /** 尚未添加官方标签池中与自己重复的候选 */
    val selectableOfficialTags: List<String>
        get() = officialTags.filterNot { it in myTags }

    /**
     * 三档状态文案。`categoryId == 10200`（游戏）时变体为「想玩 / 在玩 / 玩过」。
     */
    fun stateLabel(state: Int): String = when (state) {
        WorksStateOption.WANT -> if (isGameVariant) "想玩" else "想看"
        WorksStateOption.WATCHING -> if (isGameVariant) "在玩" else "在看"
        WorksStateOption.WATCHED -> if (isGameVariant) "玩过" else "看过"
        else -> ""
    }
}

/**
 * 作品收藏编辑页 ViewModel。
 *
 * 对标原版 `net.hpoi.ui.collect.CollectWorksActivity` 及其 `CollectWorksViewModel`：
 * 整链走 `api/works/glean/get|add|del` + `api/user/glean/textTag/get`，
 * 与周边收藏（`api/collect` 系列接口）**完全是两套接口**。
 */
class WorksCollectEditViewModel(
    private val collectRepository: CollectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorksCollectEditUiState())
    val uiState: StateFlow<WorksCollectEditUiState> = _uiState.asStateFlow()

    private var initJob: Job? = null

    /**
     * 初始化页面数据：并发拉取「当前作品收藏详情」与「官方标签候选池」。
     *
     * @param force 为 true 时无视去重门控强制重拉（加载失败后重试用）。
     */
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
            // 分类可能在不同入口下不同，命中同一条目时仍同步一次
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

            // 1. 官方标签池（作品页专属池，与手办 `api/collect/tag` 不同）
            launch {
                collectRepository.getWorksCollectTags().onSuccess { tags ->
                    _uiState.update { it.copy(officialTags = tags) }
                }
            }

            // 2. 当前收藏详情
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

    /**
     * 应用服务端返回的收藏详情。
     *
     * ⚠️ 原版 `CollectWorksActivity.java:351-353`：响应为空对象时直接隐藏删除入口，
     * 因此 `hasDelete` 只在拿到有效收藏态时才为 true。
     */
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

    /** 切换三档收藏状态。 */
    fun selectState(state: Int) {
        if (!WorksStateOption.isValid(state)) return
        _uiState.update { it.copy(selectedState = state) }
    }

    /** 简评选填，按码点截断到 500 字。 */
    fun setComment(comment: String) {
        _uiState.update { it.copy(comment = comment.takeByCodePoints(MAX_WORKS_COMMENT_LENGTH)) }
    }

    /**
     * 添加标签（官方标签点击与自定义标签输入共用同一入口）。
     *
     * 校验完全对齐原版 `CollectWorksActivity.java:419-446` 的 `W(tag)`：
     * - 长度必须在 1~8 字；
     * - 总数不得超过 15 个，超限提示「单个作品最多设置15个标签」；
     * - 重复标签提示「标签已存在」并拒绝。
     *
     * @return 是否添加成功
     */
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

    /** 触发「删除标签」二次确认（原版 `CollectWorksActivity.java:464-466` 的 `Z(str)`）。 */
    fun requestRemoveTag(tag: String) {
        _uiState.update { it.copy(tagToDelete = tag) }
    }

    /** 确认删除指定标签（原版 `CollectWorksActivity$c.a`）。 */
    fun confirmRemoveTag() {
        _uiState.update { current ->
            val target = current.tagToDelete
            current.copy(
                myTags = if (target != null) current.myTags.filterNot { it == target } else current.myTags,
                tagToDelete = null
            )
        }
    }

    /** 放弃删除标签（关闭二次确认）。 */
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

    /** 保存作品收藏（`api/works/glean/add`）。 */
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
            collectRepository.saveWorksCollect(
                worksId = fresh.worksId,
                state = fresh.selectedState,
                tagStr = fresh.myTags.joinToString(","),
                comment = fresh.comment.trim()
            ).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isSaving = false, saveSuccess = true, isAlreadyCollected = true, hasDelete = true)
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = err.message ?: "保存失败") }
                }
            )
        }
    }

    /** 取消作品收藏（`api/works/glean/del`）。 */
    fun deleteCollect() {
        val snapshot = _uiState.value
        if (snapshot.isDeleting || snapshot.worksId <= 0L) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isDeleting = true, showDeleteConfirmDialog = false, errorMessage = null)
            }
            collectRepository.deleteWorksCollect(snapshot.worksId).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(isDeleting = false, deleteSuccess = true, isAlreadyCollected = false, hasDelete = false)
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isDeleting = false, errorMessage = err.message ?: "取消收藏失败") }
                }
            )
        }
    }
}

/** 按码点截断，避免 Emoji 代理对被切半。 */
private fun String.takeByCodePoints(max: Int): String {
    if (codePointCount(0, length) <= max) return this
    return substring(0, offsetByCodePoints(0, max))
}
