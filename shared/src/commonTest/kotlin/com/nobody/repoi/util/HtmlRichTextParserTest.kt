package com.nobody.repoi.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 富文本解析器单元测试，覆盖 HTML 粗体、斜体、链接规范化与点击注解（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `HtmlRichTextParserTest`（逐用例；JUnit4 → kotlin.test）。
 * 移植说明：原测试挂 `@RunWith(RobolectricTestRunner)`，但 `HtmlRichTextParser`
 * 仅依赖 Compose 文本类型（AnnotatedString/Color/LinkAnnotation，纯 Kotlin，
 * 无 android.* 调用），shared/commonMain 同源；runner 属多余负担，KMP 下去掉。
 */
class HtmlRichTextParserTest {

    @Test
    fun normalizeUrl_formatsRelativeAndProtocolUrlsProperly() {
        assertEquals("https://www.hpoi.net/hobby/59570", HtmlRichTextParser.normalizeUrl("/hobby/59570"))
        assertEquals("https://www.hpoi.net/hobby/59570", HtmlRichTextParser.normalizeUrl("hobby/59570"))
        assertEquals("https://www.hpoi.net/album/8888", HtmlRichTextParser.normalizeUrl("/album/8888"))
        assertEquals("https://www.hpoi.net/album/8888", HtmlRichTextParser.normalizeUrl("album/8888"))
        assertEquals("https://example.com/item", HtmlRichTextParser.normalizeUrl("//example.com/item"))
        assertEquals("https://goodsmile.info/product/1", HtmlRichTextParser.normalizeUrl("https://goodsmile.info/product/1"))
        assertEquals("http://goodsmile.info/product/1", HtmlRichTextParser.normalizeUrl("http://goodsmile.info/product/1"))
        assertEquals("", HtmlRichTextParser.normalizeUrl(""))
    }

    @Test
    fun parse_emptyOrBlank_returnsEmptyAnnotatedString() {
        assertTrue(HtmlRichTextParser.parse(null).text.isEmpty())
        assertTrue(HtmlRichTextParser.parse("   ").text.isEmpty())
    }

    @Test
    fun parse_boldAndItalic_addsStyles() {
        val html = "<b>豪华特典</b>与<i>特别底座</i>"
        val annotated = HtmlRichTextParser.parse(html)
        assertTrue(annotated.text.contains("豪华特典与特别底座"))

        val boldSpan = annotated.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals("豪华特典", annotated.text.substring(boldSpan.start, boldSpan.end))

        val italicSpan = annotated.spanStyles.single { it.item.fontStyle == FontStyle.Italic }
        assertEquals("特别底座", annotated.text.substring(italicSpan.start, italicSpan.end))
    }

    @Test
    fun parse_hyperlink_createsLinkAnnotation() {
        val html = "点击前往 <a href=\"https://www.hpoi.net/hobby/123\">官方通贩</a> 查看"
        val annotated = HtmlRichTextParser.parse(html, linkColor = Color.Blue)
        assertTrue(annotated.text.contains("点击前往 官方通贩 查看"))

        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, linkAnnotations.size)
        val link = linkAnnotations.first().item
        assertTrue(link is LinkAnnotation.Url)
        assertEquals("https://www.hpoi.net/hobby/123", link.url)
    }

    @Test
    fun parse_sourceWhitespace_foldsIntoSingleSpace() {
        val html = "奶龙终于来了。\n\n说是1/5，实际大小和1/4差不多，摆放需要一定的\n空间。\r\n"
        assertEquals(
            "奶龙终于来了。 说是1/5，实际大小和1/4差不多，摆放需要一定的 空间。",
            HtmlRichTextParser.parse(html).text
        )
    }

    @Test
    fun parse_paragraphs_useSingleNewlineBetweenBlocks() {
        val html = "<p>第一段</p>\n<p>第二段</p>"
        assertEquals("第一段\n第二段", HtmlRichTextParser.parse(html).text)
    }

    @Test
    fun parse_br_keepsBreakAndDropsFollowingIndent() {
        val html = "第一行<br>\n    第二行"
        assertEquals("第一行\n第二行", HtmlRichTextParser.parse(html).text)
    }

    @Test
    fun parse_nbsp_keptAsNonBreakingSpace() {
        assertEquals("甲\u00A0\u00A0乙", HtmlRichTextParser.parse("甲&nbsp;&nbsp;乙").text)
    }
}
