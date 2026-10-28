package com.nobody.poishelf.data.remote.plugin

import com.nobody.poishelf.data.remote.model.JsonRefResolver
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

@PublishedApi
internal const val REF_MARK_A = "\"@ref\""
@PublishedApi
internal const val REF_MARK_B = "\"\$ref\""

/**
 * `@ref` 感知解码（`FastJsonRefConverterFactory` 的 Ktor 等价实现）。
 *
 * 触发条件与原版完全一致：body 字符串包含 `"@ref"` 或 `"$ref"` 时，
 * 先经 [JsonRefResolver.resolveRefs] 还原引用去重，再解码为 DTO；
 * 否则直接解码。未命中时零开销（一次字符串扫描）。
 */
suspend inline fun <reified T> HttpResponse.decodeRefAware(json: Json): T {
    val text = bodyAsText()
    return if (REF_MARK_A in text || REF_MARK_B in text) {
        val strategy = serializer<T>()
        json.decodeFromJsonElement(strategy, JsonRefResolver.resolveRefs(json.parseToJsonElement(text)))
    } else {
        json.decodeFromString(text)
    }
}

/** 无需 `@ref` 处理的普通解码（ContentNegotiation 直通时使用）。 */
suspend inline fun <reified T> HttpResponse.decodePlain(json: Json): T {
    val text = bodyAsText()
    return json.decodeFromString(text)
}

/** 原始字节直取（图片/二进制下载用）。 */
suspend inline fun HttpResponse.decodeBytes(): ByteArray = body()
