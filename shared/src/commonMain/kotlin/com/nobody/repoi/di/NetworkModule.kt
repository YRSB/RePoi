package com.nobody.repoi.di

import co.touchlab.kermit.Logger as KermitLogger
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.remote.api.ExHobbyApi
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.interceptor.CommonParams
import com.nobody.repoi.data.remote.plugin.installCommonParams
import com.nobody.repoi.util.AppLogger
import com.nobody.repoi.util.KtorLogFormatter
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * 为业务 HttpClientConfig 配置统一的日志系统与 ContentNegotiation。
 *
 * 日志级别配置为 [LogLevel.BODY]，配合 [KtorLogFormatter] 自动过滤冗余 HTTP Headers，
 * 提炼精简的请求行、响应行及 JSON 响应体，记录至 [AppLogger] 环形缓冲中。
 */
private fun HttpClientConfig<*>.applyCommonLoggingAndNegotiation(json: Json) {
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) {
                val formatted = KtorLogFormatter.format(message) ?: return
                KermitLogger.withTag("Ktor").v { formatted }
                if (formatted.startsWith("<-- 4") || formatted.startsWith("<-- 5") || formatted.contains("exception", ignoreCase = true)) {
                    AppLogger.w("Ktor", formatted)
                } else {
                    AppLogger.d("Ktor", formatted)
                }
            }
        }
        level = LogLevel.BODY
        sanitizeHeader { header -> header == HttpHeaders.Authorization }
    }
    install(ContentNegotiation) {
        json(json)
    }
}

/**
 * 网络模块（Ktor 版 `NetworkModule`）。
 *
 * - `Json` 保留 PoiShelf 三元组（ignoreUnknownKeys/coerceInputValues/isLenient）。
 * - 4 命名 clients：`hpoi`（业务）、`exhobby`（Cookie+UA）、`download`（裸，60s read）、
 *   `coil`（Referer+UA 图片头）。
 * - 发包公共参数经 `CommonParamsPlugin`（HttpSend）合并；ContentNegotiation 复用同一 Json。
 */
val networkModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }
    }
    single(named("hpoi")) {
        val json = get<Json>()
        HttpClient(OkHttp) {
            applyCommonLoggingAndNegotiation(json)
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
            defaultRequest {
                url(CommonParams.BASE_URL)
            }
        }.installCommonParams(tokenProvider = { get<TokenManager>().currentToken })
    }
    single(named("exhobby")) {
        val json = get<Json>()
        HttpClient(OkHttp) {
            applyCommonLoggingAndNegotiation(json)
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
            defaultRequest {
                url(CommonParams.EXHOBBY_BASE_URL)
                headers.append("Cookie", CommonParams.EXHOBBY_COOKIE)
                headers.append("User-Agent", CommonParams.EXHOBBY_USER_AGENT)
            }
        }
    }
    single(named("download")) {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
            }
        }
    }
    single(named("coil")) {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
            defaultRequest {
                headers.append("Referer", CommonParams.REFERER_URL)
                headers.append("User-Agent", CommonParams.USER_AGENT)
            }
        }
    }
    single { HpoiApi(get(named("hpoi")), get()) }
    single { ExHobbyApi(get(named("exhobby")), get()) }
    single { com.nobody.repoi.data.remote.interceptor.CommonParamsInterceptor(tokenProvider = { get<TokenManager>().currentToken }) }
}
