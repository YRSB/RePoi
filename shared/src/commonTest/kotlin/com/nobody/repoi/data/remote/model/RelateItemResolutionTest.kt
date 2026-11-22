package com.nobody.repoi.data.remote.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelateItemResolutionTest {

    @Test
    fun relateItem_whenAlbumWithFigureItemId_resolvesActualAlbumIdCorrectly() {

        val albumRelate = RelateItem(
            id = 38307193L,
            itemId = 388930L,
            itemType = "album",
            nameCN = "【萝莉不推荐】露露姆 毛衣",
            categoryId = 60001
        )

        assertTrue(albumRelate.isAlbum)
        assertEquals(38307193L, albumRelate.actualAlbumId)
        assertEquals(388930L, albumRelate.actualHobbyId)
        assertEquals(38307193L, albumRelate.actualId)
    }

    @Test
    fun relateItem_whenAlbumWithZeroId_fallsBackToItemId() {
        val albumRelate = RelateItem(
            id = 0L,
            itemId = 38307193L,
            itemType = "album"
        )

        assertTrue(albumRelate.isAlbum)
        assertEquals(38307193L, albumRelate.actualAlbumId)
        assertEquals(38307193L, albumRelate.actualId)
    }

    @Test
    fun relateItem_whenFigure_resolvesActualHobbyIdCorrectly() {

        val figureRelate = RelateItem(
            id = 29490234L,
            itemId = 106044L,
            itemType = "hobby",
            nameCN = "86-不存在的地域- 弗拉迪蕾娜·米丽洁 兔女郎",
            categoryId = 1
        )

        assertFalse(figureRelate.isAlbum)
        assertEquals(106044L, figureRelate.actualHobbyId)
        assertEquals(106044L, figureRelate.actualId)
    }

    @Test
    fun relateItem_whenFigureWithZeroItemId_fallsBackToId() {
        val figureRelate = RelateItem(
            id = 29490234L,
            itemId = 0L,
            itemType = "hobby"
        )

        assertFalse(figureRelate.isAlbum)
        assertEquals(29490234L, figureRelate.actualHobbyId)
        assertEquals(29490234L, figureRelate.actualId)
    }

    @Test
    fun relateItem_whenCategoryIdIsAlbumRange_recognizesAsAlbum() {
        val relate = RelateItem(
            id = 778899L,
            itemId = 112233L,
            itemType = null,
            categoryId = 60001
        )

        assertTrue(relate.isAlbum)
        assertEquals(778899L, relate.actualAlbumId)
        assertEquals(778899L, relate.actualId)
    }

    @Test
    fun relateItem_caseInsensitiveAlbumItemType() {
        val relate = RelateItem(
            id = 556677L,
            itemId = 0L,
            itemType = "Album"
        )

        assertTrue(relate.isAlbum)
        assertEquals(556677L, relate.actualAlbumId)
    }
}
