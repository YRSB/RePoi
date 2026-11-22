package com.nobody.repoi.util

import kotlinx.serialization.Serializable

@Serializable
data class FaceTextItem(
    val id: String = "",
    val content: String = ""
)

@kotlin.concurrent.Volatile
private var cachedFaceTexts: List<FaceTextItem>? = null

fun parseFaceTexts(raw: String): List<FaceTextItem> =
    runCatching { DefaultJson.decodeFromString<List<FaceTextItem>>(raw) }.getOrDefault(emptyList())

suspend fun loadFaceTextAsset(): String =
    repoi.shared.generated.resources.Res.readBytes("files/face_text.json")
        .decodeToString()

suspend fun loadFaceTexts(): List<FaceTextItem> {
    cachedFaceTexts?.let { return it }
    val parsed = runCatching { parseFaceTexts(loadFaceTextAsset()) }.getOrDefault(emptyList())
    if (parsed.isNotEmpty()) {
        cachedFaceTexts = parsed
    }
    return parsed
}
