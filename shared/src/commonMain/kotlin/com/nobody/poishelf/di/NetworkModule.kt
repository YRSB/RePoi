package com.nobody.poishelf.di

import co.touchlab.kermit.Logger as KermitLogger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.ktor.KermitKtorLogger
import com.nobody.poishelf.data.remote.api.ExHobbyApi
import com.nobody.poishelf.data.remote.api.HpoiApi
import com.nobody.poishelf.data.remote.interceptor.CommonParams
import com.nobody.poishelf.data.remote.plugin.installCommonParams
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

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
    single {
        val config: HttpClientConfig<*>.() -> Unit = {
            install(Logging) {
                logger = KermitKtorLogger(
                    severity = Severity.Verbose,
                    logger = KermitLogger.withTag("Ktor"),
                )
                level = LogLevel.INFO
                sanitizeHeader { header -> header == HttpHeaders.Authorization }
            }
            install(ContentNegotiation) {
                json(get())
            }
        }
        config
    }
    single(named("hpoi")) {
        HttpClient(OkHttp) {
            get<HttpClientConfig<*>.() -> Unit>()()
            installCommonParams(tokenProvider = { get<com.nobody.poishelf.data.local.TokenManager>().currentToken })
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
            defaultRequest {
                url(CommonParams.BASE_URL)
            }
        }
    }
    single(named("exhobby")) {
        HttpClient(OkHttp) {
            get<HttpClientConfig<*>.() -> Unit>()()
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
            get<HttpClientConfig<*>.() -> Unit>()()
            defaultRequest {
                headers.append("Referer", CommonParams.REFERER_URL)
                headers.append("User-Agent", CommonParams.USER_AGENT)
            }
        }
    }
    single { HpoiApi(get(named("hpoi")), get()) }
    single { ExHobbyApi(get(named("exhobby")), get()) }
    single { com.nobody.poishelf.data.remote.interceptor.CommonParamsInterceptor(get()) }
}
