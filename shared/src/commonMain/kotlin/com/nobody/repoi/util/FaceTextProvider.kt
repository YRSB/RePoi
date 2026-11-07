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

/**
 * CMP `files/face_text.json` 资源内容（android+jvm 共用，无需 expect）。
 */
suspend fun loadFaceTextAsset(): String =
    repoi.shared.generated.resources.Res.readBytes("files/face_text.json")
        .decodeToString()

/**
 * 加载表情列表：首次读取 CMP 资源并解析后常驻静态内存复用，避免每次展开键盘重复读流与反序列化。
 */
suspend fun loadFaceTexts(): List<FaceTextItem> {
    cachedFaceTexts?.let { return it }
    val parsed = runCatching { parseFaceTexts(loadFaceTextAsset()) }.getOrDefault(emptyList())
    if (parsed.isNotEmpty()) {
        cachedFaceTexts = parsed
    }
    return parsed
}
