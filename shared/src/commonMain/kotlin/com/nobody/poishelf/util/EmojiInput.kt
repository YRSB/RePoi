package com.nobody.poishelf.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

fun canAddSticker(count: Int): Boolean = count < 4

fun buildEmoticonParam(stickers: List<String>): String? =
    stickers.takeIf { it.isNotEmpty() }?.let { paths ->
        buildJsonArray { paths.forEach { add(JsonPrimitive(it)) } }.toString()
    }

private val emoticonUrlsJson = Json { ignoreUnknownKeys = true }

fun parseEmoticonUrls(emoticon: JsonElement?): List<String> {
    if (emoticon is JsonArray) {
        return emoticon.mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf { raw -> raw.isNotEmpty() } }
    }
    if (emoticon is JsonPrimitive) {
        val raw = emoticon.content.trim()
        if (raw.isEmpty()) return emptyList()
        if (raw.startsWith("[")) {
            return runCatching { emoticonUrlsJson.decodeFromString<List<String>>(raw) }
                .getOrDefault(emptyList())
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        return listOf(raw)
    }
    return emptyList()
}
