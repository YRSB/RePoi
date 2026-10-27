package com.nobody.repoi.network

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.setBody
import io.ktor.client.utils.EmptyContent
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.parameters

/**
 * Ktor client 插件：自动注入 Referer/User-Agent 头以及 Hpoi API 公共请求参数。
 *
 * 复刻老项目 `CommonParamsInterceptor`（OkHttp）语义：
 * - 每个请求附加公共参数 utoken/platform/appVersion/channel/machine/system（已存在则不覆盖）；
 * - POST + 表单：合并进表单体；
 * - 空 POST（无 body）：创建仅含公共参数的表单体；
 * - 其他方法（GET 等）：拼到 URL query；
 * - 非表单 POST body（如 multipart 上传）：只加头，不碰 body。
 *
 * 注意 Ktor pipeline 语义：[onRequest] 挂在 `HttpRequestPipeline.State`，
 * 此时 `request.body` 是用户设置的**原始 body**（`Parameters`/`EmptyContent`/自定义类型），
 * 而 `FormDataContent` 是 `Transform` 阶段才生成的 `OutgoingContent`。
 * 因此合并逻辑必须处理原始类型，不能只匹配 `FormDataContent`。
 */
val CommonParamsPlugin = createClientPlugin("CommonParamsPlugin", ::CommonParamsConfig) {
    val tokenProvider = pluginConfig.tokenProvider

    fun commonParams(): Map<String, String> = mapOf(
        "utoken" to tokenProvider(),
        "platform" to "android",
        "appVersion" to CommonParamsConfig.API_APP_VERSION,
        "channel" to "official",
        "machine" to CommonParamsConfig.API_MACHINE,
        "system" to CommonParamsConfig.API_SYSTEM_VERSION
    )

    fun mergeInto(formData: Parameters, common: Map<String, String>): Parameters =
        Parameters.build {
            appendAll(formData)
            val existingNames = formData.names()
            for ((key, value) in common) {
                if (!existingNames.contains(key)) append(key, value)
            }
        }

    onRequest { request, _ ->
        request.headers.append("Referer", CommonParamsConfig.REFERER_URL)
        request.headers.append("User-Agent", CommonParamsConfig.USER_AGENT)

        val common = commonParams()

        if (request.method == HttpMethod.Post) {
            when (val body = request.body) {
                // 常见写法：setBody(FormDataContent(...)) 或 submitForm 全路径。
                // FormDataContent 在 State 阶段仍是原始 body，直接合并重设。
                is FormDataContent -> {
                    request.setBody(FormDataContent(mergeInto(body.formData, common)))
                }
                // submitForm(parameters { ... }) 传的是原始 Parameters。
                is Parameters -> {
                    request.setBody(FormDataContent(mergeInto(body, common)))
                }
                // 空 POST：创建仅含公共参数的表单体。
                is EmptyContent -> {
                    request.setBody(FormDataContent(mergeInto(Parameters.Empty, common)))
                }
                // 非表单 body（如 multipart 上传）：只加头，不合并参数。
                else -> Unit
            }
        } else {
            val existingNames = request.url.parameters.names()
            common.forEach { (key, value) ->
                if (!existingNames.contains(key)) {
                    request.url.parameters.append(key, value)
                }
            }
        }
    }
}

class CommonParamsConfig {
    /** 提供当前登录 utoken；未登录时返回空串。 */
    var tokenProvider: () -> String = { "" }

    companion object {
        const val REFERER_URL = "https://www.hpoi.net/android"
        const val USER_AGENT = "okhttp/4.12.0"

        /** Hpoi 2.42 对应客户端构建版本号 (versionCode) */
        const val API_APP_VERSION = "200133"

        /** 模拟现代 Android API Level (Android 14 API 34，对应 Hpoi 2.42 targetSdk) */
        const val API_SYSTEM_VERSION = "34"

        /** 模拟现代机型设备标示 (Galaxy S24 Ultra) */
        const val API_MACHINE = "SM-S9280"
    }
}
