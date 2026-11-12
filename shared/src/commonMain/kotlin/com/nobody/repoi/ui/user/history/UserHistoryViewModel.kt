package com.nobody.repoi.ui.user.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.UserHistoryItem
import com.nobody.repoi.data.repository.CoverFreshnessRepository
import com.nobody.repoi.data.repository.UserHistoryRepository
import com.nobody.repoi.util.paging.PagedListLoader
import com.nobody.repoi.util.paging.pagedListLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class UserHistoryViewModel(
    private val userHistoryRepository: UserHistoryRepository,
    private val coverFreshnessRepository: CoverFreshnessRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserHistoryUiState())
    val uiState: StateFlow<UserHistoryUiState> = _uiState.asStateFlow()

    /**
     * 封面修复闸门：列表接口给的 `cover` 是写入快照，服务端重新落盘后必然 404，
     * 而详情接口每次都会返回当前真实封面。失败才修复，且同时在途最多 [COVER_REPAIR_CONCURRENCY] 条，
     * 正常滚动与翻页零额外请求。
     */
    private val coverRepairGate = Semaphore(COVER_REPAIR_CONCURRENCY)

    /**
     * 单条实体的本会话**失败**次数（键为 `type:id`），防止反复失败时形成请求风暴。
     * 修复成功即清零：成功既不产生新请求，也不该占用失败额度。
     */
    private val coverRepairAttempts = mutableMapOf<String, Int>()

    private val hobbyLoader: PagedListLoader<UserHistoryItem> = pagedListLoader(
        scope = viewModelScope,
        pageSize = 10,
        keySelector = { it.id }
    ) { page, _ ->
        userHistoryRepository.getUserHistoryList(UserHistoryCategory.HOBBY.itemType, page)
    }

    private val albumLoader: PagedListLoader<UserHistoryItem> = pagedListLoader(
        scope = viewModelScope,
        pageSize = 10,
        keySelector = { it.id }
    ) { page, _ ->
        userHistoryRepository.getUserHistoryList(UserHistoryCategory.ALBUM.itemType, page)
    }

    init {
        viewModelScope.launch {
            hobbyLoader.state.collect { listState ->
                updateTabState(UserHistoryCategory.HOBBY) { tab ->
                    tab.copy(
                        items = listState.items,
                        groups = groupHistoryItemsByDay(listState.items),
                        page = listState.page,
                        isLoading = listState.isLoading,
                        isRefreshing = listState.isRefreshing,
                        isLoadingMore = listState.isLoadingMore,
                        hasMore = listState.hasMore,
                        errorMessage = listState.errorMessage,
                        loadMoreError = listState.loadMoreError
                    )
                }
            }
        }
        viewModelScope.launch {
            albumLoader.state.collect { listState ->
                updateTabState(UserHistoryCategory.ALBUM) { tab ->
                    tab.copy(
                        items = listState.items,
                        groups = groupHistoryItemsByDay(listState.items),
                        page = listState.page,
                        isLoading = listState.isLoading,
                        isRefreshing = listState.isRefreshing,
                        isLoadingMore = listState.isLoadingMore,
                        hasMore = listState.hasMore,
                        errorMessage = listState.errorMessage,
                        loadMoreError = listState.loadMoreError
                    )
                }
            }
        }
        hobbyLoader.loadInitial()
    }

    /**
     * 切换历史分类 Tab。
     */
    fun selectCategory(category: UserHistoryCategory) {
        if (_uiState.value.selectedCategory == category) return
        _uiState.update { it.copy(selectedCategory = category) }

        val targetState = if (category == UserHistoryCategory.HOBBY) {
            _uiState.value.hobbyState
        } else {
            _uiState.value.albumState
        }

        if (targetState.items.isEmpty() && !targetState.isLoading) {
            val loader = if (category == UserHistoryCategory.HOBBY) hobbyLoader else albumLoader
            loader.loadInitial()
        }
    }

    /**
     * 下拉刷新当前或指定分类。
     */
    fun refresh(category: UserHistoryCategory = _uiState.value.selectedCategory) {
        val loader = if (category == UserHistoryCategory.HOBBY) hobbyLoader else albumLoader
        loader.refresh()
    }

    /**
     * 加载更多下一页历史数据。
     */
    fun loadMore(category: UserHistoryCategory = _uiState.value.selectedCategory) {
        val loader = if (category == UserHistoryCategory.HOBBY) hobbyLoader else albumLoader
        loader.loadMore()
    }

    /**
     * 进入或退出多选管理模式。
     */
    fun toggleManageMode(enable: Boolean? = null) {
        _uiState.update { current ->
            val nextMode = enable ?: !current.isManageMode
            current.copy(
                isManageMode = nextMode,
                selectedIds = if (nextMode) current.selectedIds else emptySet()
            )
        }
    }

    /**
     * 勾选或反选单个历史项。
     */
    fun toggleSelectItem(id: Long) {
        _uiState.update { current ->
            val newSelected = current.selectedIds.toMutableSet()
            if (newSelected.contains(id)) {
                newSelected.remove(id)
            } else {
                newSelected.add(id)
            }
            current.copy(
                isManageMode = true,
                selectedIds = newSelected
            )
        }
    }

    /**
     * 全选或取消全选当前 Tab 的所有历史项。
     */
    fun toggleSelectAll() {
        _uiState.update { current ->
            val currentItems = current.currentTabState.items
            val currentIds = currentItems.map { it.id }.toSet()
            val newSelected = if (current.isAllSelected) {
                current.selectedIds - currentIds
            } else {
                current.selectedIds + currentIds
            }
            current.copy(selectedIds = newSelected)
        }
    }

    /**
     * 执行批量删除选中的历史足迹。
     */
    fun deleteSelected() {
        val idsToDelete = _uiState.value.selectedIds.toList()
        if (idsToDelete.isEmpty()) return

        _uiState.update { it.copy(isDeleting = true) }

        viewModelScope.launch {
            val result = userHistoryRepository.deleteUserHistory(idsToDelete)
            result.onSuccess {
                val deletedSet = idsToDelete.toSet()
                hobbyLoader.removeItem { it.id in deletedSet }
                albumLoader.removeItem { it.id in deletedSet }
                _uiState.update { current ->
                    current.copy(
                        selectedIds = emptySet(),
                        isManageMode = false,
                        isDeleting = false,
                        userMessage = "已成功删除 ${idsToDelete.size} 条浏览历史"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        userMessage = err.message ?: "删除失败，请稍后重试"
                    )
                }
            }
        }
    }

    /**
     * 消费一次性操作反馈提示。
     */
    fun consumeUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    /**
     * 封面加载失败回调：按条回源修复历史快照里的过期封面路径。
     *
     * 为什么需要它：`api/user/read/item/get` 返回的 `cover` 是写入历史那一刻的快照，服务端重新
     * 落盘封面后旧路径必然 404；而详情接口每次都会给出当前真实封面。因此只在图片真的加载失败时
     * 按条补一次，绝不整页刷新（N 条历史 = N 个详情请求的代价不可接受）。
     *
     * @param rowId 历史记录行主键（用于精确替换列表项）
     * @param detailId 业务实体 ID（手办为 `itemId`，相册为 `albumId`）
     * @param category 条目所属分类
     */
    fun onCoverLoadFailed(rowId: Long, detailId: Long, category: UserHistoryCategory) {
        if (detailId <= 0L) return
        val entityType = category.toCoverEntityType()
        val attemptKey = "${entityType.name}:$detailId"
        val attempts = coverRepairAttempts[attemptKey] ?: 0
        if (attempts >= MAX_COVER_REPAIR_ATTEMPTS) return
        coverRepairAttempts[attemptKey] = attempts + 1

        viewModelScope.launch {
            coverRepairGate.withPermit {
                val freshCover = coverFreshnessRepository.freshCover(detailId, entityType).getOrNull()
                if (freshCover.isNullOrBlank()) return@withPermit
                val loader = if (category == UserHistoryCategory.HOBBY) hobbyLoader else albumLoader
                // 只替换真正失败的那一行：copy 后 urlText 变化会让 PoiAsyncImage 自动重置失败态
                loader.updateItem(predicate = { it.id == rowId }) { item ->
                    item.copy(cover = freshCover)
                }
                // 成功修复不消耗失败额度：否则下拉刷新把服务端快照换回旧路径后，
                // 两次刷新就会把本会话的修复机会耗尽（刷新后的修复本可命中仓储缓存、零请求）。
                coverRepairAttempts.remove(attemptKey)
            }
        }
    }

    private fun updateTabState(category: UserHistoryCategory, transform: (HistoryTabState) -> HistoryTabState) {
        _uiState.update { current ->
            if (category == UserHistoryCategory.HOBBY) {
                current.copy(hobbyState = transform(current.hobbyState))
            } else {
                current.copy(albumState = transform(current.albumState))
            }
        }
    }

    private fun UserHistoryCategory.toCoverEntityType(): CoverFreshnessRepository.EntityType =
        when (this) {
            UserHistoryCategory.HOBBY -> CoverFreshnessRepository.EntityType.HOBBY
            UserHistoryCategory.ALBUM -> CoverFreshnessRepository.EntityType.ALBUM
        }

    private companion object {
        /** 同时在途的封面修复请求上限，避免整屏失效时瞬间打满详情接口。 */
        const val COVER_REPAIR_CONCURRENCY = 3

        /** 同一实体的本会话连续失败次数上限（成功修复会清零）；超限后保持破损图标，等下次进入页面重来。 */
        const val MAX_COVER_REPAIR_ATTEMPTS = 2
    }
}
