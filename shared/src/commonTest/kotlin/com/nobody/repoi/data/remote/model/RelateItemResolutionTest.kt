package com.nobody.repoi.data.remote.model

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * 关联条目 [RelateItem] 目标 ID 解析与相册/手办类型判定单元测试。
 */
class RelateItemResolutionTest {

    @Test
    fun relateItem_whenAlbumWithFigureItemId_resolvesActualAlbumIdCorrectly() {
        // 场景：对相册评价的动态，relateItem 中 id 为相册 ID (38307193)，itemId 为关联手办 ID (388930)
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
        // 场景：手办条目动态，itemId 为条目 ID (106044)，id 为节点 ID (29490234)
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
