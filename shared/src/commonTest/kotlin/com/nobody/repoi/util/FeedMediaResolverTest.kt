package com.nobody.repoi.util

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.RelateData
import com.nobody.repoi.data.remote.model.RelateItem
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class FeedMediaResolverTest {

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
    fun resolve_albumNoneJpg_showsOfficialPlaceholder() {
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
        assertEquals("https://rfx.hpoi.net/gk/cover/sr/none.jpg", info.coverUrl)
        assertFalse(info.isAlbumNoCover)
    }

    @Test
    fun resolve_albumWithoutCover_hidesLeftCover() {
        val actionWithBlank = ActionItem(
            id = 4L,
            actionType = 3,
            relateItem = RelateItem(
                itemType = "album",
                cover = "   "
            )
        )
        val infoBlank = actionWithBlank.resolveMediaDisplayInfo()
        assertFalse(infoBlank.shouldShowLeftCover)
        assertEquals("", infoBlank.coverUrl)
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
}