package com.nobody.poishelf.data.remote.plugin

import com.nobody.poishelf.data.remote.interceptor.CommonParams
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.http.Parameters
import io.ktor.http.parameters

/**
 * 公共参数 Ktor 插件（`CommonParamsInterceptor` 的 Ktor 等价实现）。
 *
 * 行为与 OkHttp 版逐条对齐：
 * - GET/其他：公共参数拼到 query，已有同名不覆盖；
 * - POST `FormDataContent`：合并字段，已有同名不覆盖（null 值跳过）；
 * - POST 空包：合成仅含公共参数的 form；
 * - Multipart/其他 body：仅补 Referer/User-Agent 头（服务端上传链路现状：不注入）。
 *
 * 通过 HttpSend 拦截实现（DefaultRequest 无法读取/合并 form body）。
 */
class CommonParamsPlugin(val tokenProvider: () -> String) {

    companion object Plugin {
        fun install(client: HttpClient, tokenProvider: () -> String) {
            val plugin = CommonParamsPlugin(tokenProvider)
            client.plugin(HttpSend).intercept { request ->
                plugin.intercept(request)
                execute(request)
            }
        }
    }

    internal fun intercept(request: io.ktor.client.request.HttpRequestBuilder) {
        if (request.headers["Referer"] == null) {
            request.headers.append("Referer", CommonParams.REFERER_URL)
        }
        if (request.headers["User-Agent"] == null) {
            request.headers.append("User-Agent", CommonParams.USER_AGENT)
        }

        val token = runCatching { tokenProvider() }.getOrDefault("")
        val common = CommonParams.build(token)
        val method = request.method.value.uppercase()

        if (method == "POST") {
            val body = request.body
            when (body) {
                is FormDataContent -> {
                    val existing = body.formData.entries()
                        .associate { it.key to it.value.firstOrNull() }
                    val merged = CommonParams.merge(existing, token)
                    request.setBody(
                        FormDataContent(
                            Parameters.build {
                                merged.forEach { (k, v) -> append(k, v) }
                            },
                        ),
                    )
                }
                is MultiPartFormDataContent -> {
                    // 与 OkHttp 版一致：Multipart 不注入公共参数。
                }
                else -> {
                    val empty = body is io.ktor.http.content.OutgoingContent.NoContent ||
                        body is io.ktor.http.content.OutgoingContent.ByteArrayContent &&
                        (body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().isEmpty()
                    if (empty || body !is io.ktor.http.content.OutgoingContent) {
                        request.setBody(
                            FormDataContent(
                                Parameters.build {
                                    common.forEach { (k, v) -> append(k, v) }
                                },
                            ),
                        )
                    }
                }
            }
        } else {
            request.url.parameters {
                for ((key, value) in common) {
                    if (!contains(key)) append(key, value)
                }
            }
        }
    }
}

/** HttpClient 作用域安装入口。 */
fun HttpClient.installCommonParams(tokenProvider: () -> String): HttpClient {
    CommonParamsPlugin.install(this, tokenProvider)
    return this
}
