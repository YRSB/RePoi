package com.nobody.poishelf.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.poishelf.data.remote.model.PictureRelateItem
import com.nobody.poishelf.data.remote.model.UserCollectPicItem
import com.nobody.poishelf.data.repository.CollectRepository
import com.nobody.poishelf.platform.saveImageBytesToGallery
import com.nobody.poishelf.util.CdnUrlUtils
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
import com.nobody.poishelf.data.remote.model.RelateItem
import com.nobody.poishelf.data.remote.model.PicItem
import com.nobody.poishelf.data.remote.model.CollectConstants

/**
 * 大图画廊 UI 状态。
 *
 * @param isCollected 当前大图是否已收藏（星标态）
 * @param collectCount 当前大图的收藏人数（顶栏徽章）
 * @param showRaw 是否已切到原图（`pic/raw/`）规格
 * @param isLongImage 当前大图是否为超长图（超出屏幕高度数倍，触发 P2 保护提示）
 * @param relateItems 关联条目（相册 / 手办 / 文章三类）
 * @param isRelateLoading 关联条目加载中
 * @param isDownloading 原图下载写入相册中
 * @param toastMessage 一次性轻提示（下载结果、举报结果等），消费后由 UI 调 [consumeMessage] 清空
 */
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

/**
 * 大图画廊 ViewModel。
 *
 * 对标原版 `PictureGalleryActivity`，承载收藏页大图的操作层：
 * - 收藏星标 + 收藏数徽章（`api/collect/item/add` / `api/collect/del` / `api/collect/state`）；
 * - 关联条目（`api/pic/relate`）；
 * - 举报（`api/web-report/add`）；
 * - 原图渐进加载（`pic/n/` → `pic/raw/`）与「已是原图」提示。
 *
 * 无限分页由宿主回调 `UserCollectViewModel.loadNextPage(PICTURE)` 驱动，本 VM 不持有列表，
 * 只负责当前页的操作态。
 */
