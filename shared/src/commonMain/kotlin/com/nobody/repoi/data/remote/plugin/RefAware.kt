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

suspend inline fun <reified T> HttpResponse.decodeRefAware(json: Json): T {
    val text = unwrapDoubleEncodedJson(bodyAsText(), json)
    return if (REF_MARK_A in text || REF_MARK_B in text) {
        val strategy = serializer<T>()
        json.decodeFromJsonElement(strategy, JsonRefResolver.resolveRefs(json.parseToJsonElement(text)))
    } else {
        json.decodeFromString(text)
    }
}

suspend inline fun <reified T> HttpResponse.decodePlain(json: Json): T {
    val text = unwrapDoubleEncodedJson(bodyAsText(), json)
    return json.decodeFromString(text)
}

suspend inline fun HttpResponse.decodeBytes(): ByteArray = body()
