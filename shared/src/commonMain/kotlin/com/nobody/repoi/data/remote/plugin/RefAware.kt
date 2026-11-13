package com.nobody.repoi.data.remote.plugin

import com.nobody.repoi.data.remote.model.JsonRefResolver
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer

@PublishedApi
internal const val REF_MARK_A = "\"@ref\""
@PublishedApi
internal const val REF_MARK_B = "\"\$ref\""

/**
 * 当服务端某些接口（如 Spring MVC 返回裸 String 的接口在遇到 Accept: application/json 时）
 * 会误将返回的 JSON 字符串进行二次 JSON 序列化，输出被双引号包裹且内部引号被转义的文本。
 * 本函数安全识别并还原内部真正的 JSON 字符串。
 */
@PublishedApi
internal fun unwrapDoubleEncodedJson(rawText: String, json: Json): String {
    val trimmed = rawText.trim()
    if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
        val element = runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
        if (element is JsonPrimitive && element.isString) {
            val content = element.content.trim()
            if ((content.startsWith('{') && content.endsWith('}')) ||
                (content.startsWith('[') && content.endsWith(']'))
            ) {
                return content
            }
        }
    }
    return rawText
}

/**
 * `@ref` 感知解码（`FastJsonRefConverterFactory` 的 Ktor 等价实现）。
 *
 * 触发条件与原版完全一致：body 字符串包含 `"@ref"` 或 `"$ref"` 时，
 * 先经 [JsonRefResolver.resolveRefs] 还原引用去重，再解码为 DTO；
 * 否则直接解码。未命中时零开销（一次字符串扫描）。
 */
suspend inline fun <reified T> HttpResponse.decodeRefAware(json: Json): T {
    val text = unwrapDoubleEncodedJson(bodyAsText(), json)
    return if (REF_MARK_A in text || REF_MARK_B in text) {
        val strategy = serializer<T>()
        json.decodeFromJsonElement(strategy, JsonRefResolver.resolveRefs(json.parseToJsonElement(text)))
    } else {
        json.decodeFromString(text)
    }
}

/** 无需 `@ref` 处理的普通解码（ContentNegotiation 直通时使用）。 */
suspend inline fun <reified T> HttpResponse.decodePlain(json: Json): T {
    val text = unwrapDoubleEncodedJson(bodyAsText(), json)
    return json.decodeFromString(text)
}

/** 原始字节直取（图片/二进制下载用）。 */
suspend inline fun HttpResponse.decodeBytes(): ByteArray = body()
