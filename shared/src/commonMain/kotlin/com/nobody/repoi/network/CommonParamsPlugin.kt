package com.nobody.repoi.network

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.setBody
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.parameters

/**
 * Ktor client 插件：自动注入 Referer/User-Agent 头以及 Hpoi API 公共请求参数。
 *
 * 复刻老项目 `CommonParamsInterceptor`（OkHttp）语义：
 * - 每个请求附加公共参数 utoken/platform/appVersion/channel/machine/system（已存在则不覆盖）；
 * - POST + FormBody：合并进表单体；
 * - 空 POST（无 body 或空 Form）：创建仅含公共参数的表单体；
 * - 其他方法（GET 等）：拼到 URL query；
 * - 非表单 POST body（如 multipart 上传）：只加头，不碰 body。
 */
val CommonParamsPlugin = createClientPlugin("CommonParamsPlugin", ::CommonParamsConfig) {
    val tokenProvider = pluginConfig.tokenProvider

    onRequest { request, _ ->
        request.headers.append("Referer", CommonParamsConfig.REFERER_URL)
        request.headers.append("User-Agent", CommonParamsConfig.USER_AGENT)

        val commonParams = mapOf(
            "utoken" to tokenProvider(),
            "platform" to "android",
            "appVersion" to CommonParamsConfig.API_APP_VERSION,
            "channel" to "official",
            "machine" to CommonParamsConfig.API_MACHINE,
            "system" to CommonParamsConfig.API_SYSTEM_VERSION
        )

        if (request.method == HttpMethod.Post) {
            when (val body = request.body) {
                is FormDataContent -> {
                    val existingNames = body.formData.names()
                    val merged = Parameters.build {
                        appendAll(body.formData)
                        for ((key, value) in commonParams) {
                            if (!existingNames.contains(key)) append(key, value)
                        }
                    }
                    request.setBody(FormDataContent(merged))
                }
                else -> {
                    // 非表单 body（如 multipart 上传）只加头，不合并参数。
                    val isEmpty = body is io.ktor.http.content.OutgoingContent.NoContent
                    if (isEmpty) {
                        val form = Parameters.build {
                            for ((key, value) in commonParams) append(key, value)
                        }
                        request.setBody(FormDataContent(form))
                    }
                }
            }
        } else {
            val existingNames = request.url.parameters.names()
            commonParams.forEach { (key, value) ->
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