class PictureGalleryViewModel(
    private val collectRepository: CollectRepository,
    private val downloadClient: HttpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PictureGalleryUiState())
    val uiState: StateFlow<PictureGalleryUiState> = _uiState.asStateFlow()

    /** 当前页下标 */
    var currentIndex: Int = 0
        private set

    /** 切页后延迟回填收藏态的定时任务，用于取消上一次未完成的回填 */
    private var collectStateJob: Job? = null

    /** 关联条目加载任务，按 picId 去重 */
    private var relateJob: Job? = null

    /** 已加载过关联条目的 picId 集合，避免同页重复请求 */
    private val relatedPicIds = mutableSetOf<Long>()

    /**
     * 切页：重置原图开关、重置超长图标记、重新回填收藏态与关联条目。
     *
     * 对齐原版 `PictureGalleryActivity.s0()` + `t0()`：切页后**延迟 500ms** 且确认
     * 仍停留在同一页时才调 `getCollectState` 回填星标（原版用 `Thread.sleep(500)` + 下标比对）。
     *
     * @param item 当前页对应的图片条目
     * @param index 当前页下标
     */
    fun onPageChanged(item: UserCollectPicItem, index: Int) {
        currentIndex = index
        _uiState.update {
            it.copy(
                isCollected = false,
                showRaw = false,
                isLongImage = false,
                relateItems = emptyList(),
                isRelateLoading = false
            )
        }
        scheduleCollectStateRefresh(item, index)
        loadRelate(item)
    }

    /**
     * 延迟 500ms 回填收藏态，期间若已切页则丢弃结果（原版 `t0()` 的下标比对语义）。
     */
    private fun scheduleCollectStateRefresh(item: UserCollectPicItem, index: Int) {
        collectStateJob?.cancel()
        collectStateJob = viewModelScope.launch {
            delay(COLLECT_STATE_REFRESH_DELAY_MS)
            if (currentIndex != index) return@launch
            collectRepository.getCollectState(item.id)
                .onSuccess { data ->
                    if (currentIndex != index) return@onSuccess
                    val collected = data?.isCollected == true
                    _uiState.update {
                        it.copy(
                            // 原版 u0()/v0()：星标取 collect/state 的 state，收藏人数取列表项自带的 collect
                            isCollected = collected,
                            collectCount = item.collect
                        )
                    }
                }
                .onFailure {
                    // 回填失败仅回退星标，收藏人数沿用列表项自带值，不打断浏览
                    _uiState.update { it.copy(isCollected = false, collectCount = item.collect) }
                }
        }
    }

    /**
     * 收藏 / 取消收藏：乐观更新 + 1.3 倍缩放动画（对齐原版 `ValueAnimator.ofFloat(1f,1.3f,1f)`）。
     *
     * ⚠️ 收藏 / 取消收藏的 nodeId 用 `UserCollectPicItem.id`（`pic-v2` 的扁平图片 id），
     * 原版 `PictureGalleryActivity` 同样取 `y0.w(pic, "id")`。
     *
     * @return 收藏态切换后的值，供 UI 触发缩放动画
     */
    fun toggleCollect(item: UserCollectPicItem): Boolean {
        val wasCollected = _uiState.value.isCollected
        val nowCollected = !wasCollected

        // 乐观更新：先翻转星标与徽章，失败时回滚
        _uiState.update {
            it.copy(
                isCollected = nowCollected,
                collectCount = (it.collectCount + if (nowCollected) 1 else -1).coerceAtLeast(0)
            )
        }

        viewModelScope.launch {
            val result = if (nowCollected) {
                collectRepository.collectItemAdd(item.id)
            } else {
                collectRepository.collectItemDel(item.id)
            }
            result.onFailure { error ->
                // 回滚到请求前状态
                _uiState.update {
                    it.copy(
                        isCollected = wasCollected,
                        collectCount = (it.collectCount + if (nowCollected) -1 else 1).coerceAtLeast(0),
                        toastMessage = error.message ?: "操作失败，请稍后重试"
                    )
                }
            }
        }
        return nowCollected
    }

    /**
     * 切换原图规格（`pic/n/` ↔ `pic/raw/`）。
     *
     * @return 切换后的值；`true` 表示当前已是原图规格（调用方据此提示「已是原图」并保持原状）
     */
    fun toggleRawSpec(): Boolean {
        val next = !_uiState.value.showRaw
        _uiState.update { it.copy(showRaw = next) }
        return next
    }

    /**
     * 标记当前大图为超长图（宽高比低于阈值即视为超长图）。
     *
     * P2 保护：不引入 SubsamplingScaleImageView，改为在 UI 层按屏幕高度夹取解码尺寸 +
     * 对极端长图给出「建议查看原图」提示，避免 Coil 一次性解码整张巨图导致 OOM。
     */
    fun markLongImage() {
        _uiState.update { it.copy(isLongImage = true) }
    }

    /**
     * 加载当前大图的关联条目（相册 / 手办 / 文章三类），同页只请求一次。
     */
    fun loadRelate(item: UserCollectPicItem) {
        val picId = item.id
        if (picId <= 0L || picId in relatedPicIds) return
        relatedPicIds.add(picId)
        relateJob?.cancel()
        relateJob = viewModelScope.launch {
            _uiState.update { it.copy(isRelateLoading = true) }
            collectRepository.getPictureRelate(picId)
                .onSuccess { list ->
                    _uiState.update { it.copy(relateItems = list, isRelateLoading = false) }
                }
                .onFailure {
                    relatedPicIds.remove(picId)
                    _uiState.update { it.copy(isRelateLoading = false) }
                }
        }
    }

    /**
     * 举报当前大图（`api/web-report/add`，type 固定 `picture_info`）。
     *
     * @param reason 用户填写的举报理由；空白时仓储层直接短路返回失败，此处会提示「请填写举报原因」
     */
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

    /**
     * 下载当前大图原图到系统相册（平台 expect 跨平台写入）。
     *
     * 原 Android 版走 `MediaStore` + `RELATIVE_PATH` 分区存储；
     * KMP 版经 `saveImageBytesToGallery` expect（android=MediaStore 语义，jvm=Pictures 目录），
     * 下载走命名 `"download"` Ktor client（无 BODY 日志、无业务参数——与原刻意隔离一致）。
     *
     * @param item 当前页对应的图片条目
     */
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
        return (base ?: "hpoi_${com.nobody.poishelf.platform.nowMillis()}") + ".jpg"
    }

    /** 下载开始：置 loading，由 UI 显示「正在保存原图…」遮罩 */
    private fun onDownloadStarted() {
        _uiState.update { it.copy(isDownloading = true) }
    }

    /** 下载结束：写轻提示并关闭 loading */
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

    /**
     * 消费一次性轻提示。
     */
    fun consumeMessage() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    /**
     * 当前大图展示用的 URL：[showRaw] 为 true 时走 `pic/raw/`，否则走 `pic/n/`。
     */
    fun currentDisplayUrl(item: UserCollectPicItem, showRaw: Boolean): String =
        if (showRaw) CdnUrlUtils.getRawPicUrl(item.actualUrl) else CdnUrlUtils.getPicNUrl(item.actualUrl)

    companion object {
        /** 切页后回填收藏态的延迟（原版 `PictureGalleryActivity.t0()` 的 500ms） */
        const val COLLECT_STATE_REFRESH_DELAY_MS = 500L

        /** 触发「倒数第 4 张」续拉的阈值，与原版 `i10 > list.length() - 4` 对齐 */
        const val PREFETCH_DISTANCE = 4
    }
}
