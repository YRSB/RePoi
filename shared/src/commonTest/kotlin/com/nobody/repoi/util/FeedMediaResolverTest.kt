package com.nobody.repoi.util

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.RelateData
import com.nobody.repoi.data.remote.model.RelateItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 动态媒体展示解析测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `FeedMediaResolverTest`（逐用例，JUnit4 → kotlin.test）。
 */
class FeedMediaResolverTest {

    @Test
    fun resolve_hobbyItemWithoutCover_fallsBackToDefaultPlaceholder() {
        val action = ActionItem(
            id = 100L,
            actionType = 1,
            relateItem = RelateItem(
                itemType = "hobby",
                cover = ""
            )
        )
        val info = action.resolveMediaDisplayInfo()

        assertTrue(info.shouldShowLeftCover)
        assertEquals("https://rfx.hpoi.net/gk/cover/n/none.jpg", info.coverUrl)
        assertEquals(listOf("https://rfx.hpoi.net/gk/cover/raw/none.jpg"), info.allRawUrls)
    }

    @Test
    fun resolve_hobbyItemWithCover_returnsCoverNAndRaw() {
        val action = ActionItem(
            id = 1L,
            actionType = 1,
            relateItem = RelateItem(
                itemType = "hobby",
                cover = "cover/s/figure1.jpg"
            )
        )
        val info = action.resolveMediaDisplayInfo()

        assertTrue(info.shouldShowLeftCover)
        assertEquals("https://rfx.hpoi.net/gk/cover/n/figure1.jpg", info.coverUrl)
        assertEquals(listOf("https://rfx.hpoi.net/gk/cover/raw/figure1.jpg"), info.allRawUrls)
    }

    @Test
    fun resolve_albumWithCover_returnsCoverSrAndRaw() {
        val action = ActionItem(
            id = 2L,
            actionType = 3,
            relateItem = RelateItem(
                itemType = "album",
                cover = "2026/album_cover.jpg"
            )
        )
        val info = action.resolveMediaDisplayInfo()

        assertTrue(info.shouldShowLeftCover)
        assertEquals("https://rfx.hpoi.net/gk/cover/sr/2026/album_cover.jpg", info.coverUrl)
        assertEquals(listOf("https://rfx.hpoi.net/gk/cover/raw/2026/album_cover.jpg"), info.allRawUrls)
    }

    @Test
    fun resolve_albumNoneJpg_marksAlbumNoCoverAndKeepsLeftCover() {
        val action = ActionItem(
            id = 3L,
            actionType = 3,
            relateItem = RelateItem(
                itemType = "album",
                cover = "none.jpg"
            )
        )
        val info = action.resolveMediaDisplayInfo()
        assertTrue(info.shouldShowLeftCover)
        assertEquals("", info.coverUrl)
        assertTrue(info.isAlbumNoCover)
        assertTrue(info.allRawUrls.isEmpty())
    }

    @Test
    fun resolve_albumWithoutCover_marksAlbumNoCoverAndKeepsLeftCover() {
        val actionWithBlank = ActionItem(
            id = 4L,
            actionType = 3,
            relateItem = RelateItem(
                itemType = "album",
                cover = "   "
            )
        )
        val infoBlank = actionWithBlank.resolveMediaDisplayInfo()
        assertTrue(infoBlank.shouldShowLeftCover)
        assertEquals("", infoBlank.coverUrl)
        assertTrue(infoBlank.isAlbumNoCover)
    }

    @Test
    fun resolve_pictureItem_returnsPicNAndRaw() {
        val action = ActionItem(
            id = 5L,
            actionType = 2,
            relateItem = RelateItem(
                path = "2026/user_pic.jpg"
            )
        )
        val info = action.resolveMediaDisplayInfo()

        assertTrue(info.shouldShowLeftCover)
        assertEquals("https://rfx.hpoi.net/gk/pic/n/2026/user_pic.jpg", info.coverUrl)
        assertEquals(listOf("https://rfx.hpoi.net/gk/pic/raw/2026/user_pic.jpg"), info.allRawUrls)
    }

    @Test
    fun resolve_singleDuplicateCover_deduplicatesExtraImages() {
        val action = ActionItem(
            id = 6L,
            actionType = 1,
            relateItem = RelateItem(
                itemType = "hobby",
                cover = "figure_test.jpg"
            ),
            relateData = listOf(
                RelateData(path = "figure_test.jpg")
            )
        )
        val info = action.resolveMediaDisplayInfo()

        assertTrue(info.shouldShowLeftCover)
        assertTrue(info.displayExtraImages.isEmpty())
        assertEquals(listOf("https://rfx.hpoi.net/gk/cover/raw/figure_test.jpg"), info.allRawUrls)
    }

