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
 * HTML 富文本转 Compose AnnotatedString 解析器（KMP 通用实现，无需 HtmlCompat）。
 *
 * 手写子集解析（ksoup 不在 catalog）：b/strong、i/em、u、s/strike/del、font color、
 * a[href]、br/p 换行。未知标签剥除保留文本。行为对齐原版：
 * - 双 `<br>` 折叠为单换行；
 * - 末尾多余换行/空白清理；
 * - 链接经 [normalizeUrl] 规范化后加 `LinkAnnotation.Url`。
 */
object HtmlRichTextParser {

    fun parse(
        html: String?,
        linkColor: Color = Color(0xFF1E88E5),
        onLinkClick: ((String) -> Unit)? = null,
    ): AnnotatedString {
        if (html.isNullOrBlank()) return AnnotatedString("")
        // 统一预处理：双换行折叠（与原版一致）
        val prepared = html.replace(Regex("(?i)<br\\s*/?>\\s*<br\\s*/?>"), "<br/>")

        val segments = tokenize(prepared)
        val rawText = buildString { segments.forEach { append(it.text) } }.trimEnd()
        if (rawText.isEmpty()) return AnnotatedString("")

        return buildAnnotatedString {
            append(rawText)
            var cursor = 0
            for (seg in segments) {
                val len = seg.text.length
                if (len == 0) continue
                val start = cursor
                val end = (cursor + len).coerceAtMost(rawText.length)
                cursor += len
                if (start >= end) continue
                when (seg.kind) {
                    SegKind.Bold -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                    SegKind.Italic -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    SegKind.Underline -> addStyle(
                        SpanStyle(textDecoration = TextDecoration.Underline), start, end,
                    )
                    SegKind.Strike -> addStyle(
                        SpanStyle(textDecoration = TextDecoration.LineThrough), start, end,
                    )
                    is SegKind.Colored -> addStyle(SpanStyle(color = seg.kind.color), start, end)
                    is SegKind.Link -> {
                        val normalizedUrl = normalizeUrl(seg.kind.url)
                        if (normalizedUrl.isNotBlank()) {
                            val linkStyles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                            val linkAnnotation = if (onLinkClick != null) {
                                addLink(
                                    LinkAnnotation.Clickable(
                                        tag = normalizedUrl,
                                        styles = linkStyles,
                                        linkInteractionListener = { onLinkClick(normalizedUrl) },
                                    ),
                                    start, end,
                                )
                            } else {
                                addLink(
                                    LinkAnnotation.Url(url = normalizedUrl, styles = linkStyles),
                                    start, end,
                                )
                            }
                        }
                    }
                    SegKind.Plain -> {}
                }
            }
        }
    }

