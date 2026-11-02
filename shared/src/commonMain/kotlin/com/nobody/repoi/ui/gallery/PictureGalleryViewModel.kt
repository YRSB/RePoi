package com.nobody.repoi.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.model.PictureRelateItem
import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.platform.saveImageBytesToGallery
import com.nobody.repoi.util.CdnUrlUtils
import com.nobody.repoi.util.CollectInvalidationBus
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
 * 图片画廊列表状态（收藏-图片 路由自加载）。
 *
 * @param items 已加载的图片收藏列表（与收藏页同一排序，供横向画廊无限翻页）
 * @param initialIndex 打开画廊时的初始下标（优先按 PictureGalleryNavParam.picId 精确定位）
 * @param hasMore 是否还有下一页
 * @param isLoading 首屏加载中（含为定位目标图片而连续翻页）
 * @param isLoadingMore 触底续拉中
 * @param errorMessage 首屏加载失败信息（非空且 items 为空时展示错误态）
 */
data class PictureGalleryListState(
    val items: List<UserCollectPicItem> = emptyList(),
    val initialIndex: Int = 0,
    val hasMore: Boolean = true,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    /**
     * 目标图片未在收藏列表中找到（典型场景：刚在画廊取消收藏后由收藏页再次点击旧项）。
     * 路由宿主据此提示并出栈，避免按旧下标打开另一张图。
     */
    val targetMissing: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 大图画廊 ViewModel。
 *
 * 对标原版 `PictureGalleryActivity`，同时作为收藏-图片 画廊路由的数据与操作宿主：
 * - 列表自加载：按收藏页同一接口/排序分页拉取（路由化后不再依赖收藏页组合内的临时状态）；
 * - 收藏星标 + 收藏数徽章（`api/collect/item/add` / `api/collect/del` / `api/collect/state`）；
 * - 关联条目（`api/pic/relate`，按 picId 进程内缓存，避免回看时列表消失）；
 * - 举报（`api/web-report/add`）；
 * - 原图渐进加载（`pic/n/` → `pic/raw/`）与「已是原图」提示。
 */
class PictureGalleryViewModel(
    private val collectRepository: CollectRepository,
    private val userRepository: UserRepository,
    private val collectInvalidationBus: CollectInvalidationBus,
    private val downloadClient: HttpClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PictureGalleryUiState())
    val uiState: StateFlow<PictureGalleryUiState> = _uiState.asStateFlow()

    private val _listState = MutableStateFlow(PictureGalleryListState())
    val listState: StateFlow<PictureGalleryListState> = _listState.asStateFlow()

    /** 当前页下标 */
    var currentIndex: Int = 0
        private set

    /** 列表装载任务（首屏/续拉共用，重新装载时取消旧任务） */
    private var listJob: Job? = null

    /** 列表装载上下文（重试复用） */
    private var listUserId: Long = 0L
    private var listOrder: String? = null
    private var listTargetPicId: Long = 0L
    private var listTargetIndex: Int = 0

    /** 已加载到第几页（续拉从 loadedPage + 1 开始） */
    private var loadedPage: Int = 0

    /** 切页后延迟回填收藏态的定时任务，用于取消上一次未完成的回填 */
    private var collectStateJob: Job? = null

    /** 关联条目加载任务 */
    private var relateJob: Job? = null

    /**
     * 关联条目缓存（按 picId）。
     *
     * 原版每次打开底部弹层都会重新请求（`PictureGalleryActivity.W()`），
     * 因此回到看过的图片时必须能再次展示；旧实现用 `relatedPicIds` 永久去重 + 切页清空，
     * 导致第二次进入同一图片时关联条目永久消失。
     */
    private val relateCache = mutableMapOf<Long, List<PictureRelateItem>>()

    /** 当前关联条目请求对应的 picId，用于丢弃切页后的迟到响应 */
    private var currentRelatePicId: Long = 0L

    // ==================== 列表装载（路由自加载） ====================

    /** 首屏装载请求标识（用于保证 Entry 重组/返回时只装载一次，不打断用户已翻到的页码） */
    private var initialRequestKey: String? = null

    /**
     * 幂等首屏装载：同一路由参数只在首次组合时装载。
     *
     * Nav3 从关联相册/手办详情返回、横竖屏旋转等场景都会重建 Entry 组合并重跑 `LaunchedEffect`，
     * 若每次都重新 [loadInitial]，会把用户已经翻到的画廊页码与已加载列表重置回初始图片。
     */
    fun ensureInitial(userId: Long, order: String?, targetPicId: Long, targetIndex: Int) {
        val key = "$userId|$targetPicId|${order.orEmpty()}"
        val state = _listState.value
        if (initialRequestKey == key && (state.isLoading || state.items.isNotEmpty())) return
        initialRequestKey = key
        loadInitial(userId, order, targetPicId, targetIndex)
    }

    /**
     * 首屏装载收藏-图片列表并定位到目标图片。
     *
     * 画廊从收藏页跳入时只携带「图片 id + 收藏页当前下标 + 排序档」，
     * 这里按收藏页同一接口/排序逐页加载：目标下标决定需要的页数（[PAGE_SIZE]），
     * 再按 picId 精确定位，避免分页去重造成下标漂移。
     *
     * @param userId 收藏列表归属用户 id
     * @param order 图片收藏列表排序档（null 回落到收藏时间）
     * @param targetPicId 目标图片 id
     * @param targetIndex 点击项在收藏页列表中的下标
     */
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
            val targetPage = (listTargetIndex / PAGE_SIZE + 1).coerceIn(1, MAX_LOCATE_PAGE)
            val loaded = mutableListOf<UserCollectPicItem>()
            var page = 1
            var lastBatchSize = 0
            var failure: String? = null
            while (page <= targetPage) {
                val result = userRepository.getUserCollectPicList(
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
                if (loaded.any { it.id == targetPicId } || batch.size < PAGE_SIZE) break
                page++
            }
            val distinct = loaded.distinctBy { it.id }
            val located = distinct.indexOfFirst { it.id == targetPicId }
            // 目标图片已不在收藏列表中：不要拿旧下标伪装成功（否则会打开另一张图）
            val targetMissing = targetPicId > 0L && located < 0 && distinct.isNotEmpty()
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

    /** 首屏加载失败后的重试 */
    fun retryList() {
        loadInitial(listUserId, listOrder, listTargetPicId, listTargetIndex)
    }

    /**
     * 画廊滑到接近末尾时续拉下一页（对齐原版「倒数第 4 张」触发语义）。
     */
    fun loadMore() {
        val snapshot = _listState.value
        if (snapshot.isLoading || snapshot.isLoadingMore || !snapshot.hasMore || snapshot.items.isEmpty()) return
        _listState.update { it.copy(isLoadingMore = true) }
        listJob = viewModelScope.launch {
            val nextPage = loadedPage + 1
            val result = userRepository.getUserCollectPicList(
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
                    // 保留当前列表与 hasMore，用户再次滑到末尾可重试
                    _listState.update { it.copy(isLoadingMore = false) }
                }
            )
        }
    }

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
            result.onSuccess {
                // 取消收藏成功：广播给仍存活的收藏页就地剔除，避免返回后残留脏项
                if (!nowCollected) {
                    collectInvalidationBus.notifyUncollected(item.id)
                }
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
     * 加载当前大图的关联条目（相册 / 手办 / 文章三类）。
     *
     * 原版每次打开底部弹层都会重新请求（`PictureGalleryActivity.W()`），因此这里按 picId 做进程内缓存：
     * 回到看过的图片时立即回填缓存，未命中才发起请求；请求失败不写缓存，下次切回可重试。
     */
    fun loadRelate(item: UserCollectPicItem) {
        val picId = item.id
        currentRelatePicId = picId
        if (picId <= 0L) {
            // TODO(原版对齐)：原版在图片 id 为 0 时改用 `path` 调 api/pic/relate。
            // 当前收藏图片列表项均带 id，暂不扩展接口；出现 id=0 时展示空关联而不是发起无效请求。
            _uiState.update { it.copy(relateItems = emptyList(), isRelateLoading = false) }
            return
        }
        relateCache[picId]?.let { cached ->
            _uiState.update { it.copy(relateItems = cached, isRelateLoading = false) }
            return
        }
        relateJob?.cancel()
        relateJob = viewModelScope.launch {
            _uiState.update { it.copy(relateItems = emptyList(), isRelateLoading = true) }
            collectRepository.getPictureRelate(picId)
                .onSuccess { list ->
                    relateCache[picId] = list
                    if (currentRelatePicId == picId) {
                        _uiState.update { it.copy(relateItems = list, isRelateLoading = false) }
                    }
                }
                .onFailure {
                    if (currentRelatePicId == picId) {
                        _uiState.update { it.copy(relateItems = emptyList(), isRelateLoading = false) }
                    }
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
        return (base ?: "hpoi_${com.nobody.repoi.platform.nowMillis()}") + ".jpg"
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

        /** 收藏-图片 列表分页大小（与收藏页 `UserCollectViewModel.PAGE_SIZE` 保持一致，保证下标可映射） */
        const val PAGE_SIZE = 20

        /** 首屏定位目标图片时最多加载的页数上限，避免异常下标导致连环请求 */
        const val MAX_LOCATE_PAGE = 50
    }
}
