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
    fun fallsBackToFirstOfficialPicWhenExplicitCoverIsNoneJpg() {
        val pics = listOf(
            PicItem(id = 1L, path = "2026/11/boat_pic.jpg")
        )
        // 关键防护：服务端下发 none.jpg 时绝不能截断，必须能回退到首张官图
        assertEquals("2026/11/boat_pic.jpg", resolveEffectiveCoverPath("none.jpg", pics))
        assertEquals("2026/11/boat_pic.jpg", resolveEffectiveCoverPath("https://rfx.hpoi.net/gk/cover/n/none.jpg", pics))
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
        assertNull(resolveEffectiveCoverPath("none.jpg", emptyList()))
    }

    @Test
    fun resolveEffectiveCover_generatesCorrectCdnPrefixes() {
        val explicit = "2026/08/sample_cover.jpg"
        val pics = listOf(PicItem(id = 1L, path = "2026/08/official_1.jpg"))

        val coverExplicit = resolveEffectiveCover(explicit, pics)
        assertEquals(false, coverExplicit?.isOfficialPic)
        assertEquals("https://rfx.hpoi.net/gk/cover/n/2026/08/sample_cover.jpg", coverExplicit?.displayUrl)
        assertEquals("https://rfx.hpoi.net/gk/cover/raw/2026/08/sample_cover.jpg", coverExplicit?.rawUrl)

        val coverFallback = resolveEffectiveCover("none.jpg", pics)
        assertEquals(true, coverFallback?.isOfficialPic)
        assertEquals("https://rfx.hpoi.net/gk/pic/n/2026/08/official_1.jpg", coverFallback?.displayUrl)
        assertEquals("https://rfx.hpoi.net/gk/pic/raw/2026/08/official_1.jpg", coverFallback?.rawUrl)
    }
}
