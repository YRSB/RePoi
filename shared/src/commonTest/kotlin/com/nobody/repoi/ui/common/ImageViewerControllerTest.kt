package com.nobody.repoi.ui.common

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 根部大图查看器控制器与状态恢复单元测试。
 */
class ImageViewerControllerTest {

    private val testSaverScope = SaverScope { true }

    @Test
    fun `初始状态下查看器不可见且数据为空`() {
        val state = ImageViewerStateHolder()

        assertFalse(state.isVisible)
        assertTrue(state.imageUrls.isEmpty())
        assertEquals(0, state.currentIndex)
        assertNull(state.onLoadMoreCallback)
    }

    @Test
    fun `调用 show 正确更新图片列表与当前下标`() {
        val state = ImageViewerStateHolder()
        val urls = listOf("https://example.com/1.jpg", "https://example.com/2.jpg", "https://example.com/3.jpg")
        var loadMoreCalled = false

        state.show(
            images = urls,
            initialIndex = 1,
            onLoadMore = { loadMoreCalled = true }
        )

        assertTrue(state.isVisible)
        assertEquals(urls, state.imageUrls)
        assertEquals(1, state.currentIndex)
        assertNotNull(state.onLoadMoreCallback)

        state.onLoadMoreCallback?.invoke()
        assertTrue(loadMoreCalled)
    }

    @Test
    fun `show 单图重载正确展开为单项列表`() {
        val state = ImageViewerStateHolder()
        state.show("https://example.com/cover.jpg")

        assertTrue(state.isVisible)
        assertEquals(listOf("https://example.com/cover.jpg"), state.imageUrls)
        assertEquals(0, state.currentIndex)
        assertNull(state.onLoadMoreCallback)
    }

    @Test
    fun `show 传入空列表或空白 URL 时自动忽略且不打开查看器`() {
        val state = ImageViewerStateHolder()

        state.show(emptyList())
        assertFalse(state.isVisible)

        state.show(listOf("", "   "))
        assertFalse(state.isVisible)
    }

    @Test
    fun `show 初始下标越界时自动夹取到合法区间`() {
        val state = ImageViewerStateHolder()
        val urls = listOf("https://example.com/1.jpg", "https://example.com/2.jpg")

        state.show(urls, initialIndex = 99)
        assertEquals(1, state.currentIndex)

        state.show(urls, initialIndex = -5)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun `查看器处于打开状态时 updateImages 成功追加更新数据`() {
        val state = ImageViewerStateHolder()
        val initialUrls = listOf("https://example.com/1.jpg")
        state.show(initialUrls, 0)

        val updatedUrls = listOf("https://example.com/1.jpg", "https://example.com/2.jpg")
        state.updateImages(updatedUrls)

        assertEquals(updatedUrls, state.imageUrls)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun `查看器未打开时 updateImages 安全空操作`() {
        val state = ImageViewerStateHolder()
        state.updateImages(listOf("https://example.com/1.jpg"))

        assertFalse(state.isVisible)
        assertTrue(state.imageUrls.isEmpty())
    }

    @Test
    fun `调用 dismiss 重置查看器状态并释放回调引用`() {
        val state = ImageViewerStateHolder()
        state.show(listOf("https://example.com/1.jpg"), 0, onLoadMore = {})

        state.dismiss()

        assertFalse(state.isVisible)
        assertTrue(state.imageUrls.isEmpty())
        assertEquals(0, state.currentIndex)
        assertNull(state.onLoadMoreCallback)
    }

    @Test
    fun `Saver 序列化与反序列化完整保留核心状态与浏览进度`() {
        val original = ImageViewerStateHolder(
            initialImages = listOf("https://example.com/a.jpg", "https://example.com/b.jpg"),
            initialIndex = 1
        )

        val savedState = with(ImageViewerStateHolder.Saver) {
            testSaverScope.save(original)
        }
        assertNotNull(savedState)

        val restored = ImageViewerStateHolder.Saver.restore(savedState!!)
        assertNotNull(restored)
        assertEquals(original.imageUrls, restored!!.imageUrls)
        assertEquals(original.currentIndex, restored.currentIndex)
        assertTrue(restored.isVisible)
        assertNull(restored.onLoadMoreCallback)
    }

    @Test
    fun `翻页时更新 currentIndex 并在 Saver 恢复后保持最新浏览进度`() {
        val state = ImageViewerStateHolder()
        val urls = listOf("https://example.com/1.jpg", "https://example.com/2.jpg", "https://example.com/3.jpg")
        state.show(urls, initialIndex = 0)

        // 模拟用户滑动翻页切到第 3 张图 (index = 2)
        state.currentIndex = 2

        val savedState = with(ImageViewerStateHolder.Saver) {
            testSaverScope.save(state)
        }
        val restored = ImageViewerStateHolder.Saver.restore(savedState!!)

        assertNotNull(restored)
        assertEquals(2, restored!!.currentIndex)
        assertEquals(urls, restored.imageUrls)
    }

    @Test
    fun `NoOpImageViewerController 默认实例调用安全空操作不崩溃`() {
        val defaultController = NoOpImageViewerController
        assertFalse(defaultController.isVisible)
        defaultController.show("https://example.com/1.jpg")
        defaultController.updateImages(listOf("https://example.com/1.jpg"))
        defaultController.dismiss()
        assertFalse(defaultController.isVisible)
    }
}
