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
 * 供顶层脚手架（如 `PoiShelfApp`）与大图弹窗宿主交互。
 */
@Stable
class ImageViewerStateHolder internal constructor(
    initialImages: List<String> = emptyList(),
    initialIndex: Int = 0
) : ImageViewerController {

    var imageUrls by mutableStateOf(initialImages)
        internal set

    var currentIndex by mutableIntStateOf(initialIndex)
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

    override fun updateImages(images: List<String>) {
        if (!isVisible) return
        val validImages = images.filter { it.isNotBlank() }
        if (validImages.isNotEmpty() && validImages != imageUrls) {
            imageUrls = validImages
        }
    }

    override fun dismiss() {
        imageUrls = emptyList()
        currentIndex = 0
        onLoadMoreCallback = null
    }

    companion object {
        val Saver: Saver<ImageViewerStateHolder, Any> = listSaver(
            save = { holder ->
                listOf(ArrayList(holder.imageUrls), holder.currentIndex)
            },
            restore = { list ->
                @Suppress("UNCHECKED_CAST")
                val images = list[0] as List<String>
                val index = list[1] as Int
                ImageViewerStateHolder(initialImages = images, initialIndex = index)
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
