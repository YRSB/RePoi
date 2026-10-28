package com.nobody.poishelf.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FaceTextItem(
    val id: String = "",
    val content: String = ""
)

private val faceTextJson = Json { ignoreUnknownKeys = true }

fun parseFaceTexts(raw: String): List<FaceTextItem> =
    runCatching { faceTextJson.decodeFromString<List<FaceTextItem>>(raw) }.getOrDefault(emptyList())

/**
 * CMP `files/face_text.json` 资源内容（android+jvm 共用，无需 expect）。
 */
suspend fun loadFaceTextAsset(): String =
    repoi.shared.generated.resources.Res.readBytes("files/face_text.json")
        .decodeToString()

suspend fun loadFaceTexts(): List<FaceTextItem> =
    runCatching { parseFaceTexts(loadFaceTextAsset()) }.getOrDefault(emptyList())
