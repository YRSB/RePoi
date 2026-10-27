package com.nobody.repoi.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * 各端 Http 引擎：Android = OkHttp，JVM/Desktop = CIO。
 */
expect fun platformEngine(): HttpClientEngineFactory<*>

/**
 * 全局 Json 配置，对齐老项目 `NetworkModule.provideJson`。
 */
fun appJson(): Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

/**
 * Hpoi 主业务 client：CommonParams 插件 + BODY 日志 + 15s 超时。
 *
 * 对应老项目 `NetworkModule.provideOkHttpClient` + `provideRetrofit` 的组合语义
 * （baseUrl 进 defaultRequest；Wave 3 的 `HpoiApi` 类挂在此 client 上）。
 */
fun createHpoiClient(
    json: Json,
    tokenProvider: () -> String,
    engine: HttpClientEngineFactory<*> = platformEngine()
): HttpClient = HttpClient(engine) {
    applyCommonConfig(json)
    install(CommonParamsPlugin) {
        this.tokenProvider = tokenProvider
    }
    defaultRequest {
        url {
            protocol = URLProtocol.HTTPS
            host = HPOI_HOST
        }
    }
}

/**
 * 图片 client：仅 Referer/UA 头，供 Coil3 fetcher 使用。
 * 对应老项目 `@Named("ImageOkHttpClient")`。
 */
fun createImageClient(
    engine: HttpClientEngineFactory<*> = platformEngine()
): HttpClient = HttpClient(engine) {
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 20_000
        socketTimeoutMillis = 20_000
    }
    defaultRequest {
        headers.append("Referer", CommonParamsConfig.REFERER_URL)
        headers.append("User-Agent", CommonParamsConfig.USER_AGENT)
    }
}

/**
 * ExHobby 独立 client：Cookie + UA 头。
 * 对应老项目 `@Named("ExHobbyOkHttpClient")` + `@Named("ExHobbyRetrofit")`。
 */
fun createExHobbyClient(
    json: Json,
    engine: HttpClientEngineFactory<*> = platformEngine()
): HttpClient = HttpClient(engine) {
    applyCommonConfig(json)
    defaultRequest {
        url {
            protocol = URLProtocol.HTTPS
            host = EXHOBBY_HOST
        }
        headers.append("Cookie", "is18=yes")
        headers.append("User-Agent", "okhttp/4.9.0")
    }
}

private fun HttpClientConfig<*>.applyCommonConfig(json: Json) {
    install(ContentNegotiation) {
        json(json)
    }
    install(Logging) {
        logger = Logger.SIMPLE
        level = LogLevel.BODY
    }
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 15_000
        socketTimeoutMillis = 15_000
    }
    // 对齐 OkHttp retryOnConnectionFailure(true)；Ktor 默认即重试幂等请求，
    // 非幂等 POST 不自动重试，与 OkHttp 默认行为一致。
}

const val HPOI_HOST = "www.hpoi.net.cn"
const val EXHOBBY_HOST = "www.exhobby.net"
