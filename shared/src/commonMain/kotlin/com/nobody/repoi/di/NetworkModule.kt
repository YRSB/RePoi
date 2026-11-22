package com.nobody.repoi.di

import co.touchlab.kermit.Logger as KermitLogger
import com.nobody.repoi.data.remote.api.ExHobbyApi
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.interceptor.CommonParams
import com.nobody.repoi.data.remote.plugin.installCommonParams
import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.util.AppLogger
import com.nobody.repoi.util.KtorLogFormatter
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
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

private fun HttpClientConfig<*>.applyCommonLoggingAndNegotiation(json: Json) {
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) {
                val formatted = KtorLogFormatter.format(message) ?: return
                KermitLogger.withTag("Ktor").v { formatted }
                if (formatted.startsWith("<-- 4") || formatted.startsWith("<-- 5") || formatted.contains("failed with exception", ignoreCase = true)) {
                    AppLogger.w("Ktor", formatted)
                } else {
                    AppLogger.d("Ktor", formatted)
                }
            }
        }
        level = LogLevel.INFO
        sanitizeHeader { header -> header == HttpHeaders.Authorization }
    }
    install(ContentNegotiation) {
        json(json)
    }
}

val networkModule = module {
    single<HttpClientEngine> {
        createSharedHttpClientEngine()
    }
    single {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }
    }
    single(named("hpoi")) {
        val json = get<Json>()
        val engine = get<HttpClientEngine>()
        HttpClient(engine) {
            applyCommonLoggingAndNegotiation(json)
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
            defaultRequest {
                url(CommonParams.BASE_URL)
            }
        }.installCommonParams(tokenProvider = { get<UserSession>().currentToken })
    }
    single(named("exhobby")) {
        val json = get<Json>()
        val engine = get<HttpClientEngine>()
        HttpClient(engine) {
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
        val engine = get<HttpClientEngine>()
        HttpClient(engine) {
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
            }
        }
    }
    single(named("coil")) {

        val engine = get<HttpClientEngine>()
        HttpClient(engine) {
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
    single { HpoiApi(get(named("hpoi")), get()) { token -> get<UserSession>().notifyTokenExpired(token) } }
    single { ExHobbyApi(get(named("exhobby")), get()) }
    single { com.nobody.repoi.data.remote.interceptor.CommonParamsInterceptor(tokenProvider = { get<UserSession>().currentToken }) }
}

expect fun createSharedHttpClientEngine(): HttpClientEngine
