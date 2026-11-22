package com.nobody.repoi.util

object KtorLogFormatter {

    private const val MAX_BODY_LENGTH = 3000
    private val tokenValuePattern = Regex("(utoken=)([^&\\s\"}]+)", RegexOption.IGNORE_CASE)
    private val tokenJsonPattern = Regex("(\"utoken\"\\s*:\\s*\")([^\"]*)(\"|$)", RegexOption.IGNORE_CASE)

    fun sanitizeToken(text: String): String =
        tokenJsonPattern.replace(tokenValuePattern.replace(text, "$1***"), "$1***$3")

    fun format(message: String): String? {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.contains("BODY START")) {
            if (!trimmed.contains("BODY END")) return null
            val body = trimmed
                .substringAfter("BODY START")
                .substringBeforeLast("BODY END")
                .trim()
            if (body.isBlank() || body == "[EMPTY]") {
                return null
            }
            val formattedBody = if (body.length > MAX_BODY_LENGTH) {
                "${sanitizeToken(body.take(MAX_BODY_LENGTH))}\n... (共 ${body.length} 字符，已截断)"
            } else {
                sanitizeToken(body)
            }
            return "[Body] $formattedBody"
        }

        if (trimmed.startsWith("COMMON HEADERS") ||
            trimmed.startsWith("CONTENT HEADERS") ||
            trimmed.startsWith("RESPONSE HEADERS") ||
            trimmed.startsWith("-> ") ||
            trimmed.startsWith("BODY Content-Type:")
        ) {
            return null
        }

        if (trimmed.contains("failed with exception", ignoreCase = true)) {
            return sanitizeToken(trimmed)
        }

        if (trimmed.startsWith("REQUEST: ")) {
            val lines = trimmed.lines()
            val url = lines.firstOrNull { it.startsWith("REQUEST: ") }?.removePrefix("REQUEST: ")?.trim() ?: ""
            val method = lines.firstOrNull { it.startsWith("METHOD: ") }?.removePrefix("METHOD: ")?.trim()?.ifBlank { null } ?: "UNKNOWN"
            return sanitizeToken("--> $method $url")
        }

        if (trimmed.startsWith("RESPONSE: ")) {
            val lines = trimmed.lines()
            val status = lines.firstOrNull { it.startsWith("RESPONSE: ") }?.removePrefix("RESPONSE: ")?.trim() ?: ""
            val from = lines.firstOrNull { it.startsWith("FROM: ") }?.removePrefix("FROM: ")?.trim() ?: ""
            val res = if (from.isNotBlank()) {
                "<-- $status $from"
            } else {
                "<-- $status"
            }
            return sanitizeToken(res)
        }

        return sanitizeToken(trimmed)
    }
}