    @Test
    fun resolve_extraImages_collectedIntoAllRawUrls() {
        val action = ActionItem(
            id = 7L,
            actionType = 1,
            relateItem = RelateItem(
                itemType = "hobby",
                cover = "main_cover.jpg"
            ),
            relateData = listOf(
                RelateData(path = "extra1.jpg"),
                RelateData(path = "extra2.jpg")
            )
        )
        val info = action.resolveMediaDisplayInfo()

        assertTrue(info.shouldShowLeftCover)
        assertEquals(listOf("extra1.jpg", "extra2.jpg"), info.displayExtraImages)
        assertEquals(
            listOf(
                "https://rfx.hpoi.net/gk/cover/raw/main_cover.jpg",
                "https://rfx.hpoi.net/gk/pic/raw/extra1.jpg",
                "https://rfx.hpoi.net/gk/pic/raw/extra2.jpg"
            ),
            info.allRawUrls
        )
    }

    @Test
    fun resolve_albumWithCategoryId60001_resolvesAsCoverSr() {
        // 用户真实生产动态样本：actionType=104, relateItem.categoryId=60001（相册分类区间）, 封面 png
        val action = ActionItem(
            id = 41122930L,
            actionType = 104,
            relateItem = RelateItem(
                categoryId = 60001,
                cover = "2026/11/4174eeb5bdff4c54a24f788845c1cd25.png"
            ),
            relateData = listOf(
                RelateData(path = "2026/11/photo1.jpg"),
                RelateData(path = "2026/11/photo2.jpg")
            )
        )
        val info = action.resolveMediaDisplayInfo()

        assertTrue(info.shouldShowLeftCover)
        // 关键断言：相册必须解析为 cover/sr/，严禁走 cover/n/ 导致 CDN 404！
        assertEquals("https://rfx.hpoi.net/gk/cover/sr/2026/11/4174eeb5bdff4c54a24f788845c1cd25.png", info.coverUrl)
        assertEquals(listOf("2026/11/photo1.jpg", "2026/11/photo2.jpg"), info.displayExtraImages)
        assertEquals(
            listOf(
                "https://rfx.hpoi.net/gk/cover/raw/2026/11/4174eeb5bdff4c54a24f788845c1cd25.png",
                "https://rfx.hpoi.net/gk/pic/raw/2026/11/photo1.jpg",
                "https://rfx.hpoi.net/gk/pic/raw/2026/11/photo2.jpg"
            ),
            info.allRawUrls
        )
    }

    @Test
    fun resolve_noCoverWithExtraImages_fallsBackToFirstExtraAsPicN() {
        val action = ActionItem(
            id = 8L,
            actionType = 1,
            relateItem = RelateItem(
                itemType = "hobby"
            ),
            relateData = listOf(
                RelateData(path = "2026/first_extra.jpg"),
                RelateData(path = "2026/second_extra.jpg")
            )
        )
        val info = action.resolveMediaDisplayInfo()

        assertTrue(info.shouldShowLeftCover)
        assertEquals("https://rfx.hpoi.net/gk/pic/n/2026/first_extra.jpg", info.coverUrl)
        assertEquals(listOf("2026/second_extra.jpg"), info.displayExtraImages)
        assertEquals(
            listOf(
                "https://rfx.hpoi.net/gk/pic/raw/2026/first_extra.jpg",
                "https://rfx.hpoi.net/gk/pic/raw/2026/second_extra.jpg"
            ),
            info.allRawUrls
        )
    }

    @Test
    fun resolve_partialDuplicateExtraImages_filtersCoverFileName() {
        val action = ActionItem(
            id = 9L,
            actionType = 1,
            relateItem = RelateItem(
                itemType = "hobby",
                cover = "main_cover.jpg"
            ),
            relateData = listOf(
                RelateData(path = "main_cover.jpg"),
                RelateData(path = "extra_other.jpg")
            )
        )
        val info = action.resolveMediaDisplayInfo()

        assertTrue(info.shouldShowLeftCover)
        assertEquals("https://rfx.hpoi.net/gk/cover/n/main_cover.jpg", info.coverUrl)
        assertEquals(listOf("extra_other.jpg"), info.displayExtraImages)
        assertEquals(
            listOf(
                "https://rfx.hpoi.net/gk/cover/raw/main_cover.jpg",
                "https://rfx.hpoi.net/gk/pic/raw/extra_other.jpg"
            ),
            info.allRawUrls
        )
    }
}
