package com.nobody.repoi.ui.detail

import com.nobody.repoi.data.remote.model.PicItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HobbyDetailCoverFallbackTest {

    @Test
    fun returnsExplicitCoverWhenPresent() {
        val explicit = "2026/08/sample_cover.jpg"
        val pics = listOf(
            PicItem(id = 1L, path = "2026/08/official_1.jpg"),
            PicItem(id = 2L, path = "2026/08/official_2.jpg")
        )
        val result = resolveEffectiveCoverPath(explicit, pics)
        assertEquals("2026/08/sample_cover.jpg", result)
    }

    @Test
    fun fallsBackToFirstOfficialPicWhenExplicitCoverIsNull() {
        val pics = listOf(
            PicItem(id = 1L, path = "2026/08/official_1.jpg"),
            PicItem(id = 2L, path = "2026/08/official_2.jpg")
        )
        val result = resolveEffectiveCoverPath(null, pics)
        assertEquals("2026/08/official_1.jpg", result)
    }

    @Test
    fun fallsBackToFirstOfficialPicWhenExplicitCoverIsBlankOrNullLiteral() {
        val pics = listOf(
            PicItem(id = 1L, path = "2026/11/boat_pic.jpg")
        )
        assertEquals("2026/11/boat_pic.jpg", resolveEffectiveCoverPath("", pics))
        assertEquals("2026/11/boat_pic.jpg", resolveEffectiveCoverPath("   ", pics))
        assertEquals("2026/11/boat_pic.jpg", resolveEffectiveCoverPath("null", pics))
    }

    @Test
    fun skipsBlankOfficialPicAndFindsFirstValid() {
        val pics = listOf(
            PicItem(id = 1L, path = ""),
            PicItem(id = 2L, path = "  "),
            PicItem(id = 3L, path = "null"),
            PicItem(id = 4L, path = "2026/11/valid_pic.jpg")
        )
        val result = resolveEffectiveCoverPath(null, pics)
        assertEquals("2026/11/valid_pic.jpg", result)
    }

    @Test
    fun returnsNullWhenBothCoverAndOfficialPicsAreEmpty() {
        assertNull(resolveEffectiveCoverPath(null, emptyList()))
        assertNull(resolveEffectiveCoverPath("", emptyList()))
        assertNull(resolveEffectiveCoverPath("null", emptyList()))
    }
}
