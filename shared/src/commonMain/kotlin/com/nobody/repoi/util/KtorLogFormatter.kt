package com.nobody.repoi.util

/**
 * Ktor 网络日志精炼格式化器。
 *
 * 针对 Ktor 默认 Logging 插件信息冗余（长篇 HTTP Headers 刷屏）且无针对性提炼的问题，
 * 本格式化器按规范提炼出关键信息，剔除无业务价值的标准网络头：
 * 1. 请求行：提炼为 `--> METHOD URL`（如 `--> GET https://...`）；
 * 2. 响应行：提炼为 `<-- STATUS URL`（如 `<-- 200 https://...`）；
 * 3. 响应体/请求体：提炼 `BODY START ... BODY END` 中的 JSON 内容为 `[Body] ...`，并做长文本截断保护；
 * 4. 冗余头信息：彻底过滤 `COMMON HEADERS`、`CONTENT HEADERS`、`RESPONSE HEADERS` 等噪音行；
 * 5. 异常信息：透传真实的失败原因（如超时、断网等）。
 */
object KtorLogFormatter {

    private const val MAX_BODY_LENGTH = 3000
    private val tokenValuePattern = Regex("(utoken=)([^&\\s\"}]+)", RegexOption.IGNORE_CASE)
    private val tokenJsonPattern = Regex("(\"utoken\"\\s*:\\s*\")([^\"]+)(\")", RegexOption.IGNORE_CASE)

    fun sanitizeToken(text: String): String =
        tokenJsonPattern.replace(tokenValuePattern.replace(text, "$1***"), "$1***$3")

    fun format(message: String): String? {
        val sanitized = sanitizeToken(message)
        val trimmed = sanitized.trim()
        if (trimmed.isEmpty()) return null

        // 0. 网络失败/异常直接透传，防止漏报
        if (trimmed.contains("failed with exception", ignoreCase = true)) {
            return trimmed
        }

        // 1. 请求行提炼
        if (trimmed.startsWith("REQUEST: ")) {
            val lines = trimmed.lines()
            val url = lines.firstOrNull { it.startsWith("REQUEST: ") }?.removePrefix("REQUEST: ")?.trim() ?: ""
            val method = lines.firstOrNull { it.startsWith("METHOD: ") }?.removePrefix("METHOD: ")?.trim()?.ifBlank { null } ?: "UNKNOWN"
            return "--> $method $url"
        }

        // 2. 响应行提炼
        if (trimmed.startsWith("RESPONSE: ")) {
            val lines = trimmed.lines()
            val status = lines.firstOrNull { it.startsWith("RESPONSE: ") }?.removePrefix("RESPONSE: ")?.trim() ?: ""
            val from = lines.firstOrNull { it.startsWith("FROM: ") }?.removePrefix("FROM: ")?.trim() ?: ""
            return if (from.isNotBlank()) {
                "<-- $status $from"
            } else {
                "<-- $status"
            }
        }

        if (trimmed.contains("BODY START")) {
            if (!trimmed.contains("BODY END")) return null
            val body = trimmed
                .substringAfterLast("BODY START")
                .substringBeforeLast("BODY END")
                .trim()
            if (body.isBlank() || body == "[EMPTY]") {
                return null
            }
            val formattedBody = if (body.length > MAX_BODY_LENGTH) {
                "${body.take(MAX_BODY_LENGTH)}\n... (共 ${body.length} 字符，已截断)"
            } else {
                body
            }
            return "[Body] $formattedBody"
        }

        // 4. 纯冗余头信息直接过滤
        if (trimmed.startsWith("COMMON HEADERS") ||
            trimmed.startsWith("CONTENT HEADERS") ||
            trimmed.startsWith("RESPONSE HEADERS") ||
            trimmed.startsWith("-> ") ||
            trimmed.startsWith("BODY Content-Type:")
        ) {
            return null
        }

        // 5. 其他关键信息保留原样
        return trimmed
    }
}
