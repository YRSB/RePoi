package com.nobody.repoi.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * HTML 富文本转 Compose AnnotatedString 解析器。
 * 支持加粗、斜体、下划线、删除线、前景色与可点击超链接，彻底解决纯文本丢标签与链接无法交互的痛点。
 *
 * KMP 纯 Kotlin 实现（老项目经 `HtmlCompat` + Android span 中转）：
 * 手写栈式标签解析，直接生成 AnnotatedString；标签语义与老版对齐。
 */
object HtmlRichTextParser {

    /**
     * 将 HTML 字符串转换为带有样式与点击事件的 Compose AnnotatedString。
     *
     * @param html 原始 HTML 文本
     * @param linkColor 超链接高亮颜色（默认取主题主色调）
     * @param onLinkClick 自定义链接点击回调（若提供则拦截默认行为，例如做内部路由判断或 Toast 提示）
     */
    fun parse(
        html: String?,
        linkColor: Color = Color(0xFF1E88E5),
        onLinkClick: ((String) -> Unit)? = null
    ): AnnotatedString {
        if (html.isNullOrBlank()) return AnnotatedString("")

        // 统一预处理 HTML
        val preparedHtml = html
            .replace(Regex("""(?i)<br\s*/?>\s*<br\s*/?>"""), "<br/>")

        // 纯 Kotlin 栈式解析：文本 + 样式区间
        val parsed = try {
            parseHtml(preparedHtml)
        } catch (_: Exception) {
            return AnnotatedString(html)
        }

        // 清理末尾多余换行与空白（对齐老版 trimEnd 语义）
        val rawText = parsed.text.trimEnd()
        val textLength = rawText.length

        return buildAnnotatedString {
            append(rawText)

            for (span in parsed.spans) {
                val start = span.start.coerceIn(0, textLength)
                val end = span.end.coerceIn(start, textLength)
                if (start >= end) continue

                when (span) {
                    is ParsedSpan.Bold -> addStyle(
                        SpanStyle(fontWeight = FontWeight.Bold), start, end
                    )
                    is ParsedSpan.Italic -> addStyle(
                        SpanStyle(fontStyle = FontStyle.Italic), start, end
                    )
                    is ParsedSpan.Underline -> addStyle(
                        SpanStyle(textDecoration = TextDecoration.Underline), start, end
                    )
                    is ParsedSpan.Strikethrough -> addStyle(
                        SpanStyle(textDecoration = TextDecoration.LineThrough), start, end
                    )
                    is ParsedSpan.ForegroundColor -> addStyle(
                        SpanStyle(color = span.color), start, end
                    )
                    is ParsedSpan.Link -> {
                        val normalizedUrl = normalizeUrl(span.url)
                        if (normalizedUrl.isNotBlank()) {
                            val linkStyles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            val linkAnnotation = if (onLinkClick != null) {
                                LinkAnnotation.Url(
                                    url = normalizedUrl,
                                    styles = linkStyles,
                                    linkInteractionListener = { onLinkClick(normalizedUrl) }
                                )
                            } else {
                                LinkAnnotation.Url(
                                    url = normalizedUrl,
                                    styles = linkStyles
                                )
                            }
                            addLink(linkAnnotation, start, end)
                        }
                    }
                }
            }
        }
    }

