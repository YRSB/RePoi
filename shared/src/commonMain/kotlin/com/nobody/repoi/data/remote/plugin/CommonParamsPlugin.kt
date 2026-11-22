package com.nobody.repoi.data.remote.plugin

import com.nobody.repoi.data.remote.interceptor.CommonParams
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.Sender
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.Parameters
import io.ktor.http.content.OutgoingContent
import io.ktor.util.AttributeKey

private val CommonParamsAttributeKey = AttributeKey<Boolean>("PoiShelfCommonParamsApplied")

val PoiRequestTokenAttributeKey = AttributeKey<String>("PoiRequestToken")

val BypassTokenSelfHealingAttributeKey = AttributeKey<Boolean>("PoiBypassTokenSelfHealing")

class CommonParamsPlugin(val tokenProvider: () -> String) {

    companion object Plugin {
        fun install(client: HttpClient, tokenProvider: () -> String) {
            val plugin = CommonParamsPlugin(tokenProvider)
            client.plugin(HttpSend).intercept { request ->
                plugin.intercept(this, request)
            }
        }
    }

    suspend fun intercept(sender: Sender, request: HttpRequestBuilder) =
        sender.execute(applyCommonParams(request))

    internal fun applyCommonParams(request: HttpRequestBuilder): HttpRequestBuilder {
        if (request.attributes.getOrNull(CommonParamsAttributeKey) == true) return request
        if (request.headers["Referer"] == null) {
            request.headers.append("Referer", CommonParams.REFERER_URL)
        }
        if (request.headers["User-Agent"] == null) {
            request.headers.append("User-Agent", CommonParams.USER_AGENT)
        }

        val token = runCatching { tokenProvider() }.getOrDefault("")
        request.attributes.put(PoiRequestTokenAttributeKey, token)
        val common = CommonParams.build(token)
        val method = request.method.value.uppercase()

        if (method == "POST") {
            val body = request.body
            when (body) {
                is FormDataContent -> {
                    val existing = body.formData.entries()
                        .associate { it.key to it.value.firstOrNull() }
                    val merged = CommonParams.merge(existing, token)
                    request.body = FormDataContent(
                        Parameters.build {
                            merged.forEach { (k, v) -> append(k, v) }
                        },
                    )
                }
                is MultiPartFormDataContent -> {

                }
                else -> {
                    val needsSyntheticForm = when (body) {
                        is OutgoingContent.NoContent -> true
                        is OutgoingContent.ByteArrayContent -> body.bytes().isEmpty()
                        is OutgoingContent -> false
                        else -> true
                    }
                    if (needsSyntheticForm) {
                        request.body = FormDataContent(
                            Parameters.build {
                                common.forEach { (k, v) -> append(k, v) }
                            },
                        )
                    }
                }
            }
        } else {
            request.url.parameters.let { params ->
                for ((key, value) in common) {
                    if (!params.contains(key)) params.append(key, value)
                }
            }
        }
        request.attributes.put(CommonParamsAttributeKey, true)
        return request
    }
}

fun HttpClient.installCommonParams(tokenProvider: () -> String): HttpClient {
    CommonParamsPlugin.install(this, tokenProvider)
    return this
}
