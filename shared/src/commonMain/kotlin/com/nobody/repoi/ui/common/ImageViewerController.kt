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
import com.nobody.repoi.ui.navigation.ImageViewerNavKey
import com.nobody.repoi.ui.navigation.RePoiNavigator
import com.nobody.repoi.ui.navigation.navigateToImageViewer

@Stable
interface ImageViewerController {

    val isVisible: Boolean

    fun show(
        images: List<String>,
        initialIndex: Int = 0,
        onLoadMore: (() -> Unit)? = null
    )

    fun show(image: String) = show(listOf(image), 0, null)

    fun updateImages(images: List<String>)

    fun dismiss()
}

internal val NoOpImageViewerController = object : ImageViewerController {
    override val isVisible: Boolean = false
    override fun show(images: List<String>, initialIndex: Int, onLoadMore: (() -> Unit)?) {}
    override fun updateImages(images: List<String>) {}
    override fun dismiss() {}
}

val LocalImageViewer = staticCompositionLocalOf<ImageViewerController> {
    NoOpImageViewerController
}

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

    var onShowInNavigator: ((images: List<String>, initialIndex: Int) -> Unit)? = null

    var onDismissInNavigator: (() -> Unit)? = null

    override val isVisible: Boolean
        get() = imageUrls.isNotEmpty()

    override fun show(images: List<String>, initialIndex: Int, onLoadMore: (() -> Unit)?) {
        val validImages = images.filter { it.isNotBlank() }
        if (validImages.isEmpty()) return
        imageUrls = validImages
        val safeIndex = initialIndex.coerceIn(0, validImages.size - 1)
        currentIndex = safeIndex
        onLoadMoreCallback = onLoadMore
        onShowInNavigator?.invoke(validImages, safeIndex)
    }

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
        val wasVisible = isVisible
        imageUrls = emptyList()
        currentIndex = 0
        onLoadMoreCallback = null
        if (wasVisible) {
            onDismissInNavigator?.invoke()
        }
    }

    internal fun resetInternal() {
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

@Composable
fun rememberImageViewerState(
    navigator: RePoiNavigator
): ImageViewerStateHolder {
    val holder = rememberSaveable(saver = ImageViewerStateHolder.Saver) {
        ImageViewerStateHolder()
    }
    holder.onShowInNavigator = { images, initialIndex ->
        if (navigator.currentBackStack.lastOrNull() !is ImageViewerNavKey) {
            navigator.navigateToImageViewer(images, initialIndex)
        }
    }
    holder.onDismissInNavigator = {
        if (navigator.currentBackStack.lastOrNull() is ImageViewerNavKey) {
            navigator.pop()
        }
    }
    return holder
}
