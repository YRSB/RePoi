package com.nobody.repoi.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.PictureRelateItem
import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.platform.saveImageBytesToGallery
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaPipeline
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.nobody.repoi.data.remote.model.RelateItem
import com.nobody.repoi.data.remote.model.PicItem
import com.nobody.repoi.data.remote.model.CollectConstants

data class PictureGalleryUiState(
    val isCollected: Boolean = false,
    val collectCount: Int = 0,
    val showRaw: Boolean = false,
    val isLongImage: Boolean = false,
    val relateItems: List<PictureRelateItem> = emptyList(),
    val isRelateLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val toastMessage: String? = null
)

data class PictureGalleryListState(
    val items: List<UserCollectPicItem> = emptyList(),
    val initialIndex: Int = 0,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,

    val targetMissing: Boolean = false,
    val errorMessage: String? = null
)

class PictureGalleryViewModel(
    private val collectRepository: CollectRepository,
    private val downloadClient: HttpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PictureGalleryUiState())
    val uiState: StateFlow<PictureGalleryUiState> = _uiState.asStateFlow()

    private val _listState = MutableStateFlow(PictureGalleryListState())
    val listState: StateFlow<PictureGalleryListState> = _listState.asStateFlow()

    var currentIndex: Int = 0
        private set

    private var listJob: Job? = null

    private var listUserId: Long = 0L
    private var listOrder: String? = null
    private var listTargetPicId: Long = 0L
    private var listTargetIndex: Int = 0

    private var loadedPage: Int = 0

    private var collectStateJob: Job? = null

    private var relateJob: Job? = null

    private val relateCache = LinkedHashMap<String, List<PictureRelateItem>>()

    private var currentRelateKey: String = ""

    private var initialRequestKey: String? = null

    fun ensureInitial(userId: Long, order: String?, targetPicId: Long, targetIndex: Int) {
        val key = "$userId|$targetPicId|${order.orEmpty()}"
        val state = _listState.value
        if (initialRequestKey == key && (state.isLoading || state.items.isNotEmpty())) return
        initialRequestKey = key
        loadInitial(userId, order, targetPicId, targetIndex)
    }

    fun loadInitial(userId: Long, order: String?, targetPicId: Long, targetIndex: Int) {
        listJob?.cancel()
        listUserId = userId
        listOrder = order
        listTargetPicId = targetPicId
        listTargetIndex = targetIndex.coerceAtLeast(0)
        loadedPage = 0
        if (userId <= 0L) {
            _listState.value = PictureGalleryListState(errorMessage = "收藏列表加载失败：用户信息缺失")
            return
        }
        _listState.value = PictureGalleryListState(isLoading = true)
        listJob = viewModelScope.launch {
            val effectiveOrder = order ?: CollectConstants.ORDER_ADD_TIME
            val loaded = mutableListOf<UserCollectPicItem>()
            var page = 1
            var lastBatchSize = 0
            var failure: String? = null
            var reachedEnd = false
            while (page <= MAX_LOCATE_PAGE) {
                val result = collectRepository.getUserCollectPicList(
                    userId = userId,
                    order = effectiveOrder,
                    page = page,
                    pageSize = PAGE_SIZE
                )
                val batch = result.getOrNull()
                if (batch == null) {
                    failure = result.exceptionOrNull()?.message ?: "收藏列表加载失败"
                    break
                }
                lastBatchSize = batch.size
                loaded += batch
                loadedPage = page
                if (targetPicId <= 0L) break
                if (batch.size < PAGE_SIZE) reachedEnd = true
                if (loaded.any { it.id == targetPicId } || reachedEnd) break
                page++
            }
            val distinct = loaded.distinctBy { it.id }
            val located = distinct.indexOfFirst { it.id == targetPicId }
            if (targetPicId > 0L && located < 0 && !reachedEnd) {
                _listState.value = PictureGalleryListState(
                    isLoading = false,
                    errorMessage = failure ?: "图片定位失败，请重试"
                )
                return@launch
            }

            val targetMissing = targetPicId > 0L && located < 0 && reachedEnd && distinct.isNotEmpty()
            val initialIndex = when {
                located >= 0 -> located
                targetMissing -> 0
                distinct.isEmpty() -> 0
                else -> listTargetIndex.coerceIn(0, distinct.lastIndex)
            }
            _listState.value = PictureGalleryListState(
                items = distinct,
                initialIndex = initialIndex,
                hasMore = lastBatchSize >= PAGE_SIZE,
                isLoading = false,
                targetMissing = targetMissing,
                errorMessage = if (distinct.isEmpty()) failure ?: "暂无收藏图片" else null
            )
        }
    }

    fun retryList() {
        loadInitial(listUserId, listOrder, listTargetPicId, listTargetIndex)
    }

    fun loadMore() {
        val snapshot = _listState.value
        if (snapshot.isLoading || snapshot.isLoadingMore || !snapshot.hasMore || snapshot.items.isEmpty()) return
        _listState.update { it.copy(isLoadingMore = true) }
        listJob = viewModelScope.launch {
            val nextPage = loadedPage + 1
            val result = collectRepository.getUserCollectPicList(
                userId = listUserId,
                order = listOrder ?: CollectConstants.ORDER_ADD_TIME,
                page = nextPage,
                pageSize = PAGE_SIZE
            )
            result.fold(
                onSuccess = { batch ->
                    loadedPage = nextPage
                    _listState.update { current ->
                        current.copy(
                            items = (current.items + batch).distinctBy { it.id },
                            hasMore = batch.size >= PAGE_SIZE,
                            isLoadingMore = false
                        )
                    }
                },
                onFailure = {

                    _listState.update { it.copy(isLoadingMore = false) }
                }
            )
        }
    }

    fun onPageChanged(item: UserCollectPicItem, index: Int) {
        currentIndex = index
        _uiState.update {
            it.copy(
                isCollected = false,
                collectCount = item.collect,
                showRaw = false,
                isLongImage = false,
                relateItems = emptyList(),
                isRelateLoading = false
            )
        }
        scheduleCollectStateRefresh(item, index)
        loadRelate(item)
    }

    private fun scheduleCollectStateRefresh(item: UserCollectPicItem, index: Int) {
        collectStateJob?.cancel()
        collectStateJob = viewModelScope.launch {
            delay(COLLECT_STATE_REFRESH_DELAY_MS)
            if (currentIndex != index) return@launch
            collectRepository.refreshCollectState(item.id)
                .onSuccess { data ->
                    if (currentIndex != index) return@onSuccess
                    val collected = data?.isCollected == true
                    _uiState.update {
                        it.copy(

                            isCollected = collected,
                            collectCount = item.collect
                        )
                    }
                }
                .onFailure {

                    _uiState.update { it.copy(isCollected = false, collectCount = item.collect) }
                }
        }
    }

    fun toggleCollect(item: UserCollectPicItem): Boolean {
        val wasCollected = _uiState.value.isCollected
        val nowCollected = !wasCollected
        val pageIndex = currentIndex

        _uiState.update {
            it.copy(
                isCollected = nowCollected,
                collectCount = (it.collectCount + if (nowCollected) 1 else -1).coerceAtLeast(0)
            )
        }

        viewModelScope.launch {

            val result = collectRepository.toggleItemCollect(item.id, wasCollected)
            if (result.isFailure) {

                _uiState.update {
                    it.copy(
                        isCollected = wasCollected,
                        collectCount = (it.collectCount + if (nowCollected) -1 else 1).coerceAtLeast(0),
                        toastMessage = result.exceptionOrNull()?.message ?: "操作失败，请稍后重试"
                    )
                }
            } else {

                collectRepository.getCollectCount(item.id).onSuccess { count ->

                    if (currentIndex != pageIndex) return@onSuccess
                    _uiState.update { it.copy(collectCount = count.total) }
                }
            }
        }
        return nowCollected
    }

    fun toggleRawSpec(): Boolean {
        val next = !_uiState.value.showRaw
        _uiState.update { it.copy(showRaw = next) }
        return next
    }

    fun markLongImage() {
        _uiState.update { it.copy(isLongImage = true) }
    }

    fun loadRelate(item: UserCollectPicItem) {
        val targetId = item.actualId
        val targetPath = item.actualUrl
        val relateKey = when {
            targetId > 0L -> "id:$targetId"
            targetPath.isNotBlank() -> "path:$targetPath"
            else -> ""
        }
        currentRelateKey = relateKey
        if (relateKey.isEmpty()) {
            _uiState.update { it.copy(relateItems = emptyList(), isRelateLoading = false) }
            return
        }
        relateCache[relateKey]?.let { cached ->
            _uiState.update { it.copy(relateItems = cached, isRelateLoading = false) }
            return
        }
        relateJob?.cancel()
        relateJob = viewModelScope.launch {
            _uiState.update { it.copy(relateItems = emptyList(), isRelateLoading = true) }
            collectRepository.getPictureRelate(picId = targetId, path = targetPath)
                .onSuccess { list ->
                    putRelateCache(relateKey, list)
                    if (currentRelateKey == relateKey) {
                        _uiState.update { it.copy(relateItems = list, isRelateLoading = false) }
                    }
                }
                .onFailure {
                    if (currentRelateKey == relateKey) {
                        _uiState.update { it.copy(relateItems = emptyList(), isRelateLoading = false) }
                    }
                }
        }
    }

    private fun putRelateCache(key: String, list: List<PictureRelateItem>) {
        relateCache[key] = list
        while (relateCache.size > RELATE_CACHE_MAX) {
            val eldest = relateCache.keys.firstOrNull() ?: break
            relateCache.remove(eldest)
        }
    }

    fun submitReport(item: UserCollectPicItem, reason: String) {
        viewModelScope.launch {
            collectRepository.submitReport(
                nodeId = item.id,
                content = reason,
                type = CollectConstants.REPORT_TYPE_PICTURE
            )
                .onSuccess {
                    _uiState.update { it.copy(toastMessage = "举报已提交，感谢您的反馈") }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(toastMessage = error.message ?: "举报提交失败") }
                }
        }
    }

    fun downloadToGallery(
        item: UserCollectPicItem,
        showRaw: Boolean,
    ) {
        if (_uiState.value.isDownloading) return
        val url = currentDisplayUrl(item, showRaw)
        if (url.isBlank()) {
            _uiState.update { it.copy(toastMessage = "图片地址无效，无法保存") }
            return
        }
        onDownloadStarted()
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val bytes = downloadClient.get(url).bodyAsBytes()
                    if (bytes.isEmpty()) throw IllegalStateException("下载内容为空")
                    saveImageBytesToGallery(bytes, displayFileName(url, item.name)).getOrThrow()
                }
            }
            onDownloadFinished(result.isSuccess, result.exceptionOrNull()?.message)
        }
    }

    private fun displayFileName(url: String, displayName: String?): String {
        val base = (displayName?.takeIf { it.isNotBlank() } ?: url.substringAfterLast('/'))
            .substringBeforeLast('.')
            .takeIf { it.isNotBlank() }
        return (base ?: "hpoi_${com.nobody.repoi.platform.nowMillis()}") + ".jpg"
    }

    private fun onDownloadStarted() {
        _uiState.update { it.copy(isDownloading = true) }
    }

    private fun onDownloadFinished(success: Boolean, message: String? = null) {
        _uiState.update {
            it.copy(
                isDownloading = false,
                toastMessage = when {
                    success -> message ?: "已保存到相册"
                    else -> message ?: "保存失败"
                }
            )
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun currentDisplayUrl(item: UserCollectPicItem, showRaw: Boolean): String =
        if (showRaw) {
            MediaPipeline.Default.resolvePictureUrl(item.actualUrl, ImagePurpose.FULLSCREEN_RAW)
        } else {
            MediaPipeline.Default.resolvePictureUrl(item.actualUrl, ImagePurpose.CARD_STANDARD)
        }

    companion object {

        const val COLLECT_STATE_REFRESH_DELAY_MS = 500L

        const val PREFETCH_DISTANCE = 4

        const val PAGE_SIZE = 20

        const val MAX_LOCATE_PAGE = 50

        const val RELATE_CACHE_MAX = 32
    }
}
