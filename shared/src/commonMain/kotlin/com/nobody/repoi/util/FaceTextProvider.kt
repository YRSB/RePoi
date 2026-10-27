package com.nobody.repoi.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import repoi.shared.generated.resources.Res

@Serializable
data class FaceTextItem(
    val id: String = "",
    val content: String = ""
)

private val faceTextJson = Json { ignoreUnknownKeys = true }

fun parseFaceTexts(raw: String): List<FaceTextItem> =
    runCatching { faceTextJson.decodeFromString<List<FaceTextItem>>(raw) }.getOrDefault(emptyList())

/**
 * 从 compose-resources 读取颜文字库（老项目从 Android assets 读取）。
 * 调用点（`CommentSection`，Wave 5）在组合内经 `remember` 缓存结果。
 */
suspend fun loadFaceTexts(): List<FaceTextItem> =
    runCatching {
        Res.readBytes("files/face_text.json").decodeToString()
    }.map { parseFaceTexts(it) }.getOrDefault(emptyList())
