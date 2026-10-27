package com.nobody.repoi.network

import com.nobody.repoi.data.remote.model.JsonRefResolver
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

/**
 * 解码响应体为目标类型；当响应包含 Fastjson 的 `@id`/`@ref`（或 `$id`/`$ref`）
 * 引用去重标记时，先经 [JsonRefResolver] 还原为完整 JSON 树再解码。
 *
 * 对应老项目的 `FastJsonRefConverterFactory`（Retrofit 侧包装）。
 */
suspend inline fun <reified T> HttpResponse.bodyAsJsonRef(json: Json): T {
    val content = bodyAsText()
    return if (content.contains("\"@ref\"") || content.contains("\"\$ref\"")) {
        val resolved = JsonRefResolver.resolveRefs(json.parseToJsonElement(content))
        json.decodeFromString<T>(resolved.toString())
    } else {
        json.decodeFromString<T>(content)
    }
}
