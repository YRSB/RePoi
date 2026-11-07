package com.nobody.repoi.ui.common

import androidx.compose.runtime.saveable.SaverScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageViewerStateHolderTest {

    private val testSaverScope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    @Test
    fun constructor_outOfBoundsIndex_clampsSafely() {
        val holderNegative = ImageViewerStateHolder(
            initialImages = listOf("url1", "url2"),
            initialIndex = -5
        )
        assertEquals(0, holderNegative.currentIndex)

        val holderOver = ImageViewerStateHolder(
            initialImages = listOf("url1", "url2"),
            initialIndex = 10
        )
        assertEquals(1, holderOver.currentIndex)

        val holderEmpty = ImageViewerStateHolder(
            initialImages = emptyList(),
            initialIndex = 10
        )
        assertEquals(0, holderEmpty.currentIndex)
    }

    @Test
    fun show_and_dismiss() {
        val holder = ImageViewerStateHolder()
        assertFalse(holder.isVisible)

        holder.show(listOf("a", "b", "c"), initialIndex = 2)
        assertTrue(holder.isVisible)
        assertEquals(3, holder.imageUrls.size)
        assertEquals(2, holder.currentIndex)

        holder.dismiss()
        assertFalse(holder.isVisible)
        assertEquals(0, holder.imageUrls.size)
        assertEquals(0, holder.currentIndex)
    }

    @Test
    fun updateImages_shrinksList_clampsCurrentIndex() {
        val holder = ImageViewerStateHolder()
        holder.show(listOf("a", "b", "c", "d", "e"), initialIndex = 4)
        assertEquals(4, holder.currentIndex)

        // 缩减列表至 2 项，currentIndex 应自动夹取至 1，防止 IndexOutOfBoundsException
        holder.updateImages(listOf("x", "y"))
        assertEquals(listOf("x", "y"), holder.imageUrls)
        assertEquals(1, holder.currentIndex)
    }

    @Test
    fun updateImages_appendToTail_keepsCurrentIndex() {
        val holder = ImageViewerStateHolder()
        holder.show(listOf("a", "b", "c"), initialIndex = 1)

        holder.updateImages(listOf("a", "b", "c", "d", "e"))

        assertEquals(1, holder.currentIndex)
        assertEquals("b", holder.imageUrls[holder.currentIndex])
    }

    @Test
    fun updateImages_afterRestoredWindow_reanchorsByUrl() {
        val images = (0..99).map { "url_$it" }
        val seeded = ImageViewerStateHolder()
        seeded.show(images, initialIndex = 50)
        val saved = ImageViewerStateHolder.Saver.run { testSaverScope.save(seeded) }
        val restored = ImageViewerStateHolder.Saver.restore(saved!!)

        assertTrue(restored != null)
        // 恢复后只有 50 项窗口，index=25 是相对值
        assertEquals(25, restored.currentIndex)

        restored.updateImages(images)

        // 必须按 URL 重锚回全量列表的绝对下标，否则会跳到另一张图
        assertEquals(50, restored.currentIndex)
        assertEquals("url_50", restored.imageUrls[restored.currentIndex])
    }

    @Test
    fun saver_listWithinLimit_savesAndRestoresExact() {
        val images = (1..20).map { "https://example.com/img_$it.jpg" }
        val holder = ImageViewerStateHolder()
        holder.show(images, initialIndex = 7)

        val saved = ImageViewerStateHolder.Saver.run { testSaverScope.save(holder) }
        val restored = ImageViewerStateHolder.Saver.restore(saved!!)

        assertTrue(restored != null)
        assertEquals(images, restored.imageUrls)
        assertEquals(7, restored.currentIndex)
        assertTrue(restored.isVisible)
    }

    @Test
    fun saver_oversizedList_clampsTo50Window_center() {
        // 100 项，当前在第 50 项（0-indexed）
        val images = (0..99).map { "url_$it" }
        val holder = ImageViewerStateHolder()
        holder.show(images, initialIndex = 50)

        val saved = ImageViewerStateHolder.Saver.run { testSaverScope.save(holder) }
        val restored = ImageViewerStateHolder.Saver.restore(saved!!)

        assertTrue(restored != null)
        assertEquals(50, restored.imageUrls.size)
        // 50 为中心，halfWindow = 25 -> 窗口范围 [25, 75)
        assertEquals("url_25", restored.imageUrls.first())
        assertEquals("url_74", restored.imageUrls.last())
        // 原 index 50 在新列表中的下标应为 25
        assertEquals(25, restored.currentIndex)
        assertEquals("url_50", restored.imageUrls[restored.currentIndex])
    }

    @Test
    fun saver_oversizedList_clampsTo50Window_start() {
        // 100 项，当前在第 2 项
        val images = (0..99).map { "url_$it" }
        val holder = ImageViewerStateHolder()
        holder.show(images, initialIndex = 2)

        val saved = ImageViewerStateHolder.Saver.run { testSaverScope.save(holder) }
        val restored = ImageViewerStateHolder.Saver.restore(saved!!)

        assertTrue(restored != null)
        assertEquals(50, restored.imageUrls.size)
        assertEquals("url_0", restored.imageUrls.first())
        assertEquals("url_49", restored.imageUrls.last())
        assertEquals(2, restored.currentIndex)
        assertEquals("url_2", restored.imageUrls[restored.currentIndex])
    }

    @Test
    fun saver_oversizedList_clampsTo50Window_end() {
        // 100 项，当前在最后第 99 项
        val images = (0..99).map { "url_$it" }
        val holder = ImageViewerStateHolder()
        holder.show(images, initialIndex = 99)

        val saved = ImageViewerStateHolder.Saver.run { testSaverScope.save(holder) }
        val restored = ImageViewerStateHolder.Saver.restore(saved!!)

        assertTrue(restored != null)
        assertEquals(50, restored.imageUrls.size)
        assertEquals("url_50", restored.imageUrls.first())
        assertEquals("url_99", restored.imageUrls.last())
        assertEquals(49, restored.currentIndex)
        assertEquals("url_99", restored.imageUrls[restored.currentIndex])
    }

    @Test
    fun saver_restoreCorruptedIndex_clampsSafely() {
        val images = listOf("a", "b", "c")
        // 模拟外部脏数据传入 index = 99
        val corruptState = listOf(ArrayList(images), 99)
        val restored = ImageViewerStateHolder.Saver.restore(corruptState)

        assertTrue(restored != null)
        assertEquals(2, restored.currentIndex)
    }

    @Test
    fun saver_emptyList_restoresGracefully() {
        val emptyState = listOf(ArrayList<String>(), 0)
        val restored = ImageViewerStateHolder.Saver.restore(emptyState)

        assertTrue(restored != null)
        assertEquals(0, restored.currentIndex)
        assertFalse(restored.isVisible)
    }
}
