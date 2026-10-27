package com.nobody.repoi.util

import kotlinx.serialization.json.Json
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class EmojiInputTest {

    @Test
    fun parseFaceTexts_parsesSample() {
        val raw = """[{"id":"top_1","content":"_(:з」∠)_"},{"id":"moe_1","content":"(✺ω✺)"}]"""
        val faces = parseFaceTexts(raw)
        assertEquals(2, faces.size)
        assertEquals("top_1", faces[0].id)
        assertEquals("_(:з」∠)_", faces[0].content)
        assertEquals("(✺ω✺)", faces[1].content)
    }

    @Test
    fun parseFaceTexts_badJsonReturnsEmpty() {
        assertTrue(parseFaceTexts("not json").isEmpty())
        assertTrue(parseFaceTexts("").isEmpty())
    }

    @Test
    fun canAddSticker_limitsToFour() {
        assertTrue(canAddSticker(3))
        assertFalse(canAddSticker(4))
        assertFalse(canAddSticker(5))
    }

    @Test
    fun buildEmoticonParam_serializesPaths() {
        assertNull(buildEmoticonParam(emptyList()))
        assertEquals(
            """["meme/2024/a.png","meme/2024/b.gif"]""",
            buildEmoticonParam(listOf("meme/2024/a.png", "meme/2024/b.gif"))
        )
    }

    @Test
    fun getStickerUrl_buildsFullUrl() {
        assertEquals(
            "https://rfx.hpoi.net/gk/sticker/item/meme/2024/a.png",
            CdnUrlUtils.getStickerUrl("meme/2024/a.png")
        )
        assertEquals(
            "https://rfx.hpoi.net/gk/sticker/item/meme/2024/a.png",
            CdnUrlUtils.getStickerUrl("/meme/2024/a.png")
        )
        assertEquals("https://example.com/x.gif", CdnUrlUtils.getStickerUrl("https://example.com/x.gif"))
        assertEquals("", CdnUrlUtils.getStickerUrl(null))
        assertEquals("", CdnUrlUtils.getStickerUrl("  "))
    }

    @Test
    fun parseEmoticonUrls_handlesBothShapes() {
        assertEquals(
            listOf("a.png", "b.gif"),
            parseEmoticonUrls(Json.parseToJsonElement("""["a.png","b.gif"]"""))
        )
        assertEquals(
            listOf("a.png"),
            parseEmoticonUrls(Json.parseToJsonElement(""""a.png""""))
        )
        assertEquals(
            listOf("a.png"),
            parseEmoticonUrls(Json.parseToJsonElement("\"[\\\"a.png\\\"]\""))
        )
        assertTrue(parseEmoticonUrls(null).isEmpty())
        assertTrue(parseEmoticonUrls(Json.parseToJsonElement("[]")).isEmpty())
        assertTrue(parseEmoticonUrls(Json.parseToJsonElement("""""""")).isEmpty())
    }
}