    /**
     * 规范化超链接，补充缺失的 http/https 协议头或 Hpoi 相对路径。
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

    private data class Segment(val text: String, val kind: SegKind)

    private sealed interface SegKind {
        data object Plain : SegKind
        data object Bold : SegKind
        data object Italic : SegKind
        data object Underline : SegKind
        data object Strike : SegKind
        data class Colored(val color: Color) : SegKind
        data class Link(val url: String) : SegKind
    }

    // 上方 Kind 枚举为历史占位，实际使用 SegKind；保留以最小化行数变更。
    private fun tokenize(html: String): List<Segment> {
        val out = mutableListOf<Segment>()
        var bold = 0; var italic = 0; var underline = 0; var strike = 0
        val colorStack = ArrayDeque<Color?>()
        val linkStack = ArrayDeque<String?>()
        colorStack.addLast(null)
        linkStack.addLast(null)

        var i = 0
        val text = StringBuilder()
        fun flush() {
            if (text.isEmpty()) return
            val segText = text.toString()
            text.clear()
            val link = linkStack.lastOrNull()
            val color = colorStack.lastOrNull()
            val kind: SegKind = when {
                link != null -> SegKind.Link(link)
                bold > 0 -> SegKind.Bold
                italic > 0 -> SegKind.Italic
                underline > 0 -> SegKind.Underline
                strike > 0 -> SegKind.Strike
                color != null -> SegKind.Colored(color)
                else -> SegKind.Plain
            }
            // 链接内仍可叠加粗斜体？原版 HtmlCompat 取 span 叠加；此处简化：链接优先。
            out.add(Segment(segText, kind))
        }

        while (i < html.length) {
            val c = html[i]
            if (c == '<') {
                val close = html.indexOf('>', i + 1)
                if (close == -1) {
                    text.append(html.substring(i))
                    break
                }
                val raw = html.substring(i + 1, close).trim()
                val isEnd = raw.startsWith("/")
                val name = raw.trimStart('/').substringBefore(' ').substringBefore('\t')
                    .substringBefore('/').lowercase()
                val attrs = if (isEnd) "" else raw
                flush()
                when (name) {
                    "br", "p" -> {
                        // <br> 换行；<p> 段落结束换行（开头不补）。
                        if (out.isNotEmpty() || text.isNotEmpty()) out.add(Segment("\n", SegKind.Plain))
                    }
                    "b", "strong" -> if (!isEnd) bold++ else if (bold > 0) bold--
                    "i", "em" -> if (!isEnd) italic++ else if (italic > 0) italic--
                    "u" -> if (!isEnd) underline++ else if (underline > 0) underline--
                    "s", "strike", "del" -> if (!isEnd) strike++ else if (strike > 0) strike--
                    "font" -> {
                        if (!isEnd) {
                            colorStack.addLast(parseColor(attrs) ?: colorStack.lastOrNull())
                        } else if (colorStack.size > 1) {
                            colorStack.removeLast()
                        }
                    }
                    "a" -> {
                        if (!isEnd) {
                            linkStack.addLast(parseHref(attrs) ?: linkStack.lastOrNull())
                        } else if (linkStack.size > 1) {
                            linkStack.removeLast()
                        }
                    }
                    else -> {
                        // 未知标签剥除保留文本。
                    }
                }
                i = close + 1
            } else if (c == '&') {
                val semi = html.indexOf(';', i + 1).takeIf { it in (i + 1)..(i + 8) }
                if (semi == null) {
                    text.append(c); i++
                } else {
                    val entity = html.substring(i + 1, semi)
                    text.append(decodeEntity(entity))
                    i = semi + 1
                }
            } else {
                text.append(c); i++
            }
        }
        flush()
        // 合并相邻同 kind（压缩 spans）。
        if (out.isEmpty()) return out
        val merged = mutableListOf<Segment>()
        for (seg in out) {
            val last = merged.lastOrNull()
            if (last != null && last.kind == seg.kind) {
                merged[merged.lastIndex] = last.copy(text = last.text + seg.text)
            } else {
                merged.add(seg)
            }
        }
        return merged
    }

    private fun parseColor(attrs: String): Color? {
        val m = Regex("""(?i)color\s*=\s*["']?([^"'\s>]+)["']?""").find(attrs) ?: return null
        return parseCssColor(m.groupValues[1])
    }

    private fun parseHref(attrs: String): String? {
        val m = Regex("""(?i)href\s*=\s*["']?([^"'\s>]+)["']?""").find(attrs) ?: return null
        return m.groupValues[1].takeIf { it.isNotBlank() }
    }

    private fun parseCssColor(raw: String): Color? {
        val v = raw.trim()
        return when {
            v.startsWith("#") -> runCatching {
                val hex = v.removePrefix("#")
                val argb = when (hex.length) {
                    3 -> "FF" + hex.map { "$it$it" }.joinToString("")
                    4 -> hex.map { "$it$it" }.joinToString("")
                    6 -> "FF$hex"
                    8 -> hex
                    else -> return null
                }
                Color(argb.toULong(16))
            }.getOrNull()
            v.startsWith("rgb", ignoreCase = true) -> runCatching {
                val nums = Regex("""\d+""").findAll(v).map { it.value.toInt() }.toList()
                if (nums.size >= 3) Color(nums[0], nums[1], nums[2]) else null
            }.getOrNull()
            else -> namedColors[v.lowercase()]
        }
    }

    private val namedColors = mapOf(
        "black" to Color.Black, "white" to Color.White, "red" to Color.Red,
        "green" to Color(0xFF008000), "blue" to Color.Blue, "gray" to Color.Gray,
        "grey" to Color.Gray, "yellow" to Color.Yellow, "cyan" to Color.Cyan,
        "magenta" to Color.Magenta, "transparent" to Color.Transparent,
    )

    private fun decodeEntity(entity: String): String = when (entity.lowercase()) {
        "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
        else -> if (entity.startsWith("#")) {
            runCatching {
                val code = if (entity.startsWith("#x", ignoreCase = true)) {
                    entity.substring(2).toInt(16)
                } else {
                    entity.substring(1).toInt()
                }
                code.toChar().toString()
            }.getOrDefault("&$entity;")
        } else {
            "&$entity;"
        }
    }
}
