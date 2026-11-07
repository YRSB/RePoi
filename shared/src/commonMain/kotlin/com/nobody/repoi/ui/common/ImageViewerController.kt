package com.nobody.repoi.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 全局全屏大图画廊操作控制器。
 * 向整棵 Compose 视图树暴露极简的展示、动态更新与关闭能力。
 */
@Stable
interface ImageViewerController {
    /** 当前查看器是否处于打开状态 */
    val isVisible: Boolean

    /**
     * 打开全屏大图画廊。
     * @param images 图片完整 URL 列表
     * @param initialIndex 初始定位下标
     * @param onLoadMore 翻页临近末尾时的续拉回调（可选）
     */
    fun show(
        images: List<String>,
        initialIndex: Int = 0,
        onLoadMore: (() -> Unit)? = null
    )

    /** 单图便捷展示重载 */
    fun show(image: String) = show(listOf(image), 0, null)

    /**
     * 动态同步最新的图片列表（适用于画廊正处于打开状态且列表后台静默续拉出新图时）。
     * 若当前查看器未打开，本操作自动空操作（No-op）。
     */
    fun updateImages(images: List<String>)

    /** 关闭大图查看器 */
    fun dismiss()
}

internal val NoOpImageViewerController = object : ImageViewerController {
    override val isVisible: Boolean = false
    override fun show(images: List<String>, initialIndex: Int, onLoadMore: (() -> Unit)?) {}
    override fun updateImages(images: List<String>) {}
    override fun dismiss() {}
}

/**
 * 根部大图控制器 CompositionLocal。
 * 默认提供空实现，确保 Preview 与单元测试无需挂载脚手架即可安全调用。
 */
val LocalImageViewer = staticCompositionLocalOf<ImageViewerController> {
    NoOpImageViewerController
}

/**
 * 大图宿主内部状态持有者，实现 [ImageViewerController] 接口，
 * 供顶层脚手架（如 `RePoiApp`）与大图弹窗宿主交互。
 */
@Stable
class ImageViewerStateHolder internal constructor(
    initialImages: List<String> = emptyList(),
    initialIndex: Int = 0
) : ImageViewerController {

    var imageUrls by mutableStateOf(initialImages)
        internal set

    var currentIndex by mutableIntStateOf(
        if (initialImages.isEmpty()) 0 else initialIndex.coerceIn(0, initialImages.size - 1)
    )
        internal set

    var onLoadMoreCallback by mutableStateOf<(() -> Unit)?>(null)
        internal set

    override val isVisible: Boolean
        get() = imageUrls.isNotEmpty()

    override fun show(images: List<String>, initialIndex: Int, onLoadMore: (() -> Unit)?) {
        val validImages = images.filter { it.isNotBlank() }
        if (validImages.isEmpty()) return
        imageUrls = validImages
        currentIndex = initialIndex.coerceIn(0, validImages.size - 1)
        onLoadMoreCallback = onLoadMore
    }

    /**
     * 动态同步图片列表。
     *
     * 以「当前图片 URL」为锚点重新定位：进程重建后 [Saver] 只恢复以当前图为中心的 50 项窗口，
     * 此时窗口下标是相对值，直接 clamp 会把全量列表中的另一张图当成当前图。
     * 若新列表在同一位置仍是同一张图（续拉追加的常规路径），保持原下标不变。
     */
    override fun updateImages(images: List<String>) {
        if (!isVisible) return
        val validImages = images.filter { it.isNotBlank() }
        if (validImages.isEmpty() || validImages == imageUrls) return
        val anchorUrl = imageUrls.getOrNull(currentIndex)
        imageUrls = validImages
        currentIndex = when {
            anchorUrl == null -> 0
            validImages.getOrNull(currentIndex) == anchorUrl -> currentIndex
            else -> validImages.indexOf(anchorUrl).takeIf { it >= 0 }
                ?: currentIndex.coerceIn(0, validImages.size - 1)
        }
    }

    override fun dismiss() {
        imageUrls = emptyList()
        currentIndex = 0
        onLoadMoreCallback = null
    }

    companion object {
        private const val MAX_SAVED_IMAGES = 50

        val Saver: Saver<ImageViewerStateHolder, Any> = listSaver(
            save = { holder ->
                val urls = holder.imageUrls
                val current = holder.currentIndex
                val (savedUrls, savedIndex) = if (urls.size > MAX_SAVED_IMAGES) {
                    // 仅截取以当前索引为中心的安全窗口（最多 50 项），防止 Binder TransactionTooLargeException
                    val halfWindow = MAX_SAVED_IMAGES / 2
                    val start = (current - halfWindow).coerceAtLeast(0)
                    val end = (start + MAX_SAVED_IMAGES).coerceAtMost(urls.size)
                    val actualStart = (end - MAX_SAVED_IMAGES).coerceAtLeast(0)
                    val sublist = urls.subList(actualStart, end)
                    val newIndex = (current - actualStart).coerceIn(0, (sublist.size - 1).coerceAtLeast(0))
                    sublist to newIndex
                } else {
                    val safeIndex = if (urls.isEmpty()) 0 else current.coerceIn(0, urls.size - 1)
                    urls to safeIndex
                }
                listOf(ArrayList(savedUrls), savedIndex)
            },
            restore = { list ->
                @Suppress("UNCHECKED_CAST")
                val images = list[0] as List<String>
                val index = list[1] as Int
                val safeIndex = if (images.isEmpty()) 0 else index.coerceIn(0, images.size - 1)
                ImageViewerStateHolder(initialImages = images, initialIndex = safeIndex)
            }
        )
    }
}

/**
 * 在 Compose 树中记住并恢复大图宿主状态。
 * 依托 [rememberSaveable] 保障屏幕旋转或折叠屏折叠展开重建 Activity 时大图状态与进度不丢失。
 */
@Composable
fun rememberImageViewerState(): ImageViewerStateHolder {
    return rememberSaveable(saver = ImageViewerStateHolder.Saver) {
        ImageViewerStateHolder()
    }
}