    /**
     * 规范化超链接，补充缺失的 http/https 协议头或 Hpoi 相对路径
     */
    fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return ""
        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> "https://www.hpoi.net$trimmed"
            trimmed.startsWith("hobby/") || trimmed.startsWith("item/") || trimmed.startsWith("album/") ||
            trimmed.startsWith("charactar/") || trimmed.startsWith("character/") ||
            trimmed.startsWith("person/") || trimmed.startsWith("works/") ||
            trimmed.startsWith("company/") || trimmed.startsWith("series/") ||
            trimmed.startsWith("posts/") || trimmed.startsWith("post/") -> "https://www.hpoi.net/$trimmed"
            else -> "https://$trimmed"
        }
    }

    // ── 纯 Kotlin HTML 解析 ──────────────────────────────────

    private sealed interface ParsedSpan {
        val start: Int
        val end: Int

        data class Bold(override val start: Int, override val end: Int) : ParsedSpan
        data class Italic(override val start: Int, override val end: Int) : ParsedSpan
        data class Underline(override val start: Int, override val end: Int) : ParsedSpan
        data class Strikethrough(override val start: Int, override val end: Int) : ParsedSpan
        data class ForegroundColor(
            override val start: Int,
            override val end: Int,
            val color: Color
        ) : ParsedSpan
        data class Link(override val start: Int, override val end: Int, val url: String) : ParsedSpan
    }

    private data class ParsedHtml(
        val text: String,
        val spans: List<ParsedSpan>
    )

    private data class OpenTag(
        val name: String,
        val start: Int,
        val href: String? = null,
        val color: Color? = null,
        val bold: Boolean = false,
        val italic: Boolean = false
    )

    private val tagRegex = Regex("""<(/?)([a-zA-Z][a-zA-Z0-9]*)\b([^>]*)>""")
    private val hrefRegex = Regex("""(?i)href\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
    private val colorRegex = Regex("""(?i)color\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")

    private fun parseHtml(html: String): ParsedHtml {
        val text = StringBuilder()
        val spans = mutableListOf<ParsedSpan>()
        val stack = ArrayDeque<OpenTag>()

        var index = 0
        while (index < html.length) {
            val match = tagRegex.find(html, index)
            if (match == null || match.range.first != index) {
                // 普通文本：读到下一个 '<' 或结尾
                val nextTag = html.indexOf('<', index).let { if (it < 0) html.length else it }
                text.append(decodeEntities(html.substring(index, nextTag)))
                index = nextTag
                if (index < html.length && tagRegex.find(html, index)?.range?.first != index) {
                    // 非法 '<'，按普通字符处理
                    text.append('<')
                    index++
                }
                continue
            }

            val isClosing = match.groupValues[1] == "/"
            val tagName = match.groupValues[2].lowercase()
            val attrs = match.groupValues[3]
            index = match.range.last + 1

            // 自闭合 / 换行标签
            if (tagName == "br" || tagName == "p" && !isClosing) {
                if (text.isNotEmpty() && !text.endsWith('\n')) text.append('\n')
                if (tagName == "p" && !isClosing) {
                    // <p> 视为块分隔：不压栈
                }
                continue
            }
            if (tagName == "p" && isClosing) {
                if (text.isNotEmpty() && !text.endsWith('\n')) text.append('\n')
                continue
            }

            if (!isClosing) {
                when (tagName) {
                    "b", "strong" -> stack.addLast(OpenTag(tagName, text.length, bold = true))
                    "i", "em" -> stack.addLast(OpenTag(tagName, text.length, italic = true))
                    "u" -> stack.addLast(OpenTag(tagName, text.length))
                    "s", "strike", "del" -> stack.addLast(OpenTag(tagName, text.length))
                    "font" -> {
                        val color = parseColorAttr(attrs)
                        stack.addLast(OpenTag(tagName, text.length, color = color))
                    }
                    "a" -> {
                        val href = hrefRegex.find(attrs)?.let {
                            it.groupValues[1].ifEmpty { it.groupValues[2].ifEmpty { it.groupValues[3] } }
                        }.orEmpty()
                        stack.addLast(OpenTag(tagName, text.length, href = href))
                    }
                    // 未知标签：忽略
                    else -> Unit
                }
            } else {
                // 闭合标签：从栈顶找到匹配的开标签
                val matchIndex = stack.indexOfLast { it.name == tagName || tagName in it.name.aliases() }
                if (matchIndex >= 0) {
                    val open = stack.removeAt(matchIndex)
                    // 闭合标签前的栈顶残留（未闭合内层标签）一并结算
                    while (stack.size > matchIndex) {
                        val inner = stack.removeLast()
                        emitSpan(inner, text.length, spans)
                    }
                    emitSpan(open, text.length, spans)
                }
            }
        }

        // 收尾：未闭合标签结算到文本末尾
        while (stack.isNotEmpty()) {
            emitSpan(stack.removeLast(), text.length, spans)
        }

        return ParsedHtml(text.toString(), spans)
    }

    private fun String.aliases(): List<String> = when (this) {
        "b", "strong" -> listOf("b", "strong")
        "i", "em" -> listOf("i", "em")
        "s", "strike", "del" -> listOf("s", "strike", "del")
        else -> listOf(this)
    }

    private fun emitSpan(open: OpenTag, end: Int, spans: MutableList<ParsedSpan>) {
        if (open.start >= end) return
        when (open.name) {
            "b", "strong" -> spans.add(ParsedSpan.Bold(open.start, end))
            "i", "em" -> spans.add(ParsedSpan.Italic(open.start, end))
            "u" -> spans.add(ParsedSpan.Underline(open.start, end))
            "s", "strike", "del" -> spans.add(ParsedSpan.Strikethrough(open.start, end))
            "font" -> open.color?.let { spans.add(ParsedSpan.ForegroundColor(open.start, end, it)) }
            "a" -> if (open.href.orEmpty().isNotBlank()) {
                spans.add(ParsedSpan.Link(open.start, end, open.href))
            }
        }
    }

    private fun parseColorAttr(attrs: String): Color? {
        val raw = colorRegex.find(attrs)?.let {
            it.groupValues[1].ifEmpty { it.groupValues[2].ifEmpty { it.groupValues[3] } }
        }?.trim().orEmpty()
        if (raw.isEmpty()) return null
        // 支持 #RGB / #RRGGBB / #AARRGGBB
        val hex = raw.removePrefix("#")
        return try {
            when (hex.length) {
                3 -> {
                    val r = hex[0].toString().repeat(2)
                    val g = hex[1].toString().repeat(2)
                    val b = hex[2].toString().repeat(2)
                    Color(("FF$r$g$b").toLong(16))
                }
                6 -> Color(("FF$hex").toLong(16))
                8 -> Color(hex.toLong(16))
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private val entityRegex = Regex("""&(amp|lt|gt|quot|apos|nbsp|#(\d+)|#x([0-9a-fA-F]+));""")

    private fun decodeEntities(text: String): String {
        if (!text.contains('&')) return text
        return entityRegex.replace(text) { match ->
            when (val name = match.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> "\u00A0"
                else -> {
                    val decimal = match.groupValues[2]
                    val hex = match.groupValues[3]
                    try {
                        when {
                            decimal.isNotEmpty() -> decimal.toInt().toChar().toString()
                            hex.isNotEmpty() -> hex.toInt(16).toChar().toString()
                            else -> match.value
                        }
                    } catch (_: Exception) {
                        match.value
                    }
                }
            }
        }
    }
}
